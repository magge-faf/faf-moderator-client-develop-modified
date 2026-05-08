package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.api.ApiCallRecord;
import com.faforever.moderatorclient.api.ApiHistoryService;
import com.faforever.moderatorclient.ui.Controller;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ApiHistoryController implements Controller<VBox> {

    private final ApiHistoryService apiHistoryService;

    @FXML public VBox root;
    @FXML public TableView<ApiCallRecord> historyTable;
    @FXML public TableColumn<ApiCallRecord, String> timeColumn;
    @FXML public TableColumn<ApiCallRecord, String> methodColumn;
    @FXML public TableColumn<ApiCallRecord, String> urlColumn;
    @FXML public TableColumn<ApiCallRecord, String> statusColumn;
    @FXML public TableColumn<ApiCallRecord, String> durationColumn;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public VBox getRoot() { return root; }

    @FXML
    public void initialize() {
        timeColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTime().format(TIME_FMT)));
        methodColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getMethod()));
        urlColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUrl()));
        statusColumn.setCellValueFactory(c -> {
            int code = c.getValue().getStatusCode();
            return new SimpleStringProperty(code == 0 ? "ERROR" : String.valueOf(code));
        });
        durationColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDurationMs() + " ms"));

        historyTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ApiCallRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    setContextMenu(null);
                } else {
                    if (!item.isSuccess()) {
                        setStyle("-fx-background-color: #ffdddd;");
                    } else {
                        setStyle("");
                    }
                    setContextMenu(buildContextMenu(item));
                }
            }
        });

        historyTable.setItems(apiHistoryService.getHistory());
    }

    private ContextMenu buildContextMenu(ApiCallRecord item) {
        ContextMenu menu = new ContextMenu();
        String time = item.getTime().format(TIME_FMT);
        int code = item.getStatusCode();
        String status = code == 0 ? "ERROR" : String.valueOf(code);

        String method = Objects.toString(item.getMethod(), "");
        String url    = Objects.toString(item.getUrl(), "");

        menu.getItems().addAll(
            copyItem("Copy Time",     time),
            copyItem("Copy Method",   method),
            copyItem("Copy URL",      url),
            copyItem("Copy Status",   status),
            copyItem("Copy Duration", item.getDurationMs() + " ms"),
            copyItem("Copy Row",      String.join("\t", time, method, url, status, item.getDurationMs() + " ms"))
        );
        return menu;
    }

    private MenuItem copyItem(String label, String value) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(value);
            Clipboard.getSystemClipboard().setContent(content);
        });
        return item;
    }

    @FXML
    public void onClear() {
        apiHistoryService.clear();
    }
}
