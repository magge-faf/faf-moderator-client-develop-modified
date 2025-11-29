package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.TokenService;
import com.faforever.moderatorclient.api.event.FafApiFailGetEvent;
import com.faforever.moderatorclient.api.event.FafApiFailModifyEvent;
import com.faforever.moderatorclient.api.event.FafUserFailModifyEvent;
import com.faforever.moderatorclient.api.event.TokenExpiredEvent;
import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.config.ApplicationVersion;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.config.local.LocalPreferencesReaderWriter;
import com.faforever.moderatorclient.ui.main_window.*;
import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MainController implements Controller<TabPane>, DisposableBean {
    @Autowired
    private ApplicationVersion applicationVersion;

    private final ApplicationProperties applicationProperties;
    private final LocalPreferences localPreferences;
    private final LocalPreferencesReaderWriter localPreferencesReaderWriter;
    private final TokenService tokenService;
    private final FafApiCommunicationService fafApiCommunicationService;
    private final FafUserCommunicationService fafUserCommunicationService;
    private final UiService uiService;
    public TabPane root;
    public Tab userManagementTab;
    public Tab matchmakerMapPoolTab;
    public Tab mapVaultTab;
    public Tab modVaultTab;
    public Tab avatarsTab;
    public Tab recentActivityTab;
    public Tab domainBlacklistTab;
    public Tab banTab;
    public Tab votingTab;
    public Tab tutorialTab;
    public Tab messagesTab;
    public Tab reportTab;
    public Tab permissionTab;
    public Tab settingsTab;
    public Tab recentNotesTab;
    public Tab reportStatisticsTab;
    public Tab smurfManagementControllerTab;
    public Tab replayAnalysisControllerTab;
    public Tab excludedHardwareItemsTab;

    private SettingsController settingsController;
    private ModerationReportController moderationReportController;
    private UserManagementController userManagementController;
    private LadderMapPoolController ladderMapPoolController;
    private MapVaultController mapVaultController;
    private ModVaultController modVaultController;
    private AvatarsController avatarsController;
    private RecentActivityController recentActivityController;
    private DomainBlacklistController domainBlacklistController;
    private BansController bansController;
    private VotingController votingController;
    private TutorialController tutorialController;
    private MessagesController messagesController;
    private UserGroupsController userGroupsController;
    private RecentNotesController recentNotesController;
    private ReportStatisticsController reportStatisticsController;
    private SmurfManagementController smurfManagementController;
    private ReplayAnalysisController replayAnalysisController;
    private ExcludedHardwareItemsController excludedHardwareItemsController;

    private final Map<Tab, Boolean> dataLoadingState = new HashMap<>();

    private final FafApiCommunicationService communicationService;
    public static final String CONFIGURATION_FOLDER = "data";

    @Override
    public TabPane getRoot() {
        return root;
    }

    private boolean checkPermissionForTab(Tab tab, String... permissionTechnicalName) {
        if (!communicationService.hasPermission(permissionTechnicalName)) {
            tab.setDisable(true);
            return false;
        }

        tab.setDisable(false);
        return true;
    }

    private void initializeAfterLogin() {
        initUserManagementTab();
        initMatchmakerMapPoolTab();
        initMapVaultTab();
        initModVaultTab();
        initAvatarTab();
        initBanTab();
        initDomainBlacklistTab();
        initMapVaultTab();
        initMatchmakerMapPoolTab();
        initMessagesTab();
        initModVaultTab();
        initPermissionTab();
        initRecentActivityTab();
        initReportTab();
        initSettingsTab();
        initTutorialTab();
        initUserManagementTab();
        initVotingTab();
        initRecentNotesTab();
        initReportStatisticsTab();
        initSmurfControllerTab();
        initReplayAnalysisControllerTab();
        initExcludedHardwareItemsTab();
        selectActiveTab();
    }

    private void selectActiveTab() {
        var startUpTab = localPreferences.getUi().getStartUpTab();
        if (startUpTab == null) return;

        try {
            root.getTabs()
                    .stream().filter(tab -> Objects.equals(tab.getId(), startUpTab))
                    .findFirst()
                    .ifPresent(tab -> root.getSelectionModel().select(tab));
        } catch (Exception e) {
            log.error("Error selecting active tab", e);
        }
    }

    private void initExcludedHardwareItemsTab() {
        excludedHardwareItemsController = uiService.loadFxml("ui/main_window/excludedHardwareItems.fxml");
        excludedHardwareItemsTab.setContent(excludedHardwareItemsController.getRoot());
    }

    private void initReplayAnalysisControllerTab() {
        replayAnalysisController = uiService.loadFxml("ui/main_window/replayAnalysisControllerTab.fxml");
        replayAnalysisControllerTab.setContent(replayAnalysisController.getRoot());
        replayAnalysisControllerTab.setDisable(true);
    }

    private void initSettingsTab() {
        settingsController = uiService.loadFxml("ui/main_window/settingsTab.fxml");
        settingsTab.setContent(settingsController.getRoot());
    }

    private void initReportStatisticsTab() {
        reportStatisticsController = uiService.loadFxml("ui/main_window/reportStatisticsTab.fxml");
        reportStatisticsTab.setContent(reportStatisticsController.getRoot());
    }

    private void initSmurfControllerTab() {
        smurfManagementController = uiService.loadFxml("ui/main_window/smurfManagementTab.fxml");
        smurfManagementControllerTab.setContent(smurfManagementController.getRoot());
    }

    private void initRecentNotesTab() {
        recentNotesController = uiService.loadFxml("ui/main_window/recentNotesTab.fxml");
        recentNotesTab.setContent(recentNotesController.getRoot());
    }

    private void initLoading(Tab tab, Runnable loadingFunction) {
        dataLoadingState.put(tab, false);
        tab.setOnSelectionChanged(event -> {
            if (tab.isSelected() && !dataLoadingState.getOrDefault(tab, false)) {
                dataLoadingState.put(tab, true);
                loadingFunction.run();
            }
        });
    }

    private void initLoading(Tab tab) {
        initLoading(tab, () -> {});
    }

    private void initUserManagementTab() {
        if (checkPermissionForTab(userManagementTab, GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS,
                GroupPermission.ROLE_ADMIN_ACCOUNT_NOTE, GroupPermission.ROLE_ADMIN_ACCOUNT_BAN,
                GroupPermission.ROLE_READ_TEAMKILL_REPORT, GroupPermission.ROLE_WRITE_AVATAR)) {
            userManagementController = uiService.loadFxml("ui/main_window/userManagement.fxml");
            userManagementTab.setContent(userManagementController.getRoot());
        }
    }

    private void initMatchmakerMapPoolTab() {
        if (checkPermissionForTab(matchmakerMapPoolTab, GroupPermission.ROLE_WRITE_MATCHMAKER_MAP)) {
            ladderMapPoolController = uiService.loadFxml("ui/main_window/ladderMapPool.fxml");
            matchmakerMapPoolTab.setContent(ladderMapPoolController.getRoot());
            initLoading(matchmakerMapPoolTab, ladderMapPoolController::refresh);
        }
    }

    private void initReportTab() {
        if (checkPermissionForTab(reportTab, GroupPermission.ROLE_ADMIN_MODERATION_REPORT)) {
            moderationReportController = uiService.loadFxml("ui/main_window/report.fxml");
            reportTab.setContent(moderationReportController.getRoot());
            initLoading(reportTab); //TODO
            //initLoading(reportTab, moderationReportController::onRefreshAllReports);
        }
    }

    private void initMapVaultTab() {
        mapVaultController = uiService.loadFxml("ui/main_window/mapVault.fxml");
        mapVaultTab.setContent(mapVaultController.getRoot());
    }

    private void initModVaultTab() {
        modVaultController = uiService.loadFxml("ui/main_window/modVault.fxml");
        modVaultTab.setContent(modVaultController.getRoot());
    }

    private void initAvatarTab() {
        if (checkPermissionForTab(avatarsTab, GroupPermission.ROLE_WRITE_AVATAR)) {
            avatarsController = uiService.loadFxml("ui/main_window/avatars.fxml");
            avatarsTab.setContent(avatarsController.getRoot());
            initLoading(avatarsTab, avatarsController::refreshAvatars);
        }
    }

    private void initRecentActivityTab() {
        if (checkPermissionForTab(recentActivityTab, GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS,
                GroupPermission.ROLE_READ_TEAMKILL_REPORT, GroupPermission.ROLE_ADMIN_MAP)) {
            recentActivityController = uiService.loadFxml("ui/main_window/recentActivity.fxml");
            recentActivityTab.setContent(recentActivityController.getRoot());
            initLoading(recentActivityTab, recentActivityController::refreshLatestRegistrationsExtendedStats);
        }
    }

    private void initDomainBlacklistTab() {
        if (checkPermissionForTab(domainBlacklistTab, GroupPermission.ROLE_WRITE_EMAIL_DOMAIN_BAN)) {
            domainBlacklistController = uiService.loadFxml("ui/main_window/domainBlacklist.fxml");
            domainBlacklistTab.setContent(domainBlacklistController.getRoot());
            initLoading(domainBlacklistTab, domainBlacklistController::refresh);
        }
    }

    private void initBanTab() {
        if (checkPermissionForTab(banTab, GroupPermission.ROLE_ADMIN_ACCOUNT_BAN)) {
            bansController = uiService.loadFxml("ui/main_window/bans.fxml");
            banTab.setContent(bansController.getRoot());
            initLoading(banTab, bansController::onRefreshLatestBans);
        }
    }

    private void initTutorialTab() {
        if (checkPermissionForTab(tutorialTab, GroupPermission.ROLE_WRITE_TUTORIAL)) {
            tutorialController = uiService.loadFxml("ui/main_window/tutorial.fxml");
            tutorialTab.setContent(tutorialController.getRoot());
            initLoading(tutorialTab, tutorialController::onRefresh);
        }
    }

    private void initMessagesTab() {
        if (checkPermissionForTab(messagesTab, GroupPermission.ROLE_WRITE_MESSAGE)) {
            messagesController = uiService.loadFxml("ui/main_window/messages.fxml");
            messagesTab.setContent(messagesController.getRoot());
            initLoading(messagesTab, messagesController::onRefreshMessages);
        }
    }

    private void initVotingTab() {
        if (checkPermissionForTab(votingTab, GroupPermission.ROLE_ADMIN_VOTE)) {
            votingController = uiService.loadFxml("ui/main_window/voting.fxml");
            votingTab.setContent(votingController.getRoot());
            initLoading(votingTab, votingController::onRefreshSubjects);
        }
    }

    private void initPermissionTab() {
        if (checkPermissionForTab(permissionTab, GroupPermission.ROLE_READ_USER_GROUP)
                && checkPermissionForTab(permissionTab, GroupPermission.ROLE_WRITE_USER_GROUP)) {
            userGroupsController = uiService.loadFxml("ui/main_window/userGroups.fxml");
            permissionTab.setContent(userGroupsController.getRoot());
            initLoading(permissionTab, userGroupsController::onRefreshGroups);
        }
    }

    public void display() {
        if (localPreferences.getAutoLogin().isEnabled()) {
            String environment = localPreferences.getAutoLogin().getEnvironment();
            String refreshToken = localPreferences.getAutoLogin().getRefreshToken();

            if (environment == null || environment.isBlank()) {
                log.warn("Auto login configuration is missing the environment. Disabling auto login and showing login dialog.");
                localPreferences.getAutoLogin().setEnabled(false);
                display();
                return;
            }

            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("Auto login configuration is missing the refresh token. Disabling auto login and showing login dialog.");
                localPreferences.getAutoLogin().setEnabled(false);
                display();
                return;
            }

            EnvironmentProperties environmentProperties = applicationProperties.getEnvironments().get(environment);
            if (environmentProperties == null) {
                log.warn("No environment configuration found for key '{}'. Disabling auto login and showing login dialog.", environment);
                localPreferences.getAutoLogin().setEnabled(false);
                display();
                return;
            }

            fafApiCommunicationService.initialize(environmentProperties);
            fafUserCommunicationService.initialize(environmentProperties);
            tokenService.prepare(environmentProperties);

            try {
                tokenService.loginWithRefreshToken(refreshToken, true);
            } catch (Exception e) {
                log.error("Auto login failed, disabling auto login and showing manual login dialog.", e);
                localPreferences.getAutoLogin().setEnabled(false);
                display();
                return;
            }
        } else {
            LoginController loginController = uiService.loadFxml("ui/login.fxml");

            Stage loginDialog = new Stage();
            loginDialog.setOnCloseRequest(event -> System.exit(0));
            loginDialog.setTitle("FAF Moderator Client");
            loginDialog.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("/media/favicon.png"))));

            Scene scene = new Scene(loginController.getRoot());
            String stylesheet = "/style/main-light.css";
            if (localPreferences.getTabSettings().isDarkModeCheckBox()) {
                stylesheet = "/style/main-dark.css";
            }
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(stylesheet)).toExternalForm());

            loginDialog.setScene(scene);
            loginDialog.showAndWait();
        }

        initializeAfterLogin();
        checkForNewVersion();
    }

    @Override
    public void destroy() {
        log.info("Application exit received: saving local preferences to disk.");
        settingsController.onSave();
        moderationReportController.onSave();
        userManagementController.onSave();
        localPreferencesReaderWriter.write(localPreferences);
        log.info("Local preferences saved successfully.");
    }

    @EventListener
    public void onFafApiGetFailed(FafApiFailGetEvent event) {
        Platform.runLater(() ->
                ViewHelper.exceptionDialog("Querying data from API failed", MessageFormat.format("Something went wrong while fetching data of type ''{0}'' from the API. The related controls are shown empty instead now. You can proceed without causing any harm, but it is likely that some operations will not work and/or the error will pop up again.\n\nPlease contact the maintainer and give him the details from the box below.", event.getEntityClass().getSimpleName()), event.getCause(), Optional.of(event.getUrl())));
    }

    @EventListener
    public void onFafApiGetFailed(FafApiFailModifyEvent event) {
        Platform.runLater(() ->
                ViewHelper.exceptionDialog("Sending updated data to API failed", MessageFormat.format("Something went wrong while sending data of type ''{0}'' to the API. The related change was not saved. You might wanna try again. Please check if the data you entered is valid. \n\nPlease contact the maintainer and give him the details from the box below.", event.getEntityClass().getSimpleName()), event.getCause(), Optional.of(event.getUrl())));
    }

    @EventListener
    public void onFafUserGetFailed(FafUserFailModifyEvent event) {
        Platform.runLater(() ->
                ViewHelper.exceptionDialog("Sending updated data to User Service failed", MessageFormat.format("Something went wrong while sending data of type ''{0}'' to the User Service. The related change was not saved. You might wanna try again. Please check if the data you entered is valid. \n\nPlease contact the maintainer and give him the details from the box below.", event.getObjectClass().getSimpleName()), event.getCause(), Optional.of(event.getUrl())));
    }

    @EventListener
    public void onTokenExpired(TokenExpiredEvent event) {
        display();
    }

    private void checkForNewVersion() {
        long lastReminder = localPreferences.getVersionReminder().getLastReminderEpoch();
        long now = System.currentTimeMillis();

        // Only show popup if more than 3 days passed since the last reminder
        if (now - lastReminder < 3L * 24 * 60 * 60 * 1000) {
            return;
        }

        new Thread(() -> {
            try {
                String url = "https://api.github.com/repos/magge-faf/faf-moderator-client-develop-modified/releases/latest";
                var conn = new java.net.URL(url).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Java-Client");

                String json = new String(conn.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                String latestVersion = node.get("tag_name").asText();

                if (isNewerVersion(latestVersion)) {
                    Platform.runLater(() -> showUpdatePopup(latestVersion));
                }
            } catch (Exception e) {
                log.warn("Failed to check for new version", e);
            }
        }).start();
    }

    private boolean isNewerVersion(String latest) {
        return latest.compareTo(ApplicationVersion.CURRENT_VERSION) > 0;
    }

    private void showUpdatePopup(String latestVersion) {
        String downloadUrl = "https://github.com/magge-faf/faf-moderator-client-develop-modified/releases/latest";

        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Update available");
        dialog.setHeaderText("A new version is available: " + latestVersion);

        // TextField to show the URL (copyable)
        javafx.scene.control.TextField linkField = new javafx.scene.control.TextField(downloadUrl);
        linkField.setEditable(false);
        linkField.setFocusTraversable(false);

        // Button to copy the URL
        javafx.scene.control.Button copyButton = new javafx.scene.control.Button("Copy Download Link");
        copyButton.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(downloadUrl);
            clipboard.setContent(content);
        });

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, linkField, copyButton);
        dialog.getDialogPane().setContent(content);

        // Only one button to dismiss the dialog
        javafx.scene.control.ButtonType remindLaterButton = new javafx.scene.control.ButtonType(
                "Remind Me In 3 Days Again",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE
        );
        dialog.getDialogPane().getButtonTypes().add(remindLaterButton);

        dialog.showAndWait();

        // Save the reminder timestamp
        localPreferences.getVersionReminder().setLastReminderEpoch(System.currentTimeMillis());
        try {
            localPreferencesReaderWriter.write(localPreferences);
        } catch (Exception e) {
            log.warn("Failed to save version reminder timestamp", e);
        }
    }
}
