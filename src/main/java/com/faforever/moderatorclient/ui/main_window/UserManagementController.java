package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.BanStatus;
import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.commons.api.update.AvatarAssignmentUpdate;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.AvatarService;
import com.faforever.moderatorclient.api.domain.PermissionService;
import com.faforever.moderatorclient.api.domain.UserService;
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
import java.nio.file.Paths;
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

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

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
            playerIDField2SharedGamesTextfield;

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

    // Snapshots of UI state captured on FX thread before each background task starts.
    // Background threads read these instead of touching live JavaFX nodes.
    private volatile boolean snapIncludeUUID;
    private volatile boolean snapIncludeHash;
    private volatile boolean snapIncludeIP;
    private volatile boolean snapIncludeMemorySerial;
    private volatile boolean snapIncludeVolumeSerial;
    private volatile boolean snapIncludeSerial;
    private volatile boolean snapIncludeProcessorId;
    private volatile boolean snapIncludeCpuName;
    private volatile boolean snapIncludeManufacturer;
    private volatile int     snapThreshold = 10;
    private volatile boolean snapPromptOnThreshold;
    private volatile boolean snapOnlyShowActive;

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
    @FXML
    public TextArea searchHistoryTextArea;
    @FXML
    public TextArea userNotesTextArea;

    private static final String SEARCH_HISTORY_FILE = CONFIGURATION_FOLDER + File.separator +  "searchHistory.txt";
    private static final String USER_NOTES_FILE = CONFIGURATION_FOLDER + File.separator + "userNotes.txt";
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

    private final File EXCLUDED_ITEMS_FILE = new File("data/excluded_items.json");

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

        userSearchTableView.getColumns().forEach(column -> {
            if (column.getId() == null) {
                column.setId(column.getText().replaceAll("\\s+", ""));
            }
        });

        Platform.runLater(() -> {
            loadColumnLayout(userSearchTableView, localPreferences);
            loadSplitPanePositions(root, localPreferences);
            StartupSyncBans();

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
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), true, communicationService);
        ViewHelper.buildNotesTableView(userNoteTableView, userNotes, false);
        ViewHelper.buildNameHistoryTableView(userNameHistoryTableView, nameRecords);
        ViewHelper.buildBanTableView(userBansTableView, bans, false, localPreferences);
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
        editBanButton.disableProperty().bind(userBansTableView.getSelectionModel().selectedItemProperty().isNull());
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

    private void StartupSyncBans() {
        int smurfCount = bansController.loadExistingBannedUserIds(SmurfManagementController.SMURF_MANAGEMENT_USERS_JSON_PATH).size();
        checkSmurfManagementAccountsButton.setText("Run Smurf Management: " + smurfCount);
    }

    private void initializeSearchProperties() {
        //TODO check OM how it can be better
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

        openBanDialog(selectedBan, false);
    }

    private void openBanDialog(BanInfoFX banInfoFX, boolean isNew) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        banInfoController.setBanInfo(banInfoFX);
        if (isNew) {
            banInfoController.addPostedListener(bans::add);
        }

        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle(isNew ? "Apply new ban" : "Edit ban");
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

    }

    public void saveOnExitContent() {
        try {
            Files.write(Paths.get(SEARCH_HISTORY_FILE), searchHistoryTextArea.getText().getBytes());
            Files.write(Paths.get(USER_NOTES_FILE), userNotesTextArea.getText().getBytes());
        } catch (IOException e) {
            log.debug(String.valueOf(e));
        }
    }

    private void loadContent() {
        try {
            if (Files.exists(Paths.get(SEARCH_HISTORY_FILE))) {
                String searchHistory = new String(Files.readAllBytes(Paths.get(SEARCH_HISTORY_FILE)));
                searchHistoryTextArea.setText(searchHistory);
            }
            if (Files.exists(Paths.get(USER_NOTES_FILE))) {
                String userNotes = new String(Files.readAllBytes(Paths.get(USER_NOTES_FILE)));
                userNotesTextArea.setText(userNotes);
            }
        } catch (IOException e) {
            log.debug(String.valueOf(e));
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


    //TODO refactor with localpref
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
        captureSmurfCheckSettings();
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
                        log.error("Error processing user ID {}: {}", userId, e.getMessage());
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
            log.error("Error finding user ID {}: {}", userId, e.getMessage());
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
            checkRecentAccountsForSmurfsPauseButton.setText("Pause");
            pauseLock.notifyAll();
        }
    }

    private List<Object> processUsers(String attributeName, String attributeValue, String currentPlayerID) {
        List<Object> foundAccounts = new ArrayList<>();

        try {
            String displayAttr = attributeName.contains(".") ? attributeName.substring(attributeName.lastIndexOf('.') + 1) : attributeName;
            String headerLine = String.format("\n  [%s]  %s", displayAttr, attributeValue);
            if (!snapOnlyShowActive) {
                updateSmurfVillageLogTextArea(headerLine);
            }

            // --- Load JSON-based excluded items ---
            List<Map<String, Object>> excludedItems = loadExcludedItemsFromJson();

            int threshold = snapThreshold;
            boolean promptUserOnThresholdExceeded = snapPromptOnThreshold;

            synchronized (pauseLock) {
                while (isPaused) {
                    updateSmurfVillageLogTextArea("\t\t PROCESS PAUSED. Waiting for RESUME.\n");
                    pauseLock.wait();
                }
            }

            // --- Exclusion check based on JSON ---
            boolean isExcluded = false;
            for (Map<String, Object> item : excludedItems) {
                for (Object value : item.values()) {
                    if (attributeValue.equals(String.valueOf(value))) {
                        isExcluded = true;
                        break;
                    }
                }
                if (isExcluded) break;
            }

            if (isExcluded) {
                updateSmurfVillageLogTextArea(
                        String.format("\n  EXCLUDED: [%s] = [%s] (in excluded_items.json, skipping)",
                                displayAttr, attributeValue));
                return foundAccounts;
            }

            // --- Skip null or empty attributes ---
            List<PlayerFX> users = Collections.emptyList();
            if (!"recentIpAddress".equals(attributeName) || (attributeValue != null && !attributeValue.trim().isEmpty())) {
                try {
                    users = userService.findUsersByAttribute(attributeName, attributeValue);

                    // --- Threshold dialog for large result sets (runs on FX thread) ---
                    if (promptUserOnThresholdExceeded  && users.size() > threshold) {
                        final CountDownLatch latch = new CountDownLatch(1);
                        final AtomicReference<ButtonType> userChoice = new AtomicReference<>();

                        List<PlayerFX> finalUsers = users;
                        int finalThreshold = threshold;
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                            alert.setTitle("Threshold exceeded for number of matches detected");
                            String warningMessage = String.format(
                                    "Found %d accounts for [%s] = [%s], exceeding the threshold of %d.\n\n" +
                                            "Do you want to continue processing, or add this value to the exclusion list and skip it, or cancel the process?",
                                    finalUsers.size(), attributeName, attributeValue, finalThreshold);
                            alert.setContentText(warningMessage);

                            ButtonType continueButton = new ButtonType("Continue");
                            ButtonType excludeButton = new ButtonType("Add to exclusion list and skip");
                            ButtonType detailsButton = new ButtonType("Show Related Accounts");
                            ButtonType cancelButton = new ButtonType("Cancel Process");
                            alert.getButtonTypes().setAll(continueButton, excludeButton, detailsButton, cancelButton);

                            // Disable keyboard input
                            DialogPane dialogPane = alert.getDialogPane();
                            dialogPane.addEventFilter(KeyEvent.KEY_PRESSED, Event::consume);

                            boolean detailsClicked[] = {false}; // Track if details button has been clicked

                            while (true) {
                                Optional<ButtonType> result = alert.showAndWait();
                                if (result.isPresent()) {
                                    ButtonType chosen = result.get();

                                    if (chosen == detailsButton) {
                                        showUserDetailsWindow(finalUsers);
                                        detailsClicked[0] = true;

                                        // Grey out the "Show Related Accounts" button
                                        // TODO Causes some nasty bug if user clicks more than once on it
                                        Button detailsButtonNode = (Button) dialogPane.lookupButton(detailsButton);
                                        detailsButtonNode.setDisable(true);
                                    } else {
                                        userChoice.set(chosen);
                                        break;
                                    }
                                } else {
                                    // If dialog closed without selection, treat as cancel
                                    userChoice.set(cancelButton);
                                    break;
                                }
                            }

                            latch.countDown();
                        });

                        // Wait for user input
                        latch.await();

                        ButtonType resultType = userChoice.get();
                        if (resultType != null) {
                            String buttonText = resultType.getText();

                            if ("Add to exclusion list and skip".equals(buttonText)) {
                                Map<String, Object> newExcludedItem = new HashMap<>();
                                newExcludedItem.put(attributeName, attributeValue);
                                newExcludedItem.put("AddedOn", LocalDateTime.now().toString());
                                newExcludedItem.put("comment", String.format(
                                        "Excluded by user prompt: %d related accounts found for [%s = %s]",
                                        users.size(), attributeName, attributeValue));

                                excludedHardwareItemsController.saveExcludedItem(newExcludedItem);
                                excludedHardwareItemsController.updateStatsDisplay();

                                updateSmurfVillageLogTextArea(String.format(
                                        "\t\t Added [%s = %s] to exclusion list and skipped (%d related accounts found).\n",
                                        attributeName, attributeValue, users.size()));

                                return foundAccounts; // skip processing
                            } else if ("Cancel Process".equals(buttonText)) {
                                cancelRequestedByUser = true;
                                updateSmurfVillageLogTextArea("\t\t User canceled operation.\n");
                                return Collections.emptyList();
                            }
                            // Continue if "Continue" clicked
                        }
                    }

                } catch (HttpClientErrorException e) {
                    updateSmurfVillageLogTextArea(
                            String.format("\t\t ERROR fetching users for [%s] [%s]: %s\n",
                                    attributeName, attributeValue, e.getMessage()));
                }
            }

            // --- Exclude root player ---
            List<PlayerFX> otherAccounts = users.stream()
                    .filter(user -> !user.getId().equals(currentPlayerID))
                    .toList();

            // --- Add root player to table if other accounts found ---
            if (!otherAccounts.isEmpty()) {
                addNewAccountsToTable(Collections.singletonList(currentPlayerID));
                foundAccounts.add(currentPlayerID);
            }

            if (otherAccounts.isEmpty()) {
                if (!snapOnlyShowActive) {
                    updateSmurfVillageLogTextArea("   (no matches)");
                }
            } else {
                List<String> activeLines = new ArrayList<>();
                for (PlayerFX user : otherAccounts) {
                    BanInfoFX ban = user.getBans().stream()
                            .filter(b -> b.getBanStatus() == BanStatus.BANNED)
                            .findFirst()
                            .orElse(null);

                    if (snapOnlyShowActive && ban != null) {
                        continue;
                    }

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

                    if (user.getId() != null && !foundAccounts.contains(user.getId())) {
                        foundAccounts.add(user.getId());
                        addNewAccountsToTable(Collections.singletonList(user.getId()));
                    }
                }

                if (!activeLines.isEmpty()) {
                    if (snapOnlyShowActive) {
                        updateSmurfVillageLogTextArea(headerLine);
                    }
                    activeLines.forEach(line -> updateSmurfVillageLogTextArea(line));
                }
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return foundAccounts;
    }

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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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

    public Set<String> alreadyCheckedUsers = new HashSet<>();
    public Set<String> alreadyCheckedUuids = new HashSet<>();
    public Set<String> alreadyCheckedHashes = new HashSet<>();
    public Set<String> alreadyCheckedIps = new HashSet<>();
    public Set<String> alreadyCheckedMemorySerialNumbers = new HashSet<>();
    public Set<String> alreadyCheckedVolumeSerialNumbers = new HashSet<>();
    public Set<String> alreadyCheckedSerialNumbers = new HashSet<>();
    public Set<String> alreadyCheckedProcessorIds = new HashSet<>();
    public Set<String> alreadyCheckedCpuNames = new HashSet<>();
    public Set<String> alreadyCheckedBiosVersions = new HashSet<>();
    public Set<String> alreadyCheckedManufacturers = new HashSet<>();

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

        startTaskTimer();
        String initialLog = String.format("\n=== Checking PlayerID: %s ===", playerID);
        updateSmurfVillageLogTextArea(initialLog);
        Platform.runLater(() -> statusTextFieldProcessingPlayerID.setText(playerID));

        if (alreadyCheckedUsers.contains(playerID)) {
            updateSmurfVillageLogTextArea("\nSkipping, we already have checked that account: " + playerID);
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

        // Collect user attributes
        Set<PlayerFX> userFound = new HashSet<>(userService.findUsersByAttribute(propertyId, playerID));
        Set<String> uuids = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        Set<String> ips = new HashSet<>();
        Set<String> memorySerialNumbers = new HashSet<>();
        Set<String> volumeSerialNumbers = new HashSet<>();
        Set<String> serialNumbers = new HashSet<>();
        Set<String> processorIds = new HashSet<>();
        Set<String> cpuNames = new HashSet<>();
        Set<String> manufacturers = new HashSet<>();

        userFound.forEach(user -> user.getUniqueIdAssignments().forEach(item -> {
            if (snapIncludeCpuName)      cpuNames.add(item.getUniqueId().getName());
            if (snapIncludeUUID)         uuids.add(item.getUniqueId().getUuid());
            if (snapIncludeHash)         hashes.add(item.getUniqueId().getHash());
            if (snapIncludeIP)           ips.add(user.getRecentIpAddress());
            if (snapIncludeMemorySerial) memorySerialNumbers.add(item.getUniqueId().getMemorySerialNumber());
            if (snapIncludeVolumeSerial) volumeSerialNumbers.add(item.getUniqueId().getVolumeSerialNumber());
            if (snapIncludeSerial)       serialNumbers.add(item.getUniqueId().getSerialNumber());
            if (snapIncludeProcessorId)  processorIds.add(item.getUniqueId().getProcessorId());
            if (snapIncludeManufacturer) manufacturers.add(item.getUniqueId().getManufacturer());
        }));

        Set<Object> accountsWithSharedAttributes = new LinkedHashSet<>(); // deduplicated cumulative set

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

        // Process each attribute set incrementally, respecting cancellation
        for (AttributeSet attr : attributeSets) {
            if (cancelRequestedByUser) {
                updateSmurfVillageLogTextArea("\nProcess canceled during attribute scanning.\n");
                return;
            }

            processSetIncremental(
                    attr.set,
                    attr.property,
                    attr.alreadyCheckedSet,
                    attr.type,
                    accountsWithSharedAttributes,
                    playerID);
        }

        if (cancelRequestedByUser) {
            updateSmurfVillageLogTextArea("\nProcess canceled before recursion.\n");
            return;
        }

        // Log related accounts
        List<String> relatedAccounts = accountsWithSharedAttributes.stream()
                .map(Object::toString)
                .filter(id -> !id.equals(playerID))
                .toList();

        if (!relatedAccounts.isEmpty()) {
            updateSmurfVillageLogTextArea("\n  " + playerID + " → related: " + relatedAccounts + "\n");
        } else {
            updateSmurfVillageLogTextArea("\n  → no related accounts\n");
        }

        if (catchFirstLayerSmurfsOnlyCheckBox.isSelected()) {
            log.trace("Smurf tracing is disabled.");
            return;
        }

        // Recursively check found accounts, respecting cancellation
        for (Object s : accountsWithSharedAttributes) {
            if (cancelRequestedByUser) {
                updateSmurfVillageLogTextArea("\nProcess canceled during recursive lookup.\n");
                return;
            }
            onSmurfVillageLookup((String) s);
        }
    }

    // Incremental processing with immediate table addition
    private void processSetIncremental(Set<String> items, String property,
                                       Set<String> alreadyCheckedSet, String type,
                                       Set<Object> cumulativeAccounts, String currentPlayerID) {
        for (String item : items) {
            if (cancelRequestedByUser) {
                updateSmurfVillageLogTextArea("\nProcess canceled by user.\n");
                return;
            }

            Platform.runLater(() -> statusTextFieldProcessingItem.setText(type + ": " + item));

            if (!alreadyCheckedSet.contains(item)) {
                List<Object> accountsFromThisItem = processUsers(property, item, currentPlayerID);

                // Only add accounts if the list is not empty
                if (!accountsFromThisItem.isEmpty()) {
                    for (Object accountId : accountsFromThisItem) {
                        addNewAccountsToTable(Collections.singletonList(accountId));
                        cumulativeAccounts.add(accountId);
                    }
                }

                alreadyCheckedSet.add(item);
            } else {
                String message = String.format("\nIgnoring duplicate: %s [%s] already processed.", type, item);
                log.debug(message);
                updateSmurfVillageLogTextArea(message);
            }
        }
    }

    // Table insertion helper
    private void addNewAccountsToTable(List<Object> accounts) {
        for (Object accountId : accounts) {
            userSearchSmurfVillageAddToUserTable((String) accountId);
        }
    }

    private void captureSmurfCheckSettings() {
        snapIncludeUUID          = includeUUIDCheckBox.isSelected();
        snapIncludeHash          = includeUIDHashCheckBox.isSelected();
        snapIncludeIP            = includeIPCheckBox.isSelected();
        snapIncludeMemorySerial  = includeMemorySerialNumberCheckBox.isSelected();
        snapIncludeVolumeSerial  = includeVolumeSerialNumberCheckBox.isSelected();
        snapIncludeSerial        = includeSerialNumberCheckBox.isSelected();
        snapIncludeProcessorId   = includeProcessorIdCheckBox.isSelected();
        snapIncludeCpuName       = includeProcessorNameCheckBox.isSelected();
        snapIncludeManufacturer  = includeManufacturerCheckBox.isSelected();
        snapPromptOnThreshold    = promptUserOnThresholdExceededSmurfVillageLookupCheckBox.isSelected();
        snapOnlyShowActive       = onlyShowActiveAccountsCheckBox.isSelected();
        try {
            snapThreshold = Integer.parseInt(maxMatchesBeforePromptSmurfVillageLookupTextField.getText().trim());
        } catch (NumberFormatException e) {
            snapThreshold = 10;
        }
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
        captureSmurfCheckSettings();
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

        captureSmurfCheckSettings();
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

    public void onSave() {
        saveColumnLayout(userSearchTableView, localPreferences);
        saveSplitPanePositions(root, localPreferences);
    }
}

