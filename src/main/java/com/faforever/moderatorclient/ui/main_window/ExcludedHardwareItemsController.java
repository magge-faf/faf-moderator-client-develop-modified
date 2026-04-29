package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.Controller;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ExcludedHardwareItemsController implements Controller<VBox> {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final File EXCLUDED_ITEMS_FILE = new File("data/excluded_items.json");

    private static final Map<String, String> DISPLAY_NAMES;
    static {
        DISPLAY_NAMES = new LinkedHashMap<>();
        DISPLAY_NAMES.put("ip", "IP");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.uuid", "UUID");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.deviceId", "Device ID");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.manufacturer", "Manufacturer");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.name", "CPU Name");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.processorId", "Processor ID");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.serialNumber", "Serial Number");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.volumeSerialNumber", "Volume Serial Number");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.hash", "Hash");
        DISPLAY_NAMES.put("uniqueIdAssignments.uniqueId.memorySerialNumber", "Memory Serial Number");
    }

    private static final List<String> HARDWARE_KEYS = List.copyOf(DISPLAY_NAMES.keySet());

    @Getter
    @FXML private VBox root;

    @FXML private CheckBox keepCommentFieldCheckBox;
    @FXML private CheckBox keepReasonFieldCheckBox;
    @FXML private Label statsExcludedItemsLabel;
    @FXML private TextField commentField, reasonField;
    @FXML private TextField ipAddressField, uuidField, deviceIdField, manufacturerField,
            cpuNameField, processorIdField, serialNumberField,
            volumeSerialNumberField, hashField, memorySerialNumberField;
    @FXML private TableView<Map<String, Object>> excludedItemsTable;
    @FXML private Button removeItemButton;
    @FXML private TextField tableSearchField;

    private final Map<String, TextField> fieldsMap = new LinkedHashMap<>();
    private ObservableList<Map<String, Object>> excludedItemsList;
    private FilteredList<Map<String, Object>> filteredExcludedItems;

    @FXML
    public void initialize() {
        setupFieldsMap();
        setupTable();
        setupSearch();
        setupFieldListeners();
        removeItemButton.disableProperty().bind(
                excludedItemsTable.getSelectionModel().selectedItemProperty().isNull());
        loadItemsFromFile();
    }

    private void setupFieldsMap() {
        fieldsMap.put("ip", ipAddressField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.uuid", uuidField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.deviceId", deviceIdField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.manufacturer", manufacturerField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.name", cpuNameField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.processorId", processorIdField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.serialNumber", serialNumberField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.volumeSerialNumber", volumeSerialNumberField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.hash", hashField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.memorySerialNumber", memorySerialNumberField);
    }

    private void setupTable() {
        excludedItemsList = FXCollections.observableArrayList();
        filteredExcludedItems = new FilteredList<>(excludedItemsList, p -> true);
        SortedList<Map<String, Object>> sortedList = new SortedList<>(filteredExcludedItems);
        sortedList.comparatorProperty().bind(excludedItemsTable.comparatorProperty());
        excludedItemsTable.setItems(sortedList);

        TableColumn<Map<String, Object>, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(140);
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(getItemType(cd.getValue())));

        TableColumn<Map<String, Object>, String> valueCol = new TableColumn<>("Value");
        valueCol.setPrefWidth(220);
        valueCol.setCellValueFactory(cd -> new SimpleStringProperty(getItemValue(cd.getValue())));

        TableColumn<Map<String, Object>, String> commentCol = new TableColumn<>("Comment");
        commentCol.setPrefWidth(160);
        commentCol.setCellValueFactory(cd ->
                new SimpleStringProperty(Objects.toString(cd.getValue().get("comment"), "")));

        TableColumn<Map<String, Object>, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setPrefWidth(160);
        reasonCol.setCellValueFactory(cd ->
                new SimpleStringProperty(Objects.toString(cd.getValue().get("reason"), "")));

        TableColumn<Map<String, Object>, String> addedOnCol = new TableColumn<>("Added On");
        addedOnCol.setPrefWidth(160);
        addedOnCol.setCellValueFactory(cd ->
                new SimpleStringProperty(Objects.toString(cd.getValue().get("AddedOn"), "")));

        excludedItemsTable.getColumns().addAll(typeCol, valueCol, commentCol, reasonCol, addedOnCol);

        excludedItemsList.addListener((javafx.collections.ListChangeListener<Map<String, Object>>) c ->
                Platform.runLater(this::updateStatsLabel));
        filteredExcludedItems.addListener((javafx.collections.ListChangeListener<Map<String, Object>>) c ->
                Platform.runLater(this::updateStatsLabel));
    }

    private void setupSearch() {
        tableSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String lower = newVal == null ? "" : newVal.toLowerCase();
            filteredExcludedItems.setPredicate(item -> {
                if (lower.isBlank()) return true;
                if (getItemType(item).toLowerCase().contains(lower)) return true;
                if (getItemValue(item).toLowerCase().contains(lower)) return true;
                if (Objects.toString(item.get("comment"), "").toLowerCase().contains(lower)) return true;
                return Objects.toString(item.get("reason"), "").toLowerCase().contains(lower);
            });
        });
    }

    private void setupFieldListeners() {
        fieldsMap.forEach((fieldName, field) ->
                field.textProperty().addListener((obs, oldValue, newValue) ->
                        validateField(field, fieldName, newValue)));
    }

    private void validateField(TextField field, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            field.setStyle("");
            return;
        }
        boolean excluded = excludedItemsList.stream()
                .anyMatch(item -> value.equals(Objects.toString(item.get(fieldName), null)));
        field.setStyle(excluded
                ? "-fx-border-color: red; -fx-border-width: 1.5;"
                : "-fx-border-color: #4caf50; -fx-border-width: 1.5;");
    }

    @FXML
    public void handleOpenExcludedItemsFolder() {
        File folder = EXCLUDED_ITEMS_FILE.getParentFile();
        if (folder == null || !folder.exists()) {
            log.warn("Excluded items folder does not exist.");
            return;
        }
        try {
            Desktop.getDesktop().open(folder);
        } catch (IOException e) {
            log.error("Failed to open folder: {}", e.getMessage());
        }
    }

    @FXML
    public void handleAddItem() {
        Map<String, Object> newItem = new LinkedHashMap<>();
        fieldsMap.forEach((key, field) -> {
            String text = field.getText();
            if (text != null && !text.isBlank()) newItem.put(key, text);
        });
        if (commentField.getText() != null && !commentField.getText().isBlank())
            newItem.put("comment", commentField.getText());
        if (reasonField.getText() != null && !reasonField.getText().isBlank())
            newItem.put("reason", reasonField.getText());

        List<String> newHardwareKeys = HARDWARE_KEYS.stream()
                .filter(newItem::containsKey)
                .collect(Collectors.toList());

        if (newHardwareKeys.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Required",
                    "Please fill in at least one hardware field before adding.");
            return;
        }

        boolean exists = excludedItemsList.stream().anyMatch(item ->
                newHardwareKeys.stream().anyMatch(key ->
                        newItem.get(key).equals(item.get(key))));
        if (exists) {
            showAlert(Alert.AlertType.WARNING, "Duplicate",
                    "An excluded item with this hardware value already exists.");
            return;
        }

        newItem.put("AddedOn", LocalDateTime.now().toString());
        clearUserInputFields();

        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> items = readExcludedItems();
                items.add(newItem);
                writeExcludedItems(items);
                Platform.runLater(() -> excludedItemsList.add(newItem));
                log.debug("Added new excluded item: {}", newItem);
            } catch (IOException e) {
                log.error("Failed to save excluded item: {}", e.getMessage());
            }
        });
    }

    @FXML
    public void handleRemoveItem() {
        Map<String, Object> selected = excludedItemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        List<Map<String, Object>> snapshot = List.copyOf(excludedItemsList);

        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> updated = new ArrayList<>(snapshot);
                updated.remove(selected);
                writeExcludedItems(updated);
                Platform.runLater(() -> excludedItemsList.remove(selected));
                log.debug("Removed excluded item: {}", selected);
            } catch (IOException e) {
                log.error("Failed to remove excluded item: {}", e.getMessage());
            }
        });
    }

    private void clearUserInputFields() {
        fieldsMap.values().forEach(field -> {
            field.clear();
            field.setStyle("");
        });
        if (!keepCommentFieldCheckBox.isSelected()) commentField.clear();
        if (!keepReasonFieldCheckBox.isSelected()) reasonField.clear();
    }

    public void saveExcludedItem(Map<String, Object> newItem) {
        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> items = readExcludedItems();
                boolean exists = items.stream().anyMatch(item ->
                        HARDWARE_KEYS.stream().anyMatch(key ->
                                newItem.containsKey(key) && newItem.get(key).equals(item.get(key))));
                if (!exists) {
                    items.add(newItem);
                    writeExcludedItems(items);
                    Platform.runLater(() -> excludedItemsList.add(newItem));
                    log.debug("Added excluded item via saveExcludedItem: {}", newItem);
                } else {
                    log.warn("Excluded item already exists: {}", newItem);
                }
            } catch (IOException e) {
                log.error("Failed to save excluded item: {}", e.getMessage());
            }
        });
    }

    public void updateStatsDisplay() {
        loadItemsFromFile();
    }

    private void loadItemsFromFile() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> items = readExcludedItems();
                Platform.runLater(() -> {
                    excludedItemsList.setAll(items);
                    updateStatsLabel();
                    fieldsMap.forEach((key, field) -> validateField(field, key, field.getText()));
                });
            } catch (IOException e) {
                log.error("Failed to load excluded items: {}", e.getMessage());
                Platform.runLater(() -> statsExcludedItemsLabel.setText("Error loading items."));
            }
        });
    }

    private void updateStatsLabel() {
        int total = excludedItemsList.size();
        int shown = filteredExcludedItems.size();
        if (total == 0) {
            statsExcludedItemsLabel.setText("No excluded items.");
        } else if (shown < total) {
            statsExcludedItemsLabel.setText(shown + " of " + total + " items");
        } else {
            statsExcludedItemsLabel.setText(total + " item" + (total == 1 ? "" : "s"));
        }
    }

    @FXML
    public void handleOpenJsonFile() {
        if (!EXCLUDED_ITEMS_FILE.exists()) {
            showAlert(Alert.AlertType.WARNING, "File Not Found",
                    "The excluded_items.json file does not exist.");
            return;
        }
        try {
            Desktop.getDesktop().open(EXCLUDED_ITEMS_FILE);
        } catch (IOException e) {
            log.error("Failed to open excluded_items.json: {}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Open File Failed",
                    "Could not open JSON file:\n" + e.getMessage());
        }
    }

    @FXML
    public void handleOnCreateJsonBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupPath = Paths.get("data", "excluded_items_backup_" + timestamp + ".json");
        try {
            if (!EXCLUDED_ITEMS_FILE.exists()) {
                showAlert(Alert.AlertType.WARNING, "Backup Failed",
                        "The excluded_items.json file does not exist.");
                return;
            }
            Files.createDirectories(backupPath.getParent());
            Files.copy(EXCLUDED_ITEMS_FILE.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Backup created at: {}", backupPath.toAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "Backup Created",
                    "Backup successfully created at:\n" + backupPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create backup: {}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Backup Failed",
                    "An error occurred while creating the backup:\n" + e.getMessage());
        }
    }

    private List<Map<String, Object>> readExcludedItems() throws IOException {
        if (!EXCLUDED_ITEMS_FILE.exists() || EXCLUDED_ITEMS_FILE.length() == 0) {
            return new ArrayList<>();
        }
        TypeReference<Map<String, List<Map<String, Object>>>> typeRef = new TypeReference<>() {};
        Map<String, List<Map<String, Object>>> root = OBJECT_MAPPER.readValue(EXCLUDED_ITEMS_FILE, typeRef);
        return new ArrayList<>(root.getOrDefault("excluded_items", new ArrayList<>()));
    }

    private void writeExcludedItems(List<Map<String, Object>> items) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("excluded_items", items);
        EXCLUDED_ITEMS_FILE.getParentFile().mkdirs();
        OBJECT_MAPPER.writeValue(EXCLUDED_ITEMS_FILE, root);
    }

    private static String getItemType(Map<String, Object> item) {
        List<String> setKeys = HARDWARE_KEYS.stream()
                .filter(k -> item.get(k) != null && !item.get(k).toString().isBlank())
                .collect(Collectors.toList());
        if (setKeys.isEmpty()) return "Unknown";
        if (setKeys.size() == 1) return DISPLAY_NAMES.getOrDefault(setKeys.get(0), setKeys.get(0));
        return "Multiple (" + setKeys.size() + ")";
    }

    private static String getItemValue(Map<String, Object> item) {
        List<String> setKeys = HARDWARE_KEYS.stream()
                .filter(k -> item.get(k) != null && !item.get(k).toString().isBlank())
                .collect(Collectors.toList());
        if (setKeys.isEmpty()) return "";
        if (setKeys.size() == 1) return Objects.toString(item.get(setKeys.get(0)), "");
        return setKeys.stream()
                .map(k -> DISPLAY_NAMES.getOrDefault(k, k) + ": " + item.get(k))
                .collect(Collectors.joining(" | "));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
