package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.update.ApplicationUpdateService;
import com.faforever.moderatorclient.update.GithubRelease;
import com.faforever.moderatorclient.ui.Controller;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChangelogController implements Controller<VBox> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ApplicationUpdateService applicationUpdateService;

    @FXML
    public VBox root;
    @FXML
    public ComboBox<GithubRelease> buildComboBox;
    @FXML
    public Button refreshButton;
    @FXML
    public Label releaseInfoLabel;
    @FXML
    public TextArea changelogTextArea;

    private boolean loaded;

    @Override
    public VBox getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        buildComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(GithubRelease release) {
                if (release == null) {
                    return "";
                }
                String published = release.publishedAt() == null ? "unknown date" : DATE_FORMAT.format(release.publishedAt());
                return release.displayName() + " (" + published + ")";
            }

            @Override
            public GithubRelease fromString(String value) {
                return null;
            }
        });
        buildComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> showRelease(newValue));
        changelogTextArea.setText("Select Refresh to load recent GitHub release notes.");
    }

    public void loadIfNeeded() {
        if (!loaded) {
            onRefresh();
        }
    }

    @FXML
    public void onRefresh() {
        refreshButton.setDisable(true);
        releaseInfoLabel.setText("Loading recent builds...");
        changelogTextArea.setText("");

        Thread thread = new Thread(() -> {
            try {
                List<GithubRelease> releases = applicationUpdateService.fetchRecentReleases();
                Platform.runLater(() -> {
                    loaded = true;
                    buildComboBox.setItems(FXCollections.observableArrayList(releases));
                    if (releases.isEmpty()) {
                        releaseInfoLabel.setText("No GitHub releases found.");
                        changelogTextArea.setText("");
                    } else {
                        buildComboBox.getSelectionModel().selectFirst();
                    }
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                log.warn("Failed to load GitHub changelog releases", e);
                Platform.runLater(() -> {
                    releaseInfoLabel.setText("Failed to load recent builds.");
                    changelogTextArea.setText(e.getMessage() == null ? e.toString() : e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "changelog-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void showRelease(GithubRelease release) {
        if (release == null) {
            releaseInfoLabel.setText("");
            changelogTextArea.setText("");
            return;
        }

        String published = release.publishedAt() == null ? "Unknown publish date" : "Published: " + DATE_FORMAT.format(release.publishedAt());
        releaseInfoLabel.setText(published + " | " + release.htmlUrl());
        changelogTextArea.setText(release.changelogText().isBlank()
                ? "No changelog text was published for this release."
                : release.changelogText());
        changelogTextArea.positionCaret(0);
    }
}
