package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.UserDataController;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
@Slf4j
public class SmurfManagementController implements Controller<VBox> {

    public static final Path SMURF_MANAGEMENT_USERS_JSON_PATH = Paths.get("data", "smurf_management.json");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double WINDOW_WIDTH_RATIO = 0.8;
    private static final double WINDOW_HEIGHT_RATIO = 0.8;

    private static final DateTimeFormatter HUMAN_READABLE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    @Autowired
    public UserManagementController userManagementController;

    @FXML
    public VBox root;
    @FXML
    private TextField searchField;
    @FXML
    private Label countLabel;

    private TableView<UserDataController> smurfManagementTableView;
    private final ObservableList<UserDataController> smurfManagementUsersList = FXCollections.observableArrayList();
    private FilteredList<UserDataController> filteredList;

    @Override
    public VBox getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        smurfManagementTableView = createSmurfManagementTable();

        filteredList = new FilteredList<>(smurfManagementUsersList, p -> true);
        SortedList<UserDataController> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(smurfManagementTableView.comparatorProperty());
        smurfManagementTableView.setItems(sortedList);

        searchField.textProperty().addListener((obs, old, filter) -> {
            String f = filter == null ? "" : filter.toLowerCase().strip();
            filteredList.setPredicate(user -> {
                if (f.isEmpty()) return true;
                return safeGet(user, UserDataController.UserInfo::getUserName).toLowerCase().contains(f)
                        || safeGet(user, UserDataController.UserInfo::getUserId).contains(f)
                        || safeGet(user, UserDataController.UserInfo::getComment).toLowerCase().contains(f)
                        || safeGet(user, UserDataController.UserInfo::getReason).toLowerCase().contains(f);
            });
            updateCountLabel();
        });

