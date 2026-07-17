package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.BanStatus;
import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.commons.api.dto.Validity;
import com.faforever.commons.api.update.AvatarAssignmentUpdate;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.AvatarService;
import com.faforever.moderatorclient.api.domain.PermissionService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.config.ApplicationPaths;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.mapstruct.GamePlayerStatsMapper;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Point2D;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserManagementController implements Controller<SplitPane> {
    @FXML
    public Button checkTemporaryBansButton;
    @FXML
    public Button checkSmurfManagementAccountsButton;
    public Label userNotesLabel;
    @FXML
    public TextField maxMatchesBeforePromptSmurfVillageLookupTextField;
    @FXML
    public CheckBox promptUserOnThresholdExceededSmurfVillageLookupCheckBox;
    @FXML
    public TitledPane searchHistoryTitledPane;
    @FXML
    public TitledPane smurfVillageLookupSettingsTitledPane;
    @FXML
    public TitledPane smurfVillageLookupTitledPane;
    @FXML
    public TitledPane banCheckerSmurfManagementTitledPane;
    @FXML
    public TitledPane latestRegistrationsTitledPane;
    @FXML
    public Button toggleAllAdvancedIdentificationButton;
    @FXML
    public TextField daysToCheckRecentAccountsTextField;
    @FXML
    public Label ProgressLabelRecentAccountsSmurfCheck;
    public Label searchHistoryLabel;
    private volatile boolean cancelRequestedByUser = false;
    @FXML
    public Tab smurfOutputTab;
    @FXML
    public TextArea smurfOutputTextArea;
    @FXML
    public Button checkRecentAccountsForSmurfsButton;
    @FXML
    public Text statusTextRecentAccountsForSmurfs;
    @FXML
    public Button checkSharedGamesButton;
    public Button removeNoteButton;
    @FXML
    public TextField
            statusTextFieldProcessingPlayerID,
            statusTextFieldProcessingItem,
            playerIDField1SharedGamesTextfield,
            playerIDField2SharedGamesTextfield,
            ratingCheckGamesCountField;

    @FXML
    public CheckBox
            catchFirstLayerSmurfsOnlyCheckBox,
            includeProcessorNameCheckBox;

    @FXML
    public Label
            temporaryBanProgressLabel,
            smurfManagementProgressLabel,
            progressLabelSharedGames;

    @FXML
    private Button checkRecentAccountsForSmurfsPauseButton;

    private volatile boolean isPaused = false;
    private volatile boolean isStopped = false;
    private final Object pauseLock = new Object();

    // Snapshot of UI state captured on FX thread before each background task starts.
    // Background threads read this instead of touching live JavaFX nodes.
    private volatile SmurfLookupSettings smurfLookupSettings = SmurfLookupSettings.defaults();

    @FXML
    public Button checkRecentAccountsForSmurfsStopButton;
    private final StringBuilder logOutput = new StringBuilder();
    private final UiService uiService;
    private final PlatformService platformService;
    private final UserService userService;
    private final AvatarService avatarService;
    private final PermissionService permissionService;
    private final GamePlayerStatsMapper gamePlayerStatsMapper;

    private final ObservableList<PlayerFX> users = FXCollections.observableArrayList();
    private final ObservableList<UserNoteFX> userNotes = FXCollections.observableArrayList();
    private final ObservableList<BanInfoFX> bans = FXCollections.observableArrayList();
    private final ObservableList<NameRecordFX> nameRecords = FXCollections.observableArrayList();
    private final ObservableList<TeamkillFX> teamkills = FXCollections.observableArrayList();
    private final ObservableList<AvatarAssignmentFX> avatarAssignments = FXCollections.observableArrayList();
    private final ObjectProperty<AvatarFX> currentSelectedAvatar = new SimpleObjectProperty<>();
    private final ObservableList<UserGroupFX> userGroups = FXCollections.observableArrayList();
    private final ObservableList<GroupPermissionFX> groupPermissions = FXCollections.observableArrayList();

    private final Map<String, String> searchUserPropertyMapping = new LinkedHashMap<>();
    private final SimpleStringProperty searchHighlightTerm = new SimpleStringProperty();
    @FXML
    public TextArea searchHistoryTextArea;
    @FXML
    public TextArea userNotesTextArea;

    private static final String SEARCH_HISTORY_FILE_NAME = "searchHistory.txt";
    private static final String USER_NOTES_FILE_NAME = "userNotes.txt";
    @FXML
    public TextField smurfVillageLookupTextField;
    public CheckBox includeUUIDCheckBox;
    public CheckBox includeUIDHashCheckBox;
    public CheckBox includeMemorySerialNumberCheckBox;
    public CheckBox includeVolumeSerialNumberCheckBox;
    public CheckBox includeSerialNumberCheckBox;
    public CheckBox includeProcessorIdCheckBox;
    public CheckBox includeManufacturerCheckBox;
    public CheckBox includeIPCheckBox;
    public CheckBox onlyShowActiveAccountsCheckBox;
    public CheckBox suppressNoRelatedAccountsCheckBox;
    public CheckBox suppressExcludedItemsCheckBox;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;
    private final FafApiCommunicationService communicationService;
    @FXML
    public SplitPane root;

    public Tab notesTab;
    public Tab bansTab;
    public Tab teamkillsTab;
    public Tab nameHistoryTab;
    public Tab lastGamesTab;
    public Tab avatarsTab;
    public Tab userGroupsTab;

    public ComboBox<String> searchUserProperties;
    public TextField userSearchTextField;
    public TableView<UserNoteFX> userNoteTableView;
    public Button addNoteButton;
    public Button editNoteButton;
    public Button newBanButton;
    public Button editBanButton;
    public TableView<PlayerFX> userSearchTableView;
    public Button compareUidsButton;
    public TableView<NameRecordFX> userNameHistoryTableView;
    public TableView<BanInfoFX> userBansTableView;
    public TableView<TeamkillFX> userTeamkillsTableView;
    public TableView<AvatarAssignmentFX> userAvatarsTableView;
    public TableView<UserGroupFX> userGroupsTableView;
    public TableView<GroupPermissionFX> permissionsTableView;
    public Button giveAvatarButton;
    public Button takeAvatarButton;
    public TextField expiresAtTextfield;
    public Button setExpiresAtButton;
    public Button removeGroupButton;

    @FXML
    private Label statusLabelSmurfVillageLookup;

    public TableView<GamePlayerStatsFX> userLastGamesTable;
    public ChoiceBox<FeaturedModFX> featuredModFilterChoiceBox;

    @Autowired
    public BansController bansController;

    @Autowired
    ExcludedHardwareItemsController excludedHardwareItemsController;

    @Override
    public SplitPane getRoot() {
        return root;
    }

    private void disableTabOnMissingPermission(Tab tab, String permissionTechnicalName) {
        tab.setDisable(!communicationService.hasPermission(permissionTechnicalName));
    }

    @FXML
    private Button minimizeSearchHistoryButton;
    @FXML
    private Button minimizeUserNotesButton;

    private final File EXCLUDED_ITEMS_FILE =
            ApplicationPaths.resolveConfigurationDirectory().resolve("excluded_items.json").toFile();

    private final LocalPreferences localPreferences;

    @FXML
    public void initialize() {
        loadStateCheckBox();
        addListeners();
        loadContent();
        configureSearchHistoryVisibility();
        configureUserNotesVisibility();
        setLastSearchTerm();
        disableTabsBasedOnPermissions();
        setupTableViews();
        setupButtonBindings();
        configureFeaturedModFilter();
        userSearchTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        userSearchTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<PlayerFX>) change -> onSelectedUser());
        initializeSearchProperties();
        bindUIElementsToPreferences();

        Tooltip tooltip = catchFirstLayerSmurfsOnlyCheckBox.getTooltip();

        tooltip.setWrapText(true);
        tooltip.setPrefWidth(300);
        tooltip.setText(
                "When enabled, only accounts directly linked to the starting account will be detected.\n" +
                        "Accounts connected through additional layers will not be checked.\n" +
                        "Use with caution, as disabling this could potentially scan all existing FAF accounts in the galaxy, " +
                        "and the 'Safeguard' above will prevent it from continuing indefinitely.\n" +
                        "\nExample:\n" +
                        "Account A is linked to B and C.\n" +
                        "B is linked to D.\n" +
                        "C is linked to E and F.\n" +
                        "With this setting enabled, only B and C are found.\n" +
                        "D, E, and F are not found.\n" +
                        "If the setting is disabled, the scan would continue to D, E, F, and any further linked accounts."
        );

        catchFirstLayerSmurfsOnlyCheckBox.setTooltip(tooltip);
        tooltip.setShowDelay(javafx.util.Duration.seconds(1));
        tooltip.setHideDelay(javafx.util.Duration.ZERO);
        tooltip.setShowDuration(javafx.util.Duration.INDEFINITE);

        toggleAllAdvancedIdentificationButton.setOnAction(event -> {
            boolean newState = !includeMemorySerialNumberCheckBox.isSelected();
            includeMemorySerialNumberCheckBox.setSelected(newState);
            includeVolumeSerialNumberCheckBox.setSelected(newState);
            includeSerialNumberCheckBox.setSelected(newState);
            includeProcessorIdCheckBox.setSelected(newState);
            includeProcessorNameCheckBox.setSelected(newState);
            includeManufacturerCheckBox.setSelected(newState);
        });

        ViewHelper.ensureColumnIds(userSearchTableView);
        ViewHelper.ensureColumnIds(userBansTableView);
        ViewHelper.ensureColumnIds(userNoteTableView);
        ViewHelper.ensureColumnIds(userNameHistoryTableView);
        ViewHelper.ensureColumnIds(userLastGamesTable);
        ViewHelper.ensureColumnIds(userAvatarsTableView);
        ViewHelper.ensureColumnIds(userGroupsTableView);
        ViewHelper.ensureColumnIds(permissionsTableView);

        Platform.runLater(() -> {
            LocalPreferences.TabUserManagement tab = localPreferences.getTabUserManagement();
            loadColumnLayout(userSearchTableView, localPreferences);
            loadSplitPanePositions(root, localPreferences);
            ViewHelper.loadColumnLayout(userBansTableView, tab.getUserBansTableColumnWidths(), tab.getUserBansTableColumnOrder());
            ViewHelper.loadColumnLayout(userNoteTableView, tab.getUserNoteTableColumnWidths(), tab.getUserNoteTableColumnOrder());
            ViewHelper.loadColumnLayout(userNameHistoryTableView, tab.getUserNameHistoryTableColumnWidths(), tab.getUserNameHistoryTableColumnOrder());
            ViewHelper.loadColumnLayout(userLastGamesTable, tab.getUserLastGamesTableColumnWidths(), tab.getUserLastGamesTableColumnOrder());
            ViewHelper.loadColumnLayout(userAvatarsTableView, tab.getUserAvatarsTableColumnWidths(), tab.getUserAvatarsTableColumnOrder());
            ViewHelper.loadColumnLayout(userGroupsTableView, tab.getUserGroupsTableColumnWidths(), tab.getUserGroupsTableColumnOrder());
            ViewHelper.loadColumnLayout(permissionsTableView, tab.getPermissionsTableColumnWidths(), tab.getPermissionsTableColumnOrder());
            startupSyncBans();

            int smurfCount = bansController.loadExistingBannedUserIds(SmurfManagementController.SMURF_MANAGEMENT_USERS_JSON_PATH).size();
            checkSmurfManagementAccountsButton.setText("Run Smurf Management: " + smurfCount);
        });
    }

    private void bindUIElementsToPreferences() {
        for (Field field : LocalPreferences.TabUserManagement.class.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();

            try {
                Object value = field.get(localPreferences.getTabUserManagement());

                // Only attempt binding if the controller actually has a matching UI field
                Field fxField;
                try {
                    fxField = this.getClass().getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    // Skip fields that are not UI nodes (like splitPaneDividerPositions)
                    continue;
                }

                fxField.setAccessible(true);
                Object node = fxField.get(this);

                if (node instanceof TextField textField && value instanceof String) {
                    textField.setText((String) value);
                    textField.textProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            field.set(localPreferences.getTabUserManagement(), newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof TextArea textArea && value instanceof String) {
                    textArea.setText((String) value);
                    textArea.textProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            field.set(localPreferences.getTabUserManagement(), newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof CheckBox checkBox && value instanceof Boolean) {
                    checkBox.setSelected((Boolean) value);
                    checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            field.set(localPreferences.getTabUserManagement(), newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof TitledPane titledPane && value instanceof Boolean) {
                    titledPane.setExpanded((Boolean) value);
                    titledPane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            field.set(localPreferences.getTabUserManagement(), newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                }
            } catch (IllegalAccessException e) {
                log.warn("Cannot access field {}", fieldName, e);
            }
        }
    }


    private void configureSearchHistoryVisibility() {
        LocalPreferences.TabUserManagement tabUserManagement = localPreferences.getTabUserManagement();
        boolean isSearchHistoryVisible = tabUserManagement.isSearchHistoryTexAreaVisibilityState();

        searchHistoryTextArea.setVisible(isSearchHistoryVisible);
        searchHistoryTextArea.setManaged(isSearchHistoryVisible);
        minimizeSearchHistoryButton.setText(isSearchHistoryVisible ? "Minimize" : "Restore");
        minimizeSearchHistoryButton.setOpacity(0.5);
    }

    private void configureUserNotesVisibility() {
        LocalPreferences.TabUserManagement tabUserManagement = localPreferences.getTabUserManagement();
        boolean isUserNotesVisible = tabUserManagement.isUserNotesTextAreaVisibilityState();

        userNotesTextArea.setVisible(isUserNotesVisible);
        userNotesTextArea.setManaged(isUserNotesVisible);
        minimizeUserNotesButton.setText(isUserNotesVisible ? "Minimize" : "Restore");
        minimizeUserNotesButton.setOpacity(0.5);
    }


    private void setLastSearchTerm() {
        String textAreaContent = searchHistoryTextArea.getText();
        String[] lines = textAreaContent.split("\n");
        if (lines.length > 0) {
            userSearchTextField.setText(lines[0]);
        }
    }

    private void disableTabsBasedOnPermissions() {
        disableTabOnMissingPermission(notesTab, GroupPermission.ROLE_ADMIN_ACCOUNT_NOTE);
        disableTabOnMissingPermission(bansTab, GroupPermission.ROLE_ADMIN_ACCOUNT_BAN);
        disableTabOnMissingPermission(teamkillsTab, GroupPermission.ROLE_READ_TEAMKILL_REPORT);
        disableTabOnMissingPermission(avatarsTab, GroupPermission.ROLE_WRITE_AVATAR);
        disableTabOnMissingPermission(userGroupsTab, GroupPermission.ROLE_READ_USER_GROUP);
    }

    private void setupTableViews() {
        ViewHelper.buildUserTableView(platformService, userSearchTableView, users, null,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), true, communicationService, userService, uiService, searchHighlightTerm);
        ViewHelper.buildNotesTableView(userNoteTableView, userNotes, false);
        ViewHelper.buildNameHistoryTableView(userNameHistoryTableView, nameRecords);
        ViewHelper.buildBanTableView(userBansTableView, bans, false, localPreferences, userService, uiService);
        ViewHelper.buildPlayersGamesTable(userLastGamesTable, replayDownLoadFormat, platformService);
        ViewHelper.buildUserAvatarsTableView(userAvatarsTableView, avatarAssignments);
        ViewHelper.buildUserGroupsTableView(userGroupsTableView, userGroups);
        ViewHelper.buildUserPermissionsTableView(permissionsTableView, groupPermissions);
    }

    private void setupButtonBindings() {
        addNoteButton.disableProperty().bind(userSearchTableView.getSelectionModel().selectedItemProperty().isNull());
        editNoteButton.disableProperty().bind(userNoteTableView.getSelectionModel().selectedItemProperty().isNull());
        giveAvatarButton.disableProperty().bind(
                Bindings.or(userSearchTableView.getSelectionModel().selectedItemProperty().isNull(),
                        currentSelectedAvatar.isNull()));
        expiresAtTextfield.disableProperty().bind(userAvatarsTableView.getSelectionModel().selectedItemProperty().isNull());
        setExpiresAtButton.disableProperty().bind(userAvatarsTableView.getSelectionModel().selectedItemProperty().isNull());
        takeAvatarButton.disableProperty().bind(userAvatarsTableView.getSelectionModel().selectedItemProperty().isNull());
        removeGroupButton.disableProperty().bind(userGroupsTableView.getSelectionModel().selectedItemProperty().isNull());
        editBanButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !canEditBan(userBansTableView.getSelectionModel().getSelectedItem()),
                userBansTableView.getSelectionModel().selectedItemProperty()
        ));
        userBansTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateEditBanButtonText(newValue));
        updateEditBanButtonText(null);
    }

    private void configureFeaturedModFilter() {
        featuredModFilterChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(FeaturedModFX object) {
                return object == null ? "All" : object.getDisplayName();
            }

            @Override
            public FeaturedModFX fromString(String string) {
                throw new UnsupportedOperationException("Not implemented");
            }
        });

        featuredModFilterChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            userLastGamesTable.getItems().clear();
        });

        featuredModFilterChoiceBox.getItems().add(null);
        featuredModFilterChoiceBox.getSelectionModel().select(0);
    }

    private void startupSyncBans() {
        int smurfCount = bansController.loadExistingBannedUserIds(SmurfManagementController.SMURF_MANAGEMENT_USERS_JSON_PATH).size();
        checkSmurfManagementAccountsButton.setText("Run Smurf Management: " + smurfCount);
    }

    private void initializeSearchProperties() {
            searchUserPropertyMapping.put("All In One", "allInOne");
        searchUserPropertyMapping.put("Name", "login");
        searchUserPropertyMapping.put("User ID", "id");
        searchUserPropertyMapping.put("Previous Name", "names.name");
        searchUserPropertyMapping.put("Email", "email");
        searchUserPropertyMapping.put("IP Address", "recentIpAddress");

        // uniqueIdAssignments
        searchUserPropertyMapping.put("UUID", "uniqueIdAssignments.uniqueId.uuid");
        searchUserPropertyMapping.put("Hash", "uniqueIdAssignments.uniqueId.hash");
        searchUserPropertyMapping.put("Volume Serial Number", "uniqueIdAssignments.uniqueId.volumeSerialNumber");
        searchUserPropertyMapping.put("Memory Serial Number", "uniqueIdAssignments.uniqueId.memorySerialNumber");
        searchUserPropertyMapping.put("Serial Number", "uniqueIdAssignments.uniqueId.serialNumber");
        searchUserPropertyMapping.put("Device ID", "uniqueIdAssignments.uniqueId.deviceId");
        searchUserPropertyMapping.put("CPU Name", "uniqueIdAssignments.uniqueId.name");
        searchUserPropertyMapping.put("Processor ID", "uniqueIdAssignments.uniqueId.processorId");
        searchUserPropertyMapping.put("Bios Version", "uniqueIdAssignments.uniqueId.SMBIOSBIOSVersion");
        searchUserPropertyMapping.put("Manufacturer", "uniqueIdAssignments.uniqueId.manufacturer");

        // accountLinks
        searchUserPropertyMapping.put("Steam ID", "accountLinks.serviceId");
        searchUserPropertyMapping.put("GOG ID", "accountLinks.serviceId");

        searchUserProperties.getItems().addAll(searchUserPropertyMapping.keySet());
        searchUserProperties.getSelectionModel().select(0);
    }

    @EventListener
    public void onAvatarSelected(AvatarFX avatarFX) {
        currentSelectedAvatar.setValue(avatarFX);
    }

    private String lastSelectedUserId = null;

    private void onSelectedUser() {
        ObservableList<PlayerFX> selectedUsers = userSearchTableView.getSelectionModel().getSelectedItems();
        newBanButton.setDisable(selectedUsers == null || selectedUsers.isEmpty());
        compareUidsButton.setDisable(selectedUsers == null || selectedUsers.size() < 2);

        if (selectedUsers == null || selectedUsers.isEmpty()) {
            lastSelectedUserId = null;
            return;
        }

        PlayerFX user = selectedUsers.getFirst();
        if (lastSelectedUserId != null && lastSelectedUserId.equals(user.getId())) {
            return; // Skip reload if the same user selected again
        }
        lastSelectedUserId = user.getId();

        // Clear data
        nameRecords.clear();
        userNameHistoryTableView.getSortOrder().clear();
        bans.clear();
        userBansTableView.getSortOrder().clear();
        userLastGamesTable.getItems().clear();
        userLastGamesTable.getSortOrder().clear();
        userTeamkillsTableView.getSortOrder().clear();
        teamkills.clear();
        userNoteTableView.getSortOrder().clear();
        userNotes.clear();
        avatarAssignments.clear();
        userAvatarsTableView.getSortOrder().clear();
        userGroups.clear();
        userGroupsTableView.getSortOrder().clear();

        CompletableFuture.runAsync(() -> {
            List<UserNoteFX> notes = userService.getUserNotes(user.getId());
            List<NameRecordFX> names = user.getNames();
            List<BanInfoFX> userBans = user.getBans();
            List<AvatarAssignmentFX> avatars = user.getAvatarAssignments();

            Platform.runLater(() -> {
                userNotes.addAll(notes);
                nameRecords.addAll(names);
                bans.addAll(userBans);
                avatarAssignments.addAll(avatars);
            });

            if (!userGroupsTab.isDisable()) {
                permissionService.getPlayersUserGroups(user).thenAccept(playerGroups -> {
                    Platform.runLater(() -> {
                        userGroups.addAll(playerGroups);
                        groupPermissions.addAll(
                                playerGroups.stream()
                                        .flatMap(userGroupFX -> userGroupFX.getPermissions().stream())
                                        .distinct()
                                        .toList()
                        );
                    });
                });
            }

            // Fetch last played 1k games from selected user
            CompletableFuture
                    .supplyAsync(() -> gamePlayerStatsMapper.map(
                            userService.getLastThousandPlayedGamesByFeaturedMod(
                                    user.getId(),
                                    1,
                                    featuredModFilterChoiceBox.getSelectionModel().getSelectedItem()
                            )))
                    .thenAccept(gamePlayerStats ->
                            Platform.runLater(() -> userLastGamesTable.getItems().addAll(gamePlayerStats)));
        });
    }

    public void onUserSearch() {
        users.clear();
        userSearchTableView.getSortOrder().clear();
        searchHighlightTerm.set(null);

        String searchParameter = searchUserPropertyMapping.get(searchUserProperties.getValue());
        String searchPattern = userSearchTextField.getText();

        previousUserSearchTerm = currentUserSearchTerm;
        currentUserSearchTerm = userSearchTextField.getText();

        if (Objects.equals(searchParameter, "allInOne")) {
            searchParameter = determineSearchParameter(searchPattern);
            assert searchParameter != null;
            if (searchParameter.equals("unknown")) {
                log.debug("Unknown searchParameter");
            }
        }

        if (Objects.equals(searchParameter, "login")) {
            if (searchPattern.contains("[id ") && searchPattern.contains("]")) {
                int startIndex = searchPattern.indexOf("[id ") + 4;
                int endIndex = searchPattern.indexOf("]", startIndex);
                String number = searchPattern.substring(startIndex, endIndex);
                int userID = Integer.parseInt(number);
                searchPattern = String.valueOf(userID);
                searchParameter = "id";
            }
        }

        if (searchParameter.equals("uniqueIdAssignments.uniqueId.deviceId")) {
            searchPattern = URLEncoder.encode(searchPattern, StandardCharsets.UTF_8);
        }

        String finalSearchParameter = searchParameter;
        String finalSearchPattern = searchPattern;

        Task<List<PlayerFX>> searchTask = new Task<>() {
            @Override
            protected List<PlayerFX> call() {
                return userService.findUsersByAttribute(finalSearchParameter, finalSearchPattern);
            }
        };

        searchTask.setOnSucceeded(event -> {
            users.addAll(searchTask.getValue());
            searchHighlightTerm.set(finalSearchPattern);

            String userSearchText = userSearchTextField.getText();
            String currentHistory = searchHistoryTextArea.getText();

            if (!currentHistory.startsWith(userSearchText + "\n")) {
                searchHistoryTextArea.setText(userSearchText + "\n" + currentHistory);
            }
        });

        searchTask.setOnFailed(event -> log.error("User search failed", searchTask.getException()));

        new Thread(searchTask).start();
    }

    private String determineSearchParameter(String searchPattern) {
        if (isDeviceId(searchPattern)) {
            return "uniqueIdAssignments.uniqueId.deviceId";
        }
        if (isUUID(searchPattern)) {
            return "uniqueIdAssignments.uniqueId.uuid";
        }
        if (isValidIp(searchPattern)) {
            return "recentIpAddress";
        }
        if (isEmail(searchPattern)) {
            return "email";
        }
        if (isHash(searchPattern)) {
            return "uniqueIdAssignments.uniqueId.hash";
        }
        if (Character.isDigit(searchPattern.charAt(0))) {
            return "id";
        }
        if (isLoginName(searchPattern)) {
            return "login";
        }
        if (isLoginAndName(searchPattern)) {
            return "login";
        }

        // Show info popup if search parameter could not be detected
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Search Parameter Not Detected");
        alert.setHeaderText(null);
        alert.setContentText("The value \"" + searchPattern + "\" could not be automatically detected. Please select it manually from the dropdown menu.");
        alert.showAndWait();

        return null;
    }

    private boolean isDeviceId(String searchPattern) {
        Pattern pattern = Pattern.compile(".*\\\\.*(VEN_|DEV_|SUBSYS_|REV_).*(.*)+");
        Matcher matcher = pattern.matcher(searchPattern);
        return matcher.matches();
    }

    private boolean isEmail(String searchPattern) {
        Pattern pattern = Pattern.compile("^.*@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$");
        Matcher matcher = pattern.matcher(searchPattern);
        return matcher.matches();
    }

    private boolean isHash(String searchPattern) {
        String trimmedPattern = searchPattern.replaceAll("^\\s+|\\s+$", "");
        return trimmedPattern.matches("^[a-fA-F0-9]+$") && searchPattern.length() == 32;
    }

    private boolean isLoginName(String searchPattern) {
        String trimmedPattern = searchPattern.replaceAll("^\\s+|\\s+$", "");
        return trimmedPattern.indexOf('@') == -1 && (!Character.isDigit(searchPattern.charAt(0)));
    }

    private boolean isLoginAndName(String searchPattern) {
        return searchPattern.contains("[id ") && searchPattern.contains("]");
    }

    private boolean isUUID(String searchPattern) {
        String trimmedPattern = searchPattern.replaceAll("^\\s+|\\s+$", "");
        Pattern pattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        Matcher matcher = pattern.matcher(trimmedPattern);
        return matcher.matches();
    }

    private boolean isValidIp(String searchPattern) {
        if (!searchPattern.contains("*")) {
            String[] octets = searchPattern.split("\\.");
            if (octets.length != 4) {
                return false;
            }
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } else {
            String[] parts = searchPattern.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            boolean hasDot = false;
            boolean hasNumber = false;
            for (String part : parts) {
                if (part.equals("*")) {
                    hasDot = true;
                } else {
                    try {
                        int number = Integer.parseInt(part);
                        if (number >= 0 && number <= 255) {
                            hasNumber = true;
                        } else {
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
            return hasDot && hasNumber;
        }
    }

    public void onNewBan() {
        PlayerFX selectedPlayer = userSearchTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedPlayer, "You need to select a player to create a ban.");

        BanInfoFX banInfoFX = new BanInfoFX()
                .setPlayer(selectedPlayer);

        openBanDialog(banInfoFX, true);
    }

    public void onEditBan() {
        BanInfoFX selectedBan = userBansTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedBan, "You need to select a ban to edit it.");
        if (!canEditBan(selectedBan)) {
            ViewHelper.errorDialog("Permission required",
                    "Disabled ban records require senior admin permission to edit.");
            return;
        }

        openBanDialog(selectedBan, false);
    }

    private void openBanDialog(BanInfoFX banInfoFX, boolean isNew) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        banInfoController.setBanInfo(banInfoFX);
        if (isNew) {
            banInfoController.addPostedListener(bans::add);
        }

        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle(banInfoController.getDialogTitle());
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoDialog.showAndWait();
    }

    public void addNote() {
        PlayerFX selectedPlayer = userSearchTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedPlayer, "You need to select a player to create a userNote.");

        UserNoteFX userNoteFX = new UserNoteFX();
        userNoteFX.setPlayer(selectedPlayer);

        openUserNoteDialog(userNoteFX, true);
    }

    public void editNote() {
        UserNoteFX selectedUserNote = userNoteTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedUserNote, "You need to select a player note to edit it.");

        openUserNoteDialog(selectedUserNote, false);
    }

    public void removeNote() {
        UserNoteFX selectedUserNote = userNoteTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedUserNote, "You need to select a player note to delete it.");

        if (showConfirmationDialog("Are you sure you want to delete this note?")) {
            try {
                userService.deleteUserNote(selectedUserNote.getId());

                userNotes.remove(selectedUserNote);
                userNoteTableView.getItems().remove(selectedUserNote);
            } catch (Exception e) {
                log.debug("Failed to delete the note: {}", e.getMessage());
            }
        }
    }

    private void openUserNoteDialog(UserNoteFX userNoteFX, boolean isNew) {
        UserNoteController userNoteController = uiService.loadFxml("ui/userNote.fxml");
        userNoteController.setUserNoteFX(userNoteFX);
        if (isNew) {
            userNoteController.addPostedListener(userNotes::add);
        }

        Stage userNoteDialog = new Stage();
        userNoteDialog.setTitle(isNew ? "Add new player note" : "Edit player note");
        userNoteDialog.setScene(new Scene(userNoteController.getRoot()));
        userNoteDialog.showAndWait();
    }

    public boolean showConfirmationDialog(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = alert.showAndWait();

        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private void updateEditBanButtonText(BanInfoFX selectedBan) {
        if (selectedBan == null) {
            editBanButton.setText("Replace selected ban");
            return;
        }

        if (selectedBan.getBanStatus() == BanStatus.DISABLED) {
            editBanButton.setText("Edit disabled ban (needs permission)");
            return;
        }

        editBanButton.setText(selectedBan.getBanStatus() == BanStatus.BANNED
                ? "Replace or disable active ban"
                : "Edit expired ban");
    }

    private boolean canEditBan(BanInfoFX selectedBan) {
        return selectedBan != null && selectedBan.getBanStatus() != BanStatus.DISABLED;
    }

    public void onGiveAvatar() {
        PlayerFX selectedPlayer = userSearchTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedPlayer, "You need to select a player.");
        Assert.notNull(currentSelectedAvatar.get(), "You need to select an avatar.");

        AvatarAssignmentFX avatarAssignmentFX = new AvatarAssignmentFX();
        avatarAssignmentFX.setAvatar(currentSelectedAvatar.get());
        avatarAssignmentFX.setPlayer(selectedPlayer);
        avatarAssignmentFX.setSelected(false);

        String id = avatarService.createAvatarAssignment(avatarAssignmentFX);
        avatarAssignmentFX.setId(id);

        selectedPlayer.getAvatarAssignments().add(avatarAssignmentFX);
        userAvatarsTableView.getItems().add(avatarAssignmentFX);
    }

    public void onSetExpiresAt() {
        AvatarAssignmentFX avatarAssignmentFX = userAvatarsTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(avatarAssignmentFX, "You need to select a user's avatar.");

        try {
            OffsetDateTime expiresAtODT = null;
            if (!expiresAtTextfield.getText().isEmpty()) {
                LocalDateTime dateTime = LocalDateTime.parse(expiresAtTextfield.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                expiresAtODT = OffsetDateTime.of(dateTime, ZoneOffset.UTC);
            }
            AvatarAssignmentUpdate update = new AvatarAssignmentUpdate(avatarAssignmentFX.getId(), null, Optional.ofNullable(expiresAtODT));
            avatarService.patchAvatarAssignment(update);
            avatarAssignmentFX.setExpiresAt(expiresAtODT);
        } catch (DateTimeParseException e) {
            ViewHelper.errorDialog("Validation failed", "Expiration date is invalid. Must be ISO code (i.e. 2018-12-31T23:59:59");
        }
    }

    public void onTakeAvatar() {
        AvatarAssignmentFX avatarAssignmentFX = userAvatarsTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(avatarAssignmentFX, "You need to select a user's avatar.");

        avatarService.removeAvatarAssignment(avatarAssignmentFX);
        userAvatarsTableView.getItems().remove(avatarAssignmentFX);
        avatarAssignmentFX.getPlayer().getAvatarAssignments().remove(avatarAssignmentFX);
    }

    public void openGroupDialog() {
        PlayerFX selectedPlayer = userSearchTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedPlayer, "You need to select a player.");

        GroupAddUserController groupAddUserController = uiService.loadFxml("ui/groupAddUser.fxml");
        groupAddUserController.setPlayer(selectedPlayer);
        groupAddUserController.addAddedListener(userGroups::add);

        Stage userGroupDialog = new Stage();
        userGroupDialog.setTitle("Add User Group");
        userGroupDialog.setScene(new Scene(groupAddUserController.getRoot()));
        userGroupDialog.showAndWait();
    }

    public void onRemoveGroup() {
        PlayerFX selectedPlayer = userSearchTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(selectedPlayer, "You need to select a player.");

        UserGroupFX userGroupFX = userGroupsTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(userGroupFX, "You need to select a user group.");

        if (userGroupFX.getMembers().remove(selectedPlayer)) {
            permissionService.patchUserGroup(userGroupFX);
        }

        userGroups.remove(userGroupFX);
    }

    private void loadStateCheckBox() {
        LocalPreferences.TabUserManagement settings = localPreferences.getTabUserManagement();

        includeUUIDCheckBox.setSelected(settings.isIncludeUUIDCheckBox());
        includeUIDHashCheckBox.setSelected(settings.isIncludeUIDHashCheckBox());
        includeMemorySerialNumberCheckBox.setSelected(settings.isIncludeMemorySerialNumberCheckBox());
        includeVolumeSerialNumberCheckBox.setSelected(settings.isIncludeVolumeSerialNumberCheckBox());
        includeSerialNumberCheckBox.setSelected(settings.isIncludeSerialNumberCheckBox());
        includeProcessorIdCheckBox.setSelected(settings.isIncludeProcessorIdCheckBox());
        includeManufacturerCheckBox.setSelected(settings.isIncludeManufacturerCheckBox());
        includeIPCheckBox.setSelected(settings.isIncludeIPCheckBox());
        includeProcessorNameCheckBox.setSelected(settings.isIncludeProcessorNameCheckBox());
        catchFirstLayerSmurfsOnlyCheckBox.setSelected(settings.isCatchFirstLayerSmurfsOnlyCheckBox());
        onlyShowActiveAccountsCheckBox.setSelected(settings.isOnlyShowActiveAccountsCheckBox());
        suppressExcludedItemsCheckBox.setSelected(settings.isSuppressExcludedItemsCheckBox());

    }

    public void saveOnExitContent() {
        try {
            Files.createDirectories(ApplicationPaths.resolveConfigurationDirectory());
            Files.writeString(ApplicationPaths.resolveConfigurationFile(SEARCH_HISTORY_FILE_NAME), searchHistoryTextArea.getText());
            Files.writeString(ApplicationPaths.resolveConfigurationFile(USER_NOTES_FILE_NAME), userNotesTextArea.getText());
        } catch (IOException e) {
            log.warn("Failed to save search history/user notes", e);
        }
    }

    private void loadContent() {
        try {
            if (Files.exists(ApplicationPaths.resolveConfigurationFile(SEARCH_HISTORY_FILE_NAME))) {
                String searchHistory = Files.readString(ApplicationPaths.resolveConfigurationFile(SEARCH_HISTORY_FILE_NAME));
                searchHistoryTextArea.setText(searchHistory);
            }
            if (Files.exists(ApplicationPaths.resolveConfigurationFile(USER_NOTES_FILE_NAME))) {
                String userNotes = Files.readString(ApplicationPaths.resolveConfigurationFile(USER_NOTES_FILE_NAME));
                userNotesTextArea.setText(userNotes);
            }
        } catch (IOException e) {
            log.warn("Failed to load search history/user notes", e);
        }
    }

    private String previousUserSearchTerm = "";
    private String currentUserSearchTerm = "";

    public void onUserPreviousSearch() {
        if (!previousUserSearchTerm.isEmpty()) {
            userSearchTextField.setText(previousUserSearchTerm);
            onUserSearch();
        }
    }

    private boolean isValidPlayerId(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(playerId);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void onCheckSharedGames() {
        List<PlayerFX> selectedItems = new ArrayList<>(userSearchTableView.getSelectionModel().getSelectedItems());

        String playerID1 = playerIDField1SharedGamesTextfield.getText().trim();
        String playerID2 = playerIDField2SharedGamesTextfield.getText().trim();

        if (playerID1.isEmpty() || playerID2.isEmpty()) {
            if (selectedItems.size() == 2) {
                playerID1 = selectedItems.get(0).getId();
                playerID2 = selectedItems.get(1).getId();
            } else {
                checkSharedGamesButton.setText("Check - Please select exactly two users or provide two player IDs.");
                return;
            }
        }

        if (!isValidPlayerId(playerID1) || !isValidPlayerId(playerID2)) {
            checkSharedGamesButton.setText("Check - Invalid player IDs provided.");
            return;
        }

        // Show animated "Processing..." label
        progressLabelSharedGames.setText("Processing");
        progressLabelSharedGames.setVisible(true);

        // Timeline animation
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(500), e -> {
            String text = progressLabelSharedGames.getText();
            if (text.endsWith("...")) {
                progressLabelSharedGames.setText("Processing");
            } else {
                progressLabelSharedGames.setText(text + ".");
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        userLastGamesTable.getItems().clear();

        String finalPlayerID = playerID1;
        String finalPlayerID1 = playerID2;

        Task<Set<GamePlayerStatsFX>> task = new Task<>() {
            @Override
            protected Set<GamePlayerStatsFX> call() {
                List<GamePlayerStatsFX> allPlayer1Games = gamePlayerStatsMapper.map(
                        userService.getLastThousandPlayedGamesByFeaturedMod(
                                finalPlayerID, 1, featuredModFilterChoiceBox.getSelectionModel().getSelectedItem())
                );

                List<GamePlayerStatsFX> allPlayer2Games = gamePlayerStatsMapper.map(
                        userService.getLastThousandPlayedGamesByFeaturedMod(
                                finalPlayerID1, 1, featuredModFilterChoiceBox.getSelectionModel().getSelectedItem())
                );

                Set<GameFX> player1Games = allPlayer1Games.stream()
                        .map(GamePlayerStatsFX::getGame)
                        .collect(Collectors.toSet());

                Set<GameFX> player2Games = allPlayer2Games.stream()
                        .map(GamePlayerStatsFX::getGame)
                        .collect(Collectors.toSet());

                Set<GameFX> commonGames = new HashSet<>(player1Games);
                commonGames.retainAll(player2Games);

                return allPlayer1Games.stream()
                        .filter(stats -> commonGames.contains(stats.getGame()))
                        .collect(Collectors.toSet());
            }
        };

        task.setOnSucceeded(workerStateEvent -> {
            Set<GamePlayerStatsFX> result = task.getValue();
            userLastGamesTable.getItems().setAll(result);

            timeline.stop();
            progressLabelSharedGames.setVisible(false);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Shared Games Check");
            alert.setHeaderText(null);

            if (result.isEmpty()) {
                alert.setContentText("No common games found between the selected players.");
            } else {
                alert.setContentText("Found " + result.size() + " shared game(s) between the selected players.");
            }

            alert.showAndWait();
        });

        task.setOnFailed(workerStateEvent -> {
            log.warn("Error during API request: ", task.getException());
            timeline.stop();
            progressLabelSharedGames.setVisible(false);
        });

        new Thread(task).start();
    }


    public void addListeners() {
        minimizeSearchHistoryButton.setOnAction(event -> {
            handleMinimizeSearchHistory();
        });

        minimizeUserNotesButton.setOnAction(event -> {
            handleMinimizeUserNotes();
        });
    }

    @FXML
    private void handleMinimizeSearchHistory() {
        LocalPreferences.TabUserManagement tabUserManagement = localPreferences.getTabUserManagement();
        boolean isVisible = tabUserManagement.isSearchHistoryTexAreaVisibilityState();

        if (isVisible) {
            searchHistoryTextArea.setVisible(false);
            searchHistoryTextArea.setManaged(false);
            minimizeSearchHistoryButton.setText("Restore");
            tabUserManagement.setSearchHistoryTexAreaVisibilityState(false);
        } else {
            searchHistoryTextArea.setVisible(true);
            searchHistoryTextArea.setManaged(true);
            minimizeSearchHistoryButton.setText("Minimize");
            tabUserManagement.setSearchHistoryTexAreaVisibilityState(true);
        }
    }

    @FXML
    private void handleMinimizeUserNotes() {
        LocalPreferences.TabUserManagement tabUserManagement = localPreferences.getTabUserManagement();
        boolean isVisible = tabUserManagement.isUserNotesTextAreaVisibilityState();

        if (isVisible) {
            userNotesTextArea.setVisible(false);
            userNotesTextArea.setManaged(false);
            minimizeUserNotesButton.setText("Restore");
            tabUserManagement.setUserNotesTextAreaVisibilityState(false);
        } else {
            userNotesTextArea.setVisible(true);
            userNotesTextArea.setManaged(true);
            minimizeUserNotesButton.setText("Minimize");
            tabUserManagement.setUserNotesTextAreaVisibilityState(true);
        }
    }

    public void handleCheckTemporaryBans() {
        smurfOutputTextArea.setText("");
        checkTemporaryBansButton.setDisable(true);
        checkTemporaryBansButton.setText("Check Temporary Bans (awaiting data...)");
        temporaryBanProgressLabel.setText("Fetching temporary bans...");

        bansController.syncTempBannedUsersJson(() -> {
            Platform.runLater(() -> {
                int count = bansController.loadExistingBannedUserIds(bansController.PATH_TEMP_BANNED_USERS_JSON).size();
                checkTemporaryBansButton.setText("Check Temporary Bans: " + count);
                checkTemporaryBansButton.setDisable(false);
                processBannedUsers(bansController.PATH_TEMP_BANNED_USERS_JSON, temporaryBanProgressLabel, "temporary ban");
            });
        });
    }

    public void handleCheckSmurfManagementAccounts() {
        smurfOutputTextArea.setText("");
        processBannedUsers(SmurfManagementController.SMURF_MANAGEMENT_USERS_JSON_PATH, smurfManagementProgressLabel, "smurf management");
    }

    private void processBannedUsers(Path filePath, Label progressLabel, String taskName) {
        captureSmurfCheckSettings(false);
        resetPreviousStateSmurfVillageLookup();
        Set<String> userIds = bansController.loadExistingBannedUserIds(filePath);

        if (userIds.isEmpty()) {
            Platform.runLater(() -> progressLabel.setText("Sync in banTab first."));
            return;
        }

        Task<Void> task = new Task<>() {
            private long startTime;

            @Override
            protected Void call() {
                startTime = System.currentTimeMillis();
                int total = userIds.size();
                int count = 0;

                Platform.runLater(() -> progressLabel.setText("Processing " + total + " users..."));

                for (String userId : userIds) {
                    if (isCancelled()) break;

                    try {
                        final int processed = count + 1;
                        Platform.runLater(() -> progressLabel.setText(processed + "/" + total + " users processed..."));

                        // Lookup user synchronously only for Smurf Management
                        if ("smurf management".equals(taskName)) {
                            PlayerFX playerFX = findUserById(userId);
                            if (playerFX != null) {
                                ViewHelper.saveUserToJsonFile(playerFX, SmurfManagementController.SMURF_MANAGEMENT_USERS_JSON_PATH, "Added by Run Smurf Management");
                            }
                        }

                        // Existing lookup call
                        onSmurfVillageLookup(userId);

                    } catch (Exception e) {
                        log.error("Error processing user ID {}", userId, e);
                    }

                    count++;
                }

                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    long totalSeconds = durationMs / 1000;
                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;

                    String formattedTime;
                    if (hours > 0) {
                        formattedTime = String.format("%dh %dm %ds", hours, minutes, seconds);
                    } else if (minutes > 0) {
                        formattedTime = String.format("%dm %ds", minutes, seconds);
                    } else {
                        formattedTime = String.format("%ds", seconds);
                    }

                    progressLabel.setText("Done: " + userIds.size() + "/" + userIds.size() + " users in " + formattedTime + ".");
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> progressLabel.setText("Error during " + taskName + " check."));
            }

            @Override
            protected void cancelled() {
                Platform.runLater(() -> progressLabel.setText("Operation cancelled."));
            }
        };

        new Thread(task).start();
    }

    /**
     * Synchronous method to find a user by ID.
     */
    private PlayerFX findUserById(String userId) {
        try {
            List<PlayerFX> results = userService.findUsersByAttribute("id", userId);
            return results.isEmpty() ? null : results.getFirst();
        } catch (Exception e) {
            log.error("Error finding user ID {}", userId, e);
            return null;
        }
    }

    public void userSearchSmurfVillageAddToUserTable(String userID) {
        List<PlayerFX> usersFound = userService.findUsersByAttribute("id", userID);
        Platform.runLater(() -> {
            if (users.stream().noneMatch(u -> u.getId().equals(userID))) {
                users.addAll(usersFound);
            }
        });
    }

    public List<Map<String, Object>> loadExcludedItemsFromJson() {
        File excludedItemsFile = EXCLUDED_ITEMS_FILE;
        List<Map<String, Object>> excludedItems = new ArrayList<>();

        try {
            if (excludedItemsFile.exists() && excludedItemsFile.length() > 0) {
                ObjectMapper mapper = new ObjectMapper();
                TypeReference<Map<String, List<Map<String, Object>>>> typeRef = new TypeReference<>() {
                };
                Map<String, List<Map<String, Object>>> rootData = mapper.readValue(excludedItemsFile, typeRef);
                excludedItems = rootData.getOrDefault("excluded_items", new ArrayList<>());
            } else {
                log.debug("Excluded items file is empty or missing: {}", excludedItemsFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to load excluded items: {}", e.getMessage());
        }

        return excludedItems;
    }

    @FXML
    private void checkRecentAccountsForSmurfsPause() {
        synchronized (pauseLock) {
            // Toggle pause/resume state
            isPaused = !isPaused;

            if (isPaused) {
                checkRecentAccountsForSmurfsPauseButton.setText("Resume");
            } else {
                checkRecentAccountsForSmurfsPauseButton.setText("Pause");
                pauseLock.notifyAll();
            }
        }
    }

    @FXML
    public void checkRecentAccountsForSmurfsStop() {
        synchronized (pauseLock) {
            isPaused = false;
            isStopped = true;
            cancelRequestedByUser = true;
            checkRecentAccountsForSmurfsPauseButton.setText("Pause");
            pauseLock.notifyAll();
        }
    }

    private void processSetBatched(Set<String> items, String property,
                                    Set<String> alreadyCheckedSet, String type,
                                    Set<String> cumulativeAccounts, PlayerFX currentPlayer,
                                    List<Map<String, Object>> excludedItems) {
        if (cancelRequestedByUser) {
            updateSmurfVillageLogTextArea("\nProcess canceled by user.\n");
            return;
        }

        String displayAttr = property.contains(".") ? property.substring(property.lastIndexOf('.') + 1) : property;

        // Partition: skip already-seen; exclude excluded; collect the rest for the batch call
        Set<String> toProcess = new LinkedHashSet<>();
        for (String item : items) {
            if (item == null || item.isBlank()) continue;
            if (alreadyCheckedSet.contains(item)) {
                log.debug("Ignoring duplicate: {} [{}] already processed.", type, item);
                continue;
            }
            boolean excluded = false;
            for (Map<String, Object> exItem : excludedItems) {
                Object val = exItem.get(property);
                if (val != null && item.equals(String.valueOf(val))) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) {
                if (!smurfLookupSettings.suppressExcludedItems()) {
                    updateSmurfVillageLogTextArea(String.format(
                            "\n  EXCLUDED: [%s] = [%s] (in excluded_items.json, skipping)", displayAttr, item));
                }
            } else {
                toProcess.add(item);
            }
        }

        // Mark everything as processed so we don't revisit (including excluded values)
        items.stream().filter(i -> i != null && !i.isBlank()).forEach(alreadyCheckedSet::add);

        if (toProcess.isEmpty()) return;

        Platform.runLater(() -> statusTextFieldProcessingItem.setText(type + ": [" + toProcess.size() + " value(s)]"));

        try {
            synchronized (pauseLock) {
                while (isPaused) {
                    updateSmurfVillageLogTextArea("\t\t PROCESS PAUSED. Waiting for RESUME.\n");
                    pauseLock.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        List<PlayerFX> users;
        try {
            users = userService.findUsersByAttributeIn(property, toProcess);
        } catch (HttpClientErrorException e) {
            updateSmurfVillageLogTextArea(String.format(
                    "\t\t ERROR fetching users for [%s] batch: %s\n", displayAttr, e.getMessage()));
            return;
        }

        if (smurfLookupSettings.promptOnThreshold() && users.size() > smurfLookupSettings.threshold()) {
            // Batch exceeded threshold — fall back to per-value so each hot value
            // can be individually excluded, continued, or used to cancel.
            updateSmurfVillageLogTextArea(String.format(
                    "\n  [%s] batch returned %d results (threshold: %d) — switching to per-value for exclusion control",
                    displayAttr, users.size(), smurfLookupSettings.threshold()));
            for (String value : toProcess) {
                if (cancelRequestedByUser) return;
                processSingleValue(value, property, displayAttr, cumulativeAccounts, currentPlayer, excludedItems);
            }
            return;
        }

        String currentPlayerId = currentPlayer != null ? currentPlayer.getId() : null;
        String headerLine = formatLookupHeader(displayAttr, toProcess);
        boolean showMatchedValues = toProcess.size() > 1;

        // Deduplicate by player ID — API may return the same player for multiple matching values
        Map<String, PlayerFX> deduplicated = new LinkedHashMap<>();
        for (PlayerFX p : users) {
            if (p.getId() != null) deduplicated.putIfAbsent(p.getId(), p);
        }

        List<PlayerFX> otherAccounts = deduplicated.values().stream()
                .filter(p -> !p.getId().equals(currentPlayerId))
                .toList();

        StringBuilder localLog = new StringBuilder();
        boolean headerEmitted = false;
        if (!smurfLookupSettings.onlyShowActive()) {
            localLog.append(headerLine);
            headerEmitted = true;
        }

        if (otherAccounts.isEmpty()) {
            if (!smurfLookupSettings.onlyShowActive()) localLog.append("   (no matches)");
        } else {
            if (currentPlayer != null) addPlayerDirectlyToTable(currentPlayer);
            List<String> activeLines = new ArrayList<>();
            for (PlayerFX user : otherAccounts) {
                BanInfoFX ban = user.getBans().stream()
                        .filter(b -> b.getBanStatus() == BanStatus.BANNED)
                        .findFirst()
                        .orElse(null);
                if (smurfLookupSettings.onlyShowActive() && ban != null) continue;
                String banInfo;
                String prefix;
                if (ban == null) {
                    banInfo = "ACTIVE";
                    prefix = "+";
                } else if (ban.getExpiresAt() == null) {
                    banInfo = "PERM-BANNED";
                    prefix = "-";
                } else {
                    banInfo = "TEMP-BANNED until " + ban.getExpiresAt()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    prefix = "~";
                }
                activeLines.add(String.format("\n    %s  %s  id: %s   %s",
                        prefix, user.getLogin(), user.getId(), banInfo));
                if (showMatchedValues) {
                    List<String> matchedValues = getMatchingAttributeValues(user, property, toProcess);
                    if (!matchedValues.isEmpty()) {
                        activeLines.add(String.format("\n       shared %s: %s", displayAttr, String.join(" | ", matchedValues)));
                    }
                }
                addPlayerDirectlyToTable(user);
                cumulativeAccounts.add(user.getId());
            }
            if (!activeLines.isEmpty()) {
                if (smurfLookupSettings.onlyShowActive() && !headerEmitted) localLog.append(headerLine);
                activeLines.forEach(localLog::append);
            }
        }

        if (localLog.length() > 0) updateSmurfVillageLogTextArea(localLog.toString());
    }

    private void processSingleValue(String value, String property, String displayAttr,
                                     Set<String> cumulativeAccounts, PlayerFX currentPlayer,
                                     List<Map<String, Object>> excludedItems) {
        if (cancelRequestedByUser) return;

        Platform.runLater(() -> statusTextFieldProcessingItem.setText(displayAttr + ": [" + value + "]"));

        try {
            synchronized (pauseLock) {
                while (isPaused) pauseLock.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        List<PlayerFX> foundUsers;
        try {
            foundUsers = userService.findUsersByAttribute(property, value);
        } catch (HttpClientErrorException e) {
            updateSmurfVillageLogTextArea(String.format(
                    "\t\t ERROR fetching users for [%s] = [%s]: %s\n", displayAttr, value, e.getMessage()));
            return;
        }

        String currentPlayerId = currentPlayer != null ? currentPlayer.getId() : null;

        Map<String, PlayerFX> deduplicated = new LinkedHashMap<>();
        for (PlayerFX p : foundUsers) {
            if (p.getId() != null) deduplicated.putIfAbsent(p.getId(), p);
        }

        List<PlayerFX> otherAccounts = deduplicated.values().stream()
                .filter(p -> !p.getId().equals(currentPlayerId))
                .toList();

        if (smurfLookupSettings.promptOnThreshold() && otherAccounts.size() > smurfLookupSettings.threshold()) {
            boolean decided = false;
            while (!decided && !cancelRequestedByUser) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> choice = new AtomicReference<>("continue");
                List<PlayerFX> snapshot = new ArrayList<>(otherAccounts);

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Threshold Exceeded");
                    alert.setHeaderText(String.format("[%s] = [%s]%nReturned %d results (threshold: %d)",
                            displayAttr, value, snapshot.size(), smurfLookupSettings.threshold()));
                    alert.setContentText("How do you want to handle this value?");

                    ButtonType addToExclude = new ButtonType("Add to exclusion list and skip");
                    ButtonType continueBtn  = new ButtonType("Continue");
                    ButtonType showAccounts = new ButtonType("Show Related Accounts");
                    ButtonType cancelProcess = new ButtonType("Cancel Process", ButtonBar.ButtonData.CANCEL_CLOSE);

                    alert.getButtonTypes().setAll(addToExclude, continueBtn, showAccounts, cancelProcess);
                    Optional<ButtonType> result = alert.showAndWait();

                    if (result.isPresent()) {
                        if      (result.get() == addToExclude)  choice.set("exclude");
                        else if (result.get() == continueBtn)   choice.set("continue");
                        else if (result.get() == showAccounts)  choice.set("show");
                        else                                     choice.set("cancel");
                    }
                    latch.countDown();
                });

                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

                switch (choice.get()) {
                    case "cancel" -> { cancelRequestedByUser = true; return; }
                    case "exclude" -> {
                        Map<String, Object> newExcluded = new LinkedHashMap<>();
                        newExcluded.put(property, value);
                        newExcluded.put("AddedOn", LocalDateTime.now().toString());
                        newExcluded.put(
                                "comment",
                                String.format(
                                        "Excluded by user prompt: %d related accounts found for [%s = %s]",
                                        snapshot.size(),
                                        property,
                                        value));
                        excludedItems.add(newExcluded);
                        excludedHardwareItemsController.saveExcludedItem(newExcluded);
                        updateSmurfVillageLogTextArea(String.format(
                                "\n  EXCLUDED: [%s] = [%s] (added to exclusion list)", displayAttr, value));
                        return;
                    }
                    case "show" -> {
                        CountDownLatch showLatch = new CountDownLatch(1);
                        List<PlayerFX> showSnapshot = snapshot;
                        Platform.runLater(() -> { showUserDetailsWindow(showSnapshot); showLatch.countDown(); });
                        try { showLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        // loop back — dialog re-shows
                    }
                    default -> decided = true; // "continue"
                }
            }
            if (cancelRequestedByUser) return;
        }

        String headerLine = String.format("\n  [%s]  %s", displayAttr, value);
        StringBuilder localLog = new StringBuilder();
        boolean headerEmitted = false;

        if (!smurfLookupSettings.onlyShowActive()) {
            localLog.append(headerLine);
            headerEmitted = true;
        }

        if (otherAccounts.isEmpty()) {
            if (!smurfLookupSettings.onlyShowActive()) localLog.append("   (no matches)");
        } else {
            if (currentPlayer != null) addPlayerDirectlyToTable(currentPlayer);
            List<String> activeLines = new ArrayList<>();
            for (PlayerFX user : otherAccounts) {
                BanInfoFX ban = user.getBans().stream()
                        .filter(b -> b.getBanStatus() == BanStatus.BANNED)
                        .findFirst().orElse(null);
                if (smurfLookupSettings.onlyShowActive() && ban != null) continue;
                String banInfo;
                String prefix;
                if (ban == null) {
                    banInfo = "ACTIVE"; prefix = "+";
                } else if (ban.getExpiresAt() == null) {
                    banInfo = "PERM-BANNED"; prefix = "-";
                } else {
                    banInfo = "TEMP-BANNED until " + ban.getExpiresAt()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    prefix = "~";
                }
                activeLines.add(String.format("\n    %s  %s  id: %s   %s",
                        prefix, user.getLogin(), user.getId(), banInfo));
                addPlayerDirectlyToTable(user);
                cumulativeAccounts.add(user.getId());
            }
            if (!activeLines.isEmpty()) {
                if (smurfLookupSettings.onlyShowActive() && !headerEmitted) localLog.append(headerLine);
                activeLines.forEach(localLog::append);
            }
        }

        if (localLog.length() > 0) updateSmurfVillageLogTextArea(localLog.toString());
    }

    private String formatLookupHeader(String displayAttr, Collection<String> values) {
        List<String> orderedValues = new ArrayList<>(values);
        if (orderedValues.size() <= 3) {
            return String.format("\n  [%s]  %s", displayAttr, String.join("|", orderedValues));
        }

        return String.format("\n  [%s]  %d values queried", displayAttr, orderedValues.size());
    }

    private List<String> getMatchingAttributeValues(PlayerFX player, String property, Collection<String> queriedValues) {
        Set<String> playerValues = getPlayerAttributeValues(player, property);
        if (playerValues.isEmpty()) {
            return List.of();
        }

        return queriedValues.stream()
                .filter(playerValues::contains)
                .distinct()
                .toList();
    }

    private Set<String> getPlayerAttributeValues(PlayerFX player, String property) {
        if (player == null || property == null || property.isBlank()) {
            return Set.of();
        }

        Set<String> values = new LinkedHashSet<>();
        if ("recentIpAddress".equals(property)) {
            addIfNotBlank(values, player.getRecentIpAddress());
            return values;
        }

        for (UniqueIdAssignmentFx assignment : player.getUniqueIdAssignments()) {
            UniqueIdFx uniqueId = assignment.getUniqueId();
            if (uniqueId == null) {
                continue;
            }

            switch (property) {
                case "uniqueIdAssignments.uniqueId.uuid" -> addIfNotBlank(values, uniqueId.getUuid());
                case "uniqueIdAssignments.uniqueId.hash" -> addIfNotBlank(values, uniqueId.getHash());
                case "uniqueIdAssignments.uniqueId.memorySerialNumber" -> addIfNotBlank(values, uniqueId.getMemorySerialNumber());
                case "uniqueIdAssignments.uniqueId.volumeSerialNumber" -> addIfNotBlank(values, uniqueId.getVolumeSerialNumber());
                case "uniqueIdAssignments.uniqueId.serialNumber" -> addIfNotBlank(values, uniqueId.getSerialNumber());
                case "uniqueIdAssignments.uniqueId.deviceId" -> addIfNotBlank(values, uniqueId.getDeviceId());
                case "uniqueIdAssignments.uniqueId.name" -> addIfNotBlank(values, uniqueId.getName());
                case "uniqueIdAssignments.uniqueId.processorId" -> addIfNotBlank(values, uniqueId.getProcessorId());
                case "uniqueIdAssignments.uniqueId.manufacturer" -> addIfNotBlank(values, uniqueId.getManufacturer());
                case "uniqueIdAssignments.uniqueId.SMBIOSBIOSVersion" -> addIfNotBlank(values, uniqueId.getSMBIOSBIOSVersion());
                default -> {
                    return Set.of();
                }
            }
        }

        return values;
    }

    @SuppressWarnings("unchecked")
    private void showUserDetailsWindow(List<PlayerFX> users) {
        Stage detailsStage = new Stage();
        detailsStage.setTitle("Related Accounts");

        TableView<PlayerFX> table = new TableView<>();

        TableColumn<PlayerFX, String> nameCol = new TableColumn<>("Username");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRepresentation()));

        TableColumn<PlayerFX, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));

        TableColumn<PlayerFX, String> banStatusCol = new TableColumn<>("Ban Status");
        banStatusCol.setCellValueFactory(data -> {
            BanInfoFX ban = data.getValue().getBans().stream()
                    .filter(b -> b.getBanStatus() == BanStatus.BANNED)
                    .findFirst()
                    .orElse(null);

            String status;
            if (ban == null) {
                status = "Active";
            } else if (ban.getExpiresAt() == null) {
                status = "Permanent";
            } else {
                status = "Temporary (" + ban.getExpiresAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")";
            }
            return new SimpleStringProperty(status);
        });

        TableColumn<PlayerFX, String> lastLoginCol = new TableColumn<>("Last Login");
        lastLoginCol.setCellValueFactory(data -> {
            if (data.getValue().getLastLogin() != null) {
                return new SimpleStringProperty(
                        data.getValue().getLastLogin()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                );
            } else {
                return new SimpleStringProperty("Unknown");
            }
        });

        table.getColumns().addAll(nameCol, idCol, banStatusCol, lastLoginCol);
        table.setItems(FXCollections.observableArrayList(users));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Label label = new Label(String.format("Found %d related accounts:", users.size()));
        label.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 5 0;");

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> detailsStage.close());

        HBox buttonBox = new HBox(closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        VBox layout = new VBox(10, label, table, buttonBox);
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 700, 400);
        detailsStage.setScene(scene);
        detailsStage.initModality(Modality.APPLICATION_MODAL);
        detailsStage.showAndWait();
    }

    private Set<String> alreadyCheckedUsers = new HashSet<>();
    private Set<String> alreadyCheckedUuids = new HashSet<>();
    private Set<String> alreadyCheckedHashes = new HashSet<>();
    private Set<String> alreadyCheckedIps = new HashSet<>();
    private Set<String> alreadyCheckedMemorySerialNumbers = new HashSet<>();
    private Set<String> alreadyCheckedVolumeSerialNumbers = new HashSet<>();
    private Set<String> alreadyCheckedSerialNumbers = new HashSet<>();
    private Set<String> alreadyCheckedProcessorIds = new HashSet<>();
    private Set<String> alreadyCheckedCpuNames = new HashSet<>();
    private Set<String> alreadyCheckedBiosVersions = new HashSet<>();
    private Set<String> alreadyCheckedManufacturers = new HashSet<>();

    void setStatusWorking() {
        statusLabelSmurfVillageLookup.setStyle("-fx-background-color: orange; -fx-text-fill: black; -fx-background-radius: 1;");
        statusLabelSmurfVillageLookup.setText("Processing");
    }

    private void setStatusDone() {
        statusLabelSmurfVillageLookup.setStyle("-fx-background-color: green; -fx-background-radius: 5;");
        statusLabelSmurfVillageLookup.setText("Complete  ");
        statusTextFieldProcessingPlayerID.setText("Status");
    }

    public void onSmurfVillageLookup(String playerID) {
        if (cancelRequestedByUser) {
            updateSmurfVillageLogTextArea("\nProcess canceled before starting lookup.\n");
            cancelRequestedByUser = false;
            return;
        }

        if (!smurfLookupSettings.suppressCleanOutput()) {
            updateSmurfVillageLogTextArea(String.format("\n=== Checking PlayerID: %s ===", playerID));
        }
        Platform.runLater(() -> statusTextFieldProcessingPlayerID.setText(playerID));

        if (alreadyCheckedUsers.contains(playerID)) {
            if (!smurfLookupSettings.suppressCleanOutput()) {
                updateSmurfVillageLogTextArea("\nSkipping, we already have checked that account: " + playerID);
            }
            return;
        }
        alreadyCheckedUsers.add(playerID);

        // Retrieve property mappings
        String propertyId = searchUserPropertyMapping.get("User ID");
        String propertyUUID = searchUserPropertyMapping.get("UUID");
        String propertyHash = searchUserPropertyMapping.get("Hash");
        String propertyIP = searchUserPropertyMapping.get("IP Address");
        String propertyMemorySerialNumber = searchUserPropertyMapping.get("Memory Serial Number");
        String propertyVolumeSerialNumber = searchUserPropertyMapping.get("Volume Serial Number");
        String propertySerialNumber = searchUserPropertyMapping.get("Serial Number");
        String propertyProcessorId = searchUserPropertyMapping.get("Processor ID");
        String propertyCPUName = searchUserPropertyMapping.get("CPU Name");
        String propertyManufacturer = searchUserPropertyMapping.get("Manufacturer");

        // Fetch player data and extract hardware attributes
        List<PlayerFX> userFoundList = userService.findUsersByAttribute(propertyId, playerID);
        PlayerFX currentPlayer = userFoundList.isEmpty() ? null : userFoundList.getFirst();

        // Load excluded items once for the entire attribute scan
        List<Map<String, Object>> excludedItems = loadExcludedItemsFromJson();

        Set<String> uuids = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        Set<String> ips = new HashSet<>();
        Set<String> memorySerialNumbers = new HashSet<>();
        Set<String> volumeSerialNumbers = new HashSet<>();
        Set<String> serialNumbers = new HashSet<>();
        Set<String> processorIds = new HashSet<>();
        Set<String> cpuNames = new HashSet<>();
        Set<String> manufacturers = new HashSet<>();

        for (PlayerFX user : userFoundList) {
            if (smurfLookupSettings.includeIP()) addIfNotBlank(ips, user.getRecentIpAddress());
            for (var item : user.getUniqueIdAssignments()) {
                var uid = item.getUniqueId();
                if (uid == null) continue;
                if (smurfLookupSettings.includeCpuName())       addIfNotBlank(cpuNames, uid.getName());
                if (smurfLookupSettings.includeUUID())          addIfNotBlank(uuids, uid.getUuid());
                if (smurfLookupSettings.includeHash())          addIfNotBlank(hashes, uid.getHash());
                if (smurfLookupSettings.includeMemorySerial())  addIfNotBlank(memorySerialNumbers, uid.getMemorySerialNumber());
                if (smurfLookupSettings.includeVolumeSerial())  addIfNotBlank(volumeSerialNumbers, uid.getVolumeSerialNumber());
                if (smurfLookupSettings.includeSerial())        addIfNotBlank(serialNumbers, uid.getSerialNumber());
                if (smurfLookupSettings.includeProcessorId())   addIfNotBlank(processorIds, uid.getProcessorId());
                if (smurfLookupSettings.includeManufacturer())  addIfNotBlank(manufacturers, uid.getManufacturer());
            }
        }

        Set<String> accountsWithSharedAttributes = new LinkedHashSet<>();

        // Attribute metadata helper
        class AttributeSet {
            Set<String> set;
            String property;
            Set<String> alreadyCheckedSet;
            String type;

            AttributeSet(Set<String> set, String property, Set<String> alreadyCheckedSet, String type) {
                this.set = set;
                this.property = property;
                this.alreadyCheckedSet = alreadyCheckedSet;
                this.type = type;
            }
        }

        List<AttributeSet> attributeSets = List.of(
                new AttributeSet(uuids, propertyUUID, alreadyCheckedUuids, "uuid"),
                new AttributeSet(hashes, propertyHash, alreadyCheckedHashes, "hash"),
                new AttributeSet(ips, propertyIP, alreadyCheckedIps, "ip"),
                new AttributeSet(memorySerialNumbers, propertyMemorySerialNumber, alreadyCheckedMemorySerialNumbers, "memorySerialNumber"),
                new AttributeSet(volumeSerialNumbers, propertyVolumeSerialNumber, alreadyCheckedVolumeSerialNumbers, "volumeSerialNumber"),
                new AttributeSet(serialNumbers, propertySerialNumber, alreadyCheckedSerialNumbers, "serialNumber"),
                new AttributeSet(processorIds, propertyProcessorId, alreadyCheckedProcessorIds, "processorId"),
                new AttributeSet(cpuNames, propertyCPUName, alreadyCheckedCpuNames, "cpu"),
                new AttributeSet(manufacturers, propertyManufacturer, alreadyCheckedManufacturers, "manufacturer")
        );

        for (AttributeSet attr : attributeSets) {
            if (cancelRequestedByUser) {
                updateSmurfVillageLogTextArea("\nProcess canceled during attribute scanning.\n");
                return;
            }

            processSetBatched(
                    attr.set,
                    attr.property,
                    attr.alreadyCheckedSet,
                    attr.type,
                    accountsWithSharedAttributes,
                    currentPlayer,
                    excludedItems);
        }

        if (cancelRequestedByUser) {
            updateSmurfVillageLogTextArea("\nProcess canceled before recursion.\n");
            return;
        }

        if (!accountsWithSharedAttributes.isEmpty()) {
            if (smurfLookupSettings.suppressCleanOutput()) {
                updateSmurfVillageLogTextArea(String.format("\n=== Checking PlayerID: %s ===", playerID));
            }
            updateSmurfVillageLogTextArea("\n  " + playerID + " → related: " + new ArrayList<>(accountsWithSharedAttributes) + "\n");
        } else if (!smurfLookupSettings.suppressCleanOutput()) {
            updateSmurfVillageLogTextArea("\n  → no related accounts\n");
        }

        if (smurfLookupSettings.catchFirstLayerOnly()) {
            log.trace("Smurf tracing is disabled.");
            return;
        }

        for (String id : accountsWithSharedAttributes) {
            if (cancelRequestedByUser) {
                updateSmurfVillageLogTextArea("\nProcess canceled during recursive lookup.\n");
                return;
            }
            onSmurfVillageLookup(id);
        }
    }

    private static void addIfNotBlank(Set<String> set, String value) {
        if (value != null && !value.isBlank()) set.add(value);
    }

    private void addPlayerDirectlyToTable(PlayerFX player) {
        if (player == null) return;
        Platform.runLater(() -> {
            if (users.stream().noneMatch(u -> u.getId().equals(player.getId()))) {
                users.add(player);
            }
        });
    }

    private SmurfLookupSettings readSmurfLookupSettingsFromUi() {
        return new SmurfLookupSettings(
                includeUUIDCheckBox.isSelected(),
                includeUIDHashCheckBox.isSelected(),
                includeIPCheckBox.isSelected(),
                includeMemorySerialNumberCheckBox.isSelected(),
                includeVolumeSerialNumberCheckBox.isSelected(),
                includeSerialNumberCheckBox.isSelected(),
                includeProcessorIdCheckBox.isSelected(),
                includeProcessorNameCheckBox.isSelected(),
                includeManufacturerCheckBox.isSelected(),
                SmurfLookupSettings.parseThreshold(maxMatchesBeforePromptSmurfVillageLookupTextField.getText()),
                promptUserOnThresholdExceededSmurfVillageLookupCheckBox.isSelected(),
                onlyShowActiveAccountsCheckBox.isSelected(),
                suppressNoRelatedAccountsCheckBox.isSelected(),
                suppressExcludedItemsCheckBox.isSelected(),
                catchFirstLayerSmurfsOnlyCheckBox.isSelected());
    }

    private void captureSmurfCheckSettings(boolean forceEnableAllSettings) {
        SmurfLookupSettings settings = readSmurfLookupSettingsFromUi();
        smurfLookupSettings = forceEnableAllSettings ? settings.withAllLookupIdentifiersEnabled() : settings;
    }

    private void resetPreviousStateSmurfVillageLookup() {
        alreadyCheckedUsers.clear();
        alreadyCheckedUuids.clear();
        alreadyCheckedHashes.clear();
        alreadyCheckedIps.clear();
        alreadyCheckedMemorySerialNumbers.clear();
        alreadyCheckedVolumeSerialNumbers.clear();
        alreadyCheckedSerialNumbers.clear();
        alreadyCheckedProcessorIds.clear();
        alreadyCheckedCpuNames.clear();
        alreadyCheckedBiosVersions.clear();
        alreadyCheckedManufacturers.clear();
        statusTextFieldProcessingPlayerID.setText("Status");
        statusTextFieldProcessingItem.setText("Detailed Status Information");
        logOutput.setLength(0);
        isPaused = false;
    }

    public void onLookupSmurfVillage() {
        startSmurfVillageLookup(false);
    }

    public void onLookupSmurfVillageAllEnabled() {
        startSmurfVillageLookup(true);
    }

    private void startSmurfVillageLookup(boolean forceEnableAllSettings) {
        captureSmurfCheckSettings(forceEnableAllSettings);
        users.clear();
        userSearchTableView.getSortOrder().clear();

        setStatusWorking();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                isStopped = false;
                smurfOutputTextArea.setText("");
                logOutput.setLength(0);
                cancelRequestedByUser = false;
                String lookupInput = smurfVillageLookupTextField.getText().trim();

                try {
                    String searchParameter;

                    // Check if the input is numeric
                    if (lookupInput.matches("\\d+")) {
                        searchParameter = "id";
                    } else {
                        searchParameter = determineSearchParameter(lookupInput);
                    }

                    if (searchParameter == null) {
                        Platform.runLater(() ->
                                smurfOutputTextArea.appendText("Cannot determine search parameter for input: " + lookupInput + "\n")
                        );
                        return null;
                    }

                    String userID;

                    if (searchParameter.equals("id")) {
                        // numeric input, treat id as userID
                        userID = lookupInput;
                    } else {
                        // search by name or another attribute
                        List<PlayerFX> results = userService.findUsersByAttribute(searchParameter, lookupInput);

                        if (results.isEmpty()) {
                            Platform.runLater(() ->
                                    smurfOutputTextArea.appendText("Player not found: " + lookupInput + "\n")
                            );
                            return null;
                        }

                        // extract the ID from the first matching player
                        userID = String.valueOf(results.getFirst().getId());
                    }

                    startTaskTimer();
                    onSmurfVillageLookup(userID);

                } catch (Exception e) {
                    Platform.runLater(() ->
                            smurfOutputTextArea.appendText("Error: " + e.getMessage() + "\n")
                    );
                }

                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    setStatusDone();
                    resetPreviousStateSmurfVillageLookup();

                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formattedDateTime = now.format(formatter);

                    Duration duration = Duration.between(taskStartTime, Instant.now());
                    long minutes = duration.toMinutes();
                    long seconds = duration.minusMinutes(minutes).getSeconds();

                    smurfOutputTextArea.appendText(
                            "\n\nTask Done at " + formattedDateTime + " (Duration: " + minutes + "m " + seconds + "s)"
                    );
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> log.debug("Task failed"));
            }
        };

        new Thread(task).start();
    }

    private Instant taskStartTime;

    private void startTaskTimer() {
        taskStartTime = Instant.now();
    }

    public void updateSmurfVillageLogTextArea(String... texts) {
        Platform.runLater(() -> {
            try {
                if (texts != null) {
                    for (String text : texts) {
                        if (text != null) {
                            smurfOutputTextArea.appendText(text);
                        }
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                log.error("IndexOutOfBoundsException in updateSmurfVillageLogTextArea: {}", e.getMessage(), e);
            } catch (RuntimeException e) {
                log.error("RuntimeException in updateSmurfVillageLogTextArea: {}", e.getMessage(), e);
            }
        });
    }

    private Timeline loadingAnimation;
    private String currentLoginText = "";

    public void checkRecentAccountsForSmurfs() {
        smurfOutputTextArea.setText("");
        int daysBack;
        try {
            daysBack = Integer.parseInt(daysToCheckRecentAccountsTextField.getText().trim());
        } catch (NumberFormatException e) {
            statusTextRecentAccountsForSmurfs.setText("Invalid number format for days.");
            return;
        }

        captureSmurfCheckSettings(false);
        smurfLookupSettings = smurfLookupSettings.withSuppressCleanOutput(true);
        users.clear();
        userSearchTableView.getSortOrder().clear();
        resetPreviousStateSmurfVillageLookup();
        smurfOutputTextArea.setText("");
        stopLoadingAnimation();
        setStatusWorking();
        startLoadingAnimation();
        checkRecentAccountsForSmurfsButton.setDisable(true);

        final int finalDaysBack = daysBack;
        Task<Void> task = new Task<>() {
            private int count = 0;

            @Override
            protected Void call() {
                cancelRequestedByUser = false;
                isStopped = false;

                List<PlayerFX> accounts = userService.findLatestRegistrations();

                if (accounts == null || accounts.isEmpty()) {
                    Platform.runLater(() -> statusTextRecentAccountsForSmurfs.setText("No accounts found."));
                    return null;
                }

                LocalDate cutoffDate = LocalDate.now().minusDays(finalDaysBack - 1);
                Instant cutoff = cutoffDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
                log.debug("Cutoff date: {}", cutoff);

                for (PlayerFX account : accounts) {
                    if (isStopped) {
                        isStopped = false;
                        break;
                    }

                    // Honour pause between accounts so the UI responds instantly
                    synchronized (pauseLock) {
                        while (isPaused && !isStopped) {
                            try {
                                pauseLock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                isStopped = true;
                            }
                        }
                    }
                    if (isStopped) {
                        isStopped = false;
                        break;
                    }

                    Instant lastLoginTime = null;
                    try {
                        if (account.getLastLogin() != null) {
                            lastLoginTime = account.getLastLogin().toInstant();
                        }
                    } catch (Exception e) {
                        log.warn("Invalid last login format for account: {}", account.getId(), e);
                    }

                    if (lastLoginTime == null || lastLoginTime.isBefore(cutoff)) {
                        continue;
                    }

                    onSmurfVillageLookup(account.getId());
                    count++;

                    final int currentCount = count;
                    final String formattedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault())
                            .format(lastLoginTime);

                    Platform.runLater(() -> {
                        statusTextRecentAccountsForSmurfs.setText("Checked: " + currentCount);
                        currentLoginText = "Last login: " + formattedDate;
                    });
                }

                return null;
            }

            @Override
            protected void failed() {
                super.failed();
                stopLoadingAnimation();
                Platform.runLater(() -> checkRecentAccountsForSmurfsButton.setDisable(false));
                log.error("Task failed", getException());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                stopLoadingAnimation();
                Platform.runLater(() -> {
                    setStatusDone();
                    statusTextRecentAccountsForSmurfs.setText("Completed: " + count + " accounts.");
                    checkRecentAccountsForSmurfsButton.setDisable(false);
                });
                log.debug("Task completed successfully.");
            }
        };

        new Thread(task).start();
    }

    private void startLoadingAnimation() {
        stopLoadingAnimation();
        loadingAnimation = new Timeline(
                new KeyFrame(javafx.util.Duration.ZERO, e -> updateLoadingLabel(0)),
                new KeyFrame(javafx.util.Duration.seconds(0.5), e -> updateLoadingLabel(1)),
                new KeyFrame(javafx.util.Duration.seconds(1), e -> updateLoadingLabel(2)),
                new KeyFrame(javafx.util.Duration.seconds(1.5), e -> updateLoadingLabel(3))
        );
        loadingAnimation.setCycleCount(Animation.INDEFINITE);
        loadingAnimation.play();
    }

    private void updateLoadingLabel(int dotCount) {
        String dots = switch (dotCount) {
            case 1 -> ".";
            case 2 -> "..";
            case 3 -> "...";
            default -> "";
        };
        ProgressLabelRecentAccountsSmurfCheck.setText(currentLoginText + dots);
    }

    private void stopLoadingAnimation() {
        if (loadingAnimation != null) {
            loadingAnimation.stop();
            loadingAnimation = null;
        }
        ProgressLabelRecentAccountsSmurfCheck.setText("");
    }

    private static void saveColumnRecursive(TableColumn<?, ?> column, Map<String, Double> widths, List<String> order) {
        String id = column.getId();
        if (id != null) {
            widths.put(id, column.getWidth());
            order.add(id);
            log.debug("Added column: id={}, width={}", id, column.getWidth());
        } else {
            log.debug("Skipped column with no ID. Text={}, width={}", column.getText(), column.getWidth());
        }

        for (TableColumn<?, ?> subColumn : column.getColumns()) {
            saveColumnRecursive(subColumn, widths, order);
        }
    }

    public static void saveColumnLayout(TableView<?> tableView, LocalPreferences localPreferences) {
        if (tableView == null || localPreferences == null) return;

        Map<String, Double> widths = new HashMap<>();
        List<String> order = new ArrayList<>();

        log.debug("Starting to save column layout. Total top-level columns: {}", tableView.getColumns().size());

        for (TableColumn<?, ?> column : tableView.getColumns()) {
            saveColumnRecursive(column, widths, order);
        }

        log.debug("Final column order list: {}", order);
        log.debug("Final column width map: {}", widths);

        localPreferences.getTabUserManagement().setUserSearchTableTableColumnWidthsTabUserManagement(widths);
        localPreferences.getTabUserManagement().setUserSearchTableColumnOrderTabUserManagement(order);

        log.debug("Saved column layout to localPreferences.");
    }

    public static void loadColumnLayout(TableView<?> tableView, LocalPreferences localPreferences) {
        if (tableView == null) {
            log.debug("TableView is null, cannot load layout.");
            return;
        }
        if (localPreferences == null) {
            log.debug("LocalPreferences is null, cannot load layout.");
            return;
        }

        Map<String, Double> widths = localPreferences.getTabUserManagement()
                .getUserSearchTableTableColumnWidthsTabUserManagement();
        List<String> order = localPreferences.getTabUserManagement()
                .getUserSearchTableColumnOrderTabUserManagement();

        log.debug("Loading column layout. Saved widths: {}, saved order: {}", widths, order);

        if (order != null && !order.isEmpty()) {
            tableView.getColumns().sort(Comparator.comparingInt(col -> {
                int index = order.indexOf(col.getId());
                if (index == -1) {
                    log.debug("Column {} not found in saved order, placing at end", col.getId());
                }
                return index >= 0 ? index : Integer.MAX_VALUE;
            }));
            log.debug("Applied column order to TableView.");
        }

        for (TableColumn<?, ?> column : tableView.getColumns()) {
            if (column.getId() != null && widths != null && widths.containsKey(column.getId())) {
                double width = widths.get(column.getId());
                if (column.prefWidthProperty().isBound()) {
                    column.prefWidthProperty().unbind();
                    log.debug("Unbound prefWidth for column {}", column.getId());
                }
                column.setPrefWidth(width);
                log.debug("Set width for column {}: {}", column.getId(), width);
            } else {
                log.debug("Skipping column {}: no saved width", column.getId());
            }
        }

        log.debug("Finished loading column layout.");
    }

    public static void saveSplitPanePositions(SplitPane splitPane, LocalPreferences localPreferences) {
        if (splitPane == null || localPreferences == null) return;

        List<Double> positions = splitPane.getDividers().stream()
                .map(SplitPane.Divider::getPosition)
                .collect(Collectors.toList());

        localPreferences.getTabUserManagement().setRootSplitPaneDividerPositionsTabUserManagement(positions);
        log.debug("Saved SplitPane positions: {}", positions);
    }

    public static void loadSplitPanePositions(SplitPane splitPane, LocalPreferences localPreferences) {
        if (splitPane == null || localPreferences == null) return;

        List<Double> positions = localPreferences.getTabUserManagement().getRootSplitPaneDividerPositionsTabUserManagement();
        if (positions != null && !positions.isEmpty()) {
            ObservableList<SplitPane.Divider> dividers = splitPane.getDividers();
            for (int i = 0; i < Math.min(dividers.size(), positions.size()); i++) {
                dividers.get(i).setPosition(positions.get(i));
            }
            log.debug("Loaded SplitPane positions: {}", positions);
        } else {
            log.debug("No saved SplitPane positions to load.");
        }
    }

    public void onCheckRatingManipulation() {
        List<GamePlayerStatsFX> allLoaded = new ArrayList<>(userLastGamesTable.getItems());
        if (allLoaded.isEmpty()) {
            Alert warn = new Alert(Alert.AlertType.WARNING, "No game data loaded. Select a player first.", ButtonType.OK);
            warn.setTitle("Rating Manipulation Check");
            applyDialogStylesheet(warn);
            warn.showAndWait();
            return;
        }

        // Determine how many games to analyse
        int limit = Integer.MAX_VALUE;
        String countText = ratingCheckGamesCountField.getText().trim();
        if (!countText.isEmpty()) {
            try {
                limit = Integer.parseInt(countText);
                if (limit <= 0) limit = Integer.MAX_VALUE;
            } catch (NumberFormatException ignored) {}
        }
        // Create defensive copy and sort by game start time (newest-first) to ensure most recent games are analyzed
        List<GamePlayerStatsFX> games = allLoaded.stream()
                .sorted((g1, g2) -> {
                    OffsetDateTime t1 = g1.getGame() != null ? g1.getGame().getStartTime() : null;
                    OffsetDateTime t2 = g2.getGame() != null ? g2.getGame().getStartTime() : null;
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1); // Descending order (newest first)
                })
                .limit(limit)
                .toList();

        List<GamePlayerStatsFX> ratedGames = games.stream()
                .filter(g -> g.ratingChangeProperty().get() != null)
                .toList();

        if (ratedGames.isEmpty()) {
            Alert warn = new Alert(Alert.AlertType.WARNING, "No rating journal data available for the selected games.", ButtonType.OK);
            warn.setTitle("Rating Manipulation Check");
            applyDialogStylesheet(warn);
            warn.showAndWait();
            return;
        }

        String playerName = ratedGames.stream()
                .map(g -> g.getPlayer() != null ? g.getPlayer().getLogin() : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Unknown");

        // --- Settings tab: threshold spinners ---
        Spinner<Double> spLossRate     = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 100, 70, 5));
        Spinner<Integer> spNetRating   = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(-5000, -1, -200, 50));
        Spinner<Integer> spStreak      = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 200, 10, 1));
        Spinner<Integer> spDropPerGame = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 500, 20, 5));
        Spinner<Integer> spDropCount   = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 200, 10, 1));
        Spinner<Double> spInvalidRatio = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 100, 15, 5));
        Spinner<Double> spLowScore     = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 100, 40, 5));
        for (Spinner<?> sp : List.of(spLossRate, spNetRating, spStreak, spDropPerGame, spDropCount, spInvalidRatio, spLowScore)) {
            sp.setEditable(true);
            sp.setPrefWidth(110);
        }

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(12);
        settingsGrid.setVgap(8);
        settingsGrid.setPadding(new Insets(16));
        String[][] settingsRows = {
            {"Loss Rate flag when above (%)",             "default: 70"},
            {"Net Rating Change flag when below (pts)",   "default: −200"},
            {"Consecutive Loss Streak flag when >= (games)", "default: 10"},
            {"Per-game Rating Drop flag threshold (pts)", "default: 20"},
            {"Count of such drops to flag (games)",       "default: 10"},
            {"Invalid Games Ratio flag when above (%)",   "default: 15"},
            {"Low/Zero Score Games flag when above (%)",  "default: 40"},
        };
        Spinner<?>[] spinners = {spLossRate, spNetRating, spStreak, spDropPerGame, spDropCount, spInvalidRatio, spLowScore};
        for (int i = 0; i < settingsRows.length; i++) {
            settingsGrid.add(new Label(settingsRows[i][0]), 0, i);
            settingsGrid.add(spinners[i], 1, i);
            Label hint = new Label(settingsRows[i][1]);
            hint.setStyle("-fx-text-fill: gray; -fx-font-size: 11;");
            settingsGrid.add(hint, 2, i);
        }

        // --- Analysis tab ---
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12;");
        textArea.setPrefWidth(840);
        textArea.setPrefHeight(420);

        // Recomputes the analysis text from the current spinner values and updates textArea
        final List<GamePlayerStatsFX> gamesFinal = games;
        final List<GamePlayerStatsFX> ratedGamesFinal = ratedGames;
        Runnable runAnalysis = () -> {
            double lossRatePct  = spLossRate.getValue();
            int netRatingMin    = spNetRating.getValue();
            int streakMin       = spStreak.getValue();
            int dropPerGame     = spDropPerGame.getValue();
            int dropCountMin    = spDropCount.getValue();
            double invalidPct   = spInvalidRatio.getValue();
            double lowScorePct  = spLowScore.getValue();

            int fc = 0;
            StringBuilder report = new StringBuilder();
            report.append(String.format("Analyzed %d games (%d with rating data) for: %s%n%n",
                    gamesFinal.size(), ratedGamesFinal.size(), playerName));
            report.append(String.format("%-8s %-55s %s%n", "Status", "Check", "Value"));
            report.append("─".repeat(100)).append("\n");

            // Check 1: Loss rate
            long losses = ratedGamesFinal.stream()
                    .filter(g -> g.ratingChangeProperty().get().doubleValue() < 0)
                    .count();
            double lossRate = (double) losses / ratedGamesFinal.size() * 100.0;
            boolean lossRateFlag = lossRate > lossRatePct;
            if (lossRateFlag) fc++;
            report.append(formatCheckLine(lossRateFlag,
                    String.format("Loss Rate (threshold: >%.0f%%)", lossRatePct),
                    String.format("%.1f%% (%d / %d games)", lossRate, losses, ratedGamesFinal.size())));

            // Check 2: Net rating trend
            List<GamePlayerStatsFX> withFull = ratedGamesFinal.stream()
                    .filter(g -> g.beforeRatingProperty().get() != null && g.afterRatingProperty().get() != null)
                    .toList();
            int netRatingChange = 0;
            boolean netRatingFlag = false;
            if (!withFull.isEmpty()) {
                int newestAfter = withFull.get(0).afterRatingProperty().get();
                int oldestBefore = withFull.get(withFull.size() - 1).beforeRatingProperty().get();
                netRatingChange = newestAfter - oldestBefore;
                netRatingFlag = netRatingChange < netRatingMin;
                if (netRatingFlag) fc++;
            }
            report.append(formatCheckLine(netRatingFlag,
                    String.format("Net Rating Change (threshold: <%d)", netRatingMin),
                    String.format("%+d points", netRatingChange)));

            // Check 3: Max consecutive loss streak
            int maxStreak = 0, cur = 0;
            for (GamePlayerStatsFX g : ratedGamesFinal) {
                if (g.ratingChangeProperty().get().doubleValue() < 0) { cur++; maxStreak = Math.max(maxStreak, cur); }
                else cur = 0;
            }
            boolean streakFlag = maxStreak >= streakMin;
            if (streakFlag) fc++;
            report.append(formatCheckLine(streakFlag,
                    String.format("Longest Consecutive Loss Streak (threshold: >=%d)", streakMin),
                    maxStreak + " games in a row"));

            // Check 4: Heavy per-game rating drops
            long heavyDrops = ratedGamesFinal.stream()
                    .filter(g -> g.ratingChangeProperty().get().doubleValue() <= -dropPerGame)
                    .count();
            boolean heavyDropFlag = heavyDrops >= dropCountMin;
            if (heavyDropFlag) fc++;
            report.append(formatCheckLine(heavyDropFlag,
                    String.format("Games With Drop >= -%d Rating (threshold: >=%d games)", dropPerGame, dropCountMin),
                    heavyDrops + " games"));

            // Check 5: Invalid game ratio
            long invalidCount = gamesFinal.stream()
                    .filter(g -> g.getGame() != null
                            && g.getGame().getValidity() != null
                            && g.getGame().getValidity() != Validity.VALID)
                    .count();
            double invalidRate = (double) invalidCount / gamesFinal.size() * 100.0;
            boolean invalidFlag = invalidRate > invalidPct;
            if (invalidFlag) fc++;
            report.append(formatCheckLine(invalidFlag,
                    String.format("Invalid Games Ratio (threshold: >%.0f%%)", invalidPct),
                    String.format("%.1f%% (%d / %d games)", invalidRate, invalidCount, gamesFinal.size())));

            // Check 6: Low/zero score pattern
            List<GamePlayerStatsFX> scoredGames = gamesFinal.stream()
                    .filter(g -> g.getScore() != null).toList();
            if (!scoredGames.isEmpty()) {
                long lowScoreCount = scoredGames.stream().filter(g -> g.getScore() <= 0).count();
                double lowScoreRate = (double) lowScoreCount / scoredGames.size() * 100.0;
                boolean lowScoreFlag = lowScoreRate > lowScorePct;
                if (lowScoreFlag) fc++;
                report.append(formatCheckLine(lowScoreFlag,
                        String.format("Low/Zero Score Games (threshold: >%.0f%%)", lowScorePct),
                        String.format("%.1f%% (%d / %d scored games)", lowScoreRate, lowScoreCount, scoredGames.size())));
            }

            report.append("─".repeat(100)).append("\n");
            String verdict;
            if (fc == 0)       verdict = "VERDICT: CLEAN — No suspicious patterns detected.";
            else if (fc <= 2)  verdict = String.format("VERDICT: SUSPICIOUS — %d indicator(s) flagged. Manual review recommended.", fc);
            else               verdict = String.format("VERDICT: HIGH RISK — %d indicators flagged. Strong signs of rating manipulation.", fc);
            report.append("\n").append(verdict).append("\n");
            textArea.setText(report.toString());
        };

        runAnalysis.run();
        for (Spinner<?> sp : spinners) sp.valueProperty().addListener((obs, o, n) -> runAnalysis.run());

        // --- Rating History chart tab ---
        // Reverse so index 0 = oldest game (left of chart)
        List<GamePlayerStatsFX> chronological = new ArrayList<>(games);
        Collections.reverse(chronological);

        // Build one series per leaderboard, using epoch-seconds as X so the axis shows real dates.
        LinkedHashMap<String, XYChart.Series<Number, Number>> seriesMap = new LinkedHashMap<>();
        // Parallel map: epoch-second → formatted date string, for tooltips
        LinkedHashMap<Long, String> epochToLabel = new LinkedHashMap<>();
        long minEpoch = Long.MAX_VALUE;
        long maxEpoch = Long.MIN_VALUE;

        for (GamePlayerStatsFX g : chronological) {
            OffsetDateTime time = g.getScoreTime();
            if (time == null && g.getGame() != null) time = g.getGame().getStartTime();
            if (time == null) continue;
            long epochSec = time.toEpochSecond();
            minEpoch = Math.min(minEpoch, epochSec);
            maxEpoch = Math.max(maxEpoch, epochSec);
            epochToLabel.put(epochSec,
                    time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            for (LeaderboardRatingJournalFX journal : g.getLeaderboardRatingJournals()) {
                LeaderboardFX lb = journal.getLeaderboard();
                String lbName = lb != null && lb.getTechnicalName() != null ? lb.getTechnicalName()
                        : lb != null && lb.getNameKey() != null ? lb.getNameKey()
                        : "unknown";
                Double beforeMean = journal.getMeanBefore();
                Double beforeDev = journal.getDeviationBefore();
                if (beforeMean == null || beforeDev == null) continue;
                int beforeRating = (int) (beforeMean - 3 * beforeDev);
                XYChart.Series<Number, Number> series = seriesMap.computeIfAbsent(lbName, n -> {
                    XYChart.Series<Number, Number> s = new XYChart.Series<>();
                    s.setName(n);
                    return s;
                });
                series.getData().add(new XYChart.Data<>(epochSec, beforeRating));
            }
        }
        // Append final afterRating for each leaderboard from the newest game
        if (!chronological.isEmpty()) {
            GamePlayerStatsFX newest = chronological.get(chronological.size() - 1);
            OffsetDateTime newestTime = newest.getScoreTime();
            if (newestTime == null && newest.getGame() != null) newestTime = newest.getGame().getStartTime();
            long newestEpoch = newestTime != null ? newestTime.toEpochSecond() + 1 : maxEpoch + 1;
            for (LeaderboardRatingJournalFX journal : newest.getLeaderboardRatingJournals()) {
                LeaderboardFX lb = journal.getLeaderboard();
                String lbName = lb != null && lb.getTechnicalName() != null ? lb.getTechnicalName()
                        : lb != null && lb.getNameKey() != null ? lb.getNameKey()
                        : "unknown";
                Double afterMean = journal.getMeanAfter();
                Double afterDev = journal.getDeviationAfter();
                if (afterMean == null || afterDev == null) continue;
                int afterRating = (int) (afterMean - 3 * afterDev);
                XYChart.Series<Number, Number> s = seriesMap.get(lbName);
                if (s != null) s.getData().add(new XYChart.Data<>(newestEpoch, afterRating));
            }
        }

        int totalPoints = seriesMap.values().stream().mapToInt(s -> s.getData().size()).sum();

        // Compute tick unit: aim for ~8 labelled ticks, rounded to whole days
        long rangeSeconds = minEpoch == Long.MAX_VALUE ? 86400L : Math.max(86400L, maxEpoch - minEpoch);
        long tickUnit = Math.max(86400L, (rangeSeconds / 8 / 86400 + 1) * 86400L);
        long axisMin = minEpoch == Long.MAX_VALUE ? 0L : minEpoch - 86400L;
        long axisMax = minEpoch == Long.MAX_VALUE ? 86400L : maxEpoch + 86400L;

        DateTimeFormatter axisDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        NumberAxis xAxis = new NumberAxis(axisMin, axisMax, tickUnit);
        xAxis.setLabel("Date");
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelRotation(40);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number v) {
                return Instant.ofEpochSecond(v.longValue()).atZone(ZoneId.systemDefault()).format(axisDateFmt);
            }
            @Override public Number fromString(String s) { return null; }
        });

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Rating");
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Rating History — " + playerName + " (last " + games.size() + " games)");
        chart.setAnimated(false);
        chart.setLegendVisible(seriesMap.size() > 1);
        chart.setCreateSymbols(totalPoints <= 150);
        chart.setPrefWidth(840);
        chart.setPrefHeight(440);
        chart.getData().addAll(seriesMap.values());

        // Tooltips: include date + leaderboard + rating
        for (XYChart.Series<Number, Number> series : seriesMap.values()) {
            for (XYChart.Data<Number, Number> dp : series.getData()) {
                String dateLabel = epochToLabel.getOrDefault(dp.getXValue().longValue(), "");
                String label = (dateLabel.isEmpty() ? "" : dateLabel + "\n")
                        + series.getName() + ": " + dp.getYValue().intValue();
                dp.nodeProperty().addListener((obs, oldNode, node) -> {
                    if (node != null) Tooltip.install(node, new Tooltip(label));
                });
            }
        }

        // Drag-select zoom: draw a selection box; on release, zoom to that region.
        // Double-click resets to full view.
        Rectangle selectionRect = new Rectangle(0, 0, 0, 0);
        selectionRect.setFill(Color.CORNFLOWERBLUE.deriveColor(0, 1, 1, 0.25));
        selectionRect.setStroke(Color.CORNFLOWERBLUE);
        selectionRect.setStrokeWidth(1);
        selectionRect.setVisible(false);
        selectionRect.setMouseTransparent(true);
        Pane overlay = new Pane(selectionRect);
        overlay.setMouseTransparent(true);
        StackPane chartContainer = new StackPane(chart, overlay);

        final double[] dragAnchor = {0, 0};
        chart.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                dragAnchor[0] = e.getX();
                dragAnchor[1] = e.getY();
                selectionRect.setX(e.getX());
                selectionRect.setY(e.getY());
                selectionRect.setWidth(0);
                selectionRect.setHeight(0);
                selectionRect.setVisible(true);
            }
        });
        chart.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                double x = Math.min(e.getX(), dragAnchor[0]);
                double y = Math.min(e.getY(), dragAnchor[1]);
                selectionRect.setX(x);
                selectionRect.setY(y);
                selectionRect.setWidth(Math.abs(e.getX() - dragAnchor[0]));
                selectionRect.setHeight(Math.abs(e.getY() - dragAnchor[1]));
            }
        });
        chart.setOnMouseReleased(e -> {
            if (!selectionRect.isVisible()) return;
            selectionRect.setVisible(false);
            double w = selectionRect.getWidth();
            double h = selectionRect.getHeight();
            if (w < 10 || h < 10) return; // ignore tiny drags / clicks
            // Convert chart-local pixel coords to data values via axis coordinate transforms
            double x1 = selectionRect.getX();
            double x2 = x1 + w;
            double y1 = selectionRect.getY();
            double y2 = y1 + h;
            double dataX1 = xAxis.getValueForDisplay(xAxis.sceneToLocal(chart.localToScene(x1, y1)).getX()).doubleValue();
            double dataX2 = xAxis.getValueForDisplay(xAxis.sceneToLocal(chart.localToScene(x2, y1)).getX()).doubleValue();
            double dataY1 = yAxis.getValueForDisplay(yAxis.sceneToLocal(chart.localToScene(x1, y1)).getY()).doubleValue();
            double dataY2 = yAxis.getValueForDisplay(yAxis.sceneToLocal(chart.localToScene(x1, y2)).getY()).doubleValue();
            long newMin = (long) Math.min(dataX1, dataX2);
            long newMax = (long) Math.max(dataX1, dataX2);
            long newTickUnit = Math.max(3600L, (newMax - newMin) / 8);
            xAxis.setLowerBound(newMin);
            xAxis.setUpperBound(newMax);
            xAxis.setTickUnit(newTickUnit);
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(Math.min(dataY1, dataY2));
            yAxis.setUpperBound(Math.max(dataY1, dataY2));
        });
        // Double-click resets zoom to the full data range
        chart.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                xAxis.setLowerBound(axisMin);
                xAxis.setUpperBound(axisMax);
                xAxis.setTickUnit(tickUnit);
                yAxis.setAutoRanging(true);
            }
        });

        // --- Tabbed dialog ---
        Tab analysisTab = new Tab("Analysis", textArea);
        analysisTab.setClosable(false);
        Tab chartTab = new Tab("Rating History", chartContainer);
        chartTab.setClosable(false);
        Tab settingsTab = new Tab("Settings", settingsGrid);
        settingsTab.setClosable(false);

        TabPane tabPane = new TabPane(analysisTab, chartTab, settingsTab);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Rating Manipulation Analysis — " + playerName);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().setPrefWidth(880);
        dialog.getDialogPane().setPrefHeight(510);
        applyDialogStylesheet(dialog.getDialogPane());
        dialog.showAndWait();
    }

    private static String formatCheckLine(boolean flagged, String checkName, String value) {
        return String.format("[%-4s]  %-45s %s%n", flagged ? "FLAG" : "OK", checkName, value);
    }

    private void applyDialogStylesheet(Alert alert) {
        applyDialogStylesheet(alert.getDialogPane());
    }

    private void applyDialogStylesheet(javafx.scene.control.DialogPane pane) {
        String stylesheet = localPreferences.getTabSettings().isDarkModeCheckBox()
                ? "/style/main-dark.css" : "/style/main-light.css";
        var resource = getClass().getResource(stylesheet);
        if (resource != null) {
            pane.getStylesheets().add(resource.toExternalForm());
        }
    }

    public void onCompareUids() {
        List<PlayerFX> selected = List.copyOf(userSearchTableView.getSelectionModel().getSelectedItems());
        if (selected.size() < 2) return;

        // field label -> value -> (player name -> latest assignment timestamp)
        Map<String, Map<String, Map<String, OffsetDateTime>>> byField = new LinkedHashMap<>();
        for (String label : new String[]{"Hash", "UUID", "Volume S/N", "Memory S/N", "Serial Number",
                "Processor ID", "Device ID", "CPU Name", "Manufacturer", "BIOS Version"}) {
            byField.put(label, new LinkedHashMap<>());
        }

        for (PlayerFX player : selected) {
            String playerName = player.getRepresentation();
            for (UniqueIdAssignmentFx assignment : player.getUniqueIdAssignments()) {
                UniqueIdFx uid = assignment.getUniqueId();
                if (uid == null) continue;
                OffsetDateTime ts = assignment.getUpdateTime();
                recordUidValue(byField, "Hash",         uid.getHash(),              playerName, ts);
                recordUidValue(byField, "UUID",         uid.getUuid(),              playerName, ts);
                recordUidValue(byField, "Volume S/N",   uid.getVolumeSerialNumber(), playerName, ts);
                recordUidValue(byField, "Memory S/N",   uid.getMemorySerialNumber(), playerName, ts);
                recordUidValue(byField, "Serial Number",uid.getSerialNumber(),       playerName, ts);
                recordUidValue(byField, "Processor ID", uid.getProcessorId(),        playerName, ts);
                recordUidValue(byField, "Device ID",    uid.getDeviceId(),           playerName, ts);
                recordUidValue(byField, "CPU Name",     uid.getName(),               playerName, ts);
                recordUidValue(byField, "Manufacturer", uid.getManufacturer(),       playerName, ts);
                recordUidValue(byField, "BIOS Version", uid.getSMBIOSBIOSVersion(),  playerName, ts);
            }
        }

        List<SharedUidEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, OffsetDateTime>>> fieldEntry : byField.entrySet()) {
            for (Map.Entry<String, Map<String, OffsetDateTime>> valueEntry : fieldEntry.getValue().entrySet()) {
                Map<String, OffsetDateTime> players = valueEntry.getValue();
                if (players.size() < 2) continue;
                OffsetDateTime lastSeen = players.values().stream()
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
                entries.add(new SharedUidEntry(
                        fieldEntry.getKey(), valueEntry.getKey(),
                        String.join(", ", players.keySet()), lastSeen));
            }
        }

        showUidComparisonWindow(selected, entries);
    }

    private void recordUidValue(Map<String, Map<String, Map<String, OffsetDateTime>>> byField,
                                String field, String value, String playerName, OffsetDateTime ts) {
        if (value == null || value.isBlank()) return;
        byField.get(field)
               .computeIfAbsent(value, k -> new LinkedHashMap<>())
               .merge(playerName, ts, (a, b) -> {
                   if (a == null) return b;
                   if (b == null) return a;
                   return b.isAfter(a) ? b : a;
               });
    }

    @SuppressWarnings("unchecked")
    private void showUidComparisonWindow(List<PlayerFX> players, List<SharedUidEntry> entries) {
        String names = players.stream().map(PlayerFX::getRepresentation).collect(Collectors.joining(", "));
        Stage stage = new Stage();
        stage.setTitle("UID Comparison — " + names);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        TableColumn<SharedUidEntry, String> fieldCol = new TableColumn<>("Field");
        fieldCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fieldLabel()));
        fieldCol.setPrefWidth(110);

        TableColumn<SharedUidEntry, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().value()));
        valueCol.setPrefWidth(260);

        TableColumn<SharedUidEntry, String> sharedByCol = new TableColumn<>("Shared By");
        sharedByCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sharedBy()));
        sharedByCol.setPrefWidth(200);

        TableColumn<SharedUidEntry, String> lastSeenCol = new TableColumn<>("Last Seen");
        lastSeenCol.setCellValueFactory(d -> {
            OffsetDateTime ts = d.getValue().lastSeen();
            return new SimpleStringProperty(ts != null ? ts.format(fmt) : "—");
        });
        lastSeenCol.setPrefWidth(130);

        TableView<SharedUidEntry> table = new TableView<>();
        table.getColumns().addAll(fieldCol, valueCol, sharedByCol, lastSeenCol);
        table.setItems(FXCollections.observableArrayList(entries));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        String summary = entries.isEmpty()
                ? "No shared UID identifiers found between the selected accounts."
                : String.format("Found %d shared identifier(s) across %d selected accounts:", entries.size(), players.size());
        Label label = new Label(summary);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 5 0;");

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());
        HBox buttonBox = new HBox(closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        VBox layout = new VBox(10, label, table, buttonBox);
        layout.setPadding(new Insets(10));

        stage.setScene(new Scene(layout, 820, 460));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    private record SharedUidEntry(String fieldLabel, String value, String sharedBy, OffsetDateTime lastSeen) {}

    public void onSave() {
        saveColumnLayout(userSearchTableView, localPreferences);
        saveSplitPanePositions(root, localPreferences);
        LocalPreferences.TabUserManagement tab = localPreferences.getTabUserManagement();
        ViewHelper.saveColumnLayout(userBansTableView, tab.getUserBansTableColumnWidths(), tab.getUserBansTableColumnOrder());
        ViewHelper.saveColumnLayout(userNoteTableView, tab.getUserNoteTableColumnWidths(), tab.getUserNoteTableColumnOrder());
        ViewHelper.saveColumnLayout(userNameHistoryTableView, tab.getUserNameHistoryTableColumnWidths(), tab.getUserNameHistoryTableColumnOrder());
        ViewHelper.saveColumnLayout(userLastGamesTable, tab.getUserLastGamesTableColumnWidths(), tab.getUserLastGamesTableColumnOrder());
        ViewHelper.saveColumnLayout(userAvatarsTableView, tab.getUserAvatarsTableColumnWidths(), tab.getUserAvatarsTableColumnOrder());
        ViewHelper.saveColumnLayout(userGroupsTableView, tab.getUserGroupsTableColumnWidths(), tab.getUserGroupsTableColumnOrder());
        ViewHelper.saveColumnLayout(permissionsTableView, tab.getPermissionsTableColumnWidths(), tab.getPermissionsTableColumnOrder());
    }
}

