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
import java.io.File;
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
    public TextArea SuspiciousUserTextArea;

    public TitledPane userRegistrationFeedPane;
    public TitledPane teamkillFeedPane;
    public TitledPane mapUploadFeedPane;

    public TableView<PlayerFX> userRegistrationFeedTableView;
    public CheckBox IncludeGlobalBannedUserCheckBox;
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

    public void refresh() {
        teamkills.setAll(userService.findLatestTeamkills());
        teamkillFeedTableView.getSortOrder().clear();
        mapVersions.setAll(mapService.findLatestMapVersions());
        mapUploadFeedTableView.getSortOrder().clear();

        SuspiciousUserTextArea.setText("");

        ArrayList<String> excludedItems = new ArrayList<>();
        ArrayList<String> BlacklistedHash = new ArrayList<>();
        ArrayList<String> BlacklistedIP = new ArrayList<>();
        ArrayList<String> BlacklistedMemorySN = new ArrayList<>();
        ArrayList<String> BlacklistedSN = new ArrayList<>();
        ArrayList<String> BlacklistedUUID = new ArrayList<>();
        ArrayList<String> BlacklistedVolumeSN = new ArrayList<>();

        File FileExcludedItems = new File("excludedItems" + ".txt");
        File FileBlacklistedHash = new File("BlacklistedHash" + ".txt");
        File FileBlacklistedIP = new File("BlacklistedIP" + ".txt");
        File FileBlacklistedMemorySN = new File("BlacklistedMemorySN" + ".txt");
        File FileBlacklistedSN = new File("BlacklistedSN" + ".txt");
        File FileBlacklistedUUID = new File("BlacklistedUUID" + ".txt");
        File FileBlacklistedVolumeSN = new File("BlacklistedVolumeSN" + ".txt");

        try{

            Scanner s = new Scanner(FileExcludedItems);
            while (s.hasNext()){excludedItems.add(s.next());}
            s.close();
            log.debug("[info] " + FileExcludedItems + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        try{
            Scanner s = new Scanner(FileBlacklistedHash);
            while (s.hasNext()){
                String blacklistedItem = s.next();
                for (String item : excludedItems) {
                    if (blacklistedItem.equals(item)) {
                        blacklistedItem = "";
                        break;
                    }
                }
                BlacklistedHash.add(blacklistedItem);
            }
            s.close();
            log.debug("[info] " + FileBlacklistedHash + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        try{
            Scanner s = new Scanner(FileBlacklistedIP);
            while (s.hasNext()){
                String blacklistedItem = s.next();
                for (String item : excludedItems) {
                    if (blacklistedItem.equals(item)) {
                        blacklistedItem = "";
                        break;
                    }
                }
                BlacklistedIP.add(blacklistedItem);}
            s.close();
            log.debug("[info] " + FileBlacklistedIP + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        try{
            Scanner s = new Scanner(FileBlacklistedMemorySN);
            while (s.hasNext()){
                String blacklistedItem = s.next();
                for (String item : excludedItems) {
                    if (blacklistedItem.equals(item)) {
                        blacklistedItem = "";
                        break;
                    }
                }
                BlacklistedMemorySN.add(blacklistedItem);}
            s.close();
            log.debug("[info] " + FileBlacklistedMemorySN + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        try{
            Scanner s = new Scanner(FileBlacklistedSN);
            while (s.hasNext()){
                String blacklistedItem = s.next();
                for (String item : excludedItems) {
                    if (blacklistedItem.equals(item)) {
                        blacklistedItem = "";
                        break;
                    }
                }
                BlacklistedSN.add(blacklistedItem);}
            s.close();
            log.debug("[info] " + FileBlacklistedSN + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        try{
            Scanner s = new Scanner(FileBlacklistedUUID);
            while (s.hasNext()){
                String blacklistedItem = s.next();
                for (String item : excludedItems) {
                    if (blacklistedItem.equals(item)) {
                        blacklistedItem = "";
                        break;
                    }
                }
                BlacklistedUUID.add(blacklistedItem);}
            s.close();
            log.debug("[info] " + FileBlacklistedUUID + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        try{
            Scanner s = new Scanner(FileBlacklistedVolumeSN);
            while (s.hasNext()){
                String blacklistedItem = s.next();
                for (String item : excludedItems) {
                    if (blacklistedItem.equals(item)) {
                        blacklistedItem = "";
                        break;
                    }
                }
                BlacklistedVolumeSN.add(blacklistedItem);}
            s.close();
            log.debug("[info] " + FileBlacklistedVolumeSN + " loaded.");
        } catch (Exception e) {log.debug(String.valueOf(e));}

        users.setAll(userService.findLatestRegistrations());
        userRegistrationFeedTableView.getSortOrder().clear();

        List<PlayerFX> accounts = userService.findLatestRegistrations();

        for(PlayerFX account: accounts) {
            // cycle through players
            Boolean includeBannedUserGlobally = IncludeGlobalBannedUserCheckBox.isSelected();

            if (includeBannedUserGlobally.equals(account.isBannedGlobally())) {
                ObservableSet<UniqueIdFx> accountUniqueIds = account.getUniqueIds();

                for (UniqueIdFx item : accountUniqueIds) {
                    // cycle through all unique ids

                    for (String blacklistedItem : BlacklistedHash) {
                        if (blacklistedItem.equals(item.getHash())) {

                            log.debug("[!] Blacklisted Hash found: " + blacklistedItem + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted Hash found: " + blacklistedItem + " for " + account.getRepresentation() + "\n");
                        }
                    }

                    for (String blacklistedItem : BlacklistedIP) {
                        if (blacklistedItem.equals(account.getRecentIpAddress())) {
                            log.debug("[!] Blacklisted IP found: " + blacklistedItem + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted IP found: " + blacklistedItem + " for " + account.getRepresentation() + "\n");
                        }
                    }

                    for (String blacklistedItem : BlacklistedMemorySN) {
                        if (blacklistedItem.equals(item.getMemorySerialNumber())) {
                            log.debug("[!] Blacklisted Memory SN found: " + blacklistedItem + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted Memory SN found: " + blacklistedItem + " for " + account.getRepresentation() + "\n");
                        }
                    }

                    for (String blacklistedItem : BlacklistedSN) {
                        if (blacklistedItem.equals(item.getSerialNumber())) {
                            log.debug("[!] Blacklisted Serial Number found: " + blacklistedItem + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted Serial Number found: " + blacklistedItem + " for " + account.getRepresentation() + "\n");
                        }
                    }

                    for (String blacklistedItem : BlacklistedUUID) {
                        if (blacklistedItem.equals(item.getUuid())) {
                            log.debug("[!] Blacklisted UUID found: " + blacklistedItem + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted UUID found: " + blacklistedItem + " for " + account.getRepresentation() + "\n");
                        }
                    }

                    for (String blacklistedItem : BlacklistedVolumeSN) {
                        if (blacklistedItem.equals(item.getVolumeSerialNumber())) {
                            log.debug("[!] Blacklisted Volume SN found: " + blacklistedItem + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted Volume SN found: " + blacklistedItem + " for " + account.getRepresentation() + "\n");
                        }
                    }
                }
            }
        }
        log.debug("[info] Last 10k users checked.");
    }
}
