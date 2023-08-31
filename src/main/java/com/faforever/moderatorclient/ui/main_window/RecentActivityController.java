package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.MapService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecentActivityController implements Controller<VBox> {
    private final UserService userService;
    private final MapService mapService;
    private final ObservableList<PlayerFX> users = FXCollections.observableArrayList();
    private final ObservableList<TeamkillFX> teamkills = FXCollections.observableArrayList();
    private final ObservableList<MapVersionFX> mapVersions = FXCollections.observableArrayList();
    private final FafApiCommunicationService communicationService;
    private final UiService uiService;
    private final PlatformService platformService;

    public VBox root;
    public TextArea suspiciousUserTextArea;

    public TitledPane userRegistrationFeedPane;
    public TitledPane teamkillFeedPane;
    public TitledPane mapUploadFeedPane;

    public TableView<PlayerFX> userRegistrationFeedTableView;
    public Text excludedItemsText;
    public TableView<TeamkillFX> teamkillFeedTableView;
    public TableView<MapVersionFX> mapUploadFeedTableView;
    public CheckBox includeGlobalBannedUserCheckBox;
    @FXML
    public Button refreshButton;

    @Override public VBox getRoot() {return root;}

    private boolean checkPermissionForTitledPane(String permissionTechnicalName, TitledPane titledPane) {
        if (communicationService.hasPermission(permissionTechnicalName)) {
            titledPane.setDisable(false);
            return true;
        } else {
            titledPane.setDisable(true);
            return false;
        }
    }

    @FXML
    public void initialize() {
        if (checkPermissionForTitledPane(GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS, userRegistrationFeedPane)) {
            ViewHelper.buildUserTableView(platformService, userRegistrationFeedTableView, users, this::addBan,
                    playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), communicationService);
        }

        if (checkPermissionForTitledPane(GroupPermission.ROLE_READ_TEAMKILL_REPORT, teamkillFeedPane)) {
            ViewHelper.buildTeamkillTableView(teamkillFeedTableView, teamkills, true, this::addBan);
        }

        if (checkPermissionForTitledPane(GroupPermission.ROLE_ADMIN_MAP, mapUploadFeedPane)) {
            ViewHelper.buildMapFeedTableView(mapUploadFeedTableView, mapVersions, this::toggleHide);
        }

    }


    private void addBan(PlayerFX playerFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX banInfo = new BanInfoFX();
        banInfo.setPlayer(playerFX);
        banInfoController.setBanInfo(banInfo);
        banInfoController.addPostedListener(banInfoFX -> refresh());
        banInfoController.addRevokedListener(this::refresh);
        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle("Apply new ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoDialog.showAndWait();
    }

    private void toggleHide(MapVersionFX mapVersionFX) {
        mapVersionFX.setHidden(!mapVersionFX.isHidden());
        mapService.patchMapVersion(mapVersionFX);
    }

    private List<String> filterList(List<String> list, List<String> excludedItems) {
        return list.stream()
                .filter(item -> !excludedItems.contains(item))
                .collect(Collectors.toList());
    }

    public void refresh() {
        refreshButton.setText("refresh");
        Platform.runLater(() -> {
            teamkills.setAll(userService.findLatestTeamkills());
            teamkillFeedTableView.getSortOrder().clear();
            mapVersions.setAll(mapService.findLatestMapVersions());
            mapUploadFeedTableView.getSortOrder().clear();

            // Resetting UI element
            suspiciousUserTextArea.setText("");

            // Initialize data structures
            Map<String, List<String>> blacklistedData = new HashMap<>();
            List<String> whitelistUserID = new ArrayList<>();

            // Define FILE_NAMES constants
            final String[] FILE_NAMES = {"excludedItems", "blacklistedHash", "blacklistedIP", "blacklistedMemorySN",
                    "blacklistedSN", "blacklistedUUID", "blacklistedVolumeSN", "whitelistedUserID"};

            // Load data from files
            for (String fileName : FILE_NAMES) {
                File file = new File(CONFIGURATION_FOLDER + File.separator + fileName + ".txt");
                List<String> list = new ArrayList<>();
                if ("whitelistedUserID".equals(fileName)) {
                    loadList(file, whitelistUserID);
                } else {
                    loadList(file, list);
                    blacklistedData.put(fileName, list);
                }
            }

            // Filter blacklisted items
            Map<String, List<String>> filteredBlacklistedData = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : blacklistedData.entrySet()) {
                filteredBlacklistedData.put(entry.getKey(), filterList(entry.getValue(), blacklistedData.get("excludedItems")));
            }

            // Load recent registrations and loop through accounts
            users.setAll(userService.findLatestRegistrations());
            List<PlayerFX> accounts = userService.findLatestRegistrations();
            userRegistrationFeedTableView.getSortOrder().clear();

            for (PlayerFX account : accounts) {
                checkAccountAgainstBlacklists(account, whitelistUserID, filteredBlacklistedData);
            }
            log.debug("[info] Recent users checked.");
        });
    }

    private void loadList(File file, List<String> list) {
        int itemCount = 0;
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                list.add(scanner.nextLine());
                itemCount++;
            }
            log.debug("[info] " + file + " loaded with " + itemCount + " items.");
        } catch (Exception e) {
            log.debug(String.valueOf(e));
        }
    }

    private void checkAccountAgainstBlacklists(PlayerFX account, List<String> whitelistUserID, Map<String, List<String>> filteredBlacklistedData) {
        Boolean includeBannedUserGlobally = includeGlobalBannedUserCheckBox.isSelected();

        if (includeBannedUserGlobally.equals(account.isBannedGlobally())) {
            ObservableSet<UniqueIdFx> accountUniqueIds = account.getUniqueIds();

            // Check against whitelist and ignore userID
            for (String userID : whitelistUserID) {
                if (account.getId().equals(userID)) {
                    log.debug("[whitelisted userID] " + userID);
                    return;
                }
            }

            // Check against blacklists
            for (UniqueIdFx item : accountUniqueIds) {
                checkBlacklistedItem("blacklistedHash", filteredBlacklistedData.get("blacklistedHash"), item.getHash(), account);
                checkBlacklistedItem("blacklistedIP", filteredBlacklistedData.get("blacklistedIP"), account.getRecentIpAddress(), account);
                checkBlacklistedItem("blacklistedMemorySN", filteredBlacklistedData.get("blacklistedMemorySN"), item.getMemorySerialNumber(), account);
                checkBlacklistedItem("blacklistedSN", filteredBlacklistedData.get("blacklistedSN"), item.getSerialNumber(), account);
                checkBlacklistedItem("blacklistedUUID", filteredBlacklistedData.get("blacklistedUUID"), item.getUuid(), account);
                checkBlacklistedItem("blacklistedVolumeSN", filteredBlacklistedData.get("blacklistedVolumeSN"), item.getVolumeSerialNumber(), account);
            }
        }
    }

    private void checkBlacklistedItem(String fileName, List<String> blacklistedItems, String item, PlayerFX account) {
        for (String blacklistedItem : blacklistedItems) {
            if (blacklistedItem.equals(item)) {
                playBlacklistedNotificationSound();
                log.debug("[!] " + fileName + " [" + blacklistedItem + "] for " + account.getRepresentation());
                suspiciousUserTextArea.setText(suspiciousUserTextArea.getText() + "[!] " + fileName + " [" + blacklistedItem + "] for " + account.getRepresentation() + "\n");
            }
        }
    }

    private void playBlacklistedNotificationSound() {
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(AudioSystem.getAudioInputStream(new File(CONFIGURATION_FOLDER + File.separator + "blacklisted_notification_sound.wav")));
                clip.start();
            } catch (Exception e) {
                log.warn("Error in playBlacklistedNotificationSound:", e);
            }
        }
    }
