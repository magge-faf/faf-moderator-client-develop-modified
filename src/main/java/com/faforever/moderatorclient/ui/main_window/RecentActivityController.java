package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.MapService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.config.PreferencesConfig;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javafx.concurrent.Task;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;
import com.faforever.moderatorclient.config.EnvironmentProperties;

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
    private final EnvironmentProperties environmentProperties;

    public VBox root;
    public TextArea suspiciousUserTextArea;

    @FXML private Tab latestRegistrationsTab;
    @FXML private Tab latestTeamkillsTab;
    @FXML private Tab latestMapUploadsTab;

    public TableView<PlayerFX> userRegistrationFeedTableView;
    public Text excludedItemsText;
    public TableView<TeamkillFX> teamkillFeedTableView;
    public TableView<MapVersionFX> mapUploadFeedTableView;
    public CheckBox includeGlobalBannedUserCheckBox;
    @FXML public Button refreshButton;
    public TextArea statsLatestRegistrations;

    @Autowired
    public PreferencesConfig preferencesConfig;
    @FXML public TabPane recentActivityTabPane;

    @Override public VBox getRoot() {return root;}

    private boolean checkPermissionForTab(String permissionTechnicalName, Tab tab) {
        if (communicationService.hasPermission(permissionTechnicalName)) {
            tab.setDisable(false);
            return true;
        } else {
            tab.setDisable(true);
            return false;
        }
    }

    @FXML
    public void initialize() {
        refreshButton.setText("Refresh Latest " + environmentProperties.getMaxPageSize() + " Registrations"); //TODO user input

        if (checkPermissionForTab(GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS, latestRegistrationsTab)) {
            ViewHelper.buildUserTableView(platformService, userRegistrationFeedTableView, users, this::addBan,
                    playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), true, communicationService, preferencesConfig);
        }

        if (checkPermissionForTab(GroupPermission.ROLE_READ_TEAMKILL_REPORT, latestTeamkillsTab)) {
            ViewHelper.buildTeamkillTableView(teamkillFeedTableView, teamkills, true, this::addBan);
        }

        if (checkPermissionForTab(GroupPermission.ROLE_ADMIN_MAP, latestMapUploadsTab)) {
            ViewHelper.buildMapFeedTableView(mapUploadFeedTableView, mapVersions, this::toggleHide);
        }
    }

    private void addBan(PlayerFX playerFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX banInfo = new BanInfoFX();
        banInfo.setPlayer(playerFX);
        banInfoController.setBanInfo(banInfo);
        banInfoController.addPostedListener(banInfoFX -> refreshLatestRegistrationsMaxPageSize());
        banInfoController.addRevokedListener(this::refreshLatestRegistrationsMaxPageSize);
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

    public void refreshLatestRegistrationsMaxPageSize() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Map<String, List<String>> blacklistedData = new HashMap<>();
                List<String> whitelistUserID = new ArrayList<>();
                final String[] FILE_NAMES = {"excludedItems", "blacklistedHash", "blacklistedIP",
                        "blacklistedMemorySN", "blacklistedSN", "blacklistedUUID",
                        "blacklistedVolumeSN", "whitelistedUserID"};

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

                Map<String, List<String>> filteredBlacklistedData = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : blacklistedData.entrySet()) {
                    filteredBlacklistedData.put(entry.getKey(), filterList(entry.getValue(), blacklistedData.get("excludedItems")));
                }

                List<PlayerFX> latestUsers = userService.findLatestRegistrations();

                List<PlayerFX> usersToAdd = new ArrayList<>();
                StringBuilder suspiciousTextBuilder = new StringBuilder();

                int totalRegistrations = 0;
                int totalLogins = 0;
                Map<LocalDate, Integer> registrationsPerDay = new TreeMap<>(Comparator.reverseOrder());
                Map<LocalDate, Integer> loginsPerDay = new TreeMap<>(Comparator.reverseOrder());
                LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));

                for (PlayerFX user : latestUsers) {
                    usersToAdd.add(user);

                    ObservableSet<UniqueIdFx> accountUniqueIds = user.getUniqueIds();
                    boolean whitelisted = whitelistUserID.contains(user.getId());

                    if (!whitelisted) {
                        for (UniqueIdFx item : accountUniqueIds) {
                            checkBlacklistedItemForBuilder("blacklistedHash", filteredBlacklistedData.get("blacklistedHash"), item.getHash(), user, suspiciousTextBuilder);
                            checkBlacklistedItemForBuilder("blacklistedIP", filteredBlacklistedData.get("blacklistedIP"), user.getRecentIpAddress(), user, suspiciousTextBuilder);
                            checkBlacklistedItemForBuilder("blacklistedMemorySN", filteredBlacklistedData.get("blacklistedMemorySN"), item.getMemorySerialNumber(), user, suspiciousTextBuilder);
                            checkBlacklistedItemForBuilder("blacklistedSN", filteredBlacklistedData.get("blacklistedSN"), item.getSerialNumber(), user, suspiciousTextBuilder);
                            checkBlacklistedItemForBuilder("blacklistedUUID", filteredBlacklistedData.get("blacklistedUUID"), item.getUuid(), user, suspiciousTextBuilder);
                            checkBlacklistedItemForBuilder("blacklistedVolumeSN", filteredBlacklistedData.get("blacklistedVolumeSN"), item.getVolumeSerialNumber(), user, suspiciousTextBuilder);
                        }
                    }

                    // Update stats
                    LocalDate registrationDate = user.getCreateTime().toLocalDate();
                    LocalDate lastLoginDate = user.getLastLogin() != null ? user.getLastLogin().toLocalDate() : null;
                    if (!registrationDate.isBefore(currentDate.minusDays(30))) {
                        registrationsPerDay.put(registrationDate, registrationsPerDay.getOrDefault(registrationDate, 0) + 1);
                        totalRegistrations++;
                        if (lastLoginDate != null) {
                            loginsPerDay.put(registrationDate, loginsPerDay.getOrDefault(registrationDate, 0) + 1);
                            totalLogins++;
                        }
                    }
                }

                StringBuilder stats = new StringBuilder("Registrations in the last 30 days (Registrations/Logins):\n");
                for (LocalDate date : registrationsPerDay.keySet()) {
                    int registrations = registrationsPerDay.getOrDefault(date, 0);
                    int logins = loginsPerDay.getOrDefault(date, 0);
                    stats.append(date).append(": ").append(registrations).append(" / ").append(logins).append("\n");
                }
                stats.append("Total registrations: ").append(totalRegistrations).append("\n");
                stats.append("Number of users who have logged in since registration: ").append(totalLogins);

                Platform.runLater(() -> {
                    users.setAll(usersToAdd);
                    suspiciousUserTextArea.setText(suspiciousTextBuilder.toString());
                    statsLatestRegistrations.setText(stats.toString());
                    userRegistrationFeedTableView.getSortOrder().clear();
                });

                return null;
            }
        };

        new Thread(task).start();
    }

    // Helper method to append blacklisted items to the builder
    private void checkBlacklistedItemForBuilder(String fileName, List<String> blacklistedItems, String item, PlayerFX account, StringBuilder builder) {
        for (String blacklistedItem : blacklistedItems) {
            if (blacklistedItem.equals(item)) {
                builder.append("[!] ").append(fileName).append(" [").append(blacklistedItem).append("] for ").append(account.getRepresentation()).append("\n");
            }
        }
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

        ObservableSet<UniqueIdFx> accountUniqueIds = account.getUniqueIds();

        // Check against whitelist and skip logging for whitelisted userIDs
        boolean whitelisted = false;
        for (String userID : whitelistUserID) {
            if (account.getId().equals(userID)) {
                log.debug("[whitelisted userID] " + userID);
                whitelisted = true;
                break;
            }
        }

        // Check against blacklists
        if (!whitelisted) {
            for (UniqueIdFx item : accountUniqueIds) {
                checkBlacklistedItem("blacklistedHash", filteredBlacklistedData.get("blacklistedHash"), item.getHash(), account);
                checkBlacklistedItem("blacklistedIP", filteredBlacklistedData.get("blacklistedIP"), account.getRecentIpAddress(), account);
                checkBlacklistedItem("blacklistedMemorySN", filteredBlacklistedData.get("blacklistedMemorySN"), item.getMemorySerialNumber(), account);
                checkBlacklistedItem("blacklistedSN", filteredBlacklistedData.get("blacklistedSN"), item.getSerialNumber(), account);
                checkBlacklistedItem("blacklistedUUID", filteredBlacklistedData.get("blacklistedUUID"), item.getUuid(), account);
                checkBlacklistedItem("blacklistedVolumeSN", filteredBlacklistedData.get("blacklistedVolumeSN"), item.getVolumeSerialNumber(), account);
            }
        }

        // Always add the account to the users list
        Platform.runLater(() -> {
            if (!users.contains(account)) {
                users.add(account);
            }
        });
    }

    private void checkBlacklistedItem(String fileName, List<String> blacklistedItems, String item, PlayerFX account) {
        for (String blacklistedItem : blacklistedItems) {
            if (blacklistedItem.equals(item)) {
                log.debug("[!] {} [{}] for {}", fileName, blacklistedItem, account.getRepresentation());
                suspiciousUserTextArea.setText(suspiciousUserTextArea.getText() + "[!] " + fileName + " [" + blacklistedItem + "] for " + account.getRepresentation() + "\n");
            }
        }
    }
}