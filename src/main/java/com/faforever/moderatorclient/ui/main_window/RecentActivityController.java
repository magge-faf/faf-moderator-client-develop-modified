package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.MapService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.MapVersionFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.faforever.moderatorclient.ui.domain.TeamkillFX;
import com.faforever.moderatorclient.ui.domain.UniqueIdFx;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
    public CheckBox includeGlobalBannedUserCheckBox;
    public CheckBox excludedItemsCheckBox;
    public TableView<TeamkillFX> teamkillFeedTableView;
    public TableView<MapVersionFX> mapUploadFeedTableView;

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
    private void checkBlacklistedItem(String fileName, List<String> blacklistedItems, String item, PlayerFX account) {
        for (String blacklistedItem : blacklistedItems) {
            if (blacklistedItem.equals(item)) {
                log.debug("[!] " + fileName + " : " + blacklistedItem + " for " + account.getRepresentation());
                suspiciousUserTextArea.setText(suspiciousUserTextArea.getText() + "[!] " + fileName + " : " + blacklistedItem + " for " + account.getRepresentation() + "\n");
            }
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

    private void loadList(File file, List<String> list, List<String> excludedItems) {
        try (Scanner s = new Scanner(file)) {
            while (s.hasNext()) {
                String blacklistedItem = s.next();
                if (!excludedItems.contains(blacklistedItem)) {
                    list.add(blacklistedItem);
                }
            }
        } catch (FileNotFoundException e) {
            log.debug(String.valueOf(e));
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            int lineCount = 0;
            while (line != null) {
                excludedItems.add(line);
                lineCount++;
                line = reader.readLine();
            }
            log.debug("[info] " + file + " loaded. Total items: " + lineCount);
        } catch (IOException e) {
            log.debug(String.valueOf(e));
        }
    }


    public void refresh() {
        teamkills.setAll(userService.findLatestTeamkills());
        teamkillFeedTableView.getSortOrder().clear();
        mapVersions.setAll(mapService.findLatestMapVersions());
        mapUploadFeedTableView.getSortOrder().clear();

        suspiciousUserTextArea.setText("");

        List<String> excludedItems = new ArrayList<>();
        List<String> blacklistedHash = new ArrayList<>();
        List<String> blacklistedIP = new ArrayList<>();
        List<String> blacklistedMemorySN = new ArrayList<>();
        List<String> blacklistedSN = new ArrayList<>();
        List<String> blacklistedUUID = new ArrayList<>();
        List<String> blacklistedVolumeSN = new ArrayList<>();

        File fileExcludedItems = new File("excludedItems" + ".txt");
        File fileBlacklistedHash = new File("BlacklistedHash" + ".txt");
        File fileBlacklistedIP = new File("BlacklistedIP" + ".txt");
        File fileBlacklistedMemorySN = new File("BlacklistedMemorySN" + ".txt");
        File fileBlacklistedSN = new File("BlacklistedSN" + ".txt");
        File fileBlacklistedUUID = new File("BlacklistedUUID" + ".txt");
        File fileBlacklistedVolumeSN = new File("BlacklistedVolumeSN" + ".txt");

        try {
            Scanner s = new Scanner(fileExcludedItems);
            while (s.hasNext()) {
                excludedItems.add(s.nextLine());
            }
            s.close();
            log.debug("[info] " + fileExcludedItems + " loaded.");
        } catch (Exception e) {
            log.debug(String.valueOf(e));
        }

        loadList(fileBlacklistedHash, blacklistedHash, excludedItems);
        loadList(fileBlacklistedIP, blacklistedIP, excludedItems);
        loadList(fileBlacklistedMemorySN, blacklistedMemorySN, excludedItems);
        loadList(fileBlacklistedSN, blacklistedSN, excludedItems);
        loadList(fileBlacklistedUUID, blacklistedUUID, excludedItems);
        loadList(fileBlacklistedVolumeSN, blacklistedVolumeSN, excludedItems);

        users.setAll(userService.findLatestRegistrations());
        userRegistrationFeedTableView.getSortOrder().clear();

        List<PlayerFX> accounts = userService.findLatestRegistrations();

        for (PlayerFX account : accounts) {
            // cycle through players
            Boolean includeBannedUserGlobally = includeGlobalBannedUserCheckBox.isSelected();

            if (includeBannedUserGlobally.equals(account.isBannedGlobally())) {
                ObservableSet<UniqueIdFx> accountUniqueIds = account.getUniqueIds();

                for (UniqueIdFx item : accountUniqueIds) {
                    // cycle through all unique ids
                    checkBlacklistedItem("blacklistedHash", blacklistedHash, item.getHash(), account);
                    checkBlacklistedItem("blacklistedIP", blacklistedIP, account.getRecentIpAddress(), account);
                    checkBlacklistedItem("blacklistedMemorySN", blacklistedMemorySN, item.getMemorySerialNumber(), account);
                    checkBlacklistedItem("blacklistedSN", blacklistedSN, item.getSerialNumber(), account);
                    checkBlacklistedItem("blacklistedUUID", blacklistedUUID, item.getUuid(), account);
                    checkBlacklistedItem("blacklistedVolumeSN", blacklistedVolumeSN, item.getVolumeSerialNumber(), account);
                }
            }
        }
        log.debug("[info] Last 10k users checked.");
    }
}
