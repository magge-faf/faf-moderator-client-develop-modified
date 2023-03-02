package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.event.FafApiFailGetEvent;
import com.faforever.moderatorclient.api.event.FafApiFailModifyEvent;
import com.faforever.moderatorclient.api.event.FafUserFailModifyEvent;
import com.faforever.moderatorclient.api.event.TokenExpiredEvent;
import com.faforever.moderatorclient.ui.main_window.AvatarsController;
import com.faforever.moderatorclient.ui.main_window.DomainBlacklistController;
import com.faforever.moderatorclient.ui.main_window.LadderMapPoolController;
import com.faforever.moderatorclient.ui.main_window.MapVaultController;
import com.faforever.moderatorclient.ui.main_window.ModVaultController;
import com.faforever.moderatorclient.ui.main_window.RecentActivityController;
import com.faforever.moderatorclient.ui.main_window.SettingsController;
import com.faforever.moderatorclient.ui.main_window.TutorialController;
import com.faforever.moderatorclient.ui.main_window.UserGroupsController;
import com.faforever.moderatorclient.ui.main_window.UserManagementController;
import com.faforever.moderatorclient.ui.main_window.VotingController;
import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MainController implements Controller<TabPane> {
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
    private final Map<Tab, Boolean> dataLoadingState = new HashMap<>();
    private final FafApiCommunicationService communicationService;
    public static final String CONFIGURATION_FOLDER = "Configuration";

    @Override
    public TabPane getRoot() {
        Properties config = new Properties();
        root.getSelectionModel().select(userManagementTab); // Set a default tab
        try (InputStream input = new FileInputStream(CONFIGURATION_FOLDER + "/config.properties")) {
            config.load(input);
            String userChoiceDefaultTab = config.getProperty("user.choice.tab");
            if(userChoiceDefaultTab != null){
                switch (userChoiceDefaultTab) {
                    case "userManagementTab" -> root.getSelectionModel().select(userManagementTab);
                    case "reportTab" -> root.getSelectionModel().select(reportTab);
                    case "recentActivityTab" -> root.getSelectionModel().select(recentActivityTab);
                }
            }
        } catch (IOException e) {
            log.debug(String.valueOf(e));
        }
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
    }
    private void initSettingsTab() {
        settingsController = uiService.loadFxml("ui/main_window/settingsTab.fxml");
        settingsTab.setContent(settingsController.getRoot());

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
            initLoading(reportTab, moderationReportController::onRefreshAllReports);
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
            initLoading(avatarsTab, avatarsController::refresh);
        }
    }

    private void initRecentActivityTab() {
        if (checkPermissionForTab(recentActivityTab, GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS,
                GroupPermission.ROLE_READ_TEAMKILL_REPORT, GroupPermission.ROLE_ADMIN_MAP)) {
            recentActivityController = uiService.loadFxml("ui/main_window/recentActivity.fxml");
            recentActivityTab.setContent(recentActivityController.getRoot());
            initLoading(recentActivityTab, recentActivityController::refresh);
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
        LoginController loginController = uiService.loadFxml("ui/login.fxml");

        Stage loginDialog = new Stage();
        loginDialog.setOnCloseRequest(event -> System.exit(0));
        loginDialog.setTitle("Login - magge's modified Mordor");
        loginDialog.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("/media/favicon.png"))));
        Scene scene = new Scene(loginController.getRoot());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/main.css")).toExternalForm());
        loginDialog.setScene(scene);
        loginDialog.showAndWait();
        initializeAfterLogin();
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
}