        smurfManagementTableView.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.C) {
                UserDataController selected = smurfManagementTableView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getUserInfo() != null) {
                    String toCopy = safeGet(selected, UserDataController.UserInfo::getUserName)
                            + " [id " + safeGet(selected, UserDataController.UserInfo::getUserId) + "]";
                    javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(toCopy);
                    clipboard.setContent(content);
                }
                event.consume();
            }
        });

        smurfManagementUsersList.addListener((javafx.collections.ListChangeListener<UserDataController>) c -> updateCountLabel());

        ensureJsonFileExists();
        loadSmurfManagementUsers();

        setupContextMenu(smurfManagementTableView);
        setupDoubleClickAction(smurfManagementTableView);

        root.getChildren().add(smurfManagementTableView);
    }

    private void updateCountLabel() {
        countLabel.setText(filteredList.size() + " / " + smurfManagementUsersList.size() + " entries");
    }

    private void ensureJsonFileExists() {
        try {
            if (!Files.exists(SMURF_MANAGEMENT_USERS_JSON_PATH)) {
                Files.createDirectories(SMURF_MANAGEMENT_USERS_JSON_PATH.getParent());
                Files.writeString(SMURF_MANAGEMENT_USERS_JSON_PATH, "[]");
            } else if (Files.size(SMURF_MANAGEMENT_USERS_JSON_PATH) == 0) {
                Files.writeString(SMURF_MANAGEMENT_USERS_JSON_PATH, "[]");
            }
        } catch (IOException e) {
            log.error("Failed to ensure {} exists.", SMURF_MANAGEMENT_USERS_JSON_PATH, e);
        }
    }

    public TableView<UserDataController> createSmurfManagementTable() {
        TableView<UserDataController> table = new TableView<>();

        DateTimeFormatter formatter = HUMAN_READABLE_FORMATTER;

        table.getColumns().addAll(
                createNumericColumn("User ID",    user -> safeGet(user, UserDataController.UserInfo::getUserId)),
                createColumn      ("User Name",   user -> safeGet(user, UserDataController.UserInfo::getUserName)),
                createColumn      ("Comment",     user -> safeGet(user, UserDataController.UserInfo::getComment)),
                createColumn      ("Reason",      user -> safeGet(user, UserDataController.UserInfo::getReason)),

                createDateColumn("Last Login", user -> {
                    List<UserDataController.LoginEntry> lastLogins = user.getAccountHistory().getLastLogins();
                    if (lastLogins == null || lastLogins.isEmpty()) return "Never";
                    Instant lastLogin = parseInstantSafe(lastLogins.getLast().getAddedOn());
                    return lastLogin == null ? "" : formatter.format(lastLogin);
                }),

                createDateColumn("Date Added", user -> {
                    Instant added = parseInstantSafe(safeGet(user, UserDataController.UserInfo::getAddedOn));
                    return added == null ? "" : formatter.format(added);
                }),

                createDateColumn("Last Activity", user -> {
                    Instant lastEdit = parseInstantSafe(safeGet(user, UserDataController.UserInfo::getLastEdit));
                    return lastEdit == null ? "" : formatter.format(lastEdit);
                }),

                createColumn("Last Event", user -> {
                    List<UserDataController.HistoryEntry> history = user.getAccountHistory().getHistory();
                    if (history == null || history.isEmpty()) return "";
                    return history.get(history.size() - 1).getAction();
                }),

                createNumericColumn("IP Count",   user -> String.valueOf(user.getHardwareInfo().getIpAddresses().size())),
                createNumericColumn("UUID Count",  user -> String.valueOf(user.getHardwareInfo().getUuidEntries().size())),

                createColumn("Last IP", user -> {
                    List<UserDataController.IpAddressEntry> ips = user.getHardwareInfo().getIpAddresses();
                    return (ips == null || ips.isEmpty()) ? "" : ips.get(ips.size() - 1).getIp();
                }),

                createColumn    ("Ban Status",     user -> getFirstBan(user, UserDataController.BanInfo::getBanStatus)),
                createDateColumn("Ban Expires At", user -> getFirstBan(user, UserDataController.BanInfo::getBanExpiresAt)),
                createDateColumn("Ban Created At", user -> getFirstBan(user, UserDataController.BanInfo::getBanCreatedAt))
        );

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        return table;
    }

    // ---- Column factories ----

    private TableColumn<UserDataController, String> createColumn(String title, Function<UserDataController, String> mapper) {
        TableColumn<UserDataController, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new SimpleStringProperty(mapper.apply(cell.getValue())));
        return col;
    }

    private TableColumn<UserDataController, String> createNumericColumn(String title, Function<UserDataController, String> mapper) {
        TableColumn<UserDataController, String> col = createColumn(title, mapper);
        col.setComparator(Comparator.comparingInt(s -> {
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { return Integer.MIN_VALUE; }
        }));
        return col;
    }

    private TableColumn<UserDataController, String> createDateColumn(String title, Function<UserDataController, String> mapper) {
        TableColumn<UserDataController, String> col = createColumn(title, mapper);
        col.setComparator(Comparator.comparing(SmurfManagementController::parseDateForSort));
        return col;
    }

    private static Instant parseDateForSort(String s) {
        if (s == null || s.isBlank() || "Never".equals(s)) return Instant.EPOCH;
        try {
            return HUMAN_READABLE_FORMATTER.parse(s, Instant::from);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    // ---- Helpers ----

    private Instant parseInstantSafe(String str) {
        try {
            return str == null ? null : Instant.parse(str);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String safeGet(UserDataController user, Function<UserDataController.UserInfo, String> mapper) {
        return Optional.ofNullable(user.getUserInfo()).map(mapper).orElse("");
    }

    private String getFirstBan(UserDataController user, Function<UserDataController.BanInfo, String> mapper) {
        List<UserDataController.BanInfo> bans = Optional.ofNullable(user.getUserInfo())
                .map(UserDataController.UserInfo::getBans)
                .orElse(Collections.emptyList());
        return bans.isEmpty() ? "" : mapper.apply(bans.getFirst());
    }

    // ---- Context menu ----

    private void setupContextMenu(TableView<UserDataController> table) {
        ContextMenu menu = new ContextMenu();

        MenuItem copyIdItem = new MenuItem("Copy User ID");
        copyIdItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) copyToClipboard(safeGet(selected, UserDataController.UserInfo::getUserId));
        });

        MenuItem copyNameItem = new MenuItem("Copy Username");
        copyNameItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) copyToClipboard(safeGet(selected, UserDataController.UserInfo::getUserName));
        });

        MenuItem editCommentItem = new MenuItem("Edit Comment");
        editCommentItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) editUserField(selected, true);
        });

        MenuItem editReasonItem = new MenuItem("Edit Reason");
        editReasonItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) editUserField(selected, false);
        });

        MenuItem removeUserItem = new MenuItem("Remove User");
        removeUserItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) removeUser(selected);
        });

        menu.getItems().addAll(copyIdItem, copyNameItem, new SeparatorMenuItem(), editCommentItem, editReasonItem, new SeparatorMenuItem(), removeUserItem);
        table.setContextMenu(menu);
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void editUserField(UserDataController user, boolean isComment) {
        String label = isComment ? "Comment" : "Reason";
        String current = isComment
                ? safeGet(user, UserDataController.UserInfo::getComment)
                : safeGet(user, UserDataController.UserInfo::getReason);
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Edit " + label);
        dialog.setHeaderText("Edit " + label + " for: " + safeGet(user, UserDataController.UserInfo::getUserName));
        dialog.setContentText(label + ":");
        dialog.showAndWait().ifPresent(newValue -> {
            if (user.getUserInfo() != null) {
                if (isComment) user.getUserInfo().setComment(newValue);
                else user.getUserInfo().setReason(newValue);
            }
            smurfManagementTableView.refresh();
            saveUserToSmurfManagement();
        });
    }

    private void setupDoubleClickAction(TableView<UserDataController> table) {
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                UserDataController selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) showHwInfo(selected);
            }
        });
    }

    // ---- Data operations ----

    private void removeUser(UserDataController user) {
        smurfManagementUsersList.removeIf(u -> Objects.equals(u.getUserInfo().getUserId(), user.getUserInfo().getUserId()));
        saveUserToSmurfManagement();
    }

    public void loadSmurfManagementUsers() {
        CompletableFuture.runAsync(() -> {
            try {
                List<UserDataController> users = OBJECT_MAPPER.readValue(SMURF_MANAGEMENT_USERS_JSON_PATH.toFile(),
                        new TypeReference<>() {});
                Platform.runLater(() -> smurfManagementUsersList.setAll(users));
            } catch (IOException e) {
                log.error("Failed to read: {}", SMURF_MANAGEMENT_USERS_JSON_PATH, e);
            }
        });
    }

    private void saveUserToSmurfManagement() {
        List<UserDataController> snapshot = List.copyOf(smurfManagementUsersList);
        CompletableFuture.runAsync(() -> {
            try {
                OBJECT_MAPPER.writeValue(SMURF_MANAGEMENT_USERS_JSON_PATH.toFile(), snapshot);
            } catch (IOException e) {
                log.error("Failed to write {}", SMURF_MANAGEMENT_USERS_JSON_PATH, e);
            }
        });
    }

    // ---- Hardware info popup ----

    private void showHwInfo(UserDataController user) {
        Stage stage = new Stage();
        TableView<Map<String, Object>> table = createHwTable(user);

        ScrollPane scrollPane = new ScrollPane(table);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox layout = new VBox(scrollPane);
        layout.setSpacing(5);
        layout.setPadding(new Insets(5));

        double w = Screen.getPrimary().getVisualBounds().getWidth();
        double h = Screen.getPrimary().getVisualBounds().getHeight();
        Scene scene = new Scene(layout, w * WINDOW_WIDTH_RATIO, h * WINDOW_HEIGHT_RATIO);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/main-dark.css")).toExternalForm());

        stage.setScene(scene);
        stage.setTitle("HWID Info: " + safeGet(user, UserDataController.UserInfo::getUserName)
                + " [id " + safeGet(user, UserDataController.UserInfo::getUserId) + "]");
        stage.setResizable(true);
        stage.show();
    }

    private TableView<Map<String, Object>> createHwTable(UserDataController user) {
        TableView<Map<String, Object>> table = new TableView<>();
        table.getColumns().addAll(
                createHwColumn("Type",     "type",    120),
                createHwColumn("Value",    "value",   500),
                createHwColumn("Added On", "addedOn", 180)
        );
        table.getItems().addAll(flattenHardwareInfo(user));
        return table;
    }

    private TableColumn<Map<String, Object>, String> createHwColumn(String title, String key, double prefWidth) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(Objects.toString(cd.getValue().get(key), "")));
        col.setPrefWidth(prefWidth);
        return col;
    }

    private <T> void addHardwareEntries(
            List<Map<String, Object>> entries, String type, List<T> list,
            Function<T, String> valueExtractor, Function<T, String> addedOnExtractor) {
        if (list == null) return;
        for (T item : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("value", valueExtractor.apply(item));
            String addedOnIso = addedOnExtractor.apply(item);
            if (addedOnIso != null && !addedOnIso.isBlank()) {
                try {
                    map.put("addedOn", HUMAN_READABLE_FORMATTER.format(Instant.parse(addedOnIso)));
                } catch (Exception e) {
                    map.put("addedOn", addedOnIso);
                }
            } else {
                map.put("addedOn", "");
            }
            entries.add(map);
        }
    }

    private List<Map<String, Object>> flattenHardwareInfo(UserDataController user) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (user.getHardwareInfo() == null) return entries;

        UserDataController.HardwareInfo hw = user.getHardwareInfo();
        addHardwareEntries(entries, "IP Address",     hw.getIpAddresses(),            UserDataController.IpAddressEntry::getIp,                    UserDataController.IpAddressEntry::getAddedOn);
        addHardwareEntries(entries, "UUID",           hw.getUuidEntries(),            UserDataController.UuidEntry::getUuid,                       UserDataController.UuidEntry::getAddedOn);
        addHardwareEntries(entries, "Device ID",      hw.getDeviceIdEntries(),        UserDataController.DeviceIdEntry::getDeviceId,               UserDataController.DeviceIdEntry::getAddedOn);
        addHardwareEntries(entries, "Serial Number",  hw.getSerialNumberEntries(),    UserDataController.SerialNumberEntry::getSerialNumber,        UserDataController.SerialNumberEntry::getAddedOn);
        addHardwareEntries(entries, "Processor ID",   hw.getProcessorIdEntries(),     UserDataController.ProcessorIdEntry::getProcessorId,          UserDataController.ProcessorIdEntry::getAddedOn);
        addHardwareEntries(entries, "CPU Name",       hw.getCpuNameEntries(),         UserDataController.CpuNameEntry::getCpuName,                  UserDataController.CpuNameEntry::getAddedOn);
        addHardwareEntries(entries, "BIOS Version",   hw.getBiosVersionEntries(),     UserDataController.BiosVersionEntry::getBiosVersion,          UserDataController.BiosVersionEntry::getAddedOn);
        addHardwareEntries(entries, "Manufacturer",   hw.getManufacturerEntries(),    UserDataController.ManufacturerEntry::getManufacturer,        UserDataController.ManufacturerEntry::getAddedOn);
        addHardwareEntries(entries, "Hash",           hw.getHashEntries(),            UserDataController.HashEntry::getHash,                        UserDataController.HashEntry::getAddedOn);
        addHardwareEntries(entries, "Memory Serial",  hw.getMemorySerialNumberEntries(), UserDataController.MemorySerialNumberEntry::getMemorySerialNumber, UserDataController.MemorySerialNumberEntry::getAddedOn);
        addHardwareEntries(entries, "Volume Serial",  hw.getVolumeSerialNumberEntries(), UserDataController.VolumeSerialNumberEntry::getVolumeSerialNumber, UserDataController.VolumeSerialNumberEntry::getAddedOn);

        Comparator<String> byDateDesc = Comparator.comparingLong((String s) -> {
            if (s == null || s.isBlank()) return Long.MIN_VALUE;
            try { return HUMAN_READABLE_FORMATTER.parse(s, Instant::from).toEpochMilli(); }
            catch (Exception e) { return Long.MIN_VALUE; }
        }).reversed();
        entries.sort(Comparator.comparing(m -> Objects.toString(m.get("addedOn"), ""), byDateDesc));
        return entries;
    }

    // ---- FXML actions ----

    @FXML
    public void reloadSmurfManagement() {
        loadSmurfManagementUsers();
    }

    @FXML
    public void createBackupJson() {
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path backupPath = Paths.get("data", "smurf_management_backup_" + timestamp + ".json");
        try {
            if (!Files.exists(SMURF_MANAGEMENT_USERS_JSON_PATH)) {
                showAlert(Alert.AlertType.WARNING, "Backup Failed", "Original JSON file does not exist.");
                return;
            }
            Files.createDirectories(backupPath.getParent());
            Files.copy(SMURF_MANAGEMENT_USERS_JSON_PATH, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Backup created at: {}", backupPath);
            showAlert(Alert.AlertType.INFORMATION, "Backup Created", "Backup successfully created at:\n" + backupPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create backup at: {}", backupPath, e);
            showAlert(Alert.AlertType.ERROR, "Backup Failed", "Failed to create backup:\n" + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    @FXML
    public void runSmurfManagementButton() {
        loadSmurfManagementUsers();
        userManagementController.handleCheckSmurfManagementAccounts();
    }

    public void openJsonFile() throws IOException {
        openFile(String.valueOf(SMURF_MANAGEMENT_USERS_JSON_PATH));
    }

    public void openFile(String fileName) throws IOException {
        Desktop.getDesktop().open(new File(fileName));
    }
}
