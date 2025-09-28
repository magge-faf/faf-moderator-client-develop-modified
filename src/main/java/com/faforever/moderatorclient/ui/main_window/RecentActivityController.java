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

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
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
    public TextArea statsLatestRegistrations;

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
                    playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), true, communicationService);
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
        Platform.runLater(() -> {
            teamkills.setAll(userService.findLatestTeamkills());
            teamkillFeedTableView.getSortOrder().clear();
            mapVersions.setAll(mapService.findLatestMapVersions());
            mapUploadFeedTableView.getSortOrder().clear();

            // Resetting UI element
            suspiciousUserTextArea.setText("");


            // Load recent registrations and loop through accounts
            try {
                users.setAll(userService.findLatestRegistrations());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

            userRegistrationFeedTableView.getSortOrder().clear();


            // Stats about user registrations and logins in the last 30 days
            int totalRegistrations = 0;
            int totalLogins = 0;
            Map<LocalDate, Integer> registrationsPerDay = new TreeMap<>(Comparator.reverseOrder());
            Map<LocalDate, Integer> loginsPerDay = new TreeMap<>(Comparator.reverseOrder());

            LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

            for (PlayerFX user : users) {
                LocalDate registrationDate = LocalDate.parse(user.getCreateTime().format(formatter), formatter);
                LocalDate lastLoginDate = user.getLastLogin() != null ? LocalDate.parse(user.getLastLogin().format(formatter), formatter) : null;

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

            stats.append("Total registrations: ").append(totalRegistrations).append(" registrations\n");
            stats.append("Number of users who have logged in since registration: ").append(totalLogins).append(" logins");
            statsLatestRegistrations.setText(stats.toString());

        });
    }
}
