package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.Avatar;
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

    @Override
    public SplitPane getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        UrlImageViewTableCell.loadProgressLabel = avatarLoadProgressLabel;
        ViewHelper.buildAvatarTableView(avatarTableView, avatars);
        ViewHelper.buildAvatarAssignmentTableView(avatarAssignmentTableView, avatarAssignments, this::removeAvatarFromPlayer);

        editAvatarButton.disableProperty().bind(avatarTableView.getSelectionModel().selectedItemProperty().isNull());
        deleteAvatarButton.disableProperty().bind(avatarTableView.getSelectionModel().selectedItemProperty().isNull());

        avatarTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            avatarAssignments.clear();
            Optional.ofNullable(newValue).ifPresent(avatar -> {
                avatarAssignments.addAll(avatar.getAssignments());
                loadAvatarImageAsync(avatar);
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
        UrlImageViewTableCell.totalRequested = 0;
        UrlImageViewTableCell.totalCompleted = 0;
        UrlImageViewTableCell.updateProgressLabel();
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
            avatars.setAll(loadAvatarsTask.getValue());
            avatarTableView.getSortOrder().clear();
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
        avatarAssignments.clear();
        avatarTableView.getSortOrder().clear();
        avatarTableView.setPlaceholder(new ProgressIndicator());

        String pattern = searchAvatarsTextField.getText();
        boolean byId = searchAvatarsByIdRadioButton.isSelected();
        boolean byTooltip = searchAvatarsByTooltipRadioButton.isSelected();
        boolean byUser = searchAvatarsByAssignedUserRadioButton.isSelected();

        Task<List<AvatarFX>> searchTask = new Task<>() {
            @Override
            protected List<AvatarFX> call() {
                List<Avatar> result;
                if (byId) {
                    result = avatarService.findAvatarsById(pattern);
                } else if (byTooltip) {
                    result = avatarService.findAvatarsByTooltip(pattern);
                } else if (byUser) {
                    result = avatarService.findAvatarsByAssignedUser(pattern);
                } else {
                    result = avatarService.getAllAvatars();
                }
                return avatarMapper.map(result);
            }
        };

        searchTask.setOnSucceeded(event -> {
            avatars.setAll(searchTask.getValue());
            avatarTableView.getSortOrder().clear();
        });

        searchTask.setOnFailed(e -> {
            Throwable t = searchTask.getException();
            log.error("Avatar search failed", t);
            ViewHelper.errorDialog("Error", "Avatar search failed: " + t.getMessage());
        });

        new Thread(searchTask, "SearchAvatarsThread").start();
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
        Image cached = AvatarCache.getInstance().get(cacheKey);
        if (cached != null) {
            avatar.setImage(cached);
            return;
        }

        if (!UrlImageViewTableCell.loadingUrls.add(cacheKey)) return;

        UrlImageViewTableCell.imageLoadExecutor.submit(() -> {
            try {
                Image img = new Image(avatar.getUrl(), true);
                AvatarCache.getInstance().put(cacheKey, img);
                img.progressProperty().addListener((obs, oldProg, newProg) -> {
                    if (newProg.doubleValue() >= 1.0) {
                        Platform.runLater(() -> avatar.setImage(img));
                    }
                });
            } catch (Exception ignored) {
            } finally {
                UrlImageViewTableCell.loadingUrls.remove(cacheKey);
            }
        });
    }

}
