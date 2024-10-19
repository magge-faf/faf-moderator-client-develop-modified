package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.ui.domain.UserNoteFX;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import com.faforever.moderatorclient.ui.Controller;
import javafx.scene.layout.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javafx.scene.input.ClipboardContent;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecentNotesController implements Controller<Region> {
    @FXML
    public VBox root;
    @FXML
    public TableView<UserNoteFX> notesTable;
    @FXML
    public TableColumn<UserNoteFX, Integer> noteIdColumn;
    @FXML
    public TableColumn<UserNoteFX, String> playerIdColumn;
    @FXML
    public TableColumn<UserNoteFX, String> authorIdColumn;
    @FXML
    public TableColumn<UserNoteFX, String> noteColumn;
    @FXML
    public TableColumn<UserNoteFX, String> noteCreatedColumn;
    @FXML

    public TableColumn<UserNoteFX, String> noteUpdatedColumn;
    private final UserService userService;
    public Button refreshNotes;

    @Override
    public VBox getRoot() {
        loadUserNotes();
        return root;
    }

    private void loadUserNotes() {
        log.debug("Loading all user notes.");
        List<UserNoteFX> userNotes = userService.getAllUserNotes();

        ObservableList<UserNoteFX> notesData = FXCollections.observableArrayList(userNotes);
        notesTable.setItems(notesData);
    }

    @FXML
    public void initialize() {
        setupColumns();

        notesTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copySelectedCellToClipboard();
                event.consume();
            }
        });

        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyNoteItem = new MenuItem("Copy Note");
        copyNoteItem.setOnAction(event -> copySelectedItemToClipboardMouse("Note"));

        MenuItem copyModeratorItem = new MenuItem("Copy Moderator");
        copyModeratorItem.setOnAction(event -> copySelectedItemToClipboardMouse("Moderator"));

        MenuItem copyUserItem = new MenuItem("Copy User");
        copyUserItem.setOnAction(event -> copySelectedItemToClipboardMouse("User"));

        MenuItem copyCreatedItem = new MenuItem("Copy Created");
        copyCreatedItem.setOnAction(event -> copySelectedItemToClipboardMouse("Created"));

        MenuItem copyUpdatedItem = new MenuItem("Copy Updated");
        copyUpdatedItem.setOnAction(event -> copySelectedItemToClipboardMouse("Updated"));

        contextMenu.getItems().addAll(copyNoteItem, copyModeratorItem, copyUserItem, copyCreatedItem, copyUpdatedItem);

        notesTable.setOnContextMenuRequested((ContextMenuEvent event) -> {
            contextMenu.show(notesTable, event.getScreenX(), event.getScreenY());
        });
    }

    private void setupColumns() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        noteCreatedColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatter.format(cellData.getValue().getCreateTime()))
        );
        noteUpdatedColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatter.format(cellData.getValue().getUpdateTime()))
        );
        noteIdColumn.setCellValueFactory(cellData -> {
            String idString = cellData.getValue().getId();
            int id = Integer.parseInt(idString);
            return new SimpleIntegerProperty(id).asObject();
        });
        authorIdColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getAuthor().getLogin())
        );
        playerIdColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getPlayer().getRepresentation())
        );
        noteColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getNote())
        );

        noteCreatedColumn.setComparator(Comparator.comparing(dateStr ->
                OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        ));

        noteCreatedColumn.setSortable(true);
        noteIdColumn.setSortable(true);

        Platform.runLater(() -> {
            noteIdColumn.setSortType(TableColumn.SortType.DESCENDING);
            notesTable.getSortOrder().add(noteIdColumn);
            notesTable.sort();
        });
    }

    private void copySelectedItemToClipboardMouse(String field) {
        int selectedIndex = notesTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            UserNoteFX selectedNote = notesTable.getItems().get(selectedIndex);
            String contentString = switch (field) {
                case "Note" -> selectedNote.getNote();
                case "Moderator" -> selectedNote.getAuthor().getLogin();
                case "User" -> selectedNote.getPlayer().getRepresentation();
                case "Created" -> selectedNote.getCreateTime().toString();
                case "Updated" -> selectedNote.getUpdateTime().toString();
                default -> "";
            };

            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(contentString);

            clipboard.setContent(content);
        }
    }

    private void copySelectedCellToClipboard() {
        int selectedIndex = notesTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            UserNoteFX selectedNote = notesTable.getItems().get(selectedIndex);

            String contentString = String.format("%s %s %s %s %s %s ",
                    selectedNote.getCreateTime(),
                    selectedNote.getUpdateTime(),
                    selectedNote.getId(),
                    selectedNote.getAuthor().getLogin(),
                    selectedNote.getPlayer().getRepresentation(),
                    selectedNote.getNote());

            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(contentString);
            clipboard.setContent(content);
        }
    }

}
