package com.faforever.moderatorclient.api;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class ApiHistoryService {

    private static final int MAX_ENTRIES = 1000;
    private final ObservableList<ApiCallRecord> history = FXCollections.observableArrayList();

    public ObservableList<ApiCallRecord> getHistory() {
        return history;
    }

    public void record(String method, String url, int statusCode, long durationMs, boolean success) {
        ApiCallRecord entry = new ApiCallRecord(LocalTime.now(), method, url, statusCode, durationMs, success);
        Platform.runLater(() -> {
            history.add(0, entry);
            if (history.size() > MAX_ENTRIES) {
                history.remove(MAX_ENTRIES, history.size());
            }
        });
    }

    public void clear() {
        Platform.runLater(history::clear);
    }
}
