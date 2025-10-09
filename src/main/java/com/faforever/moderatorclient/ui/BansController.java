package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.BanDurationType;
import com.faforever.commons.api.dto.BanStatus;
import com.faforever.moderatorclient.api.domain.BanService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class BansController implements Controller<HBox> {
    private final UiService uiService;
    private final BanService banService;
    public HBox root;
    public ToggleGroup filterGroup;
    public TextField filter;
    public RadioButton playerRadioButton;
    public RadioButton banIdRadioButton;
    public TableView<BanInfoFX> banTableView;
    public CheckBox onlyActiveCheckBox;
    public Button editBanButton;
    private FilteredList<BanInfoFX> filteredList;
    private ObservableList<BanInfoFX> itemList;
    private boolean inSearchMode = false;
    private final LocalPreferences localPreferences;

    @FXML
    private VBox permSyncProgressContainer;
    @FXML
    private ProgressIndicator permSyncProgressIndicator;
    @FXML
    private Label permSyncProgressLabel;
    @FXML
    private VBox tempSyncProgressContainer;
    @FXML
    private ProgressIndicator tempSyncProgressIndicator;
    @FXML
    private Label tempSyncProgressLabel;
    @FXML
    private Label permBanCountLabel;
    @FXML
    private Label tempBanCountLabel;

    @Override
    public HBox getRoot() {
        return root;
    }

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
    public Path PATH_TEMP_BANNED_USERS_JSON = Paths.get("data", "temporary_banned_users_synced.json");
    public Path PATH_PERM_BANNED_USERS_JSON = Paths.get("data", "permanent_banned_users_synced.json");

    @FXML
    public void initialize() {
        itemList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(itemList);
        SortedList<BanInfoFX> sortedItemList = new SortedList<>(filteredList);
        sortedItemList.comparatorProperty().bind(banTableView.comparatorProperty());
        ViewHelper.buildBanTableView(banTableView, sortedItemList, true, localPreferences );
        playerRadioButton.setUserData((Supplier<List<BanInfoFX>>) () -> banService.getBanInfoByBannedPlayerNameContains(filter.getText()));
        banIdRadioButton.setUserData((Supplier<List<BanInfoFX>>) () -> Collections.singletonList(banService.getBanInfoById(filter.getText())));
        editBanButton.disableProperty().bind(banTableView.getSelectionModel().selectedItemProperty().isNull());
        InvalidationListener onlyActiveBansChangeListener = (observable) ->
                filteredList.setPredicate(banInfoFX -> !onlyActiveCheckBox.isSelected() || banInfoFX.getBanStatus() == BanStatus.BANNED);
        onlyActiveCheckBox.selectedProperty().addListener(onlyActiveBansChangeListener);
        onlyActiveBansChangeListener.invalidated(onlyActiveCheckBox.selectedProperty());
        updateBanCounts();
    }

    private void updateBanCounts() {
        permBanCountLabel.setText("(" + loadExistingBannedUserIds(PATH_PERM_BANNED_USERS_JSON).size() + ")");
        tempBanCountLabel.setText("(" + loadExistingBannedUserIds(PATH_TEMP_BANNED_USERS_JSON).size() + ")");
    }

    public void onRefreshLatestBans() {
        banService.getLatestBans().thenAccept(banInfoFXES -> Platform.runLater(() -> {
            itemList.setAll(banInfoFXES);
            updateBanCounts();
        })).exceptionally(throwable -> {
            log.error("error loading bans", throwable);
            return null;
        });
        filter.clear();
        inSearchMode = false;
    }

    public void editBan() {
        BanInfoFX selectedItem = banTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            log.info("Could not delete ban, there was no message selected");
            return;
        }
        openBanDialog(selectedItem, false);
    }

    private void openBanDialog(BanInfoFX banInfoFX, boolean isNew) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        banInfoController.setBanInfo(banInfoFX);
        banInfoController.addPostedListener(banInfoFX1 -> {
            if (inSearchMode) {
                onSearch();
                return;
            }
            onRefreshLatestBans();
        });

        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle(isNew ? "Apply new ban" : "Edit ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoDialog.showAndWait();
    }

    public void addBan() {
        openBanDialog(new BanInfoFX(), true);
    }

    public void onSearch() {
        List<BanInfoFX> banInfoFXES = ((Supplier<List<BanInfoFX>>) filterGroup.getSelectedToggle().getUserData()).get();
        itemList.setAll(banInfoFXES);
        inSearchMode = true;
    }

    public void syncPermBannedUsersJson() {
        if (permSyncProgressContainer == null || permSyncProgressIndicator == null || permSyncProgressLabel == null) {
            log.debug("FXML components not loaded: permSyncProgress* is null.");
            return;
        }

        permSyncProgressContainer.setVisible(true);
        permSyncProgressContainer.setManaged(true);
        permSyncProgressIndicator.setVisible(true);
        permSyncProgressIndicator.setManaged(true);
        permSyncProgressLabel.setVisible(true);
        permSyncProgressLabel.setManaged(true);

        startSyncTask(
                PATH_PERM_BANNED_USERS_JSON,
                BanDurationType.PERMANENT,
                permSyncProgressContainer,
                permSyncProgressIndicator,
                permSyncProgressLabel,
                "permSyncProgress"
        );
    }

    public void syncTempBannedUsersJson() {
        if (tempSyncProgressContainer == null || tempSyncProgressIndicator == null || tempSyncProgressLabel == null) {
            log.debug("FXML components not loaded: tempSyncProgress* is null.");
            return;
        }

        tempSyncProgressContainer.setVisible(true);
        tempSyncProgressContainer.setManaged(true);
        tempSyncProgressIndicator.setVisible(true);
        tempSyncProgressIndicator.setManaged(true);
        tempSyncProgressLabel.setVisible(true);
        tempSyncProgressLabel.setManaged(true);

        startSyncTask(
                PATH_TEMP_BANNED_USERS_JSON,
                BanDurationType.TEMPORARY,
                tempSyncProgressContainer,
                tempSyncProgressIndicator,
                tempSyncProgressLabel,
                "tempSyncProgress"
        );
    }

    private void startSyncTask(Path destinationPath, BanDurationType banDurationType, VBox progressContainer, ProgressIndicator progressIndicator, Label progressLabel, String sourceEvent) {
        BansController thisController = this;

        Task<Boolean> syncTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    List<BanInfoFX> bans = banService.getLatestBans().get();

                    List<BanInfoFX> currentBansOfType = bans.stream()
                            .filter(ban -> ban.getBanStatus() == BanStatus.BANNED && ban.getDuration() == banDurationType)
                            .toList();

                    Set<String> existingBannedUserIds = thisController.loadExistingBannedUserIds(destinationPath);
                    Set<String> currentBannedUserIds = currentBansOfType.stream()
                            .map(ban -> String.valueOf(ban.getPlayer().getId()))
                            .collect(Collectors.toSet());

                    File bannedUsersFile = destinationPath.toFile();

                    if (bannedUsersFile.exists()) {
                        List<Map<String, Object>> existingRawData = objectMapper.readValue(bannedUsersFile, new TypeReference<>() {
                        });
                        List<Map<String, Object>> updatedData = existingRawData.stream()
                                .filter(userData -> {
                                    if (userData.containsKey("userInfo")) {
                                        Map<String, Object> userInfo = (Map<String, Object>) userData.get("userInfo");
                                        return userInfo.containsKey("userId") && currentBannedUserIds.contains(String.valueOf(userInfo.get("userId")));
                                    }
                                    return false;
                                })
                                .toList();
                        objectMapper.writeValue(bannedUsersFile, updatedData);
                        existingBannedUserIds = thisController.loadExistingBannedUserIds(destinationPath);
                    }

                    int totalBans = currentBansOfType.size();
                    int processedCount = 0;

                    for (BanInfoFX bannedUser : currentBansOfType) {
                        String userIdToCheck = String.valueOf(bannedUser.getPlayer().getId());

                        if (existingBannedUserIds.contains(userIdToCheck)) {
                            processedCount++;
                            updateProgress(processedCount, totalBans);
                            updateMessage(String.format("%d/%d (%s)", processedCount, totalBans, banDurationType.toString().toLowerCase()));
                            continue;
                        }

                        log.debug("Syncing ({}/{} - {} remaining): {}", processedCount + 1, totalBans, banDurationType, bannedUser.getPlayer().getRepresentation());
                        List<PlayerFX> bannedUserListResult = userService.findUsersByAttribute("id", bannedUser.getPlayer().getId());
                        if (!bannedUserListResult.isEmpty()) {
                            PlayerFX bannedUserPlayerFX = bannedUserListResult.getFirst();
                            ViewHelper.saveUserToJsonFile(bannedUserPlayerFX, destinationPath, sourceEvent );
                            existingBannedUserIds.add(userIdToCheck);
                        } else {
                            log.warn("Could not find player with ID {} for ban {}", bannedUser.getPlayer().getId(), bannedUser.getId());
                        }
                        processedCount++;
                        updateProgress(processedCount, totalBans);
                        updateMessage(String.format("%d/%d (%s)", processedCount, totalBans, banDurationType.toString().toLowerCase()));
                    }
                    return true;
                } catch (InterruptedException e) {
                    log.error("Sync task interrupted ({})", banDurationType, e);
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (ExecutionException e) {
                    log.error("Error while syncing banned users ({})", banDurationType, e);
                    throw e;
                } catch (IOException e) {
                    log.error("Error while cleaning/writing banned users JSON ({})", banDurationType, e);
                    throw e;
                }
            }
        };

        syncTask.setOnSucceeded(workerStateEvent -> {
            log.info("Successfully synced {} banned users.", banDurationType.toString().toLowerCase());
            progressContainer.setVisible(false);
            progressContainer.setManaged(false);
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
            progressLabel.setVisible(false);
            progressLabel.setVisible(false);
            updateBanCounts();
        });

        syncTask.setOnFailed(workerStateEvent -> {
            Throwable ex = syncTask.getException();
            log.error("Failed to sync {} banned users.", banDurationType.toString().toLowerCase(), ex);
            progressContainer.setVisible(false);
            progressContainer.setManaged(false);
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
            progressLabel.setVisible(false);
            progressLabel.setVisible(false);
        });

        progressIndicator.progressProperty().bind(syncTask.progressProperty());
        progressLabel.textProperty().bind(syncTask.messageProperty());

        Thread thread = new Thread(syncTask);
        thread.setDaemon(true);
        thread.start();
    }

    public Set<String> loadExistingBannedUserIds(Path filePath) {
        Set<String> userIds = new HashSet<>();
        File bannedUsersFile = filePath.toFile();

        if (bannedUsersFile.exists()) {
            try {
                List<Map<String, Object>> bannedUsersData = objectMapper.readValue(bannedUsersFile,
                        new TypeReference<>() {});

                for (Map<String, Object> userData : bannedUsersData) {
                    if (userData.containsKey("userInfo")) {
                        Map<String, Object> userInfo = (Map<String, Object>) userData.get("userInfo");
                        if (userInfo.containsKey("userId")) {
                            userIds.add(String.valueOf(userInfo.get("userId")));
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error while loading banned users JSON from {}", filePath, e);
            }
        }
        return userIds;
    }
}