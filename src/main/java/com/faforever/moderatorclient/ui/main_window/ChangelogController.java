package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.update.ApplicationUpdateService;
import com.faforever.moderatorclient.update.GithubRelease;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.PlatformService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChangelogController implements Controller<VBox> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String PROJECT_ISSUES_URL = "https://github.com/magge-faf/faf-moderator-client-develop-modified/issues";

    private final ApplicationUpdateService applicationUpdateService;
    private final PlatformService platformService;
    private final LocalPreferences localPreferences;

    @FXML
    public VBox root;
    @FXML
    public ComboBox<GithubRelease> buildComboBox;
    @FXML
    public Label releaseInfoLabel;
    @FXML
    public WebView changelogWebView;

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
        changelogWebView.setStyle(localPreferences.getTabSettings().isDarkModeCheckBox()
                ? "-fx-background-color: #1f2329;"
                : "-fx-background-color: #ffffff;");
        showChangelogMessage("Select Refresh to load recent GitHub release notes.");
    }

    public void loadIfNeeded() {
        if (!loaded) {
            onRefresh();
        }
    }

    @FXML
    public void onRefresh() {
        releaseInfoLabel.setText("Loading recent builds...");
        showChangelogMessage("");

        Thread thread = new Thread(() -> {
            try {
                List<GithubRelease> releases = applicationUpdateService.fetchRecentReleases();
                Platform.runLater(() -> {
                    loaded = true;
                    buildComboBox.setItems(FXCollections.observableArrayList(releases));
                    if (releases.isEmpty()) {
                        releaseInfoLabel.setText("No GitHub releases found.");
                        showChangelogMessage("");
                    } else {
                        buildComboBox.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to load GitHub changelog releases", e);
                Platform.runLater(() -> {
                    releaseInfoLabel.setText("Failed to load recent builds.");
                    showChangelogMessage(e.getMessage() == null ? e.toString() : e.getMessage());
                });
            }
        }, "changelog-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void onOpenProjectIssues() {
        platformService.showDocument(PROJECT_ISSUES_URL);
    }

    private void showRelease(GithubRelease release) {
        if (release == null) {
            releaseInfoLabel.setText("");
            showChangelogMessage("");
            return;
        }

        String published = release.publishedAt() == null ? "Unknown publish date" : "Published: " + DATE_FORMAT.format(release.publishedAt());
        releaseInfoLabel.setText(published + " | " + release.htmlUrl());
        showChangelogMarkdown(release.changelogText().isBlank()
                ? "No changelog text was published for this release."
                : release.changelogText());
    }

    private void showChangelogMessage(String message) {
        changelogWebView.getEngine().loadContent(buildHtml("<p>" + escapeHtml(message) + "</p>"));
    }

    private void showChangelogMarkdown(String markdown) {
        changelogWebView.getEngine().loadContent(buildHtml(renderMarkdown(markdown)));
    }

    private String buildHtml(String body) {
        boolean darkMode = localPreferences.getTabSettings().isDarkModeCheckBox();
        String backgroundColor = darkMode ? "#1f2329" : "#ffffff";
        String textColor = darkMode ? "#dce1e8" : "#202124";
        String headingColor = darkMode ? "#f2f5f8" : "#202124";
        String codeBackgroundColor = darkMode ? "#303640" : "#f1f3f4";
        String codeTextColor = darkMode ? "#f0f3f6" : "#202124";
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <style>
                    body {
                      font-family: "Segoe UI", Arial, sans-serif;
                      font-size: 13px;
                      line-height: 1.45;
                      color: %s;
                      background: %s;
                      margin: 14px;
                    }
                    h1, h2, h3 {
                      margin: 0 0 8px 0;
                      line-height: 1.2;
                      color: %s;
                    }
                    h1 { font-size: 22px; }
                    h2 { font-size: 18px; margin-top: 16px; }
                    h3 { font-size: 15px; margin-top: 12px; }
                    p { margin: 0 0 10px 0; }
                    ul { margin: 4px 0 10px 22px; padding: 0; }
                    li { margin: 4px 0; }
                    code {
                      font-family: Consolas, "Cascadia Mono", monospace;
                      background: %s;
                      color: %s;
                      border-radius: 4px;
                      padding: 1px 4px;
                    }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(textColor, backgroundColor, headingColor, codeBackgroundColor, codeTextColor, body);
    }

    private String renderMarkdown(String markdown) {
        StringBuilder html = new StringBuilder();
        int openListDepth = 0;

        for (String line : markdown.split("\\R", -1)) {
            if (line.isBlank()) {
                openListDepth = closeLists(html, openListDepth, 0);
                continue;
            }

            String trimmed = line.stripLeading();
            if (trimmed.startsWith("### ")) {
                openListDepth = closeLists(html, openListDepth, 0);
                html.append("<h3>").append(renderInline(trimmed.substring(4))).append("</h3>");
            } else if (trimmed.startsWith("## ")) {
                openListDepth = closeLists(html, openListDepth, 0);
                html.append("<h2>").append(renderInline(trimmed.substring(3))).append("</h2>");
            } else if (trimmed.startsWith("# ")) {
                openListDepth = closeLists(html, openListDepth, 0);
                html.append("<h1>").append(renderInline(trimmed.substring(2))).append("</h1>");
            } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                int desiredDepth = line.indexOf(trimmed) >= 2 ? 2 : 1;
                while (openListDepth < desiredDepth) {
                    html.append("<ul>");
                    openListDepth++;
                }
                openListDepth = closeLists(html, openListDepth, desiredDepth);
                html.append("<li>").append(renderInline(trimmed.substring(2))).append("</li>");
            } else {
                openListDepth = closeLists(html, openListDepth, 0);
                html.append("<p>").append(renderInline(trimmed)).append("</p>");
            }
        }

        closeLists(html, openListDepth, 0);
        return html.toString();
    }

    private int closeLists(StringBuilder html, int openListDepth, int desiredDepth) {
        while (openListDepth > desiredDepth) {
            html.append("</ul>");
            openListDepth--;
        }
        return openListDepth;
    }

    private String renderInline(String text) {
        String rendered = escapeHtml(text);
        rendered = Pattern.compile("`([^`]+)`").matcher(rendered).replaceAll("<code>$1</code>");
        rendered = Pattern.compile("\\*\\*([^*]+)\\*\\*").matcher(rendered).replaceAll("<strong>$1</strong>");
        return rendered;
    }

    private String escapeHtml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
