package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.commons.replay.ChatMessage;
import com.faforever.commons.replay.ModeratorEvent;
import com.faforever.commons.replay.ReplayDataParser;
import com.faforever.commons.replay.ReplayMetadata;
import com.faforever.commons.replay.GameOption;
import com.faforever.commons.replay.body.Event;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.BanService;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.config.TemplateAndReasonConfig;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.GameFX;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import javafx.scene.input.Clipboard;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.springframework.beans.factory.annotation.Autowired;
import com.faforever.moderatorclient.ui.main_window.SettingsController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.scene.paint.Color;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;
import static java.text.MessageFormat.format;



@Component
@Slf4j
@RequiredArgsConstructor
public class ModerationReportController implements Controller<Region> {
    @Autowired
    public SettingsController settingsController;

    private final ObjectMapper objectMapper;
    private final ModerationReportService moderationReportService;
    private final UiService uiService;
    private final FafApiCommunicationService communicationService;
    private final PlatformService platformService;
    private final ObservableList<PlayerFX> reportedPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final BanService banService;
    public Button createReportForumButton;
    public TableColumn lastActivity;
    @FXML
    public Button copyModeratorEventsButton;
    @FXML
    public Button copyChatLogButtonOffenderOnly;
    @FXML
    private CheckBox enforceRatingCheckBox;
    @FXML
    private CheckBox gameResultCheckBox;
    @FXML
    private CheckBox jsonStatsCheckBox;
    @FXML
    private CheckBox gameEndedCheckBox;

    private FilteredList<ModerationReportFX> filteredItemList;
    private ObservableMap<Integer, ModerationReportFX> itemMap;
    private ObservableList<ModerationReportFX> itemList;
    private ModerationReportFX currentlySelectedItemNotNull;

    public Button copyReporterIdButton;
    @FXML
    public TableView<Offender> mostReportedAccountsTableView;
    public TextArea moderatorStatisticsTextArea;
    public CheckBox filterLogCheckBox;
    public CheckBox automaticallyLoadChatLogCheckBox;
    public Button useTemplateWithoutReasonsButton;
    public TableView<ModeratorStatistics> moderatorStatisticsTableView;
    public Button useTemplateWithReasonsButton;
    @FXML
    public TextFlow moderatorEventTextFlow;
    public TextField getModeratorEventsForReplayIdTextField;
    @FXML
    public Button getModeratorEventsReplayIdButton;
    public CheckBox pingOfTypeMoveFilterCheckBox;
    public CheckBox pingOfTypeAttackFilterCheckBox;
    public CheckBox pingOfTypeAlertFilterCheckBox;
    public CheckBox selfDestructionFilterCheckBox;
    public TextField selfDestructionFilterAmountTextField;
    @FXML
    public CheckBox focusArmyFromFilterCheckBox;
    public CheckBox showAdvancedStatisticsModeratorEventsCheckBox;
    public CheckBox textMarkerTypeFilterCheckBox;
    public Button saveSettingsModeratorEventsButton;
    public Region root;
    public ChoiceBox<ChooseableStatus> statusChoiceBox;
    public TextField playerNameFilterTextField;
    public TableView<ModerationReportFX> reportTableView;
    public Button editReportButton;
    public TableView<PlayerFX> reportedPlayerTableView;
    @FXML
    public TextFlow chatLogTextFlow;
    public Button copyReportedUserIdButton;
    public Button copyChatLogButton;
    public Button copyReportIdButton;
    public Button copyGameIdButton;
    public Button startReplayButton;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;

    @Override
    public SplitPane getRoot() {
        return (SplitPane) root;
    }

    public void onCopyReportedUserID() {
        setSysClipboardText(copyReportedUserIdButton.getId());
    }

    public void onCopyChatLog() {
        setSysClipboardText(copyChatLogButton.getId());
    }

    public void onCopyChatLogButtonOffenderOnly() {
        setSysClipboardText(copyChatLogButtonOffenderOnly.getId());
    }

    public void onCopyModeratorEvents() {
        setSysClipboardText(copyModeratorEventsButton.getId());
    }

    public void onCopyReporterIdButton() {
        setSysClipboardText(copyReporterIdButton.getId());
    }

    public void onCopyReportID() {
        setSysClipboardText(copyReportIdButton.getId() + ",");
    }

    public void onCopyGameID() {
        setSysClipboardText(copyGameIdButton.getId());
    }

