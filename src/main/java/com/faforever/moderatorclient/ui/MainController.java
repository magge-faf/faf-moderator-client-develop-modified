package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.LobbyOAuthService;
import com.faforever.moderatorclient.api.TokenService;
import com.faforever.moderatorclient.api.event.FafApiFailGetEvent;
import com.faforever.moderatorclient.api.event.FafApiFailModifyEvent;
import com.faforever.moderatorclient.api.event.FafUserFailModifyEvent;
import com.faforever.moderatorclient.api.event.TokenExpiredEvent;
import com.faforever.moderatorclient.config.ApplicationPaths;
import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.config.ApplicationVersion;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.config.local.LocalPreferencesReaderWriter;
import com.faforever.moderatorclient.replay.ReplayStorageService;
import com.faforever.moderatorclient.update.ApplicationUpdateService;
import com.faforever.moderatorclient.update.GithubRelease;
import com.faforever.moderatorclient.update.GithubReleaseAsset;
import com.faforever.moderatorclient.ui.main_window.*;
import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
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
    private final LobbyOAuthService lobbyOAuthService;
    private final UiService uiService;
    private final PlatformService platformService;
    private final ApplicationUpdateService applicationUpdateService;
    private final ReplayStorageService replayStorageService;
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
    public Tab ircChatTab;
    public Tab reportTab;
    public Tab permissionTab;
    public Tab settingsTab;
    public Tab recentNotesTab;
    public Tab reportStatisticsTab;
    public Tab smurfManagementControllerTab;
    public Tab replayAnalysisControllerTab;
    public Tab excludedHardwareItemsTab;
    public Tab apiHistoryTab;

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
    private IrcChatController ircChatController;
    private UserGroupsController userGroupsController;
    private RecentNotesController recentNotesController;
    private ReportStatisticsController reportStatisticsController;
    private SmurfManagementController smurfManagementController;
    private ReplayAnalysisController replayAnalysisController;
    private ExcludedHardwareItemsController excludedHardwareItemsController;
    private ApiHistoryController apiHistoryController;

    private final Map<Tab, Boolean> dataLoadingState = new HashMap<>();

    private final FafApiCommunicationService communicationService;
    public static final String CONFIGURATION_FOLDER = ApplicationPaths.CONFIGURATION_DIRECTORY_NAME;

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
        replayStorageService.purgeReplayFilesIfEnabled();
        initUserManagementTab();
        initMatchmakerMapPoolTab();
        initMapVaultTab();
        initModVaultTab();
        initAvatarTab();
        initBanTab();
        initDomainBlacklistTab();
        initMessagesTab();
        initIrcChatTab();
        initPermissionTab();
        initRecentActivityTab();
        initReportTab();
        initSettingsTab();
        initTutorialTab();
        initVotingTab();
        initRecentNotesTab();
        initReportStatisticsTab();
        initSmurfControllerTab();
        initReplayAnalysisControllerTab();
        initExcludedHardwareItemsTab();
        initApiHistoryTab();
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
        if (checkPermissionForTab(excludedHardwareItemsTab, GroupPermission.ROLE_ADMIN_MODERATION_REPORT)) {
            excludedHardwareItemsController = uiService.loadFxml("ui/main_window/excludedHardwareItems.fxml");
            excludedHardwareItemsTab.setContent(excludedHardwareItemsController.getRoot());
        }
    }

    private void initApiHistoryTab() {
        apiHistoryController = uiService.loadFxml("ui/main_window/apiHistoryTab.fxml");
        apiHistoryTab.setContent(apiHistoryController.getRoot());
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
        if (checkPermissionForTab(reportStatisticsTab, GroupPermission.ROLE_ADMIN_MODERATION_REPORT)) {
            reportStatisticsController = uiService.loadFxml("ui/main_window/reportStatisticsTab.fxml");
            reportStatisticsTab.setContent(reportStatisticsController.getRoot());
        }
    }

    private void initSmurfControllerTab() {
        if (checkPermissionForTab(smurfManagementControllerTab, GroupPermission.ROLE_ADMIN_MODERATION_REPORT)) {
            smurfManagementController = uiService.loadFxml("ui/main_window/smurfManagementTab.fxml");
            smurfManagementControllerTab.setContent(smurfManagementController.getRoot());
        }
    }

    private void initRecentNotesTab() {
        if (checkPermissionForTab(recentNotesTab, GroupPermission.ROLE_ADMIN_MODERATION_REPORT)) {
            recentNotesController = uiService.loadFxml("ui/main_window/recentNotesTab.fxml");
            recentNotesTab.setContent(recentNotesController.getRoot());
        }
    }

    private void initLoading(Tab tab, Runnable loadingFunction) {
        dataLoadingState.put(tab, false);
        tab.setOnSelectionChanged(event -> {
            if (tab.isSelected() && !dataLoadingState.getOrDefault(tab, false)) {
                dataLoadingState.put(tab, true);
                try {
                    loadingFunction.run();
                } catch (RuntimeException e) {
                    log.error("Error during tab initialization for {}", tab.getId(), e);
                }
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
            initLoading(reportTab);
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

    private void initIrcChatTab() {
        ircChatController = uiService.loadFxml("ui/main_window/ircChatTab.fxml");
        ircChatTab.setContent(ircChatController.getRoot());
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
            lobbyOAuthService.prepare(environmentProperties);
            tokenService.prepare(environmentProperties);

            try {
                tokenService.loginWithRefreshToken(refreshToken, true);
            } catch (Exception e) {
                log.warn("Auto login failed (refresh token expired or revoked), clearing stored token and showing manual login dialog.", e);
                localPreferences.getAutoLogin().setEnabled(false);
                localPreferences.getAutoLogin().setRefreshToken(null);
                localPreferencesReaderWriter.write(localPreferences);
                display();
                return;
            }
        } else {
            LoginController loginController = uiService.loadFxml("ui/login.fxml");

            Stage loginDialog = new Stage();
            loginDialog.setOnCloseRequest(event -> System.exit(0));
            loginDialog.setTitle("FAF Moderator Client");
            try (var iconStream = Objects.requireNonNull(this.getClass().getResourceAsStream("/media/favicon.png"))) {
                loginDialog.getIcons().add(new Image(iconStream));
            } catch (java.io.IOException e) {
                log.warn("Failed to load application icon", e);
            }

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
        boolean preferencesSaved = settingsController == null || settingsController.saveOnExit();
        if (moderationReportController != null) moderationReportController.onSave();
        if (userManagementController != null) userManagementController.onSave();
        if (bansController != null) bansController.onSave();
        if (recentNotesController != null) recentNotesController.onSave();
        if (avatarsController != null) avatarsController.onSave();
        if (mapVaultController != null) mapVaultController.onSave();
        if (modVaultController != null) modVaultController.onSave();
        if (votingController != null) votingController.onSave();
        if (tutorialController != null) tutorialController.onSave();
        if (messagesController != null) messagesController.onSave();
        if (ircChatController != null) ircChatController.onSave();
        if (userGroupsController != null) userGroupsController.onSave();
        if (recentActivityController != null) recentActivityController.onSave();
        if (apiHistoryController != null) apiHistoryController.onSave();
        if (preferencesSaved) {
            log.info("Local preferences saved successfully.");
        } else {
            log.warn("Local preferences were not saved successfully.");
        }
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
        Thread versionCheckThread = new Thread(() -> {
            try {
                applicationUpdateService.fetchLatestRelease().ifPresent(release -> {
                    if (applicationUpdateService.shouldShowUpdate(release, localPreferences.getVersionReminder())) {
                        Platform.runLater(() -> showUpdatePopup(release));
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to check for new version", e);
            }
        });
        versionCheckThread.setDaemon(true);
        versionCheckThread.start();
    }

    private boolean isNewerVersion(String latest) {
        return applicationUpdateService.isNewerVersion(latest);
    }

    private void showUpdatePopup(GithubRelease release) {
        Optional<String> autoUpdateUnavailableReason = applicationUpdateService.describeAutomaticUpdateUnavailableReason(release);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update Available");
        dialog.setHeaderText("Version " + release.displayName() + " is available");

        ButtonType updateButtonType = new ButtonType("Backup + Update + Restart", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButtonType = new ButtonType("Not Now", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, closeButtonType);

        Label message = new Label("You are running " + ApplicationVersion.CURRENT_VERSION + ".");
        message.setWrapText(true);

        Label autoUpdateRequirement = new Label(
                "Automatic update requires PowerShell on Windows. Use Manual Download if PowerShell is not installed or enabled."
        );
        autoUpdateRequirement.setWrapText(true);

        Button changelogButton = new Button("Show Change Log");
        changelogButton.setOnAction(event -> showChangelogDialog(release));

        Button downloadButton = new Button("Manual Download");
        downloadButton.setOnAction(event -> platformService.showDocument(
                applicationUpdateService.findMatchingAsset(release)
                        .map(GithubReleaseAsset::browserDownloadUrl)
                        .orElse(release.htmlUrl())
        ));

        VBox content = new VBox(12, message, autoUpdateRequirement, new HBox(8, changelogButton, downloadButton));
        if (autoUpdateUnavailableReason.isPresent()) {
            Label autoUpdateInfo = new Label(autoUpdateUnavailableReason.get());
            autoUpdateInfo.setWrapText(true);
            content.getChildren().add(autoUpdateInfo);
        }

        dialog.getDialogPane().setContent(content);
        applyDialogStyles(dialog.getDialogPane());

        ButtonType result = dialog.showAndWait().orElse(closeButtonType);
        if (result == updateButtonType) {
            if (autoUpdateUnavailableReason.isPresent()) {
                showAutomaticUpdateUnavailable(autoUpdateUnavailableReason.get());
                return;
            }
            startAutomaticUpdate(release);
            return;
        }

        localPreferences.getVersionReminder().scheduleForNextStart(
                release.tagName(),
                localPreferences.getVersionReminder().getReminderDelayDays()
        );
        localPreferencesReaderWriter.write(localPreferences);
    }

    private void showAutomaticUpdateUnavailable(String detail) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Self-Update Unavailable");
        alert.setHeaderText("Backup + Update + Restart is not available here");
        alert.setContentText(detail);
        applyDialogStyles(alert.getDialogPane());
        alert.showAndWait();
    }

    private void startAutomaticUpdate(GithubRelease release) {
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Preparing Update");
        progressAlert.setHeaderText("Preparing " + release.displayName());

        Label statusLabel = new Label("Starting update preparation...");
        statusLabel.setWrapText(true);
        VBox content = new VBox(12, new ProgressIndicator(), statusLabel);
        progressAlert.getDialogPane().setContent(content);
        progressAlert.getButtonTypes().clear();
        applyDialogStyles(progressAlert.getDialogPane());

        if (root != null && root.getScene() != null && root.getScene().getWindow() != null) {
            progressAlert.initOwner(root.getScene().getWindow());
        }
        progressAlert.initModality(Modality.APPLICATION_MODAL);
        progressAlert.setOnCloseRequest(event -> event.consume());

        Thread updateThread = new Thread(() -> {
            try {
                applicationUpdateService.prepareUpdateAndLaunchInstaller(release,
                        status -> Platform.runLater(() -> statusLabel.setText(status)));
                Platform.runLater(() -> {
                    progressAlert.close();
                    Platform.exit();
                    System.exit(0);
                });
            } catch (Exception e) {
                log.warn("Failed to prepare automatic update", e);
                Platform.runLater(() -> {
                    progressAlert.close();
                    ViewHelper.exceptionDialog(
                            "Automatic update failed",
                            "The update could not be prepared. Your current installation is still intact.",
                            e,
                            Optional.ofNullable(release.htmlUrl())
                    );
                });
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();

        progressAlert.show();
    }

    private void showChangelogDialog(GithubRelease release) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Latest Change Log");
        alert.setHeaderText(release.displayName());

        String published = release.publishedAt() == null
                ? "Unknown publish date"
                : "Published: " + DateTimeFormatter.ISO_LOCAL_DATE.format(release.publishedAt());
        Label publishedLabel = new Label(published);

        TextArea changelogArea = new TextArea(release.changelogText().isBlank()
                ? "No changelog text was published for this release."
                : release.changelogText());
        changelogArea.setEditable(false);
        changelogArea.setWrapText(true);
        changelogArea.setPrefRowCount(20);
        changelogArea.setPrefColumnCount(80);

        VBox content = new VBox(10, publishedLabel, changelogArea);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyDialogStyles(alert.getDialogPane());
        alert.showAndWait();
    }

    private void applyDialogStyles(DialogPane dialogPane) {
        String stylesheet = localPreferences.getTabSettings().isDarkModeCheckBox()
                ? "/style/main-dark.css" : "/style/main-light.css";
        dialogPane.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource(stylesheet)).toExternalForm()
        );
    }
}
