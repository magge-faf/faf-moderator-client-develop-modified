package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.Avatar;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.AvatarService;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.mapstruct.AvatarMapper;
import com.faforever.moderatorclient.ui.AvatarInfoController;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.UiService;
import com.faforever.moderatorclient.ui.ViewHelper;
import com.faforever.moderatorclient.ui.caches.AvatarCache;
import com.faforever.moderatorclient.ui.data_cells.UrlImageViewTableCell;
import com.faforever.moderatorclient.ui.domain.AvatarAssignmentFX;
import com.faforever.moderatorclient.ui.domain.AvatarFX;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarsController implements Controller<SplitPane> {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UiService uiService;
    private final AvatarService avatarService;
    private final AvatarMapper avatarMapper;
    private final ObservableList<AvatarFX> avatars = FXCollections.observableArrayList();
    private final ObservableList<AvatarAssignmentFX> avatarAssignments = FXCollections.observableArrayList();

    public TableView<AvatarFX> avatarTableView;
    public TableView<AvatarAssignmentFX> avatarAssignmentTableView;
    public SplitPane root;
    public RadioButton showAllAvatarsRadioButton;
    public RadioButton searchAvatarsByIdRadioButton;
    public RadioButton searchAvatarsByTooltipRadioButton;
    public RadioButton searchAvatarsByAssignedUserRadioButton;
    public TextField searchAvatarsTextField;

    public Button editAvatarButton;
    public Button deleteAvatarButton;

    public Label avatarLoadProgressLabel;

    @Autowired
    private final EnvironmentProperties environmentProperties;

    @Autowired
    private final FafApiCommunicationService fafApiCommunicationService;

    @Override
    public SplitPane getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        avatarTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            avatarAssignments.clear();
            Optional.ofNullable(newValue).ifPresent(avatar -> {
                avatarAssignments.addAll(avatar.getAssignments());

                // Load avatar preview immediately when row is selected
                loadAvatarImageAsync(avatar);
            });

            if (newValue != null) {
                applicationEventPublisher.publishEvent(newValue);
            }
        });

        UrlImageViewTableCell.loadProgressLabel = avatarLoadProgressLabel;
        ViewHelper.buildAvatarTableView(avatarTableView, avatars);
        ViewHelper.buildAvatarAssignmentTableView(avatarAssignmentTableView, avatarAssignments, this::removeAvatarFromPlayer);

        editAvatarButton.disableProperty().bind(avatarTableView.getSelectionModel().selectedItemProperty().isNull());
        deleteAvatarButton.disableProperty().bind(avatarTableView.getSelectionModel().selectedItemProperty().isNull());

        avatarTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            avatarAssignments.clear();
            Optional.ofNullable(newValue).ifPresent(avatar -> {
                avatarAssignments.addAll(avatar.getAssignments());
            });

            if (newValue != null) {
                applicationEventPublisher.publishEvent(newValue);
            }
        });
    }

    private void removeAvatarFromPlayer(AvatarAssignmentFX a) {
        AvatarAssignmentFX avatarAssignmentFX = a;
        Assert.notNull(avatarAssignmentFX, "You need to select a user's avatar.");

        avatarService.removeAvatarAssignment(avatarAssignmentFX);
        avatarAssignmentTableView.getItems().remove(avatarAssignmentFX);
        avatarAssignmentFX.getPlayer().getAvatarAssignments().remove(avatarAssignmentFX);
        avatarAssignmentFX.getAvatar().setAssignments(avatarAssignmentTableView.getItems());
        avatarAssignmentTableView.refresh();
        refreshAvatars();
        Optional.ofNullable(a.getAvatar()).ifPresent(avatar -> avatarAssignments.addAll(a.getAvatar().getAssignments()));
    }

    public void refreshAvatars() {
        avatars.clear();
        avatarAssignments.clear();
        avatarTableView.setPlaceholder(new ProgressIndicator());
        Task<List<AvatarFX>> loadAvatarsTask = new Task<>() {
            @Override
            protected List<AvatarFX> call() {
                int page = 1;
                int pageSize = environmentProperties.getMaxResultPageSizeAvatars();
                List<Avatar> currentPage;
                List<AvatarFX> allAvatars = FXCollections.observableArrayList();

                do {
                    currentPage = avatarService.getAllAvatarsPage(page, pageSize);
                    List<AvatarFX> fxPage = avatarMapper.map(currentPage);
                    allAvatars.addAll(fxPage);
                    page++;
                } while (currentPage.size() == pageSize);

                return allAvatars;
            }
        };

        loadAvatarsTask.setOnSucceeded(event -> {
            List<AvatarFX> loadedAvatars = loadAvatarsTask.getValue();
            avatars.setAll(loadedAvatars);
            avatarTableView.getSortOrder().clear();

            // Preload images in background
            for (AvatarFX avatar : loadedAvatars) {
                String url = avatar.getUrl();
                if (url == null || url.isEmpty() || avatar.getImage() != null) continue;

                String cacheKey = url + avatar.getUpdateTime();
                if (AvatarCache.getInstance().containsKey(cacheKey)) {
                    avatar.setImage(AvatarCache.getInstance().get(cacheKey));
                    continue;
                }

                if (UrlImageViewTableCell.loadingUrls.add(cacheKey)) {
                    UrlImageViewTableCell.totalRequested++;
                    UrlImageViewTableCell.updateProgressLabel();

                    UrlImageViewTableCell.runningTasks.put(cacheKey,
                            UrlImageViewTableCell.imageLoadExecutor.submit(() -> {
                                try {
                                    fafApiCommunicationService.checkRateLimit();
                                    Image img = new Image(url, true);
                                    AvatarCache.getInstance().put(cacheKey, img);

                                    img.progressProperty().addListener((obs, oldProg, newProg) -> {
                                        if (newProg.doubleValue() >= 1.0) {
                                            Platform.runLater(() -> {
                                                avatar.setImage(img);
                                                UrlImageViewTableCell.totalCompleted++;
                                                UrlImageViewTableCell.updateProgressLabel();
                                            });
                                        }
                                    });

                                } catch (Exception e) {
                                    log.warn("Failed preloading avatar: {}", url, e);
                                } finally {
                                    UrlImageViewTableCell.loadingUrls.remove(cacheKey);
                                    UrlImageViewTableCell.runningTasks.remove(cacheKey);
                                }
                            })
                    );
                }
            }
        });

        loadAvatarsTask.setOnFailed(e -> {
            Throwable t = loadAvatarsTask.getException();
            log.error("Failed to load avatars", t);
            ViewHelper.errorDialog("Error", "Failed to load avatars: " + t.getMessage());
        });

        new Thread(loadAvatarsTask, "LoadAvatarsThread").start();
    }

    private void openAvatarDialog(AvatarFX avatarFX, boolean isNew) {
        AvatarInfoController avatarInfoController = uiService.loadFxml("ui/avatarInfo.fxml");
        avatarInfoController.setAvatar(avatarFX);

        Stage avatarInfoDialog = new Stage();
        avatarInfoDialog.setTitle(isNew ? "Add new avatar" : "Edit avatar");
        avatarInfoDialog.setScene(new Scene(avatarInfoController.getRoot()));
        avatarInfoDialog.showAndWait();
        refreshAvatars();
    }

    public void onSearchAvatars() {
        avatars.clear();
        avatarTableView.getSortOrder().clear();

        List<Avatar> avatarSearchResult;
        String pattern = searchAvatarsTextField.getText();

        if (searchAvatarsByIdRadioButton.isSelected()) {
            avatarSearchResult = avatarService.findAvatarsById(pattern);
        } else if (searchAvatarsByTooltipRadioButton.isSelected()) {
            avatarSearchResult = avatarService.findAvatarsByTooltip(pattern);
        } else if (searchAvatarsByAssignedUserRadioButton.isSelected()) {
            avatarSearchResult = avatarService.findAvatarsByAssignedUser(pattern);
        } else {
            avatarSearchResult = avatarService.getAllAvatars();
        }
        avatars.addAll(avatarMapper.map(avatarSearchResult));
    }

    public void onAddAvatar() {
        openAvatarDialog(new AvatarFX(), true);
    }

    public void onEditAvatar() {
        AvatarFX avatarFX = avatarTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(avatarFX, "You need to select an avatar first.");

        openAvatarDialog(avatarFX, false);
    }

    public void onDeleteAvatar() {
        AvatarFX avatarFX = avatarTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(avatarFX, "You need to select an avatar first.");

        if (avatarFX.getAssignments().isEmpty()) {
            boolean confirmed = ViewHelper.confirmDialog("Delete avatar " + avatarFX.getTooltip(),
                    "Are you sure that you want to delete this avatar?");

            if (confirmed) {
                avatarService.deleteAvatar(avatarFX.getId());
                avatars.remove(avatarFX);
            }
        } else {
            ViewHelper.errorDialog("Deleting avatar failed", "You can't remove an avatar as long as it has assignments.");
        }
    }

    private void loadAvatarImageAsync(AvatarFX avatar) {
        if (avatar.getUrl() == null || avatar.getUrl().isEmpty() || avatar.getImage() != null) return;

        String cacheKey = avatar.getUrl() + avatar.getUpdateTime();
        if (AvatarCache.getInstance().containsKey(cacheKey)) {
            avatar.setImage(AvatarCache.getInstance().get(cacheKey));
            return;
        }

        Runnable load = () -> {
            try {
                Image img = new Image(avatar.getUrl(), false);
                AvatarCache.getInstance().put(cacheKey, img);
                Platform.runLater(() -> avatar.setImage(img));
            } catch (Exception ignored) {
            }
        };

        Thread t = new Thread(load);
        t.setDaemon(true);
        t.setName("LoadAvatar-" + avatar.getId());
        t.start();
    }

}
