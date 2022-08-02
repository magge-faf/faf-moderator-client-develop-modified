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
        ArrayList<String> BlacklistedPermanentVSN = new ArrayList<>();
        ArrayList<String> BlacklistedTemporaryVSN = new ArrayList<>();
        ArrayList<String> BlacklistedPermanentIP = new ArrayList<>();

        File f1 = new File("BlacklistedPermanentVSN.txt");
        if(f1.exists() && !f1.isDirectory()) {
            try{
            Scanner s = new Scanner(f1);

            while (s.hasNext()){
                BlacklistedPermanentVSN.add(s.next());
            }
            s.close();
                log.debug("[info] BlacklistedPermanentVSN.txt loaded.");
            }
            catch (Exception e){log.debug(String.valueOf(e));}
        }

        File f2 = new File("BlacklistedTemporaryVSN.txt");
        if(f2.exists() && !f2.isDirectory()) {
            try{
                Scanner s = new Scanner(f2);
                while (s.hasNext()){
                    BlacklistedTemporaryVSN.add(s.next());
                }
                s.close();
                log.debug("[info] BlacklistedTemporaryVSN.txt loaded.");
            }
            catch (Exception e){log.debug(String.valueOf(e));}
        }

        File f3 = new File("BlacklistedPermanentIP.txt");
        if(f3.exists() && !f3.isDirectory()) {
            try{
                Scanner s = new Scanner(f3);
                while (s.hasNext()){
                    BlacklistedPermanentIP.add(s.next());
                }
                s.close();
                log.debug("[info] BlacklistedPermanentIP.txt loaded.");
            }
            catch (Exception e){log.debug(String.valueOf(e));}
        }

        users.setAll(userService.findLatestRegistrations());
        userRegistrationFeedTableView.getSortOrder().clear();

        List<PlayerFX> accounts = userService.findLatestRegistrations();

        for(PlayerFX account: accounts) {
            // cycle through players
            Boolean user_already_banned;
            user_already_banned = IncludeGlobalBannedUserCheckBox.isSelected();
            if (user_already_banned.equals(account.isBannedGlobally())) {
                ObservableSet<UniqueIdFx> account_unique_ids = account.getUniqueIds();
                for (UniqueIdFx item : account_unique_ids) {
                    // cycle through all unique ids
                    for (String item_blacklist_permanent_vsn : BlacklistedPermanentVSN) {
                        if (item_blacklist_permanent_vsn.equals(item.getVolumeSerialNumber())) {
                            log.debug("[!] Blacklisted VSN found: " + item_blacklist_permanent_vsn + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted VSN found: " + item_blacklist_permanent_vsn + " for " + account.getRepresentation() + "\n");
                        }
                    }
                    for (String item_blacklist_permanent_ip : BlacklistedPermanentIP) {
                        if (item_blacklist_permanent_ip.equals(account.getRecentIpAddress())) {
                            log.debug("[!] Blacklisted IP found: " + item_blacklist_permanent_ip + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted IP found: " + item_blacklist_permanent_ip + " for " + account.getRepresentation() + "\n");
                        }
                    }
                    for (String item_blacklist_temporary_vsn : BlacklistedTemporaryVSN) {
                        if (item_blacklist_temporary_vsn.equals(item.getVolumeSerialNumber())) {
                            log.debug("[!] Blacklisted temporary VSN found: " + item_blacklist_temporary_vsn + " for " + account.getRepresentation());
                            SuspiciousUserTextArea.setText(SuspiciousUserTextArea.getText() + "[!] Blacklisted temporary VSN found: " + item_blacklist_temporary_vsn + " for " + account.getRepresentation() + "\n");
                        }
                    }
                }
            }
        }
        log.debug("[info] Last 10k users checked.");
    }
}