    public void onStartReplay() throws IOException, InterruptedException {
        String replayId = startReplayButton.getId();
        String replayUrl = "https://replay.faforever.com/" + replayId;
        Path tempFilePath = Files.createTempFile("faf_replay_", ".fafreplay");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(replayUrl))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFilePath));

        String cmd = "cmd /c " + tempFilePath;
        Runtime.getRuntime().exec(cmd);
    }

    private void removeTrailingComma(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    public void onUseTemplateWithoutReasonsButton() {
        try {
            ObservableList<ModerationReportFX> selectedItems = reportTableView.getSelectionModel().getSelectedItems();

            String selectedReportIds = selectedItems.stream()
                    .map(item -> String.valueOf(item.getId()))
                    .collect(Collectors.joining(","));

            String selectedGameIds = selectedItems.stream()
                    .map(ModerationReportFX::getGame)
                    .filter(Objects::nonNull)
                    .map(game -> String.valueOf(game.getId()))
                    .distinct()
                    .collect(Collectors.joining(","));

            removeTrailingComma(new StringBuilder(selectedReportIds));
            removeTrailingComma(new StringBuilder(selectedGameIds));

            String result;
            result = selectedReportIds + "\n\n" + "DAY_NUMBER day ban - ReplayID " + selectedGameIds + " - SOME_REASON";
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(result);
            Clipboard.getSystemClipboard().setContent(clipboardContent);

            useTemplateWithoutReasonsButton.setText("Copied");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> useTemplateWithoutReasonsButton.setText("Template No Reasons"));
                }
            }, 750);
        } catch (NullPointerException e) {
            log.debug(String.valueOf(e));
        }
    }

    public void onUseTemplateWithReasonsButton() {
        try {
            ObservableList<ModerationReportFX> selectedItems = reportTableView.getSelectionModel().getSelectedItems();

            String selectedIds = selectedItems.stream()
                    .map(item -> String.valueOf(item.getId()))
                    .collect(Collectors.joining(","));

            String selectedGameIds = selectedItems.stream()
                    .map(ModerationReportFX::getGame)
                    .filter(Objects::nonNull)
                    .map(game -> String.valueOf(game.getId()))
                    .distinct()
                    .collect(Collectors.joining(","));

            TemplateAndReasonConfig templateAndReasonConfig = loadTemplateAndReasonConfig();
            if (templateAndReasonConfig == null) {
                log.error("Error importing templatesAndReasons.json - Maybe JSON style errors.");
                return;
            }

            List<TemplateAndReasonConfig> templates = templateAndReasonConfig.getTemplates();
            List<String> reasons = templateAndReasonConfig.getReasons();

            Platform.runLater(() -> {
                Stage templateStage = new Stage();
                templateStage.setTitle("Select Template");

                GridPane gridPane = new GridPane();
                gridPane.setHgap(10);
                gridPane.setVgap(10);
                gridPane.setPadding(new Insets(10));

                ComboBox<TemplateAndReasonConfig> templateComboBox = new ComboBox<>();
                templateComboBox.getItems().addAll(templates);
                templateComboBox.setValue(templates.getFirst());
                templateComboBox.setCellFactory(param -> new ListCell<TemplateAndReasonConfig>() {
                    @Override
                    protected void updateItem(TemplateAndReasonConfig item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? "" : item.getName());
                    }
                });
                templateComboBox.setButtonCell(new ListCell<TemplateAndReasonConfig>() {
                    @Override
                    protected void updateItem(TemplateAndReasonConfig item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? "" : item.getName());
                    }
                });

                gridPane.add(new Label("Template:"), 0, 0);
                gridPane.add(templateComboBox, 1, 0);

                List<CheckBox> reasonCheckBoxes = reasons.stream()
                        .map(CheckBox::new)
                        .toList();

                for (int i = 0; i < reasonCheckBoxes.size(); i++) {
                    gridPane.add(reasonCheckBoxes.get(i), 0, i + 1);
                }

                Button okButton = new Button("OK");
                gridPane.add(okButton, 0, reasonCheckBoxes.size() + 1);

                Scene scene = new Scene(gridPane);
                templateStage.setScene(scene);
                templateStage.show();

                okButton.setOnAction(event -> {
                    TemplateAndReasonConfig selectedTemplate = templateComboBox.getValue();
                    if (selectedTemplate == null) {
                        return;
                    }

                    StringBuilder selectedReasons = new StringBuilder();
                    for (CheckBox checkBox : reasonCheckBoxes) {
                        if (checkBox.isSelected()) {
                            selectedReasons.append(checkBox.getText()).append(", ");
                        }
                    }

                    if (!selectedReasons.isEmpty()) {
                        selectedReasons.setLength(selectedReasons.length() - 2); // Remove trailing comma
                    }

                    // Format result based on the template
                    String result = selectedTemplate.getFormat()
                            .replace("{reportIds}", selectedIds)
                            .replace("{reason}", selectedReasons.toString());

                    if (selectedGameIds.isEmpty()) {
                        result = result.replace("ReplayID", "");
                        result = result.replace(" -  {gameIds}", "");
                    } else {
                        result = result.replace("{gameIds}", selectedGameIds);
                    }

                    // Copy result to clipboard
                    ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(result);
                    Clipboard.getSystemClipboard().setContent(clipboardContent);

                    useTemplateWithReasonsButton.setText("Copied");
                    templateStage.close();

                    // Reset button text after a delay
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> useTemplateWithReasonsButton.setText("Template With Reasons"));
                        }
                    }, 750);
                });
            });
        } catch (Exception e) {
            log.warn(String.valueOf(e));
        }
    }

    private TemplateAndReasonConfig loadTemplateAndReasonConfig() {
        File templatesAndReasonsFile = new File(CONFIGURATION_FOLDER + File.separator + "templatesAndReasons.json");
        try {
            return objectMapper.readValue(templatesAndReasonsFile, TemplateAndReasonConfig.class);
        } catch (IOException e) {
            log.warn(String.valueOf(e));
        }
        return null;
    }

    private void showInTableRepeatedOffenders(List<ModerationReportFX> reps) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                List<ModerationReportFX> reports = Lists.newArrayList(reps.listIterator());

                Map<String, Long> offendersAwaitingReports = reports.stream()
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.AWAITING))
                        .flatMap(report -> report.getReportedUsers().stream())
                        .collect(Collectors.groupingBy(PlayerFX::getRepresentation, Collectors.counting()));

                Map<String, Long> offendersCompletedReports = reports.stream()
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.COMPLETED))
                        .flatMap(report -> report.getReportedUsers().stream())
                        .collect(Collectors.groupingBy(PlayerFX::getRepresentation, Collectors.counting()));

                Map<String, Long> offendersDiscardedReports = reports.stream()
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.DISCARDED))
                        .flatMap(report -> report.getReportedUsers().stream())
                        .collect(Collectors.groupingBy(PlayerFX::getRepresentation, Collectors.counting()));

                Map<String, Long> offendersProcessingReports = reports.stream()
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.PROCESSING))
                        .flatMap(report -> report.getReportedUsers().stream())
                        .collect(Collectors.groupingBy(PlayerFX::getRepresentation, Collectors.counting()));

                Map<String, Long> sortedOffendersAwaitingReports = offendersAwaitingReports.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

                List<Offender> offenders = sortedOffendersAwaitingReports.entrySet().stream().map(entry -> {
                            String offenderUsername = entry.getKey();
                            Long offenderReportCount = entry.getValue();
                            Long offenderTotalReportCountCompleted = offendersCompletedReports.getOrDefault(offenderUsername, 0L);
                            Long offenderTotalReportCountDiscarded = offendersDiscardedReports.getOrDefault(offenderUsername, 0L);
                            Long offenderTotalReportCountProcessing = offendersProcessingReports.getOrDefault(offenderUsername, 0L);

                            Optional<OffsetDateTime> maxCreateTime = reports.stream()
                                    .filter(report -> report.getReportedUsers().stream()
                                            .anyMatch(user -> user.getRepresentation().equals(offenderUsername)))
                                    .map(ModerationReportFX::getCreateTime)
                                    .max(Comparator.naturalOrder());

                            if (maxCreateTime.isPresent()) {
                                LocalDateTime lastReported = maxCreateTime.get().toLocalDateTime();
                                return new Offender(
                                        offenderUsername,
                                        offenderReportCount != null ? offenderReportCount.intValue() : 0,
                                        offenderTotalReportCountCompleted != null ? offenderTotalReportCountCompleted.intValue() : 0,
                                        offenderTotalReportCountDiscarded != null ? offenderTotalReportCountDiscarded.intValue() : 0,
                                        offenderTotalReportCountProcessing != null ? offenderTotalReportCountProcessing.intValue() : 0,
                                        lastReported
                                );
                            } else {
                                log.debug("MaxCreateTime is not present.");
                                return null;
                            }
                        }).filter(Objects::nonNull)
                        .collect(Collectors.toList());
                Platform.runLater(() -> mostReportedAccountsTableView.setItems(FXCollections.observableArrayList(offenders)));

                mostReportedAccountsTableView.setOnKeyPressed(event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.C) {
                        Offender selectedOffender = mostReportedAccountsTableView.getSelectionModel().getSelectedItem();
                        if (selectedOffender != null) {
                            StringSelection stringSelection = new StringSelection(selectedOffender.getPlayer());
                            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(stringSelection, null);
                        }
                    }
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    public void onModeratorEventsReplayIdButton() {
        String replayID = getModeratorEventsForReplayIdTextField.getText();

        if (replayID == null || replayID.isEmpty()) {
            return;
        }

        try {
            Integer.parseInt(replayID);
        } catch (NumberFormatException e) {
            log.warn("Replay ID must be a valid integer.");
            return;
        }

        // Create and initialize the ModerationReportFX, PlayerFX, and GameFX objects
        // with the necessary default data for processing it in the showChatLog method.
        ModerationReportFX fakeReport = new ModerationReportFX();
        PlayerFX playerFX = new PlayerFX();
        GameFX gameFX = new GameFX();

        playerFX.setLogin("");
        fakeReport.setReporter(playerFX);
        gameFX.setId(replayID);
        fakeReport.setGame(gameFX);
        currentlySelectedItemNotNull = fakeReport;

        showChatLog(fakeReport);
    }

    Properties properties = new Properties();
    String PROPERTIES_FILE = CONFIGURATION_FOLDER + "/config.properties";

    private void loadSettingsModeratorEvents() {
        File propertiesFile = new File(PROPERTIES_FILE);
        if (propertiesFile.exists()) {
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                Properties properties = new Properties();
                properties.load(in);
                enforceRatingCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("enforceRatingCheckBox", "false")));
                gameResultCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("gameResultCheckBox", "false")));
                jsonStatsCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("jsonStatsCheckBox", "false")));
                gameEndedCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("gameEndedCheckBox", "false")));
                focusArmyFromFilterCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("focusArmyFromFilterCheckBox", "false")));
                pingOfTypeAlertFilterCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("pingOfTypeAlertFilterCheckBox", "false")));
                pingOfTypeMoveFilterCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("pingOfTypeMoveFilterCheckBox", "false")));
                pingOfTypeAttackFilterCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("pingOfTypeAttackFilterCheckBox", "false")));
                selfDestructionFilterCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("selfDestructionFilterCheckBox", "false")));
                selfDestructionFilterAmountTextField.setText(properties.getProperty("selfDestructionFilterAmountTextField", "0"));
                textMarkerTypeFilterCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("textMarkerTypeFilterCheckBox", "false")));
                showAdvancedStatisticsModeratorEventsCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("showAdvancedStatisticsModeratorEventsCheckBox", "false")));
            } catch (IOException e) {
                log.warn(String.valueOf(e));
            }
        } else {
            log.debug("Properties file does not exist.");
        }
    }

    public void onSaveSettingsModeratorEvents() {
        File propertiesFile = new File(PROPERTIES_FILE);
        if (propertiesFile.exists()) {
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                properties.load(in);
            } catch (IOException e) {
                log.warn(String.valueOf(e));
            }
        }

        properties.setProperty("enforceRatingCheckBox", Boolean.toString(enforceRatingCheckBox.isSelected()));
        properties.setProperty("gameResultCheckBox", Boolean.toString(gameResultCheckBox.isSelected()));
        properties.setProperty("jsonStatsCheckBox", Boolean.toString(jsonStatsCheckBox.isSelected()));
        properties.setProperty("gameEndedCheckBox", Boolean.toString(gameEndedCheckBox.isSelected()));
        properties.setProperty("focusArmyFromFilterCheckBox", Boolean.toString(focusArmyFromFilterCheckBox.isSelected()));
        properties.setProperty("pingOfTypeAlertFilterCheckBox", Boolean.toString(pingOfTypeAlertFilterCheckBox.isSelected()));
        properties.setProperty("pingOfTypeMoveFilterCheckBox", Boolean.toString(pingOfTypeMoveFilterCheckBox.isSelected()));
        properties.setProperty("pingOfTypeAttackFilterCheckBox", Boolean.toString(pingOfTypeAttackFilterCheckBox.isSelected()));
        properties.setProperty("selfDestructionFilterCheckBox", Boolean.toString(selfDestructionFilterCheckBox.isSelected()));
        properties.setProperty("selfDestructionFilterAmountTextField", String.valueOf(selfDestructionFilterAmountTextField.getText()));
        properties.setProperty("textMarkerTypeFilterCheckBox", Boolean.toString(textMarkerTypeFilterCheckBox.isSelected()));
        properties.setProperty("showAdvancedStatisticsModeratorEventsCheckBox", Boolean.toString(showAdvancedStatisticsModeratorEventsCheckBox.isSelected()));

        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            properties.store(out, null);
        } catch (IOException e) {
            log.warn(String.valueOf(e));
        }
    }

    public void handleCopyAllStatsButtonAction() {
        StringBuilder content = new StringBuilder(moderatorStatisticsTextArea.getText() + "\n\n");
        content.append("**Moderator Statistics**\n\n");
        content.append("| **Moderator** | **All Reports** | **Completed** | **Discarded** | **Processing** | **Last Activity** |\n");
        content.append("|---------------|-----------------|---------------|---------------|----------------|-------------------|\n");

        // Iterate through the moderatorStatisticsTableView rows and format them into a Markdown table
        for (int i = 0; i < moderatorStatisticsTableView.getItems().size(); i++) {
            for (TableColumn<?, ?> column : moderatorStatisticsTableView.getColumns()) {
                Object cellData = column.getCellData(i);
                content.append("| ").append(cellData == null ? "" : cellData.toString()).append(" ");
            }
            content.append("|\n");
        }

        content.append("\n\n");

        banService.getLatestBans().thenAccept(banInfos -> {
            if (banInfos.isEmpty()) {
                log.warn("No ban information retrieved.");
                return;
            }

            log.info("Retrieved ban information: {} entries.", banInfos.size());

            // Calculate total bans for each moderator
            Map<String, Long> modTotalBansCount = new HashMap<>();
            banInfos.forEach(info -> {
                String moderatorLogin = info.getAuthor().getLogin();
                modTotalBansCount.put(moderatorLogin, modTotalBansCount.getOrDefault(moderatorLogin, 0L) + 1);
            });

            // Filter for moderators with at least 10 total bans
            Map<String, Long> filteredMods = modTotalBansCount.entrySet().stream()
                    .filter(entry -> entry.getValue() >= 10)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Map<String, Long> modPermanentBansCount = banInfos.stream()
                    .filter(info -> "PERMANENT".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .collect(Collectors.groupingBy(info -> info.getAuthor().getLogin(), Collectors.counting()));

            Map<String, Long> modTemporaryBansCount = banInfos.stream()
                    .filter(info -> "TEMPORARY".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .collect(Collectors.groupingBy(info -> info.getAuthor().getLogin(), Collectors.counting()));

            content.append("**Moderator Ban Statistics**\n\n");
            content.append("| **Moderator** | **Total Bans** | **Permanent Bans** | **Temporary Bans** |\n");
            content.append("|---------------|----------------|-------------------|-------------------|\n");

            // Create a list of moderators sorted by total bans in descending order
            List<String> sortedModerators = filteredMods.keySet().stream()
                    .sorted((m1, m2) -> Long.compare(
                            filteredMods.get(m2),
                            filteredMods.get(m1)))
                    .toList();

            for (String moderator : sortedModerators) {
                long permanentBanCount = modPermanentBansCount.getOrDefault(moderator, 0L);
                long temporaryBanCount = modTemporaryBansCount.getOrDefault(moderator, 0L);
                long totalBanCount = permanentBanCount + temporaryBanCount;

                content.append("| ")
                        .append(moderator)
                        .append(" | ")
                        .append(totalBanCount)
                        .append(" | ")
                        .append(permanentBanCount)
                        .append(" | ")
                        .append(temporaryBanCount)
                        .append(" |\n");
            }

            // Add summary about ban statistics
            int totalBans = banInfos.size();
            long permanentBans = banInfos.stream()
                    .filter(info -> "PERMANENT".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .count();
            long temporaryBans = banInfos.stream()
                    .filter(info -> "TEMPORARY".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .count();

            content.append("\n**Summary**\n\n");
            content.append("- **Total Bans**: ").append(totalBans).append("\n");
            content.append("- **Permanent Bans**: ").append(permanentBans).append("\n");
            content.append("- **Temporary Bans**: ").append(temporaryBans).append("\n");

            Platform.runLater(() -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(content.toString());
                clipboard.setContent(clipboardContent);
                System.out.println("Content copied to clipboard.");
            });
        });
    }

    // TODO refactor with setter getter and use Integer
    public static class ModeratorStatistics {
        private final StringProperty moderator;
        private final LongProperty completedReports;
        private final LongProperty discardedReports;
        private final LongProperty processingReports;
        private final LongProperty allReports;
        @Setter
        private LocalDateTime lastActivity;

        public ModeratorStatistics(String moderator) {
            this.moderator = new SimpleStringProperty(moderator);
            this.completedReports = new SimpleLongProperty(0L);
            this.discardedReports = new SimpleLongProperty(0L);
            this.processingReports = new SimpleLongProperty(0L);
            this.allReports = new SimpleLongProperty(0L);
        }

        public String getLastActivity() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return lastActivity.format(formatter);
        }

        public String getModerator() {
            return moderator.get();
        }

        public void setModerator(String moderator) {
            this.moderator.set(moderator);
        }

        public Long getCompletedReports() {
            return completedReports.get();
        }

        public void setCompletedReports(Long completedReports) {
            this.completedReports.set(completedReports);
        }

        public Long getDiscardedReports() {
            return discardedReports.get();
        }

        public void setDiscardedReports(Long discardedReports) {
            this.discardedReports.set(discardedReports);
        }

        public Long getProcessingReports() {
            return processingReports.get();
        }

        public void setProcessingReports(Long processingReports) {
            this.processingReports.set(processingReports);
        }

        public Long getAllReports() {
            return allReports.get();
        }

        public void setAllReports(Long allReports) {
            this.allReports.set(allReports);
        }

    }

    private void processStatisticsModerator(List<ModerationReportFX> reps) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                //TODO ref readable
                ArrayList<ModerationReportFX> reports = Lists.newArrayList(reps.iterator());

                Map<PlayerFX, Map<ModerationReportStatus, Integer>> moderatorReportCounts = new HashMap<>();
                for (ModerationReportFX report : reports) {
                    if (report.getLastModerator() != null) {
                        moderatorReportCounts.computeIfAbsent(report.getLastModerator(), k -> new HashMap<>());
                        moderatorReportCounts.get(report.getLastModerator()).compute(report.getReportStatus(), (k, v) -> v == null ? 1 : v + 1);
                    }
                }

                Map<ModerationReportStatus, Integer> totalReportCounts = new HashMap<>();
                for (ModerationReportFX report : reports) {
                    totalReportCounts.compute(report.getReportStatus(), (k, v) -> v == null ? 1 : v + 1);
                }

                int totalReports = 0;
                for (Integer count : totalReportCounts.values()) {
                    totalReports += count;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("All Reports: ").append(totalReports).append(" | ");
                sb.append("Completed: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.COMPLETED, 0)).append(" | ");
                sb.append("Discarded: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.DISCARDED, 0)).append(" | ");
                sb.append("Awaiting: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.AWAITING, 0)).append(" | ");
                sb.append("Processing: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.PROCESSING, 0));

                Set<String> uniqueModerators = new HashSet<>();
                for (ModerationReportFX report : reports) {
                    if (report.getLastModerator() != null) {
                        uniqueModerators.add(report.getLastModerator().getRepresentation());
                    }
                }

                ObservableList<ModeratorStatistics> data = FXCollections.observableArrayList();
                for (String moderator : uniqueModerators) {
                    data.add(new ModeratorStatistics(moderator));
                }

                Map<String, Long> reportsByModeratorAndStatus = reports.stream()
                        .filter(report -> report.getLastModerator() != null)
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.DISCARDED)
                                || report.getReportStatus().equals(ModerationReportStatus.COMPLETED)
                                || report.getReportStatus().equals(ModerationReportStatus.PROCESSING))
                        .collect(Collectors.groupingBy(r -> r.getLastModerator().getRepresentation() + "-" + r.getReportStatus().name(), Collectors.counting()));

                for (ModeratorStatistics moderatorStat : data) {
                    moderatorStat.setCompletedReports(reportsByModeratorAndStatus.getOrDefault(moderatorStat.getModerator() + "-COMPLETED", 0L));
                    moderatorStat.setDiscardedReports(reportsByModeratorAndStatus.getOrDefault(moderatorStat.getModerator() + "-DISCARDED", 0L));
                    moderatorStat.setProcessingReports(reportsByModeratorAndStatus.getOrDefault(moderatorStat.getModerator() + "-PROCESSING", 0L));
                }

                Map<String, Long> allReportsByModerator = reports.stream()
                        .filter(report -> report.getLastModerator() != null)
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.DISCARDED)
                                || report.getReportStatus().equals(ModerationReportStatus.COMPLETED)
                                || report.getReportStatus().equals(ModerationReportStatus.PROCESSING))
                        .collect(Collectors.groupingBy(r -> r.getLastModerator().getRepresentation(), Collectors.counting()));

                for (ModeratorStatistics moderatorStat : data) {
                    moderatorStat.setAllReports(allReportsByModerator.getOrDefault(moderatorStat.getModerator(), 0L));
                }

                Map<String, OffsetDateTime> maxLastActivityByModerator = reports.stream()
                        .filter(report -> report.getLastModerator() != null)
                        .collect(Collectors.groupingBy(report -> report.getLastModerator().getRepresentation(),
                                Collectors.mapping(ModerationReportFX::getUpdateTime, Collectors.maxBy(Comparator.naturalOrder()))
                        ))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().isPresent())
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));

                for (ModeratorStatistics moderatorStat : data) {
                    moderatorStat.setLastActivity(maxLastActivityByModerator.get(moderatorStat.getModerator()).toLocalDateTime());
                }
                Platform.runLater(() -> {
                    moderatorStatisticsTableView.setItems(data);
                    moderatorStatisticsTableView.getSortOrder().add(lastActivity);
                    lastActivity.setSortType(TableColumn.SortType.DESCENDING);
                    moderatorStatisticsTableView.sort();
                    moderatorStatisticsTextArea.setText(sb.toString());
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    @Getter
    @Setter
    public static class Offender {
        private final StringProperty player;
        private final Integer currentOffenseCount;
        private final Integer totalOffenseCountCompleted;
        private final Integer totalOffenseCountDiscarded;
        private final Integer totalOffenseCountProcessing;
        private LocalDateTime lastReported;

        public Offender(String player,
                        int offenseCount,
                        int totalOffenseCountCompleted,
                        int totalOffenseCountDiscarded,
                        int totalOffenseCountProcessing,
                        LocalDateTime lastReported) {

            this.player = new SimpleStringProperty(player);
            this.currentOffenseCount = offenseCount;
            this.totalOffenseCountCompleted = totalOffenseCountCompleted;
            this.totalOffenseCountProcessing = totalOffenseCountProcessing;
            this.totalOffenseCountDiscarded = totalOffenseCountDiscarded;
            this.lastReported = lastReported;
        }

        public String getPlayer() {
            return player.get();
        }

    }

    private void resetButtonsToInvalidState() {
        copyChatLogButton.setText("Chat Log n/a");
        copyChatLogButton.setId("");
        copyChatLogButtonOffenderOnly.setText("Chat Offender n/a");
        copyChatLogButtonOffenderOnly.setId("");
        copyModeratorEventsButton.setText("Moderator Events n/a");
        copyModeratorEventsButton.setId("");
        copyGameIdButton.setText("Game ID n/a");
        copyGameIdButton.setId("");
        startReplayButton.setText("Replay n/a");
        startReplayButton.setId("");
        Text messageTextNoGame = new Text("No Game ID was reported.");
        chatLogTextFlow.getChildren().clear();
        chatLogTextFlow.getChildren().add(messageTextNoGame);
        moderatorEventTextFlow.getChildren().clear();
        moderatorEventTextFlow.getChildren().add(messageTextNoGame);
    }

    private void initializeUserTableView() {
        ViewHelper.buildUserTableView(platformService, reportedPlayerTableView, reportedPlayersOfCurrentlySelectedReport, this::addBan,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), false, communicationService);
    }

    private void setupReportSelectionListener() {
        reportTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                updateReportDetails(newValue);
            } catch (Exception e) {
                log.debug("Exception for selected report: ");
                resetButtonsToInvalidState();
            }
        });
    }

    private void updateReportDetails(ModerationReportFX newValue) {
        reportedPlayersOfCurrentlySelectedReport.setAll(newValue.getReportedUsers());
        currentlySelectedItemNotNull = newValue;

        copyReportIdButton.setText("Report ID: " + newValue.getId());
        copyReportIdButton.setId(newValue.getId());

        copyReporterIdButton.setText(newValue.getReporter().getRepresentation());
        copyReporterIdButton.setId(newValue.getReporter().getRepresentation());

        // Since ~2023, reports have only one offender; legacy reports may have several.
        for (PlayerFX offender : reportedPlayersOfCurrentlySelectedReport) {
            copyReportedUserIdButton.setId(offender.getRepresentation());
            copyReportedUserIdButton.setText(offender.getRepresentation());
            createReportForumButton.setId(offender.getId());
            createReportForumButton.setText("Search Forum:\n" + offender.getRepresentation());
        }

        if (newValue.getGame() != null) {
            copyGameIdButton.setId(newValue.getGame().getId());
            copyGameIdButton.setText("Game ID: " + newValue.getGame().getId());

            startReplayButton.setId(newValue.getGame().getId());
            startReplayButton.setText("Start Replay: " + newValue.getGame().getId());

            if (automaticallyLoadChatLogCheckBox.isSelected()) {
                showChatLog(currentlySelectedItemNotNull);
            }
        } else {
            resetButtonsToInvalidState();
        }
    }

    @FXML
    public void initialize() {
        loadSettingsModeratorEvents();
        setupReportSelectionListener();
        statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        statusChoiceBox.getSelectionModel().select(ChooseableStatus.AWAITING_PROCESSING); // Set Default Selection
        editReportButton.disableProperty().bind(reportTableView.getSelectionModel().selectedItemProperty().isNull());
        initializeItemMapAndListeners();
        initializeReportTableView();
        initializeUserTableView();
        Text messageTextNoSelection = new Text("Please select a report to view details.");
        chatLogTextFlow.getChildren().clear();
        chatLogTextFlow.getChildren().add(messageTextNoSelection);
    }

    private void initializeItemMapAndListeners() {
        itemMap = FXCollections.observableHashMap();
        itemList = FXCollections.observableArrayList();

        MapChangeListener<Integer, ModerationReportFX> listener = entry -> {
            if (entry.wasRemoved()) {
                itemList.remove(entry.getValueRemoved());
            } else if (entry.wasAdded()) {
                itemList.add(entry.getValueAdded());
            }
        };
        itemMap.addListener(listener);
    }

    private void initializeReportTableView() {
        filteredItemList = new FilteredList<>(itemList);
        renewFilter();
        SortedList<ModerationReportFX> sortedItemList = new SortedList<>(filteredItemList);
        sortedItemList.comparatorProperty().bind(reportTableView.comparatorProperty());
        ViewHelper.buildModerationReportTableView(reportTableView, sortedItemList, this::showChatLog);
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener(observable -> renewFilter());
        playerNameFilterTextField.textProperty().addListener(observable -> renewFilter());
        reportTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                updateReportDetails(newValue);
            } catch (Exception e) {
                log.debug(String.valueOf(e));
                resetButtonsToInvalidState();
            }
        });
    }

    public static void setSysClipboardText(String writeMe) {
        System.setProperty("java.awt.headless", "false");
        java.awt.datatransfer.Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable tText = new StringSelection(writeMe);
        clip.setContents(tText, null);
    }

    private void addBan(PlayerFX accountFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX ban = new BanInfoFX();
        ban.setPlayer(accountFX);
        banInfoController.setBanInfo(ban);
        banInfoController.addPostedListener(banInfoFX -> onRefreshInitialReports());
        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle("Apply new ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoController.preSetReportId(currentlySelectedItemNotNull.getId());
        banInfoDialog.showAndWait();
    }

    private final Object reportLock = new Object();
    private boolean isFetchingReport = false;

    private void renewFilter() {
        Platform.runLater(() -> {
            log.debug("Updating filtered item list in UI thread...");
            filteredItemList.setPredicate(moderationReportFx -> {
                String filterText = playerNameFilterTextField.getText().toLowerCase();
                Optional<String> reportIdFilter = extractReportId(filterText);

                if (reportIdFilter.isPresent()) {
                    if (!moderationReportFx.getId().toLowerCase().contains(reportIdFilter.get())) {
                        return false;
                    }
                } else if (!Strings.isNullOrEmpty(filterText)) {
                    if (moderationReportFx.getReportedUsers().stream()
                            .map(accountFX -> accountFX.getLogin().toLowerCase())
                            .noneMatch(login -> login.contains(filterText)) &&
                            !moderationReportFx.getReporter().getLogin().toLowerCase().contains(filterText)) {
                        return false;
                    }
                }

                ChooseableStatus selectedItem = statusChoiceBox.getSelectionModel().getSelectedItem();
                if (selectedItem == ChooseableStatus.AWAITING_PROCESSING) {
                    ModerationReportStatus status = moderationReportFx.getReportStatus();
                    return status == ModerationReportStatus.AWAITING || status == ModerationReportStatus.PROCESSING;
                }

                return selectedItem.toString().equals("ALL") ||
                        moderationReportFx.getReportStatus() == selectedItem.getModerationReportStatus();
            });
        });
    }

    private Optional<String> extractReportId(String filterText) {
        // Extract report ID from text if it starts with "rid" for searching
        Pattern pattern = Pattern.compile("rid(\\w+)");
        Matcher matcher = pattern.matcher(filterText);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    @Getter
    @Setter
    private AtomicInteger totalReportsLoaded = new AtomicInteger(0);
    @Getter
    private final AtomicInteger activeApiRequests = new AtomicInteger(0);

    public void onRefreshInitialReports() {
        synchronized (reportLock) {
            if (isFetchingReport) {
                log.debug("Initial reports are already being fetched.");
                return;
            }
            isFetchingReport = true;
        }

        int initialPageSize = 100; // Only load the first 100 reports
        activeApiRequests.incrementAndGet();
        loadInitialReports(1, initialPageSize);
    }

    private void loadInitialReports(int currentPage, int pageSize) {
        moderationReportService.getPageOfReports(currentPage, pageSize).thenAccept(reportFxes -> {
            Platform.runLater(() -> {
                itemList.setAll(reportFxes); // Display only the initial reports
                showInTableRepeatedOffenders(reportFxes);

                log.debug("Initial reports loaded. Total count: {}", reportFxes.size());
                totalReportsLoaded.set(reportFxes.size());
                log.debug("totalReportsLoaded: Total count: {}", totalReportsLoaded.get());

                // After the initial load burst, fetch all reports in one swoop
                onRefreshAllReports();
            });
        }).exceptionally(throwable -> {
            log.error("Error loading initial reports", throwable);
            return null;
        }).whenComplete((result, throwable) -> {
            if (activeApiRequests.decrementAndGet() == 0) {
                synchronized (reportLock) {
                    isFetchingReport = false;
                }
            }
        });
    }

    @FXML
    private void onRefreshAllReports() {
        totalReportsLoaded.set(0);
        activeApiRequests.incrementAndGet();
        moderationReportService.getAllReports().thenAccept(allReports -> Platform.runLater(() -> {
            itemList.setAll(allReports);
            processStatisticsModerator(allReports);
            showInTableRepeatedOffenders(allReports);
            log.debug("All reports loaded. Total count: {}", allReports.size());
            totalReportsLoaded.set(allReports.size());
        })).exceptionally(throwable -> {
            log.error("Error loading all reports", throwable);
            return null;
        }).whenComplete((result, throwable) -> {
            if (activeApiRequests.decrementAndGet() == 0) {
                synchronized (reportLock) {
                    isFetchingReport = false;
                }
            }
        });
    }

    public void onEdit() {
        ObservableList<ModerationReportFX> selectedItems = reportTableView.getSelectionModel().getSelectedItems();

        if (selectedItems.isEmpty()) {
            return;
        }

        if (selectedItems.size() > 5) {
            if (!showConfirmationDialog(selectedItems.size())) {
                return;
            }
        }

        openEditDialog(selectedItems);
    }

    private boolean showConfirmationDialog(int numberOfReports) {
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Confirm Bulk Action");
        confirmationDialog.setHeaderText("Apply Changes to Multiple Reports");
        confirmationDialog.setContentText(String.format("You are about to apply changes to %d reports. Are you really sure you want to proceed?",
                numberOfReports));

        ButtonType confirmButton = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmationDialog.getButtonTypes().setAll(confirmButton, cancelButton);

        Optional<ButtonType> result = confirmationDialog.showAndWait();
        return result.isPresent() && result.get() == confirmButton;
    }

    private void openEditDialog(ObservableList<ModerationReportFX> selectedItems) {
        try {
            EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
            editModerationReportController.setSelectedReports(new ArrayList<>(selectedItems));

            editModerationReportController.setOnSaveRunnable(this::onRefreshInitialReports);

            Stage editDialog = new Stage();
            int numberOfReports = selectedItems.size();
            String title = "Edit Selected Reports (" + numberOfReports + " Report" + (numberOfReports > 1 ? "s" : "") + ")";
            editDialog.setTitle(title);
            editDialog.setScene(new Scene(editModerationReportController.getRoot()));
            editDialog.showAndWait();

        } catch (Exception e) {
            log.error("Error while editing reports", e);
        }
    }

    @Getter
    private enum ChooseableStatus {
        ALL(null),
        AWAITING(ModerationReportStatus.AWAITING),
        PROCESSING(ModerationReportStatus.PROCESSING),
        COMPLETED(ModerationReportStatus.COMPLETED),
        DISCARDED(ModerationReportStatus.DISCARDED),
        AWAITING_PROCESSING(null);// Combine AWAITING and PROCESSING

        private final ModerationReportStatus moderationReportStatus;

        ChooseableStatus(ModerationReportStatus moderationReportStatus) {
            this.moderationReportStatus = moderationReportStatus;
        }

    }

    private boolean isTaskRunning = false;

    @SneakyThrows
    private void showChatLog(ModerationReportFX report) {
        if (isTaskRunning) {
            log.debug("Task is already running for report: {}", report.getId());
            return;
        }

        isTaskRunning = true;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Path tempFilePath = null;
                try {
                    GameFX game = report.getGame();
                    String header = formatChatHeader(report, game);
                    tempFilePath = createTempFile(game);

                    if (tempFilePath == null) {
                        updateUIUnavailable(header, "An error occurred while creating a temporary file.");
                        return null;
                    }

                    HttpResponse<Path> response = downloadReplay(game, tempFilePath);

                    if (response == null || response.statusCode() == 404) {
                        updateUIUnavailable(header, """
                                Server Status Code 404 - Replay not available.
                                Please note that new replays may take some time to become accessible when they got immediately reported after a game.
                                Additionally, legacy replays are hosted on a separate server, which may occasionally experience issues requiring a restart.""");
                    } else {
                        String offenderNames = report.getReportedUsers().stream()
                                .map(PlayerFX::getRepresentation)
                                .collect(Collectors.joining(", "));
                        String reporter = String.valueOf(report.getReporter().getRepresentation());
                        String filePathGamingModeratorTask = CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt";
                        StringBuilder contentGamingModeratorTask = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new FileReader(filePathGamingModeratorTask))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.replace("%offenderNames%", offenderNames);
                                line = line.replace("%reporter%", reporter);

                                contentGamingModeratorTask.append(line).append("\n");
                            }
                        } catch (IOException e) {
                            log.warn(String.valueOf(e));
                        }

                        try {
                            processAndDisplayReplay(header, tempFilePath, String.valueOf(contentGamingModeratorTask));
                        } catch (Exception e) {
                            log.error("An error occurred while processing and displaying the replay", e);
                        }
                    }
                } finally {
                    deleteTempFile(tempFilePath);
                    isTaskRunning = false;
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void showModeratorEvent(List<ModeratorEvent> moderatorEvents, Map<Integer, PlayerInfo> playerInfoMap) {
        boolean enforceRating = enforceRatingCheckBox.isSelected();
        boolean gameEnded = gameEndedCheckBox.isSelected();
        boolean gameResult = gameResultCheckBox.isSelected();
        boolean jsonStats = jsonStatsCheckBox.isSelected();
        boolean pingOfTypeMoveFilter = pingOfTypeMoveFilterCheckBox.isSelected();
        boolean pingOfTypeAttackFilter = pingOfTypeAttackFilterCheckBox.isSelected();
        boolean pingOfTypeAlertFilter = pingOfTypeAlertFilterCheckBox.isSelected();
        boolean selfDestructionFilter = selfDestructionFilterCheckBox.isSelected();
        boolean focusArmyFromFilter = focusArmyFromFilterCheckBox.isSelected();
        boolean textMarkerTypeFilter = textMarkerTypeFilterCheckBox.isSelected();

        String textValue = selfDestructionFilterAmountTextField.getText();
        int selfDestructionFilterAmount = textValue.isEmpty() ? 0 : Integer.parseInt(textValue);

        String moderatorEventsLog = moderatorEvents.stream()
                .filter(event -> {
                    String message = event.message();
                    boolean filterOut = false;
                    if (enforceRating) filterOut |= message.contains("command 'EnforceRating' and data");
                    if (gameEnded) filterOut |= message.contains("command 'GameEnded' and data");
                    if (gameResult) filterOut |= message.contains("command 'GameResult' and data");
                    if (jsonStats) filterOut |= message.contains("command 'JsonStats' and data");
                    if (pingOfTypeMoveFilter) filterOut |= message.contains("Created a ping of type 'Move'");
                    if (pingOfTypeAttackFilter) filterOut |= message.contains("Created a ping of type 'Attack'");
                    if (pingOfTypeAlertFilter) filterOut |= message.contains("Created a ping of type 'Alert'");
                    if (focusArmyFromFilter) filterOut |= message.contains("focus army from");
                    if (textMarkerTypeFilter) filterOut |= message.contains("Created a marker with the text");

                    if (selfDestructionFilter && message.contains("Self-destructed")) {
                        Pattern pattern = Pattern.compile("Self-destructed (\\d+) units");
                        Matcher matcher = pattern.matcher(message);
                        if (matcher.find()) {
                            int unitsDestroyed = Integer.parseInt(matcher.group(1));
                            if (unitsDestroyed < selfDestructionFilterAmount) {
                                filterOut = true;
                            }
                        }
                    }
                    return !filterOut;
                })
                .map(event -> {
                    long timeMillis = event.time().toMillis();
                    String formattedChatMessageTime = formatChatMessageTime(timeMillis);

                    // If the message involves "focus army from", inject player names
                    // TODO ConExecute missing
                    String formattedMessage = event.message();
                    if (formattedMessage.contains("focus army from")) {
                        Pattern pattern = Pattern.compile("focus army from (\\d+) to (\\d+)");
                        Matcher matcher = pattern.matcher(formattedMessage);
                        if (matcher.find()) {
                            int fromArmy = Integer.parseInt(matcher.group(1));
                            int toArmy = Integer.parseInt(matcher.group(2));
                            String fromPlayer = playerInfoMap.getOrDefault(fromArmy, new PlayerInfo(-1, "Unknown Player")).getPlayerName();
                            String toPlayer = playerInfoMap.getOrDefault(toArmy, new PlayerInfo(-1, "Unknown Player")).getPlayerName();

                            formattedMessage = String.format(
                                    "focus army from %d (%s) to %d (%s)",
                                    fromArmy, fromPlayer, toArmy, toPlayer
                            );
                        }
                    }

                    return String.format("%s from %s : %s",
                            formattedChatMessageTime,
                            event.playerNameFromCommandSource(),
                            formattedMessage
                    );
                })
                .collect(Collectors.joining("\n"));

        Platform.runLater(() -> {
            copyModeratorEventsButton.setId(moderatorEventsLog);
            copyModeratorEventsButton.setText("Copy Moderator Events");
            moderatorEventTextFlow.getChildren().clear();
            updateModeratorEventToColorTextFlow(moderatorEventTextFlow, moderatorEventsLog,
                    extractName(copyReporterIdButton.getText()),
                    extractName(copyReportedUserIdButton.getText()));
        });
    }


    private String formatChatMessageTime(long timeMillis) {
        if (timeMillis >= 0) {
            return DurationFormatUtils.formatDuration(timeMillis, "HH:mm:ss");
        } else {
            return "N/A"; // replay data contains negative timestamps for whatever reason
        }
    }

    private String formatChatHeader(ModerationReportFX report, GameFX game) {
        return format("CHAT LOG -- Report ID {0} -- Replay ID {1} -- Title \"{2}\"\n\n",
                report.getId(), game.getId(), game.getName());
    }

    private Path createTempFile(GameFX game) {
        try {
            return Files.createTempFile(format("faf_replay_" + game.getId()), "");
        } catch (IOException e) {
            log.error("An error occurred while creating a temporary file.", e);
            return null;
        }
    }

    private HttpResponse<Path> downloadReplay(GameFX game, Path tempFilePath) {
        try {
            String replayUrl = game.getReplayUrl(replayDownLoadFormat);
            log.info("Downloading replay from {} to {}", replayUrl, tempFilePath);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(replayUrl))
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFilePath));
        } catch (Exception e) {
            log.error("An error occurred while downloading the replay.", e);
            return null;
        }
    }

    private void updateUIUnavailable(String header, String message) {
        Platform.runLater(() -> {
            startReplayButton.setText("Replay n/a");
            copyChatLogButton.setText("Chat Log n/a");
            copyChatLogButton.setId("");
            copyChatLogButtonOffenderOnly.setText("Chat Offender n/a");
            copyChatLogButtonOffenderOnly.setId("");
            copyModeratorEventsButton.setText("Moderator Events n/a");
            copyModeratorEventsButton.setId("");
            chatLogTextFlow.getChildren().clear();
            Text headerText = new Text(header);
            Text messageText = new Text(message);
            chatLogTextFlow.getChildren().addAll(headerText, messageText);
        });
    }

    private void processAndDisplayReplay(String header, Path tempFilePath, String promptAI) {
        try {
            ReplayDataParser replayDataParser = new ReplayDataParser(tempFilePath, objectMapper);
            String chatLog = generateChatLog(replayDataParser);

            StringBuilder chatLogFiltered = new StringBuilder();
            StringBuilder chatLogFilteredOffenderOnly = new StringBuilder();
            String reportedUser = extractName(copyReportedUserIdButton.getText());

            chatLogFiltered.append(header);

            String metadataInfo = generateMetadataInfo(replayDataParser);
            chatLogFiltered.append(metadataInfo);

            String filteredChatLog = filterAndAppendChatLog(chatLog);
            chatLogFiltered.append(filteredChatLog);

            //TODO It seems armies map/PlayerInfo are 0-based, but raw player index data from moderatorEvent is 1-based, need to debug this
            Map<Integer, PlayerInfo> playerInfoMap = replayDataParser.getArmies().entrySet().stream()
                    .filter(entry -> entry.getValue().containsKey("PlayerName"))
                    .collect(Collectors.toMap(
                            entry -> entry.getKey() + 1,
                            entry -> new PlayerInfo(entry.getKey() + 1, (String) entry.getValue().get("PlayerName"))
                    ));

            List<ModeratorEvent> moderatorEvents = replayDataParser.getModeratorEvents();
            showModeratorEvent(moderatorEvents, playerInfoMap);

            chatLogFiltered.append("\n").append(promptAI);

            for (String line : filteredChatLog.split("\n")) {
                if (line.contains(reportedUser)) {
                    chatLogFilteredOffenderOnly.append(line).append("\n");
                }
            }

            chatLogFilteredOffenderOnly.append("\n\n").append(promptAI);

            Platform.runLater(() -> {
                copyChatLogButton.setId(chatLogFiltered.toString());
                copyChatLogButton.setText("Copy Chat Log");

                copyChatLogButtonOffenderOnly.setText("Copy Chat Offender");
                copyChatLogButtonOffenderOnly.setId(chatLogFilteredOffenderOnly.toString());

                updateChatLogToColorTextFlow(chatLogTextFlow, String.valueOf(chatLogFiltered),
                        extractName(copyReporterIdButton.getText()),
                        extractName(copyReportedUserIdButton.getText()));
            });
        } catch (Exception e) {
            log.error("An error occurred while parsing replay data.", e);
        }
    }

    private String extractName(String fullNameWithId) {
        if (fullNameWithId.contains("[")) {
            return fullNameWithId.split("\\[")[0].trim();
        }
        return fullNameWithId.trim();
    }

    private void addColoredTextForPointOfInterest(String line, TextFlow textFlow) {
        Text text = new Text(line);
        text.setFill(Color.ORANGE);
        textFlow.getChildren().add(text);
    }

    List<String> keywordsPointOfInterestReplay = List.of(
            "Desynced Replay at Game Time:",
            "does not exist in any of the team names.",
            "Non-Default Common Army:",
            "Cheats Enabled:"
    );

    public void updateChatLogToColorTextFlow(TextFlow textFlow, String filteredLog, String reporterName, String offenderName) {

        String colorOffender = "LIGHTCORAL";
        String colorReporter = "LIGHTBLUE";

        if (textFlow == null) {
            log.debug("TextFlow is not initialized in updateChatLogToColorTextFlow");
            return;
        }

        textFlow.getChildren().clear();

        String[] lines = filteredLog.split("\n");

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                textFlow.getChildren().add(new Text("\n"));
                continue;
            }

            if (line.contains("boundsType=LOGICAL")) {
                continue;
            }

            boolean isProcessed = false;

            for (String keyword : keywordsPointOfInterestReplay) {
                if (line.contains(keyword)) {
                    addColoredTextForPointOfInterest(line, textFlow);
                    isProcessed = true;
                    break;
                }
            }

            if (isProcessed) {
                textFlow.getChildren().add(new Text("\n"));
                continue;
            }

            int offenderIndex = line.indexOf(offenderName);
            int reporterIndex = line.indexOf(reporterName);

            if (offenderIndex != -1 && reporterIndex != -1) {
                if (offenderIndex < reporterIndex) {
                    processLineWithBothNames(textFlow, line, offenderName, reporterName, colorOffender, colorReporter);
                } else {
                    processLineWithBothNames(textFlow, line, reporterName, offenderName, colorReporter, colorOffender);
                }
            } else if (offenderIndex != -1) {
                processLineWithSingleName(textFlow, line, offenderName, colorOffender);
            } else if (reporterIndex != -1) {
                processLineWithSingleName(textFlow, line, reporterName, colorReporter);
            } else {
                Text text = new Text(line);
                text.setFill(Color.WHITE);
                textFlow.getChildren().add(text);
            }

            textFlow.getChildren().add(new Text("\n"));
        }
    }

    private void processLineWithSingleName(TextFlow textFlow, String line, String name, String color) {
        String[] parts = line.split(name);
        Text textBefore = new Text(parts[0]);
        textBefore.setFill(Color.WHITE);
        textFlow.getChildren().add(textBefore);

        Text nameText = new Text(name);
        nameText.setFill(Color.web(color));
        textFlow.getChildren().add(nameText);

        if (parts.length > 1) {
            Text textAfter = new Text(parts[1]);
            textAfter.setFill(Color.WHITE);
            textFlow.getChildren().add(textAfter);
        }
    }

    private void processLineWithBothNames(TextFlow textFlow, String line, String firstName, String secondName, String firstColor, String secondColor) {
        String[] firstParts = line.split(firstName, 2);

        Text textBeforeFirst = new Text(firstParts[0]);
        textBeforeFirst.setFill(Color.WHITE);
        textFlow.getChildren().add(textBeforeFirst);

        Text firstNameText = new Text(firstName);
        firstNameText.setFill(Color.web(firstColor));
        textFlow.getChildren().add(firstNameText);

        String[] secondParts = firstParts[1].split(secondName, 2);

        Text textBetween = new Text(secondParts[0]);
        textBetween.setFill(Color.WHITE);
        textFlow.getChildren().add(textBetween);

        Text secondNameText = new Text(secondName);
        secondNameText.setFill(Color.web(secondColor));
        textFlow.getChildren().add(secondNameText);

        if (secondParts.length > 1) {
            Text textAfterSecond = new Text(secondParts[1]);
            textAfterSecond.setFill(Color.WHITE);
            textFlow.getChildren().add(textAfterSecond);
        }
    }

    private void deleteTempFile(Path tempFilePath) {
        if (tempFilePath != null) {
            try {
                Files.delete(tempFilePath);
            } catch (IOException e) {
                log.error("An error occurred while deleting the temporary file.", e);
            }
        }
    }

    public void updateModeratorEventToColorTextFlow(TextFlow textFlow, String moderatorEvents, String reporterName, String offenderName) {
        String colorOffender = "LIGHTCORAL";
        String colorReporter = "LIGHTBLUE";

        if (textFlow == null) {
            return;
        }

        textFlow.getChildren().clear();
        String[] events = moderatorEvents.split("\n");

        for (String event : events) {
            TextFlow eventFlow = new TextFlow();
            String[] parts = event.split("from ", 2);

            if (parts.length > 1) {
                String timestamp = parts[0].trim() + " from ";
                String rest = parts[1].trim();
                int colonIndex = rest.indexOf(':');

                if (colonIndex > 0) {
                    String namePart = rest.substring(0, colonIndex).trim();
                    String eventMessage = rest.substring(colonIndex + 1).trim();

                    Text timestampText = new Text(timestamp);
                    timestampText.setStyle("-fx-fill: white;");

                    Text nameText = new Text(namePart + ": ");
                    if (namePart.equalsIgnoreCase(reporterName)) {
                        nameText.setStyle("-fx-fill: " + colorReporter + ";");
                    } else if (namePart.equalsIgnoreCase(offenderName)) {
                        nameText.setStyle("-fx-fill: " + colorOffender + ";");
                    } else {
                        nameText.setStyle("-fx-fill: white;");
                    }

                    Text defaultMessageText = new Text(eventMessage);
                    defaultMessageText.setStyle("-fx-fill: white;");

                    eventFlow.getChildren().addAll(timestampText, nameText, defaultMessageText);
                } else {
                    Text fallbackText = new Text(event);
                    fallbackText.setStyle("-fx-fill: white;");
                    eventFlow.getChildren().add(fallbackText);
                }
            } else {
                Text fallbackText = new Text(event);
                fallbackText.setStyle("-fx-fill: white;");
                eventFlow.getChildren().add(fallbackText);
            }

            textFlow.getChildren().add(eventFlow);
        }
    }

    private String generateChatLog(ReplayDataParser replayDataParser) {
        return replayDataParser.getChatMessages().stream()
                .map(this::formatChatMessage)
                .collect(Collectors.joining("\n"));
    }

    private String formatChatMessage(ChatMessage message) {
        long timeMillis = message.getTime().toMillis();
        String formattedTime = timeMillis >= 0
                ? DurationFormatUtils.formatDuration(timeMillis, "HH:mm:ss")
                : "N/A";

        return format("[{0}] from {1} to {2}: {3}",
                formattedTime, message.getSender(), message.getReceiver(), message.getMessage());
    }

    private static String getValueForKey(List<GameOption> gameOptions, String key) {
        for (GameOption option : gameOptions) {
            if (option.getKey().equals(key)) {
                return (String) option.getValue();
            }
        }
        return "Not Found";
    }

    public String formatTicksToTime(int ticks) {
        // Convert ticks to total seconds (1 tick = 0.1 second)
        int totalSeconds = ticks / 10;

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Getter
    @Setter
    public class PlayerInfo {
        private Integer index;
        private String playerName;

        public PlayerInfo(Integer index, String playerName) {
            this.index = index;
            this.playerName = playerName;
        }

        @Override
        public String toString() {
            return "PlayerInfo{index=" + index + ", playerName='" + playerName + "'}";
        }
    }

    public List<String> processReplayForQuitEventsPlayers(List<Event> events, Map<Integer, Map<String, Object>> armies) {
        int ticks = 0;
        int playerIndexFromArmiesMap = -1; // TODO: -1 could represent spectators; need to double-check

        Map<Integer, PlayerInfo> playerInfoMap = armies.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey("PlayerName"))
                .collect(Collectors.toMap(
                        entry -> entry.getKey(), // Adjust index
                        entry -> new PlayerInfo(entry.getKey(), (String) entry.getValue().get("PlayerName"))
                ));

        List<String> playerQuitEventMessages = new ArrayList<>();

        for (Event event : events) {
            if (event instanceof Event.CommandSourceTerminated) {
                PlayerInfo playerInfo = playerInfoMap.getOrDefault(playerIndexFromArmiesMap, new PlayerInfo(-1, "Unknown Player"));
                String playerName = playerInfo.getPlayerName();

                String message = String.format(
                        "%s quit event: %s",
                        formatTicksToTime(ticks), playerName
                );
                playerQuitEventMessages.add(message);
            } else if (event instanceof Event.SetCommandSource(int playerIndex)) {
                playerIndexFromArmiesMap = playerIndex;
            } else if (event instanceof Event.Advance(int ticksToAdvance)) {
                ticks += ticksToAdvance;
            }
        }

        return playerQuitEventMessages;
    }

    public record DesyncResult(boolean desync, int tick) {

    }

    public DesyncResult checkReplayEventsForDesync(List<Event> events) {
        String previousChecksum = null;
        int previousTick = -1;

        for (Event event : events) {
            if (event instanceof Event.VerifyChecksum(String hash, int tick)) {

                if (tick == previousTick && !Objects.equals(previousChecksum, hash)) {
                    log.warn("Replay desynced at game time {}: expected checksum {}, got {}",
                            formatTicksToTime(tick), previousChecksum, hash);
                    return new DesyncResult(true, previousTick);
                }

                previousChecksum = hash;
                previousTick = tick;
            }
        }

        return new DesyncResult(false, -1);
    }

    private String generateMetadataInfo(ReplayDataParser replayDataParser) {
        ReplayMetadata metadata = replayDataParser.getMetadata();
        List<GameOption> gameOptions = replayDataParser.getGameOptions();
        Map<String, Map<String, ?>> mods = replayDataParser.getMods();
        List<Event> events = replayDataParser.getEvents();
        Map<Integer, Map<String, Object>> armies = replayDataParser.getArmies();

        List<String> playerQuitGameMessages = processReplayForQuitEventsPlayers(events, armies);
        DesyncResult desyncResult = checkReplayEventsForDesync(events);

        if (desyncResult.desync()) {
            log.debug("Replay is desynchronized.");
        } else {
            log.debug("Replay is synchronized.");
        }

        //log.debug("Debug Mods: {}", mods);
        //log.debug("Debug Metadata: {}", metadata);
        //log.debug("Debug Game Options: {}", gameOptions);
        //log.debug("Debug Events: {}", events);

        final String CHEATS_ENABLED_KEY = "CheatsEnabled";
        final String VICTORY_KEY = "Victory";
        final String SHARE_KEY = "Share";
        final String COMMON_ARMY_KEY = "CommonArmy";
        final String DEMORALIZATION = "demoralization";
        final String ASSASSINATION = "Assassination (default)";
        final String OFF_STRING = "Off";

        String commonArmy = getValueForKey(gameOptions, COMMON_ARMY_KEY);
        log.debug("commonArmy value: {}", commonArmy);
        String cheatsEnabled = getValueForKey(gameOptions, CHEATS_ENABLED_KEY);
        log.debug("cheatsEnabled value: {}", cheatsEnabled);
        String victoryCondition = getValueForKey(gameOptions, VICTORY_KEY);
        log.debug("victoryCondition value: {}", victoryCondition);
        String shareCondition = getValueForKey(gameOptions, SHARE_KEY);
        log.debug("shareCondition value: {}", shareCondition);

        double launchedAt = metadata.getLaunchedAt();
        double gameEnd = metadata.getGameEnd();
        double totalTime = gameEnd - launchedAt;
        String formattedTotalTime = formatGameTotalTime(totalTime);

        StringBuilder report = new StringBuilder();

        for (String message : playerQuitGameMessages) {
            report.append(message).append("\n");
        }

        if (Boolean.parseBoolean(cheatsEnabled)) {
            report.append("[!] Cheats Enabled: ").append(cheatsEnabled).append("\n");
        }

        if (desyncResult.desync()) {
            report.append("[!] Desynced Replay at Game Time: ")
                    .append(formatTicksToTime(desyncResult.tick())).append("\n");
        }

        if (!commonArmy.equalsIgnoreCase(OFF_STRING) && !commonArmy.equalsIgnoreCase("Not Found")) {
            report.append("[!] Non-Default Common Army: ").append(commonArmy).append("\n");
        }

        String reporterLoginName = String.valueOf(currentlySelectedItemNotNull.getReporter().getLogin());

        boolean reporterParticipated = false;
        for (Map.Entry<String, List<String>> entry : metadata.getTeams().entrySet()) {
            List<String> playerNames = entry.getValue();
            if (playerNames.contains(reporterLoginName)) {
                reporterParticipated = true;
                break;
            }
        }

        if (!reporterParticipated) {
            report.append(String.format("[!] Reporter '%s' does not exist in any of the team names.\n", reporterLoginName));
        }

        report.append("\nHost: ").append(metadata.getHost()).append("\n")
                .append("Victory Condition: ").append(DEMORALIZATION.equalsIgnoreCase(victoryCondition) ? ASSASSINATION : victoryCondition).append("\n")
                .append("Share Condition: ").append(shareCondition).append("\n")
                .append("Number of Players: ").append(metadata.getNumPlayers()).append("\n")
                .append("Teams:\n").append(formatTeams(metadata.getTeams())).append("\n")
                .append("Map Name: ").append(metadata.getMapname()).append("\n")
                .append("Game Total Time: ").append(formattedTotalTime).append("\n\n");

        return report.toString();
    }

    private String formatTeams(Map<String, List<String>> teams) {
        StringBuilder formattedTeams = new StringBuilder();
        teams.forEach((teamNumber, players) -> {
            formattedTeams.append("Team ").append(teamNumber).append(": ").append(String.join(", ", players)).append("\n");
        });
        return formattedTeams.toString();
    }

    private String formatGameTotalTime(double totalTime) {
        int totalSeconds = (int) totalTime;
        int hours = totalSeconds / 3600;
        int remainder = totalSeconds % 3600;
        int minutes = remainder / 60;
        int seconds = remainder % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String filterAndAppendChatLog(String chatLog) throws IOException {
        StringBuilder filteredChatLog = new StringBuilder();
        BufferedReader bufReader = new BufferedReader(new StringReader(chatLog));

        String compileSentences = "Can you give me some mass, |Can you give me some energy, |" +
                "Can you give me one Engineer, | to notify: | to allies: Sent Mass | to allies: Sent Energy |" +
                " to allies: sent |give me Mass";

        Pattern pattern = Pattern.compile(compileSentences);
        String chatLine;

        while ((chatLine = bufReader.readLine()) != null) {
            boolean matchFound = pattern.matcher(chatLine).find();
            if (filterLogCheckBox.isSelected() && matchFound) {
                continue;
            }
            filteredChatLog.append(chatLine).append("\n");
        }

        return filteredChatLog.toString();
    }

    public void onCreateReportForumButton() throws IOException {
        String reportedUserId = createReportForumButton.getId();
        String url = "https://forum.faforever.com/search?term=" + reportedUserId +
                "&in=titlesposts&matchWords=all&sortBy=relevance&sortDirection=desc&showAs=posts";

        String browser = settingsController.getSelectedBrowser();

        if ("selectBrowser".equalsIgnoreCase(browser)) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Browser Selection Required");
            alert.setHeaderText("No Browser Selected");
            alert.setContentText("Please go to the Settings tab (top right) and select a browser.");

            alert.showAndWait();
            return;
        }

        String cmd;

        if ("Microsoft Edge".equalsIgnoreCase(browser)) {
            cmd = "cmd /c start microsoft-edge:" + url;
        } else {
            cmd = "cmd /c start " + browser + " \"" + url + "\"";
        }

        Runtime.getRuntime().exec(cmd);
    }
}
