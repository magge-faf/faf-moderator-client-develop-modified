package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.api.ApiCallRecord;
import com.faforever.moderatorclient.api.ApiHistoryService;
import com.faforever.moderatorclient.ui.Controller;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

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
                } else if (!item.isSuccess()) {
                    setStyle("-fx-background-color: #ffdddd;");
                } else {
                    setStyle("");
                }
            }
        });

        historyTable.setItems(apiHistoryService.getHistory());
    }

    @FXML
    public void onClear() {
        apiHistoryService.clear();
    }
}
