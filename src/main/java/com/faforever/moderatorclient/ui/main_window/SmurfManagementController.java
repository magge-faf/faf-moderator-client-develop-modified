package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.config.ApplicationPaths;
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
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    public static final Path SMURF_MANAGEMENT_USERS_JSON_PATH =
            ApplicationPaths.resolveConfigurationDirectory().resolve("smurf_management.json");

    // Shared with ViewHelper.saveUserToJsonFile: bulk "Run Smurf Management" checks write this file from
    // several worker threads, and comment/reason edits or user removal write it from the UI thread. All of
    // those read-modify-write cycles must be serialized on this single lock or concurrent writers clobber
    // each other's changes (e.g. a comment edit silently reverting a bulk check's updates, or vice versa).
    public static final Object SMURF_MANAGEMENT_JSON_LOCK = new Object();

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

    @SuppressWarnings("unchecked")
    public TableView<UserDataController> createSmurfManagementTable() {
        TableView<UserDataController> table = new TableView<>();

        DateTimeFormatter formatter = HUMAN_READABLE_FORMATTER;

        table.getColumns().addAll(
                createDateColumn("Date Added", user -> {
                    Instant added = parseInstantSafe(safeGet(user, UserDataController.UserInfo::getAddedOn));
                    return added == null ? "" : formatter.format(added);
                }),

                createNumericColumn("User ID",    user -> safeGet(user, UserDataController.UserInfo::getUserId)),
                createColumn      ("User Name",   user -> safeGet(user, UserDataController.UserInfo::getUserName)),

                createDateColumn("Last Login", user -> {
                    List<UserDataController.LoginEntry> lastLogins = user.getAccountHistory().getLastLogins();
                    if (lastLogins == null || lastLogins.isEmpty()) return "Never";
                    Instant lastLogin = parseInstantSafe(lastLogins.getLast().getAddedOn());
                    return lastLogin == null ? "" : formatter.format(lastLogin);
                }),

                createColumn      ("Comment",     user -> safeGet(user, UserDataController.UserInfo::getComment)),
                createColumn      ("Reason",      user -> safeGet(user, UserDataController.UserInfo::getReason)),

                createDateColumn("Last Record Update", user -> {
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
                createDateColumn("Ban Expires At", user -> getFirstBan(user, ban -> formatBanExpiry(ban.getBanExpiresAt()))),
                createDateColumn("Ban Created At", user -> getFirstBan(user, ban -> formatBanCreatedAt(ban.getBanCreatedAt())))
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
        if (s == null || s.isBlank() || "Never".equals(s) || "Permanent".equals(s)) return Instant.EPOCH;
        String datePart = s.contains(" (") ? s.substring(0, s.indexOf(" (")) : s;
        try {
            return HUMAN_READABLE_FORMATTER.parse(datePart, Instant::from);
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

    /**
     * Parses either an {@link Instant}-style ISO string (ends in "Z") or an
     * {@link OffsetDateTime}-style ISO string (e.g. "+02:00"), as used by ban dates.
     */
    private static Instant parseAnyInstant(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Instant.parse(str);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(str).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private String formatBanCreatedAt(String raw) {
        Instant created = parseAnyInstant(raw);
        return created == null ? "" : HUMAN_READABLE_FORMATTER.format(created);
    }

    private String formatBanExpiry(String raw) {
        Instant expires = parseAnyInstant(raw);
        if (expires == null) return "Permanent";
        return HUMAN_READABLE_FORMATTER.format(expires) + " (" + humanizeRelative(Instant.now(), expires) + ")";
    }

    /**
     * Renders the gap between two instants as a compact "y m d h m" string,
     * e.g. "in 1y 2m 3d 4h 5m" or "1y 2m 3d 4h 5m ago".
     */
    private static String humanizeRelative(Instant from, Instant to) {
        boolean future = to.isAfter(from);
        Instant earlier = future ? from : to;
        Instant later = future ? to : from;

        ZonedDateTime zEarlier = earlier.atZone(ZoneId.systemDefault());
        ZonedDateTime zLater = later.atZone(ZoneId.systemDefault());

        Period period = Period.between(zEarlier.toLocalDate(), zLater.toLocalDate());
        ZonedDateTime afterDatePart = zEarlier.plus(period);
        if (afterDatePart.isAfter(zLater)) {
            period = period.minusDays(1);
            afterDatePart = zEarlier.plus(period);
        }
        Duration remainder = Duration.between(afterDatePart, zLater);

        long years = period.getYears();
        long months = period.getMonths();
        long days = period.getDays();
        long hours = remainder.toHours();
        long minutes = remainder.toMinutesPart();

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append("y ");
        if (months > 0) sb.append(months).append("m ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || sb.length() == 0) sb.append(minutes).append("m");

        String duration = sb.toString().trim();
        return future ? "in " + duration : duration + " ago";
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

        MenuItem viewHistoryItem = new MenuItem("View Event History");
        viewHistoryItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) showEventHistory(selected);
        });

        MenuItem removeUserItem = new MenuItem("Remove User");
        removeUserItem.setOnAction(e -> {
            UserDataController selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) removeUser(selected);
        });

        menu.getItems().addAll(copyIdItem, copyNameItem, new SeparatorMenuItem(), editCommentItem, editReasonItem,
                new SeparatorMenuItem(), viewHistoryItem, new SeparatorMenuItem(), removeUserItem);
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
        String userId = safeGet(user, UserDataController.UserInfo::getUserId);
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Edit " + label);
        dialog.setHeaderText("Edit " + label + " for: " + safeGet(user, UserDataController.UserInfo::getUserName));
        dialog.setContentText(label + ":");
        dialog.showAndWait().ifPresent(newValue -> updateUserOnDisk(userId, existingUser -> {
            if (isComment) existingUser.getUserInfo().setComment(newValue);
            else existingUser.getUserInfo().setReason(newValue);
        }));
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
        String userId = safeGet(user, UserDataController.UserInfo::getUserId);
        // Drop it from the visible list immediately for snappy feedback; the on-disk removal below
        // is the source of truth and reconciles the list again once it completes.
        smurfManagementUsersList.removeIf(u -> Objects.equals(safeGet(u, UserDataController.UserInfo::getUserId), userId));
        CompletableFuture.runAsync(() -> {
            synchronized (SMURF_MANAGEMENT_JSON_LOCK) {
                try {
                    List<UserDataController> current = OBJECT_MAPPER.readValue(SMURF_MANAGEMENT_USERS_JSON_PATH.toFile(),
                            new TypeReference<>() {});
                    current.removeIf(u -> Objects.equals(safeGet(u, UserDataController.UserInfo::getUserId), userId));
                    OBJECT_MAPPER.writeValue(SMURF_MANAGEMENT_USERS_JSON_PATH.toFile(), current);
                } catch (IOException e) {
                    log.error("Failed to remove user {} from {}", userId, SMURF_MANAGEMENT_USERS_JSON_PATH, e);
                }
            }
            loadSmurfManagementUsers();
        });
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

    /**
     * Re-reads the current on-disk state, applies {@code mutator} to the matching user, and writes the
     * result back — all under {@link #SMURF_MANAGEMENT_JSON_LOCK} so this can't race with a concurrent
     * bulk "Run Smurf Management" check (or another edit) writing the same file.
     */
    private void updateUserOnDisk(String userId, java.util.function.Consumer<UserDataController> mutator) {
        CompletableFuture.runAsync(() -> {
            synchronized (SMURF_MANAGEMENT_JSON_LOCK) {
                try {
                    List<UserDataController> current = OBJECT_MAPPER.readValue(SMURF_MANAGEMENT_USERS_JSON_PATH.toFile(),
                            new TypeReference<>() {});
                    current.stream()
                            .filter(u -> Objects.equals(safeGet(u, UserDataController.UserInfo::getUserId), userId))
                            .findFirst()
                            .ifPresent(mutator);
                    OBJECT_MAPPER.writeValue(SMURF_MANAGEMENT_USERS_JSON_PATH.toFile(), current);
                } catch (IOException e) {
                    log.error("Failed to update user {} in {}", userId, SMURF_MANAGEMENT_USERS_JSON_PATH, e);
                }
            }
            loadSmurfManagementUsers();
        });
    }

    // ---- Event history popup ----

    private void showEventHistory(UserDataController user) {
        Stage stage = new Stage();

        List<UserDataController.HistoryEntry> historyEntries = Optional.ofNullable(user.getAccountHistory())
                .map(UserDataController.AccountHistory::getHistory)
                .orElse(Collections.emptyList());
        ObservableList<UserDataController.HistoryEntry> allEntries = FXCollections.observableArrayList(historyEntries);
        FilteredList<UserDataController.HistoryEntry> filteredEntries = new FilteredList<>(allEntries, e -> true);
        SortedList<UserDataController.HistoryEntry> sortedEntries = new SortedList<>(filteredEntries,
                Comparator.comparing((UserDataController.HistoryEntry entry) -> {
                    Instant ts = parseAnyInstant(entry.getTimestamp());
                    return ts == null ? Instant.EPOCH : ts;
                }).reversed());

        TableView<UserDataController.HistoryEntry> historyTable = new TableView<>();

        TableColumn<UserDataController.HistoryEntry, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(cd -> {
            Instant ts = parseAnyInstant(cd.getValue().getTimestamp());
            return new SimpleStringProperty(ts == null ? Objects.toString(cd.getValue().getTimestamp(), "") : HUMAN_READABLE_FORMATTER.format(ts));
        });
        timeCol.setPrefWidth(180);

        TableColumn<UserDataController.HistoryEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(cd -> new SimpleStringProperty(Objects.toString(cd.getValue().getAction(), "")));
        actionCol.setPrefWidth(180);

        TableColumn<UserDataController.HistoryEntry, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cd -> new SimpleStringProperty(Objects.toString(cd.getValue().getDescription(), "")));

        historyTable.getColumns().addAll(timeCol, actionCol, descCol);
        historyTable.setItems(sortedEntries);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        DatePicker fromPicker = new DatePicker();
        fromPicker.setPromptText("From");
        DatePicker toPicker = new DatePicker();
        toPicker.setPromptText("To");
        Label countLabel = new Label();

        Runnable applyTimeframe = () -> {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            filteredEntries.setPredicate(entry -> {
                if (from == null && to == null) return true;
                Instant ts = parseAnyInstant(entry.getTimestamp());
                if (ts == null) return true;
                LocalDate entryDate = ts.atZone(ZoneId.systemDefault()).toLocalDate();
                if (from != null && entryDate.isBefore(from)) return false;
                return to == null || !entryDate.isAfter(to);
            });
            countLabel.setText(filteredEntries.size() + " / " + allEntries.size() + " events");
        };

        fromPicker.valueProperty().addListener((obs, oldVal, newVal) -> applyTimeframe.run());
        toPicker.valueProperty().addListener((obs, oldVal, newVal) -> applyTimeframe.run());

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
        });

        HBox filterBar = new HBox(8, new Label("From:"), fromPicker, new Label("To:"), toPicker, clearButton, countLabel);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(5));

        applyTimeframe.run();

        VBox layout = new VBox(filterBar, historyTable);
        layout.setSpacing(5);
        layout.setPadding(new Insets(5));

        double w = Screen.getPrimary().getVisualBounds().getWidth();
        double h = Screen.getPrimary().getVisualBounds().getHeight();
        Scene scene = new Scene(layout, w * WINDOW_WIDTH_RATIO, h * WINDOW_HEIGHT_RATIO);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/main-dark.css")).toExternalForm());

        stage.setScene(scene);
        stage.setTitle("Event History: " + safeGet(user, UserDataController.UserInfo::getUserName)
                + " [id " + safeGet(user, UserDataController.UserInfo::getUserId) + "]");
        stage.setResizable(true);
        stage.show();
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

    @SuppressWarnings("unchecked")
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
        Path backupPath = ApplicationPaths.resolveConfigurationDirectory().resolve("smurf_management_backup_" + timestamp + ".json");
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
        openPath(new File(fileName));
    }

    private static void openPath(File path) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            new ProcessBuilder("cmd", "/c", "start", "", path.getAbsolutePath()).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", path.getAbsolutePath()).start();
        } else {
            new ProcessBuilder("xdg-open", path.getAbsolutePath()).start();
        }
    }
}
