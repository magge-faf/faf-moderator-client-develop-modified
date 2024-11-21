package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.commons.api.update.AvatarAssignmentUpdate;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.AvatarService;
import com.faforever.moderatorclient.api.domain.PermissionService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.mapstruct.GamePlayerStatsMapper;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.text.Text;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserManagementController implements Controller<SplitPane> {
    public CheckBox includeProcessorNameCheckBox;
    public TextField statusTextFieldProcessingPlayerID;
    public TextField statusTextFieldProcessingItem;
    public Tab logSmurfVillageTab;
    public TextArea logSmurfVillageTabTextArea;
    public Button checkRecentAccountsForSmurfsButton;
    public Text statusTextRecentAccountsForSmurfs;
    public TextField amountTextFieldRecentAccountsForSmurfsAmount;
    public CheckBox catchFirstLayerSmurfsOnlyCheckBox;
    public TextField playerIDField1SharedGamesTextfield;
    public TextField playerIDField2SharedGamesTextfield;
    public Button checkSharedGamesButton;
    public Button removeNoteButton;
    @FXML
    public TextField pagesMaxAmountGamesTextfield;
    @FXML
    private Button checkRecentAccountsForSmurfsPauseButton;

    private volatile boolean isPaused = false;
    private volatile boolean isStopped = false;
    private final Object pauseLock = new Object(); // Monitor object for synchronization

    @FXML
    public Button checkRecentAccountsForSmurfsStopButton;
    private int depthCounter = 0;
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
    public TextArea SearchHistoryTextArea;
    public TextArea UserNotesTextArea;

    private static final String SEARCH_HISTORY_FILE = CONFIGURATION_FOLDER + File.separator +  "searchHistory.txt";
    private static final String USER_NOTES_FILE = CONFIGURATION_FOLDER + File.separator + "userNotes.txt";

    public TextField smurfVillageLookupTextField;
    public Tab searchSmurfVillageLookupTab;
    public TextArea searchSmurfVillageTabTextArea;
    public Tab settingsSmurfVillageLookupTab;
    public CheckBox includeUUIDCheckBox;
    public CheckBox includeUIDHashCheckBox;
    public CheckBox includeMemorySerialNumberCheckBox;
    public CheckBox includeVolumeSerialNumberCheckBox;
    public CheckBox includeSerialNumberCheckBox;
    public CheckBox includeProcessorIdCheckBox;
    public CheckBox includeManufacturerCheckBox;
    public TextField depthScanningInputTextField;
    public CheckBox includeIPCheckBox;
    public TextField maxUniqueUsersThresholdTextField;

    @FXML
    private Button saveSettingsButton;
    @FXML
    private CheckBox excludeItemsCheckBox;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;
    private final FafApiCommunicationService communicationService;

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
    public Button loadMoreGamesButton;
    private Runnable loadMoreGamesRunnable;
    private int userGamesPage = 1;

    private static final Path CONFIG_FILE_PATH = Paths.get(CONFIGURATION_FOLDER + File.separator + "config.properties");
    private final Properties properties = new Properties();

    @Autowired
    public SettingsController settingsController;

    @Override
    public SplitPane getRoot() {
        return root;
    }

    private void disableTabOnMissingPermission(Tab tab, String permissionTechnicalName) {
        tab.setDisable(!communicationService.hasPermission(permissionTechnicalName));
    }

    @FXML
    public void initialize() {
        loadProperties();
        loadContent();
        // Set last search term for userSearchTextField
        String textAreaContent = SearchHistoryTextArea.getText();
        String[] lines = textAreaContent.split("\n");
        if (lines.length > 0) {
            userSearchTextField.setText(lines[0]);
        }

        saveSettingsButton.setOnAction(event -> {
            try {
                saveOnExitSettings();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        disableTabOnMissingPermission(notesTab, GroupPermission.ROLE_ADMIN_ACCOUNT_NOTE);
        disableTabOnMissingPermission(bansTab, GroupPermission.ROLE_ADMIN_ACCOUNT_BAN);
        disableTabOnMissingPermission(teamkillsTab, GroupPermission.ROLE_READ_TEAMKILL_REPORT);
        disableTabOnMissingPermission(avatarsTab, GroupPermission.ROLE_WRITE_AVATAR);
        disableTabOnMissingPermission(userGroupsTab, GroupPermission.ROLE_READ_USER_GROUP);

        ViewHelper.buildUserTableView(platformService, userSearchTableView, users, null,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), true, communicationService);
        ViewHelper.buildNotesTableView(userNoteTableView, userNotes, false);
        ViewHelper.buildNameHistoryTableView(userNameHistoryTableView, nameRecords);
        ViewHelper.buildBanTableView(userBansTableView, bans, false);
        ViewHelper.buildPlayersGamesTable(userLastGamesTable, replayDownLoadFormat, platformService);

        addNoteButton.disableProperty().bind(userSearchTableView.getSelectionModel().selectedItemProperty().isNull());
        editNoteButton.disableProperty().bind(userNoteTableView.getSelectionModel().selectedItemProperty().isNull());
        //removeNoteButton.disableProperty().bind(userSearchTableView.getSelectionModel().selectedItemProperty().isNull());

        loadMoreGamesButton.visibleProperty()
                .bind(Bindings.createBooleanBinding(() -> !userLastGamesTable.getItems().isEmpty() && userLastGamesTable.getItems().size() % 100 == 0, userLastGamesTable.getItems()));

        featuredModFilterChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(FeaturedModFX object) {
                return object == null ? "All" : object.getDisplayName();
            }

            @Override
            public FeaturedModFX fromString(String string) {
                throw (new UnsupportedOperationException("Not implemented"));
            }
        });
        featuredModFilterChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            userLastGamesTable.getItems().clear();
            userGamesPage = 1;
            if (loadMoreGamesRunnable != null) loadMoreGamesRunnable.run();
        });

        featuredModFilterChoiceBox.getItems().add(null);
        featuredModFilterChoiceBox.getSelectionModel().select(0);
        CompletableFuture.supplyAsync(userService::getFeaturedMods)
                .thenAccept(featuredMods -> Platform.runLater(() -> featuredModFilterChoiceBox.getItems().addAll(featuredMods)));

        ViewHelper.buildTeamkillTableView(userTeamkillsTableView, teamkills, false, null);
        ViewHelper.buildUserAvatarsTableView(userAvatarsTableView, avatarAssignments);
        ViewHelper.buildUserGroupsTableView(userGroupsTableView, userGroups);
        ViewHelper.buildUserPermissionsTableView(permissionsTableView, groupPermissions);
        giveAvatarButton.disableProperty().bind(
                Bindings.or(userSearchTableView.getSelectionModel().selectedItemProperty().isNull(),
                        currentSelectedAvatar.isNull()));
        expiresAtTextfield.disableProperty().bind(userAvatarsTableView.getSelectionModel().selectedItemProperty().isNull());
        setExpiresAtButton.disableProperty().bind(userAvatarsTableView.getSelectionModel().selectedItemProperty().isNull());
        takeAvatarButton.disableProperty().bind(userAvatarsTableView.getSelectionModel().selectedItemProperty().isNull());
        removeGroupButton.disableProperty().bind(userGroupsTableView.getSelectionModel().selectedItemProperty().isNull());

        //userSearchTableView.getSelectionModel().selectedItemProperty().addListener(this::onSelectedUser);
        userSearchTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        userSearchTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<PlayerFX>) change -> {
            onSelectedUser();
        });
        editBanButton.disableProperty().bind(userBansTableView.getSelectionModel().selectedItemProperty().isNull());

        initializeSearchProperties();
    }

    private void initializeSearchProperties() {
        searchUserPropertyMapping.put("All In One", "allInOne");
        searchUserPropertyMapping.put("Name", "login");
        searchUserPropertyMapping.put("User ID", "id");
        searchUserPropertyMapping.put("Previous Name", "names.name");
        searchUserPropertyMapping.put("Email", "email");
        searchUserPropertyMapping.put("IP Address", "recentIpAddress");
        searchUserPropertyMapping.put("UUID", "uniqueIds.uuid");
        searchUserPropertyMapping.put("Hash", "uniqueIds.hash");
        searchUserPropertyMapping.put("Volume Serial Number", "uniqueIds.volumeSerialNumber");
        searchUserPropertyMapping.put("Memory Serial Number", "uniqueIds.memorySerialNumber");
        searchUserPropertyMapping.put("Serial Number", "uniqueIds.serialNumber");
        searchUserPropertyMapping.put("Device ID", "uniqueIds.deviceId");
        searchUserPropertyMapping.put("CPU Name", "uniqueIds.name");
        searchUserPropertyMapping.put("Processor ID", "uniqueIds.processorId");
        searchUserPropertyMapping.put("Bios Version", "uniqueIds.SMBIOSBIOSVersion");
        searchUserPropertyMapping.put("Manufacturer", "uniqueIds.manufacturer");
        searchUserPropertyMapping.put("Steam ID", "accountLinks.serviceId");
        searchUserPropertyMapping.put("GOG ID", "accountLinks.serviceId");

        searchUserProperties.getItems().addAll(searchUserPropertyMapping.keySet());
        searchUserProperties.getSelectionModel().select(0);
    }

    @EventListener
    public void onAvatarSelected(AvatarFX avatarFX) {
        currentSelectedAvatar.setValue(avatarFX);
    }

    private void onSelectedUser() {
        // Clear previous data
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

        ObservableList<PlayerFX> selectedUsers = userSearchTableView.getSelectionModel().getSelectedItems();

        if (selectedUsers != null && !selectedUsers.isEmpty()) {
            for (PlayerFX user : selectedUsers) {
                teamkills.addAll(userService.findTeamkillsByUserId(user.getId()));
                userNotes.addAll(userService.getUserNotes(user.getId()));
                nameRecords.addAll(user.getNames());
                bans.addAll(user.getBans());
                avatarAssignments.addAll(user.getAvatarAssignments());

                if (!userGroupsTab.isDisable()) {
                    permissionService.getPlayersUserGroups(user).thenAccept(playerGroups -> {
                        userGroups.addAll(playerGroups);
                        groupPermissions.addAll(playerGroups.stream().flatMap(userGroupFX -> userGroupFX.getPermissions().stream()).distinct().toList());
                    });
                }

                userGamesPage = 1;
                int numberOfPagesToLoad = 50; // TODO Customize value in settingsTab

                for (int i = 0; i < numberOfPagesToLoad; i++) {
                    final int currentPage = userGamesPage + i;
                    loadMoreGamesRunnable = () -> CompletableFuture.supplyAsync(() ->
                                    gamePlayerStatsMapper.map(userService.getLastHundredPlayedGamesByFeaturedMod(user.getId(), currentPage, featuredModFilterChoiceBox.getSelectionModel().getSelectedItem())))
                            .thenAccept(gamePlayerStats -> Platform.runLater(() -> userLastGamesTable.getItems().addAll(gamePlayerStats)));
                    loadMoreGamesRunnable.run();
                }
            }
        }

        newBanButton.setDisable(selectedUsers == null || selectedUsers.isEmpty());
    }

    public void onUserSearch() {
        users.clear();
        userSearchTableView.getSortOrder().clear();

        String searchParameter = searchUserPropertyMapping.get(searchUserProperties.getValue());
        String searchPattern = userSearchTextField.getText();

        previousUserSearchTerm = currentUserSearchTerm;
        currentUserSearchTerm = userSearchTextField.getText();

        log.debug("beforeSearchParameter: {}", searchParameter);
        log.debug("beforeSearchPattern: {}", searchPattern);

        if (Objects.equals(searchParameter, "allInOne")) {
            searchParameter = determineSearchParameter(searchPattern);
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
        log.debug("afterSearchParameter: {}", searchParameter);
        log.debug("afterSearchPattern: {}", searchPattern);

        List<PlayerFX> usersFound = userService.findUsersByAttribute(searchParameter, searchPattern);
        users.addAll(usersFound);

        SearchHistoryTextArea.setText(userSearchTextField.getText() + "\n" + SearchHistoryTextArea.getText());
    }

    private String determineSearchParameter(String searchPattern) {

        if (isUUID(searchPattern)) {
            return "uniqueIds.uuid";
        }
        if (isValidIp(searchPattern)) {
            return "recentIpAddress";
        }
        if (isEmail(searchPattern)) {
            return "email";
        }
        if (isHash(searchPattern)) {
            return "uniqueIds.hash";
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
        return "unknown";
    }

    private boolean isEmail(String searchPattern) {
        Pattern pattern = Pattern.compile("^.*@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$");
        Matcher matcher = pattern.matcher(searchPattern);
        return matcher.matches();
    }

    private boolean isHash(String searchPattern) {
        return searchPattern.matches("^[a-fA-F0-9]+$") && searchPattern.length() == 32;
    }

    private boolean isLoginName(String searchPattern) {
        return searchPattern.indexOf('@') == -1 && (!Character.isDigit(searchPattern.charAt(0)));
    }

    private boolean isLoginAndName(String searchPattern) {
        return searchPattern.contains("[id ") && searchPattern.contains("]");
    }

    private boolean isUUID(String searchPattern) {
        Pattern pattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        Matcher matcher = pattern.matcher(searchPattern);
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

    public void loadMoreGames() {
        userGamesPage++;
        loadMoreGamesRunnable.run();
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

    private List<String> loadExcludedItems() {
        List<String> excludedItems = new ArrayList<>();
        File fileExcludedItems = new File(CONFIGURATION_FOLDER + "/excludedItems" + ".txt");
        try {
            Scanner s = new Scanner(fileExcludedItems);
            while (s.hasNextLine()) {
                excludedItems.add(s.nextLine());
            }
            s.close();
        } catch (Exception e) {
            log.debug(String.valueOf(e));
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

    private void processUsers(String attributeName, String attributeValue, int threshold, StringBuilder logOutput, ArrayList<Object> foundSmurfs) {
        checkRecentAccountsForSmurfsPauseButton.setOnAction(event -> checkRecentAccountsForSmurfsPause());
        try {
            String text = String.format("\nProcessing ... [%s] [%s]", attributeName, attributeValue);
            updateSmurfVillageLogTextArea(text);

            List<String> excludedItems = loadExcludedItems();
            boolean excludeItemsSelected = excludeItemsCheckBox.isSelected();

            synchronized (pauseLock) {
                while (isPaused) {
                    text = "\t\t PROCESS PAUSED. Waiting for RESUME.\n";
                    updateSmurfVillageLogTextArea(text);
                    pauseLock.wait();
                }
            }

            if (excludeItemsSelected && excludedItems.contains(attributeValue)) {

                text = String.format("\t\t EXCLUSION CHECK: attribute [%s] with value [%s] found in excludedItems.txt, skipping.\n", attributeName, attributeValue);
                updateSmurfVillageLogTextArea(text);
                return;
            }

            List<PlayerFX> users = userService.findUsersByAttribute(attributeName, attributeValue);

            if (users.size() > threshold) {
                text = String.format("\t\t THRESHOLD CHECK: Ignoring %s [%s] (found %d, limit %d, added to excludedItems.txt)\n", attributeName, attributeValue, users.size(), threshold);
                updateSmurfVillageLogTextArea(text);

                File fileExcludedItems = new File(CONFIGURATION_FOLDER + File.separator + "excludedItems" + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileExcludedItems, true))) {
                    writer.newLine();
                    writer.write(attributeValue);  // Append the attributeValue to the last line
                } catch (IOException e) {
                    log.warn("Failed to write to excludedItems.txt: {}", e.getMessage());
                }
                log.debug("{} was added to the excludedItems.txt", attributeValue);
                return;
            }

            if (users.size() != 1) {
                logOutput.append("\nMULTIPLE USERS\n");
                logOutput.append(String.format("  - Attribute: %s with value: [%s]", attributeName, attributeValue));

                for (PlayerFX user : users) {
                    String accountStatus = user.isBannedGlobally() ? "banned: " : "active: ";
                    String name = user.getRepresentation();
                    String output = String.format("\n      %-20s %-10s", accountStatus, name);
                    logOutput.append(output);
                    text = String.format(output);
                    updateSmurfVillageLogTextArea(text);

                    if (user.getId() != null && !foundSmurfs.contains(user.getId())) {
                        foundSmurfs.add(user.getId());
                    }
                }
            }
            else {
                updateSmurfVillageLogTextArea("\t\t - unique");
            }
        } catch (IndexOutOfBoundsException e) {
            log.error("An IndexOutOfBoundsException occurred: {}", e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

    public void writeSmurfVillageLookup2File(StringBuilder logOutput) {
        try {
            File folder = new File("SmurfVillageLookup");
            if (!folder.exists()) {
                if (!folder.mkdir()) {
                    log.error("Failed to create directory: {}", folder.getAbsolutePath());
                    throw new IOException("Could not create directory: " + folder.getAbsolutePath());
                }
            }
            String fileName = "SmurfVillageLookup/UserID_" + smurfVillageLookupTextField.getText() + ".txt";
            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
            bw.append(logOutput.toString());
            bw.close();
            log.debug("Output was added in {}", fileName);
        } catch (IOException e) {
            log.warn("Error in writeSmurfVillageLookup2File", e);
        }
    }

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
        log.debug("Checking {}", playerID);
        AtomicReference<String> text = new AtomicReference<>(String.format("\n\n--- Checking: " + playerID));
        updateSmurfVillageLogTextArea(text.get());
        text.set(String.format("\nPlayer IDs seen so far: " + alreadyCheckedUsers));
        updateSmurfVillageLogTextArea(text.get());

        statusTextFieldProcessingPlayerID.setText(playerID);

        if (alreadyCheckedUsers.contains(playerID)) {
            text.set(String.format("\nSkipping, we already have seen that account: " + playerID));
            updateSmurfVillageLogTextArea(text.get());
            return;
        }
        alreadyCheckedUsers.add(playerID);

        updateSmurfVillageLogTextArea("\n=== [PlayerID: " + playerID + " ===\n");

        users.clear();
        userSearchTableView.getSortOrder().clear();

        String propertyId = searchUserPropertyMapping.get("User ID");
        String propertyUUID = searchUserPropertyMapping.get("UUID");
        String propertyHash = searchUserPropertyMapping.get("Hash");
        String propertyIP = searchUserPropertyMapping.get("IP Address");
        String propertyMemorySerialNumber = searchUserPropertyMapping.get("Memory Serial Number");
        String propertyVolumeSerialNumber = searchUserPropertyMapping.get("Volume Serial Number");
        String propertySerialNumber = searchUserPropertyMapping.get("Serial Number");
        String propertyProcessorId = searchUserPropertyMapping.get("Processor ID");
        String propertyCPUName = searchUserPropertyMapping.get("CPU Name");
        String propertyBiosVersion = searchUserPropertyMapping.get("Bios Version"); // Bios values are not implemented
        String propertyManufacturer = searchUserPropertyMapping.get("Manufacturer");

        Set<PlayerFX> userFound = new HashSet<>(userService.findUsersByAttribute(propertyId, playerID));
        Set<String> uuids = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        Set<String> ips = new HashSet<>();
        Set<String> memorySerialNumbers = new HashSet<>();
        Set<String> volumeSerialNumbers = new HashSet<>();
        Set<String> serialNumbers = new HashSet<>();
        Set<String> processorIds = new HashSet<>();
        Set<String> cpuNames = new HashSet<>();
        Set<String> biosVersions = new HashSet<>();
        Set<String> manufacturers = new HashSet<>();

        userFound.forEach(user -> user.getUniqueIds().forEach(item -> {
            if (includeProcessorNameCheckBox.isSelected()) {
                cpuNames.add(item.getUuid());
            }
            if (includeUUIDCheckBox.isSelected()) {
                uuids.add(item.getUuid());
            }
            if (includeUIDHashCheckBox.isSelected()) {
                hashes.add(item.getHash());
            }
            if (includeIPCheckBox.isSelected()) {
                ips.add(user.getRecentIpAddress());
            }
            if (includeMemorySerialNumberCheckBox.isSelected()) {
                memorySerialNumbers.add(item.getMemorySerialNumber());
            }
            if (includeVolumeSerialNumberCheckBox.isSelected()) {
                volumeSerialNumbers.add(item.getVolumeSerialNumber());
            }
            if (includeSerialNumberCheckBox.isSelected()) {
                serialNumbers.add(item.getSerialNumber());
            }
            if (includeProcessorIdCheckBox.isSelected()) {
                processorIds.add(item.getProcessorId());
            }
            //biosVersions.add(item.getSMBIOSBIOSVersion());
            //if (includeManufacturerCheckBox.isSelected())
            if (includeManufacturerCheckBox.isSelected()) {
                manufacturers.add(item.getManufacturer());
            }
        }));

        int maxUniqueUsersThreshold = Integer.parseInt(maxUniqueUsersThresholdTextField.getText());

        ArrayList<Object> foundSmurfs = new ArrayList<>();

        processSet(uuids, propertyUUID, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedUuids, "uuid");
        processSet(hashes, propertyHash, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedHashes, "hash");
        processSet(ips, propertyIP, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedIps, "ip");
        processSet(memorySerialNumbers, propertyMemorySerialNumber, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedMemorySerialNumbers, "memorySerialNumber");
        processSet(volumeSerialNumbers, propertyVolumeSerialNumber, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedVolumeSerialNumbers, "volumeSerialNumber");
        processSet(serialNumbers, propertySerialNumber, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedSerialNumbers, "serialNumber");
        processSet(processorIds, propertyProcessorId, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedProcessorIds, "processorId");
        processSet(cpuNames, propertyCPUName, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedCpuNames, "cpu");
        processSet(manufacturers, propertyManufacturer, maxUniqueUsersThreshold, logOutput, foundSmurfs, alreadyCheckedManufacturers, "manufacturer");
        //biosVersions.stream().forEach(biosVersion -> processUsers(propertyBiosVersion, biosVersion, searchPattern, maxUniqueUsersThreshold, logOutput));

        if (!foundSmurfs.isEmpty()) {
            logOutput.append("\n\n").append(playerID).append(" is related through unique items to --> ").append(foundSmurfs).append("\n");
            updateSmurfVillageLogTextArea("\n" +  playerID + " is related through unique items to --> " + foundSmurfs);

        }
        depthCounter += 1;
        int depthThreshold = Integer.parseInt(depthScanningInputTextField.getText());
        if (depthCounter >= depthThreshold) {
            log.debug("Depth limit reached: {}/{}", depthCounter, depthThreshold);
            searchSmurfVillageTabTextArea.setText(logOutput.toString());
            writeSmurfVillageLookup2File(logOutput);
            return;
        }

        if (catchFirstLayerSmurfsOnlyCheckBox.isSelected()) {
            log.debug("The 'catchFirstLayerSmurfsOnlyCheckBox' is enabled. Smurf tracing is disabled.");
            searchSmurfVillageTabTextArea.setText(logOutput.toString());
            writeSmurfVillageLookup2File(logOutput);
            return;
        }

        foundSmurfs.forEach(s -> onSmurfVillageLookup((String) s));

        searchSmurfVillageTabTextArea.setText(logOutput.toString());
        writeSmurfVillageLookup2File(logOutput);

    }

    private void processSet(Set<String> items, String property, int maxUniqueUsersThreshold, StringBuilder logOutput, List<Object> foundSmurfs, Set<String> alreadyCheckedSet, String type) {
        items.forEach(item -> {
            statusTextFieldProcessingItem.setText(type + ": " + item);
            if (!alreadyCheckedSet.contains(item)) {
                processUsers(property, item, maxUniqueUsersThreshold, logOutput, (ArrayList<Object>) foundSmurfs);
                alreadyCheckedSet.add(item);
            } else {
                String message = String.format("\nIgnoring duplicate: %s [%s] already processed.\n", type, item);
                log.debug(message);
                updateSmurfVillageLogTextArea(message);
            }
        });
    }

    public boolean checkStartUpExcludedItems() {
        String filePath = CONFIGURATION_FOLDER + "/excludedItems.txt";
        Path path = Paths.get(filePath);

        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                return !content.isEmpty();
            } catch (IOException e) {
                return false;
            }
        }
        return false;
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
        depthCounter = 0;
        isPaused = false;
        log.debug("resetPreviousStateSmurfVillageLookup");
    }

    public void onLookupSmurfVillage() {
        boolean safeCheck = checkAndAlertExcludedItems();

        if (safeCheck){
            return;
        }
        setStatusWorking();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                isStopped = false;
                searchSmurfVillageTabTextArea.setText("");
                logSmurfVillageTabTextArea.setText("");
                depthCounter = 0;
                logOutput.setLength(0);
                String lookupPlayerID = smurfVillageLookupTextField.getText();
                onSmurfVillageLookup(lookupPlayerID);
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    setStatusDone();
                    resetPreviousStateSmurfVillageLookup();
                    logSmurfVillageTabTextArea.appendText("\n\nTask Done");

                    if (searchSmurfVillageTabTextArea.getText().isEmpty()) {
                        searchSmurfVillageTabTextArea.setText("No additional accounts found.");
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> log.debug("Task failed"));
            }
        };

        new Thread(task).start();
    }

    public void updateSmurfVillageLogTextArea(String... texts) {
        Platform.runLater(() -> {
            try {
                if (texts != null) {
                    for (String text : texts) {
                        if (text != null) {
                            logSmurfVillageTabTextArea.appendText(text);
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

    public boolean checkAndAlertExcludedItems() {
        if (!checkStartUpExcludedItems()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information Dialog");
            alert.setHeaderText(null); // No header text
            alert.setContentText("Your excludedItems.txt is empty or missing. " +
                    "See Settings-Tab (Top Right) to get the latest version from Zulip channel.");

            alert.showAndWait();
            return true;
        }
        return false;
    }


    public void checkRecentAccountsForSmurfs() {
        boolean safeCheck = checkAndAlertExcludedItems();

        if (safeCheck){
            return;
        }
        resetPreviousStateSmurfVillageLookup();
        searchSmurfVillageTabTextArea.setText("");
        logSmurfVillageTabTextArea.setText("");
        setStatusWorking();
        // Define a task to run in the background
        Task<Void> task = new Task<>() {
            private int count = 0;
            private int amountAccountsCheck = 0;
            private static final int DELAY_MS = 150;
            // Setting a low delay can lead to UI-thread exceptions and unpredictable behavior.

            @Override
            protected Void call() throws ExecutionException, InterruptedException {
                List<PlayerFX> accounts = userService.findLatestRegistrations();

                if (accounts == null || accounts.isEmpty()) {
                    Platform.runLater(() -> statusTextRecentAccountsForSmurfs.setText("No accounts found."));
                    return null;
                }

                try {
                    amountAccountsCheck = Integer.parseInt(amountTextFieldRecentAccountsForSmurfsAmount.getText());
                } catch (NumberFormatException e) {
                    Platform.runLater(() -> statusTextRecentAccountsForSmurfs.setText("Invalid number format."));
                    return null;
                }

                for (PlayerFX account : accounts) {
                    if (isStopped) {
                        isStopped = false;
                        break;
                    }

                    if (count >= amountAccountsCheck) {
                        final int currentCount = count;
                        Platform.runLater(() -> statusTextRecentAccountsForSmurfs.setText(String.format("Reached the limit: %d/%d", currentCount, amountAccountsCheck)));
                        break;
                    }

                    onSmurfVillageLookup(account.getId());
                    count++;

                    // Update status on the JavaFX Application Thread
                    final int currentCount = count;
                    Platform.runLater(() -> statusTextRecentAccountsForSmurfs.setText(String.format("Status: %d/%d", currentCount, amountAccountsCheck)));

                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Platform.runLater(() -> statusTextRecentAccountsForSmurfs.setText("Task interrupted."));
                        log.error("Task interrupted", e);
                        return null;
                    }
                }

                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> resetPreviousStateSmurfVillageLookup());
                super.failed();
                log.error("Task failed", getException());
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    resetPreviousStateSmurfVillageLookup();
                    setStatusDone();
                });
                super.succeeded();
                if (searchSmurfVillageTabTextArea.getText().isEmpty()) {
                    searchSmurfVillageTabTextArea.setText("No additional accounts found.");
                }
                log.debug("Task completed successfully.");
            }
        };

        new Thread(task).start();
    }

    private void loadProperties() {
        if (CONFIG_FILE_PATH.toFile().exists()) {
            try (InputStream in = new FileInputStream(CONFIG_FILE_PATH.toFile())) {
                properties.load(in);
            } catch (IOException e) {
                log.warn("Error loading config file:", e);
            }
            String value = properties.getProperty("amount_check_recent_accounts", "42");
            amountTextFieldRecentAccountsForSmurfsAmount.setText(value);
            excludeItemsCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("excludeItemsCheckBox", String.valueOf(true))));
            includeUUIDCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeUUIDCheckBox", String.valueOf(true))));
            includeUIDHashCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeUIDHashCheckBox", String.valueOf(true))));
            includeMemorySerialNumberCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeMemorySerialNumberCheckBox", String.valueOf(false))));
            includeVolumeSerialNumberCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeVolumeSerialNumberCheckBox", String.valueOf(false))));
            includeSerialNumberCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeSerialNumberCheckBox", String.valueOf(false))));
            includeProcessorIdCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeProcessorIdCheckBox", String.valueOf(false))));
            includeManufacturerCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeManufacturerCheckBox", String.valueOf(false))));
            includeIPCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeIPCheckBox", String.valueOf(false))));
            includeProcessorNameCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("includeProcessorNameCheckBox", String.valueOf(false))));
            catchFirstLayerSmurfsOnlyCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("catchFirstLayerSmurfsOnlyCheckBox", String.valueOf(true))));
            String depthScanningInput = properties.getProperty("depthScanningInputTextField", "1000");
            depthScanningInputTextField.setText(depthScanningInput);
            String maxUniqueUsersThreshold = properties.getProperty("maxUniqueUsersThresholdTextField", "100");
            maxUniqueUsersThresholdTextField.setText(maxUniqueUsersThreshold);
            String selectedBrowser = properties.getProperty("browserComboBox", "selectBrowser");
            settingsController.setSelectedBrowser(selectedBrowser);
            log.debug("Configuration loaded.");
        }
    }

    @FXML
    public void saveOnExitSettings() throws IOException {
        if (CONFIG_FILE_PATH.toFile().exists()) {
            try (InputStream in = new FileInputStream(CONFIG_FILE_PATH.toFile())) {
                properties.load(in);
                properties.setProperty("excludeItemsCheckBox", Boolean.toString(excludeItemsCheckBox.isSelected()));
                properties.setProperty("includeProcessorNameCheckBox", Boolean.toString(includeProcessorNameCheckBox.isSelected()));
                properties.setProperty("includeUUIDCheckBox", Boolean.toString(includeUUIDCheckBox.isSelected()));
                properties.setProperty("includeUIDHashCheckBox", Boolean.toString(includeUIDHashCheckBox.isSelected()));
                properties.setProperty("includeMemorySerialNumberCheckBox", Boolean.toString(includeMemorySerialNumberCheckBox.isSelected()));
                properties.setProperty("includeVolumeSerialNumberCheckBox", Boolean.toString(includeVolumeSerialNumberCheckBox.isSelected()));
                properties.setProperty("includeSerialNumberCheckBox", Boolean.toString(includeSerialNumberCheckBox.isSelected()));
                properties.setProperty("includeProcessorIdCheckBox", Boolean.toString(includeProcessorIdCheckBox.isSelected()));
                properties.setProperty("includeManufacturerCheckBox", Boolean.toString(includeManufacturerCheckBox.isSelected()));
                properties.setProperty("includeIPCheckBox", Boolean.toString(includeIPCheckBox.isSelected()));
                properties.setProperty("catchFirstLayerSmurfsOnlyCheckBox", Boolean.toString(catchFirstLayerSmurfsOnlyCheckBox.isSelected()));
                properties.setProperty("depthScanningInputTextField", depthScanningInputTextField.getText());
                properties.setProperty("maxUniqueUsersThresholdTextField", maxUniqueUsersThresholdTextField.getText());
                String browserName = settingsController.getSelectedBrowser();
                properties.setProperty("browserComboBox", browserName);
                String startingTab = settingsController.getDefaultTab();
                properties.setProperty("user.choice.tab", startingTab);
                log.debug("Configuration saved.");

                try (OutputStream out = new FileOutputStream(CONFIG_FILE_PATH.toFile())) {
                    properties.store(out, null);
                } catch (IOException e) {
                    log.warn("Error saving config file:", e);
                }
            }
        }
    }

    public void savePropertiesAmountToCheckRecentAccounts() throws IOException {
        properties.setProperty("amount_check_recent_accounts", amountTextFieldRecentAccountsForSmurfsAmount.getText());

        try (OutputStream out = new FileOutputStream(CONFIG_FILE_PATH.toFile())) {
            properties.store(out, null);
        } catch (IOException e) {
            log.warn("Error saving config file:", e);
        }
    }

    public void saveOnExitContent() {
        try {
            Files.write(Paths.get(SEARCH_HISTORY_FILE), SearchHistoryTextArea.getText().getBytes());
            Files.write(Paths.get(USER_NOTES_FILE), UserNotesTextArea.getText().getBytes());
        } catch (IOException e) {
            log.debug(String.valueOf(e));
        }
    }

    private void loadContent() {
        try {
            if (Files.exists(Paths.get(SEARCH_HISTORY_FILE))) {
                String searchHistory = new String(Files.readAllBytes(Paths.get(SEARCH_HISTORY_FILE)));
                SearchHistoryTextArea.setText(searchHistory);
            }
            if (Files.exists(Paths.get(USER_NOTES_FILE))) {
                String userNotes = new String(Files.readAllBytes(Paths.get(USER_NOTES_FILE)));
                UserNotesTextArea.setText(userNotes);
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

        if (isValidPlayerId(playerID1) && isValidPlayerId(playerID2)) {
            userGamesPage = 1;
            int numberOfPagesToLoad = Integer.parseInt(pagesMaxAmountGamesTextfield.getText());

            List<GamePlayerStatsFX> allPlayer1Games = Collections.synchronizedList(new ArrayList<>());
            List<GamePlayerStatsFX> allPlayer2Games = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < numberOfPagesToLoad; i++) {
                final int currentPage = userGamesPage + i;

                try {
                    // Sequential API calls with a delay to respect rate limits
                    List<GamePlayerStatsFX> player1Games = gamePlayerStatsMapper.map(
                            userService.getLastHundredPlayedGamesByFeaturedMod(
                                    playerID1, currentPage, featuredModFilterChoiceBox.getSelectionModel().getSelectedItem())
                    );
                    allPlayer1Games.addAll(player1Games);

                    Thread.sleep(250);

                    List<GamePlayerStatsFX> player2Games = gamePlayerStatsMapper.map(
                            userService.getLastHundredPlayedGamesByFeaturedMod(
                                    playerID2, currentPage, featuredModFilterChoiceBox.getSelectionModel().getSelectedItem())
                    );
                    allPlayer2Games.addAll(player2Games);

                    Thread.sleep(250);
                } catch (Exception e) {
                    log.warn("Error during API request: ", e);
                    Platform.runLater(() -> checkSharedGamesButton.setText("Check - Error during request."));
                    return;
                }
            }

            Set<GameFX> player1Games = allPlayer1Games.stream()
                    .map(GamePlayerStatsFX::getGame)
                    .collect(Collectors.toSet());

            Set<GameFX> player2Games = allPlayer2Games.stream()
                    .map(GamePlayerStatsFX::getGame)
                    .collect(Collectors.toSet());

            Set<GameFX> commonGames = new HashSet<>(player1Games);
            commonGames.retainAll(player2Games);

            Set<GamePlayerStatsFX> commonGameStats = allPlayer1Games.stream()
                    .filter(stats -> commonGames.contains(stats.getGame()))
                    .collect(Collectors.toSet());

            Platform.runLater(() -> {
                userLastGamesTable.getItems().clear();

                if (!commonGameStats.isEmpty()) {
                    userLastGamesTable.getItems().setAll(commonGameStats);
                } else {
                    log.info("No common games found.");
                    checkSharedGamesButton.setText("Check");
                }
            });
        } else {
            checkSharedGamesButton.setText("Check - Invalid player IDs provided.");
        }
    }
}
