package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.commons.api.update.AvatarAssignmentUpdate;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.AvatarService;
import com.faforever.moderatorclient.api.domain.PermissionService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.mapstruct.GamePlayerStatsMapper;
import com.faforever.moderatorclient.ui.BanInfoController;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.GroupAddUserController;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.UiService;
import com.faforever.moderatorclient.ui.UserNoteController;
import com.faforever.moderatorclient.ui.ViewHelper;
import com.faforever.moderatorclient.ui.domain.AvatarAssignmentFX;
import com.faforever.moderatorclient.ui.domain.AvatarFX;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.FeaturedModFX;
import com.faforever.moderatorclient.ui.domain.GamePlayerStatsFX;
import com.faforever.moderatorclient.ui.domain.GroupPermissionFX;
import com.faforever.moderatorclient.ui.domain.NameRecordFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.faforever.moderatorclient.ui.domain.TeamkillFX;
import com.faforever.moderatorclient.ui.domain.UserGroupFX;
import com.faforever.moderatorclient.ui.domain.UserNoteFX;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserManagementController implements Controller<SplitPane> {
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
    public TextArea SearchHistoryTextField;
    public TextArea NotesTextArea;
    public TextField smurfVillageLookupTextField;
    public Tab searchSmurfVillageLookupTab;
    public TextArea searchSmurfVillageTabTextField;
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

    public TableView<GamePlayerStatsFX> userLastGamesTable;
    public ChoiceBox<FeaturedModFX> featuredModFilterChoiceBox;
    public Button loadMoreGamesButton;
    private Runnable loadMoreGamesRunnable;
    private int userGamesPage = 1;

    @Override
    public SplitPane getRoot() {
        return root;
    }

    private void disableTabOnMissingPermission(Tab tab, String permissionTechnicalName) {
        tab.setDisable(!communicationService.hasPermission(permissionTechnicalName));
    }



    @FXML
    public void initialize() {
        saveSettingsButton.setOnAction(event -> saveSettings());
        Properties props = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        excludeItemsCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("excludeItemsCheckBox")));
        includeUUIDCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeUUIDCheckBox")));
        includeUIDHashCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeUIDHashCheckBox")));
        includeMemorySerialNumberCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeMemorySerialNumberCheckBox")));
        includeVolumeSerialNumberCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeVolumeSerialNumberCheckBox")));
        includeSerialNumberCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeSerialNumberCheckBox")));
        includeProcessorIdCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeProcessorIdCheckBox")));
        includeManufacturerCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeManufacturerCheckBox")));
        includeIPCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("includeIPCheckBox")));
        String depthScanningInput = props.getProperty("depthScanningInputTextField");
        depthScanningInputTextField.setText(depthScanningInput);
        String maxUniqueUsersThreshold = props.getProperty("maxUniqueUsersThresholdTextField");
        maxUniqueUsersThresholdTextField.setText(maxUniqueUsersThreshold);
        log.debug("[info] config loaded");
        disableTabOnMissingPermission(notesTab, GroupPermission.ROLE_ADMIN_ACCOUNT_NOTE);
        disableTabOnMissingPermission(bansTab, GroupPermission.ROLE_ADMIN_ACCOUNT_BAN);
        disableTabOnMissingPermission(teamkillsTab, GroupPermission.ROLE_READ_TEAMKILL_REPORT);
        disableTabOnMissingPermission(avatarsTab, GroupPermission.ROLE_WRITE_AVATAR);
        disableTabOnMissingPermission(userGroupsTab, GroupPermission.ROLE_READ_USER_GROUP);

        ViewHelper.buildUserTableView(platformService, userSearchTableView, users, null,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), communicationService);
        ViewHelper.buildNotesTableView(userNoteTableView, userNotes, false);
        ViewHelper.buildNameHistoryTableView(userNameHistoryTableView, nameRecords);
        ViewHelper.buildBanTableView(userBansTableView, bans, false);
        ViewHelper.buildPlayersGamesTable(userLastGamesTable, replayDownLoadFormat, platformService);

        addNoteButton.disableProperty().bind(userSearchTableView.getSelectionModel().selectedItemProperty().isNull());
        editNoteButton.disableProperty().bind(userNoteTableView.getSelectionModel().selectedItemProperty().isNull());

        loadMoreGamesButton.visibleProperty()
                .bind(Bindings.createBooleanBinding(() -> userLastGamesTable.getItems().size() != 0 && userLastGamesTable.getItems().size() % 100 == 0, userLastGamesTable.getItems()));

        featuredModFilterChoiceBox.setConverter(new StringConverter<FeaturedModFX>() {
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

        userSearchTableView.getSelectionModel().selectedItemProperty().addListener(this::onSelectedUser);
        editBanButton.disableProperty().bind(userBansTableView.getSelectionModel().selectedItemProperty().isNull());

        initializeSearchProperties();
    }

    @FXML
    private void saveSettings() {
        Properties props = new Properties();
        props.setProperty("excludeItemsCheckBox", Boolean.toString(excludeItemsCheckBox.isSelected()));
        props.setProperty("includeUUIDCheckBox", Boolean.toString(includeUUIDCheckBox.isSelected()));
        props.setProperty("includeUIDHashCheckBox", Boolean.toString(includeUIDHashCheckBox.isSelected()));
        props.setProperty("includeMemorySerialNumberCheckBox", Boolean.toString(includeMemorySerialNumberCheckBox.isSelected()));
        props.setProperty("includeVolumeSerialNumberCheckBox", Boolean.toString(includeVolumeSerialNumberCheckBox.isSelected()));
        props.setProperty("includeSerialNumberCheckBox", Boolean.toString(includeSerialNumberCheckBox.isSelected()));
        props.setProperty("includeProcessorIdCheckBox", Boolean.toString(includeProcessorIdCheckBox.isSelected()));
        props.setProperty("includeManufacturerCheckBox", Boolean.toString(includeManufacturerCheckBox.isSelected()));
        props.setProperty("includeIPCheckBox", Boolean.toString(includeIPCheckBox.isSelected()));
        props.setProperty("depthScanningInputTextField", depthScanningInputTextField.getText());

        try (OutputStream out = new FileOutputStream("config.properties")) {
            props.store(out, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("[info] config saved");
        saveSettingsButton.setText("Save Settings: saved");
    }
    private void initializeSearchProperties() {
        searchUserPropertyMapping.put("Name", "login");
        searchUserPropertyMapping.put("Id", "id");
        searchUserPropertyMapping.put("Email", "email");
        searchUserPropertyMapping.put("Steam Id", "accountLinks.serviceId");
        searchUserPropertyMapping.put("Gog Id", "accountLinks.serviceId");
        searchUserPropertyMapping.put("Ip Address", "recentIpAddress");
        searchUserPropertyMapping.put("Previous Name", "names.name");
        searchUserPropertyMapping.put("UID Hash", "uniqueIds.hash");
        searchUserPropertyMapping.put("Device Id", "uniqueIds.deviceId");
        searchUserPropertyMapping.put("CPU Name", "uniqueIds.name");
        searchUserPropertyMapping.put("UUID", "uniqueIds.uuid");
        searchUserPropertyMapping.put("Serial Number", "uniqueIds.serialNumber");
        searchUserPropertyMapping.put("Processor Id", "uniqueIds.processorId");
        searchUserPropertyMapping.put("Bios Version", "uniqueIds.SMBIOSBIOSVersion");
        searchUserPropertyMapping.put("Volume Serial Number", "uniqueIds.volumeSerialNumber");
        searchUserPropertyMapping.put("Memory Serial Number", "uniqueIds.memorySerialNumber");
        searchUserPropertyMapping.put("Manufacturer", "uniqueIds.manufacturer");

        searchUserProperties.getItems().addAll(searchUserPropertyMapping.keySet());
        searchUserProperties.getSelectionModel().select(0);
    }

    @EventListener
    public void onAvatarSelected(AvatarFX avatarFX) {
        currentSelectedAvatar.setValue(avatarFX);
    }

    private void onSelectedUser(ObservableValue<? extends PlayerFX> observable, PlayerFX oldValue, PlayerFX newValue) {
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

        if (newValue != null) {
            teamkills.addAll(userService.findTeamkillsByUserId(newValue.getId()));
            userNotes.addAll(userService.getUserNotes(newValue.getId()));
            nameRecords.addAll(newValue.getNames());
            bans.addAll(newValue.getBans());
            avatarAssignments.addAll(newValue.getAvatarAssignments());
            if (!userGroupsTab.isDisable()) {
                permissionService.getPlayersUserGroups(newValue).thenAccept(playerGroups -> {
                    userGroups.addAll(playerGroups);
                    groupPermissions.addAll(playerGroups.stream().flatMap(userGroupFX -> userGroupFX.getPermissions().stream()).distinct().collect(Collectors.toList()));
                });
            }

            userGamesPage = 1;
            loadMoreGamesRunnable = () -> CompletableFuture.supplyAsync(() -> gamePlayerStatsMapper.map(userService.getLastHundredPlayedGamesByFeaturedMod(newValue.getId(), userGamesPage, featuredModFilterChoiceBox.getSelectionModel().getSelectedItem())))
                    .thenAccept(gamePlayerStats -> Platform.runLater(() -> userLastGamesTable.getItems().addAll(gamePlayerStats)));
            loadMoreGamesRunnable.run();
        }

        newBanButton.setDisable(newValue == null);
    }

    public void onUserSearch() {
        users.clear();
        userSearchTableView.getSortOrder().clear();

        String property = searchUserPropertyMapping.get(searchUserProperties.getValue());
        String searchPattern = userSearchTextField.getText();
        List<PlayerFX> usersFound = userService.findUsersByAttribute(property, searchPattern);
        users.addAll(usersFound);

        SearchHistoryTextField.setText(userSearchTextField.getText() + "\n" + SearchHistoryTextField.getText());
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
        File fileExcludedItems = new File("excludedItems" + ".txt");
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

    private void processUsers(String attributeName, String attributeValue, int threshold, StringBuilder logOutput, ArrayList<Object> foundSmurfs) {
        List<String> excludedItems = loadExcludedItems();
        if (excludedItems.contains(attributeValue) && excludeItemsCheckBox.isSelected()) {
            logOutput.append("\n\nThe " + attributeName + " [" + attributeValue + "] is an excluded item, skipping.");
        } else {
            List<PlayerFX> users = userService.findUsersByAttribute(attributeName, attributeValue);
            attributeName = attributeName.replaceAll("uniqueIds.", "");
            if (users.size() > threshold) {
                logOutput.append(String.format("\nToo many users found with %s [%s]. It might not be relatable. Threshold is %d and found were %d users", attributeName, attributeValue, threshold, users.size()));
            } else {
                logOutput.append("\n\n<------------------------------------------------------------>\n");
                logOutput.append("\nList of users for " + attributeName + " with same value [" + attributeValue + "]\n");
                users.forEach(user -> {
                        String statusBanned = "    <-- NOT banned";
                        if (user.isBannedGlobally()) {
                            statusBanned = "    <-- Banned";
                        }
                        logOutput.append("\n" + user.getRepresentation() + " " + statusBanned);
                        if(user.getId() != null && !foundSmurfs.contains(user.getId())) {
                            foundSmurfs.add(user.getId());
                            usersNotBanned.append(user.getId());
                        }
                });
            }
        }
    }

    public static final List<String> alreadyCheckedUsers = new ArrayList<>();
    StringBuilder logOutput = new StringBuilder();
    StringBuilder usersNotBanned = new StringBuilder();
    int depthCounter = 0;

    public void onSmurfVillageLookup(String playerID) {
        log.debug("[info] Checking " + playerID);
        if (alreadyCheckedUsers.contains(playerID)) {
            log.debug("[info] skipping, we already know that account");
            // User ID has already been checked, return without calling the function again
            return;
        }
        log.debug("[info] " + playerID + " added to alreadyCheckedUsers");
        alreadyCheckedUsers.add(playerID);
        // Perform additional logic to find other users...
        //modifiableList.add(playerID);
        //log.debug(playerID + " was added to modifiableList.");

        //searchUserPropertyMapping.put("Email", "email");
        //searchUserPropertyMapping.put("Steam Id", "accountLinks.serviceId");
        //searchUserPropertyMapping.put("Gog Id", "accountLinks.serviceId");
        //searchUserPropertyMapping.put("Device Id", "uniqueIds.deviceId");

        users.clear();
        userSearchTableView.getSortOrder().clear();
        //String property = searchUserPropertyMapping.get(searchUserProperties.getValue());
        String propertyId = searchUserPropertyMapping.get("Id");
        String propertyUUID = searchUserPropertyMapping.get("UUID");
        String propertyHash= searchUserPropertyMapping.get("UID Hash");
        String propertyIP = searchUserPropertyMapping.get("Ip Address");
        String propertyMemorySerialNumber = searchUserPropertyMapping.get("Memory Serial Number");
        String propertyVolumeSerialNumber = searchUserPropertyMapping.get("Volume Serial Number");
        String propertySerialNumber = searchUserPropertyMapping.get("Serial Number");
        String propertyProcessorId = searchUserPropertyMapping.get("Processor Id");
        String propertyCPUName = searchUserPropertyMapping.get("CPU Name");
        String propertyBiosVersion = searchUserPropertyMapping.get("Bios Version");
        String propertyManufacturer = searchUserPropertyMapping.get("Manufacturer");

        List<PlayerFX> userFound = userService.findUsersByAttribute(propertyId, playerID);

        List<String> uuids = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        List<String> ips = new ArrayList<>();
        List<String> memorySerialNumbers = new ArrayList<>();
        List<String> volumeSerialNumbers = new ArrayList<>();
        List<String> serialNumbers = new ArrayList<>();
        List<String> processorIds = new ArrayList<>();
        List<String> CPUNames = new ArrayList<>();
        List<String> biosVersions = new ArrayList<>();
        List<String> manufacturers = new ArrayList<>();

        Map<String, Set<String>> uniqueIds = new HashMap<>();

    //    uniqueIds.put("uuids", new HashSet<>());
    //    uniqueIds.put("hashes", new HashSet<>());
    //    uniqueIds.put("ips", new HashSet<>());
    //    uniqueIds.put("memorySerialNumbers", new HashSet<>());
    //    uniqueIds.put("volumeSerialNumbers", new HashSet<>());
    //    uniqueIds.put("serialNumbers", new HashSet<>());
    //    uniqueIds.put("processorIds", new HashSet<>());
    //    uniqueIds.put("biosVersions", new HashSet<>());
    //    uniqueIds.put("manufacturers", new HashSet<>());
//
        userFound.forEach(user->{
            String statusBanned = "";
            if (user.isBannedGlobally()) {
                statusBanned = "    <-- Banned";
                logOutput.append("Smurf village population for: " + user.getRepresentation() + statusBanned);
            }
            else {
                logOutput.append("Smurf village population for: " + user.getRepresentation());
            }
//
    //        user.getUniqueIds().forEach(item -> {
    //            uniqueIds.get("uuids").add(item.getUuid());
    //            uniqueIds.get("hashes").add(item.getHash());
    //            uniqueIds.get("ips").add(user.getRecentIpAddress());
    //            uniqueIds.get("memorySerialNumbers").add(item.getMemorySerialNumber());
    //            uniqueIds.get("volumeSerialNumbers").add(item.getVolumeSerialNumber());
    //            uniqueIds.get("serialNumbers").add(item.getSerialNumber());
    //            uniqueIds.get("processorIds").add(item.getProcessorId());
    //            uniqueIds.get("biosVersions").add(item.getSMBIOSBIOSVersion());
    //    userFound.forEach(user->{
    //        logOutput.append("Smurf village population for: " + user.getRepresentation());

            //TODO recap that mess with a HashSet?
            user.getUniqueIds().forEach(item -> {
                if (includeUUIDCheckBox.isSelected())
                {
                    if (!uuids.contains(item.getUuid())) {uuids.add(item.getUuid());}
                }
                if (includeUIDHashCheckBox.isSelected())
                {
                    if (!hashes.contains(item.getHash())) {hashes.add(item.getHash());}
                }

                if (includeIPCheckBox.isSelected())
                {
                    if (!ips.contains(user.getRecentIpAddress())) {ips.add(user.getRecentIpAddress());}
                }

                if (includeMemorySerialNumberCheckBox.isSelected())
                {
                    if (!memorySerialNumbers.contains(item.getMemorySerialNumber())) {memorySerialNumbers.add(item.getMemorySerialNumber());}
                }

                if (includeVolumeSerialNumberCheckBox.isSelected())
                {
                    if (!volumeSerialNumbers.contains(item.getVolumeSerialNumber())) {volumeSerialNumbers.add(item.getVolumeSerialNumber());}
                }

                if (includeSerialNumberCheckBox.isSelected())
                {
                    if (!serialNumbers.contains(item.getSerialNumber())) {serialNumbers.add(item.getSerialNumber());}

                }
                if (includeProcessorIdCheckBox.isSelected())
                {
                    if (!processorIds.contains(item.getProcessorId())) {processorIds.add(item.getProcessorId());}
                }
                //if (!biosVersions.contains(item.getSMBIOSBIOSVersion())) { TODO server gives 400 bugged or wrong search term?
                //    biosVersions.add(item.getSMBIOSBIOSVersion());
                //}
                if (includeManufacturerCheckBox.isSelected())
                {
                    if (!manufacturers.contains(item.getManufacturer())) {manufacturers.add(item.getManufacturer());
                }

                }
            });
        });

        int maxUniqueUsersThreshold = 30;

        //TODO make threshold config in tab settings
        //TODO make toggable to ignore values from excluded file, but note user what was ignored and why
        //TODO steam/GOG checker?
        //TODO IP VPN checker?
        //TODO depth factor? search the found smurf ids as well if threshold was not hit
        //TODO pattern search for similiar emails?

        ArrayList<Object> foundSmurfs = new ArrayList<>();
        uuids.stream().forEach(uuid -> processUsers(propertyUUID, uuid, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        hashes.stream().forEach(hash -> processUsers(propertyHash, hash, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        ips.stream().forEach(ip -> processUsers(propertyIP, ip, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        memorySerialNumbers.stream().forEach(memorySerialNumber -> processUsers(propertyMemorySerialNumber, memorySerialNumber, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        volumeSerialNumbers.stream().forEach(volumeSerialNumber -> processUsers(propertyVolumeSerialNumber, volumeSerialNumber, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        serialNumbers.stream().forEach(serialNumber -> processUsers(propertySerialNumber, serialNumber, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        processorIds.stream().forEach(processorId -> processUsers(propertyProcessorId, processorId, maxUniqueUsersThreshold, logOutput, foundSmurfs));
        //biosVersions.stream().forEach(biosVersion -> processUsers(propertyBiosVersion, biosVersion, searchPattern, maxUniqueUsersThreshold, logOutput));
        manufacturers.stream().forEach(manufacturer -> processUsers(propertyManufacturer, manufacturer, maxUniqueUsersThreshold, logOutput, foundSmurfs));

        searchSmurfVillageTabTextField.setText(logOutput.toString());
        log.debug("[info]" + playerID + " is somehow related through unique items to --> " + foundSmurfs.toString());
        depthCounter+=1;
        StringBuilder plusSigns = new StringBuilder();
        for (int i = 0; i < depthCounter; i++) {
            plusSigns.append("+");
        }
        logOutput.append("\n\n-> Current depth " + depthCounter + " " + plusSigns + "\n\n");
        foundSmurfs.forEach(s -> onSmurfVillageLookup((String) s));
    }

    public void onLookup() {
        depthCounter=0;
        logOutput = new StringBuilder();
        usersNotBanned = new StringBuilder();

        String lookupPlayerID = smurfVillageLookupTextField.getText();
        onSmurfVillageLookup(lookupPlayerID);

    }
}


