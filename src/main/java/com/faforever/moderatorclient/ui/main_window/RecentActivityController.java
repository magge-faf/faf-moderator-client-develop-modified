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
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javafx.concurrent.Task;

import java.time.temporal.IsoFields;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

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
    public Tab statsRegistrationsTab;
    public TableView statsRegistrationsTableView;
    @FXML
    public Button refreshLatestRegistrationsButton;

    @FXML private Tab latestRegistrationsTab;
    @FXML private Tab latestTeamkillsTab;
    @FXML private Tab latestMapUploadsTab;

    public TableView<PlayerFX> userRegistrationFeedTableView;
    public TableView<TeamkillFX> teamkillFeedTableView;
    public TableView<MapVersionFX> mapUploadFeedTableView;
    public TextArea statsLatestRegistrations;

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
        refreshLatestRegistrationsExtendedStats();
        refreshLatestRegistrationsButton.setOnAction(event -> refreshLatestRegistrationsExtendedStats());
        if (checkPermissionForTab(GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS, latestRegistrationsTab)) {
            ViewHelper.buildUserTableView(platformService, userRegistrationFeedTableView, users, this::addBan,
                    playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), true, communicationService);
        }

        if (checkPermissionForTab(GroupPermission.ROLE_READ_TEAMKILL_REPORT, latestTeamkillsTab)) {
            ViewHelper.buildTeamkillTableView(teamkillFeedTableView, teamkills, true);
        }

        if (checkPermissionForTab(GroupPermission.ROLE_ADMIN_MAP, latestMapUploadsTab)) {
            ViewHelper.buildMapFeedTableView(mapUploadFeedTableView, mapVersions, this::toggleHide);
        }
    }

    private void addBan(PlayerFX playerFX) {
    }

    private void toggleHide(MapVersionFX mapVersionFX) {
        mapVersionFX.setHidden(!mapVersionFX.isHidden());
        mapService.patchMapVersion(mapVersionFX);
    }

    @FXML
    public void refreshLatestRegistrationsExtendedStats() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {

                List<PlayerFX> latestUsers = userService.findLatestRegistrations();

                List<PlayerFX> usersToAdd = new ArrayList<>();

                int totalRegistrations = 0;
                int totalLogins = 0;

                LocalDate today = LocalDate.now(ZoneId.of("UTC"));
                LocalDate threeMonthsAgo = today.minusMonths(3);

                // Maps to hold daily stats
                Map<LocalDate, Integer> registrationsPerDay = new TreeMap<>(Comparator.reverseOrder());
                Map<LocalDate, Integer> loginsPerDay = new TreeMap<>(Comparator.reverseOrder());

                // Maps to hold weekly stats
                Map<Integer, Integer> registrationsPerWeek = new TreeMap<>(Comparator.reverseOrder());
                Map<Integer, Integer> loginsPerWeek = new TreeMap<>(Comparator.reverseOrder());

                for (PlayerFX user : latestUsers) {
                    usersToAdd.add(user);

                    LocalDate registrationDate = user.getCreateTime().toLocalDate();
                    LocalDate lastLoginDate = user.getLastLogin() != null ? user.getLastLogin().toLocalDate() : null;

                    if (!registrationDate.isBefore(threeMonthsAgo)) {
                        // Daily stats
                        registrationsPerDay.put(registrationDate, registrationsPerDay.getOrDefault(registrationDate, 0) + 1);
                        totalRegistrations++;

                        if (lastLoginDate != null && !lastLoginDate.isBefore(registrationDate)) {
                            loginsPerDay.put(registrationDate, loginsPerDay.getOrDefault(registrationDate, 0) + 1);
                            totalLogins++;
                        }

                        // Weekly stats (ISO week number)
                        int weekNumber = registrationDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                        registrationsPerWeek.put(weekNumber, registrationsPerWeek.getOrDefault(weekNumber, 0) + 1);
                        if (lastLoginDate != null && !lastLoginDate.isBefore(registrationDate)) {
                            loginsPerWeek.put(weekNumber, loginsPerWeek.getOrDefault(weekNumber, 0) + 1);
                        }
                    }
                }

                double avgRegistrationsPerDay = registrationsPerDay.values().stream().mapToInt(Integer::intValue).average().orElse(0);
                double loginRate = totalRegistrations > 0 ? (totalLogins * 100.0 / totalRegistrations) : 0;

                StringBuilder stats = new StringBuilder();

                stats.append("\nDataset from last 3 months:\n");

                stats.append("\nWeekly summary (ISO week number):\n");
                for (Integer week : registrationsPerWeek.keySet()) {
                    int reg = registrationsPerWeek.getOrDefault(week, 0);
                    int log = loginsPerWeek.getOrDefault(week, 0);
                    stats.append("Week ").append(week).append(": ").append(reg).append(" / ").append(log).append("\n");
                }

                stats.append("\nTotal registrations: ").append(totalRegistrations).append("\n");
                stats.append("Total users who logged in: ").append(totalLogins).append("\n");
                stats.append(String.format("Average registrations per day: %.2f\n", avgRegistrationsPerDay));
                stats.append(String.format("Login rate: %.2f%%", loginRate));

                stats.append("\n\nRegistrations in the last 3 months (Registrations / Logins):\n");
                for (LocalDate date : registrationsPerDay.keySet()) {
                    int reg = registrationsPerDay.getOrDefault(date, 0);
                    int log = loginsPerDay.getOrDefault(date, 0);
                    stats.append(date).append(": ").append(reg).append(" / ").append(log).append("\n");
                }

                Platform.runLater(() -> {
                    users.setAll(usersToAdd);
                    statsLatestRegistrations.setText(stats.toString());
                    userRegistrationFeedTableView.getSortOrder().clear();
                });

                return null;
            }
        };

        new Thread(task).start();
    }
}
