package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.moderatorclient.ui.main_window.ReportStatisticsController;
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
import com.faforever.moderatorclient.config.local.LocalPreferences;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.*;
import javafx.scene.input.Clipboard;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
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
    private final ObjectMapper objectMapper;
    private final ModerationReportService moderationReportService;
    private final UiService uiService;
    private final FafApiCommunicationService fafApiCommunicationService;
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
    public Button referenceOnlyButton;
    @FXML
    public Text moderatorStatisticsLastWeekText;
    @FXML
    public Text moderatorStatisticsThisWeekText;
    @FXML
    public Text moderatorStatisticsLastMonthText;
    @FXML
    public Text moderatorStatisticsThisMonthText;
    @FXML
    public TextField quotaUserInputTextField;
    @FXML
    public Text quotaResultModeratorsText;
    @FXML
    public Button calculateQuotaButton;
    @FXML
    public TextField initialReportsLoadingTextField;
    @FXML
    public CheckBox fetchReportsOnStartupCheckBox;
    @FXML
    private CheckBox showEnforceRatingCheckBox;
    @FXML
    private CheckBox showGameResultCheckBox;
    @FXML
    private CheckBox showJsonStatsCheckBox;
    @FXML
    private CheckBox showGameEndedCheckBox;
    @FXML
    private FilteredList<ModerationReportFX> filteredItemList;
    @FXML
    private ObservableMap<Integer, ModerationReportFX> itemMap;
    @FXML
    private ObservableList<ModerationReportFX> itemList;
    private ModerationReportFX currentlySelectedItemNotNull;

    public Button copyReporterIdButton;
    @FXML
    public TableView<Offender> mostReportedAccountsTableView;
    @FXML
    public TextArea moderatorStatisticsTextArea;
    @FXML
    public CheckBox showNotifyChatMessages;
    @FXML
    public CheckBox autoLoadChatLogCheckBox;
    public Button useTemplateWithoutReasonsButton;
    public TableView<ModeratorStatistics> moderatorStatisticsTableView;
    public Button useTemplateWithReasonsButton;
    @FXML
    public TextFlow moderatorEventTextFlow;
    @FXML
    public TextField getModeratorEventsForReplayIdTextField;
    @FXML
    public Button getModeratorEventsReplayIdButton;
    public CheckBox showPingMoveCheckBox;
    @FXML
    public CheckBox showPingAttackCheckBox;
    @FXML
    public CheckBox showPingAlertCheckBox;
    @FXML
    public CheckBox showSelfDestructionUnitsCheckBox;
    @FXML
    public TextField thresholdToShowSelfDestructionUnitsEventTextField;
    @FXML
    public CheckBox showFocusArmyFromCheckBox;
    @FXML
    public CheckBox showTextMarkersCheckBox;
    public ChoiceBox<ChooseableStatus> statusChoiceBox;
    @FXML
    public SplitPane root;
    @FXML
    public TextField playerNameFilterTextField;
    @FXML
    public TableView<ModerationReportFX> reportTableView;
    @FXML
    public Button editReportButton;
    @FXML
    public TableView<PlayerFX> reportedPlayerTableView;
    @FXML
    public TextFlow chatLogTextFlow;
    public Button copyReportedUserIdButton;
    public Button copyChatLogButton;
    public Button copyReportIdButton;
    public Button copyGameIdButton;
    public Button startReplayButton;

    private final LocalPreferences localPreferences;

    @Autowired
    public ReportStatisticsController reportStatisticsController;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;

    @Override
    public SplitPane getRoot() {
        return root;
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
                if (!templates.isEmpty()) {
                    templateComboBox.setValue(templates.getFirst());
                }
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

                Platform.runLater(() -> {
                    mostReportedAccountsTableView.setItems(FXCollections.observableArrayList(offenders));

                    mostReportedAccountsTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                        if (newSelection != null) {
                            String fullPlayer = newSelection.getPlayer();
                            String playerNameOnly = fullPlayer.split("\\s*\\[")[0];
                            playerNameFilterTextField.setText(playerNameOnly);
                        }
                    });

                    // Ctrl + C copy functionality
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

        // TODO u call getlatest ban here and on initial = twice server requets. use cached bans from first ban

        /*banService.getLatestBans().thenAccept(banInfos -> {
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
        })*/;
    }

    public void onReferenceOnly() {
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

            if (selectedGameIds.isEmpty()) {
                result = selectedReportIds + "\n\n" + "Reference - REFERENCE_REASON";
            } else {
                result = selectedReportIds + "\n\n" + "Reference - ReplayID " + selectedGameIds + " - REFERENCE_REASON";
            }

            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(result);
            Clipboard.getSystemClipboard().setContent(clipboardContent);

            referenceOnlyButton.setText("Copied");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> referenceOnlyButton.setText("Reference Only"));
                }
            }, 750);
        } catch (NullPointerException e) {
            log.debug(String.valueOf(e));
        }
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
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                ArrayList<ModerationReportFX> reports = Lists.newArrayList(reps.iterator());
                updateModeratorStatistics();


                //moderatorStatisticsLastWeekTextArea.appendText("**Moderator Statistics**\n\n");
                //List list;
                //list = getModeratorActivityReport(ModeratorActivityPeriod.valueOf("LAST_WEEK"));
                //moderatorStatisticsLastWeekTextArea.appendText(list.toString());

                if (reports.isEmpty()) {
                    log.debug("processStatisticsModerator List is Empty");
                    return null;
                }

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
                    if (moderatorStatisticsTextArea != null) {
                        if (moderatorStatisticsTextArea.getText() != null) {
                            moderatorStatisticsTextArea.setText(sb.toString());
                        }
                    }
                    reportStatisticsController.onUpdateStatisticsButtonLastYear();
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
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), false, fafApiCommunicationService);
        Platform.runLater(() -> {
            loadColumnLayout(reportTableView, localPreferences);
            loadSplitPanePositions(root, localPreferences);
        });
    }

    public void onSave() {
        saveColumnLayout(reportTableView, localPreferences);
        saveSplitPanePositions(root, localPreferences);
    }

    private static void saveColumnRecursive(TableColumn<?, ?> column, Map<String, Double> widths, List<String> order) {
        String id = column.getId();
        if (id != null) {
            widths.put(id, column.getWidth());
            order.add(id);
            log.debug("Added column: id={}, width={}", id, column.getWidth());
        } else {
            log.debug("Skipped column with no ID. Text={}, width={}", column.getText(), column.getWidth());
        }

        for (TableColumn<?, ?> subColumn : column.getColumns()) {
            saveColumnRecursive(subColumn, widths, order);
        }
    }

    public static void saveColumnLayout(TableView<?> tableView, LocalPreferences localPreferences) {
        if (tableView == null || localPreferences == null) return;

        Map<String, Double> widths = new HashMap<>();
        List<String> order = new ArrayList<>();

        log.debug("Starting to save column layout. Total top-level columns: {}", tableView.getColumns().size());

        for (TableColumn<?, ?> column : tableView.getColumns()) {
            saveColumnRecursive(column, widths, order);
        }

        log.debug("Final column order list: {}", order);
        log.debug("Final column width map: {}", widths);

        localPreferences.getTabReports().setReportTableColumnWidthsTabReports(widths);
        localPreferences.getTabReports().setReportTableColumnOrderTabReports(order);

        log.debug("Saved column layout to localPreferences.");
    }

    public static void loadColumnLayout(TableView<?> tableView, LocalPreferences localPreferences) {
        if (tableView == null) {
            log.debug("TableView is null, cannot load layout.");
            return;
        }
        if (localPreferences == null) {
            log.debug("LocalPreferences is null, cannot load layout.");
            return;
        }

        Map<String, Double> widths = localPreferences.getTabReports().getReportTableColumnWidthsTabReports();
        List<String> order = localPreferences.getTabReports().getReportTableColumnOrderTabReports();

        log.debug("Loading column layout. Saved widths: {}, saved order: {}", widths, order);

        if (order != null && !order.isEmpty()) {
            tableView.getColumns().sort(Comparator.comparingInt(col -> {
                int index = order.indexOf(col.getId());
                if (index == -1) {
                    log.debug("Column {} not found in saved order, placing at end", col.getId());
                }
                return index >= 0 ? index : Integer.MAX_VALUE;
            }));
            log.debug("Applied column order to TableView.");
        }

        for (TableColumn<?, ?> column : tableView.getColumns()) {
            if (column.getId() != null && widths != null && widths.containsKey(column.getId())) {
                double width = widths.get(column.getId());
                column.setPrefWidth(width);
                log.debug("Set width for column {}: {}", column.getId(), width);
            } else {
                log.debug("Skipping column {}: no saved width", column.getId());
            }
        }

        log.debug("Finished loading column layout.");
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

            if (localPreferences.getTabReports().isAutoLoadChatLogCheckBox()) {
                showChatLog(currentlySelectedItemNotNull);
            }
        } else {
            resetButtonsToInvalidState();
        }
    }

    public void loadCheckboxStates() {
        LocalPreferences.TabReports tabReports = localPreferences.getTabReports();

        showEnforceRatingCheckBox.setSelected(tabReports.isShowEnforceRatingCheckBox());
        showGameResultCheckBox.setSelected(tabReports.isShowGameResultCheckBox());
        showJsonStatsCheckBox.setSelected(tabReports.isShowJsonStatsCheckBox());
        showGameEndedCheckBox.setSelected(tabReports.isShowGameEndedCheckBox());
        showNotifyChatMessages.setSelected(tabReports.isAutoLoadChatLogCheckBox());
        autoLoadChatLogCheckBox.setSelected(tabReports.isAutoLoadChatLogCheckBox());
        showFocusArmyFromCheckBox.setSelected(tabReports.isShowFocusArmyFromCheckBox());
        showPingAlertCheckBox.setSelected(tabReports.isPingOfTypeAlertFilterCheckBox());
        showPingMoveCheckBox.setSelected(tabReports.isPingOfTypeMoveFilterCheckBox());
        showPingAttackCheckBox.setSelected(tabReports.isPingOfTypeAttackFilterCheckBox());
        showSelfDestructionUnitsCheckBox.setSelected(tabReports.isShowSelfDestructionUnitsCheckBox());
        showTextMarkersCheckBox.setSelected(tabReports.isTextMarkerTypeFilterCheckBox());
        thresholdToShowSelfDestructionUnitsEventTextField.setText(tabReports.getThresholdToShowSelfDestructionUnitsEventTextField());
        fetchReportsOnStartupCheckBox.setSelected(tabReports.isFetchReportsOnStartupCheckBox());
    }

    @FXML
    public void initialize() {
        loadCheckboxStates();
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
        bindUIElementsToPreferences();

        if (fetchReportsOnStartupCheckBox.isSelected()) {
            onRefreshInitialReports();
        }

        reportedPlayerTableView.getColumns().forEach(column -> {
            if (column.getId() == null) {
                column.setId(column.getText().replaceAll("\\s+", "")); // e.g., "Last Login" -> "LastLogin"
            }
        });
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
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(writeMe);
        clipboard.setContent(content);
    }

    private void addBan(PlayerFX accountFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX ban = new BanInfoFX();
        ban.setPlayer(accountFX);
        banInfoController.setBanInfo(ban);
        // Dont refresh reports, when ban was applied
        //banInfoController.addPostedListener(banInfoFX -> onRefreshInitialReports());
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
        // Extract report ID from a text if it starts with "rid" for searching
        Pattern pattern = Pattern.compile("rid(\\w+)");
        Matcher matcher = pattern.matcher(filterText);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    @Getter
    @Setter
    private AtomicInteger totalReportsLoaded = new AtomicInteger(0); // Needed for number in window title
    @Getter
    private final AtomicInteger activeApiRequests = new AtomicInteger(0);

    public void onRefreshInitialReports() {
        synchronized (reportLock) {
            if (isFetchingReport) {
                log.debug("Reports are already being fetched.");
                return;
            }
            isFetchingReport = true;
        }

        int initialPageSize = Integer.parseInt(localPreferences.getTabReports().getInitialReportsLoadingTextField());
        int fullPageSize = 10_000;
        int batchSize = 2;

        activeApiRequests.incrementAndGet();

        // Step 1: Load initial reports quickly
        moderationReportService.getPageOfReports(1, initialPageSize).thenAccept(reportFxes -> {
            Platform.runLater(() -> {
                itemList.setAll(reportFxes);
                showInTableRepeatedOffenders(reportFxes);
                totalReportsLoaded.set(reportFxes.size());
            });

            // Step 2: Load all reports in background
            moderationReportService.getAllReportsPaged(fullPageSize, batchSize)
                    .thenAccept(allReports -> Platform.runLater(() -> {
                        ModerationReportFX previousSelection = reportTableView.getSelectionModel().getSelectedItem();
                        String previousSelectionId = previousSelection != null ? previousSelection.getId() : null;

                        itemList.clear();
                        itemList.addAll(allReports);

                        cachedReports.setAll(allReports);
                        processStatisticsModerator(allReports);
                        showInTableRepeatedOffenders(allReports);
                        totalReportsLoaded.set(allReports.size());
                        log.debug("All reports loaded. Total count: {}", allReports.size());

                        if (previousSelectionId != null) {
                            allReports.stream()
                                    .filter(r -> previousSelectionId.equals(r.getId()))
                                    .findFirst()
                                    .ifPresent(r -> Platform.runLater(() -> {
                                        reportTableView.getSelectionModel().select(r);
                                        reportTableView.scrollTo(r);
                                    }));
                        }
                    }))
                    .exceptionally(throwable -> {
                        log.error("Error loading all reports", throwable);
                        return null;
                    })
                    .whenComplete((result, throwable) -> {
                        if (activeApiRequests.decrementAndGet() == 0) {
                            synchronized (reportLock) {
                                isFetchingReport = false;
                            }
                        }
                    });

        }).exceptionally(throwable -> {
            log.error("Error loading initial reports", throwable);
            return null;
        });
    }

    private final ObservableList<ModerationReportFX> cachedReports = FXCollections.observableArrayList();

    public ObservableList<ModerationReportFX> getAllCachedReports() {
        return cachedReports;
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
        confirmationDialog.setContentText(String.format(
                "You are about to apply changes to %d reports. Are you really sure you want to proceed?",
                numberOfReports));

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmationDialog.getButtonTypes().setAll(confirmButtonType, cancelButtonType);

        // Get the buttons
        Button confirmButton = (Button) confirmationDialog.getDialogPane().lookupButton(confirmButtonType);
        Button cancelButton = (Button) confirmationDialog.getDialogPane().lookupButton(cancelButtonType);

        // Style cancel button as red
        cancelButton.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white;");

        // Countdown for confirm button
        final int[] secondsLeft = {3};
        confirmButton.setDisable(true);
        confirmButton.setText("Confirm (" + secondsLeft[0] + ")");
        confirmButton.setStyle("-fx-background-color: lightgray; -fx-text-fill: black;");

        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            secondsLeft[0]--;
            if (secondsLeft[0] > 0) {
                confirmButton.setText("Confirm (" + secondsLeft[0] + ")");
            } else {
                confirmButton.setText("Confirm");
                confirmButton.setDisable(false);
                // Change color to green
                confirmButton.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: white;");
            }
        }));
        countdown.setCycleCount(3);
        countdown.play();

        Optional<ButtonType> result = confirmationDialog.showAndWait();
        return result.isPresent() && result.get() == confirmButtonType;
    }

    private void openEditDialog(ObservableList<ModerationReportFX> selectedItems) {
        try {
            EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
            editModerationReportController.setSelectedReports(new ArrayList<>(selectedItems));

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
    private enum ModeratorActivityPeriod {
        LAST_WEEK(7),
        LAST_MONTH(30);

        private final int days;

        ModeratorActivityPeriod(int days) {
            this.days = days;
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
        LocalPreferences.TabReports settings = localPreferences.getTabReports();

        boolean enforceRating = settings.isShowEnforceRatingCheckBox();
        boolean gameEnded = settings.isShowGameEndedCheckBox();
        boolean gameResult = settings.isShowGameResultCheckBox();
        boolean jsonStats = settings.isShowJsonStatsCheckBox();
        boolean pingOfTypeMoveFilter = settings.isPingOfTypeMoveFilterCheckBox();
        boolean pingOfTypeAttackFilter = settings.isPingOfTypeAttackFilterCheckBox();
        boolean pingOfTypeAlertFilter = settings.isPingOfTypeAlertFilterCheckBox();
        boolean selfDestructionFilter = settings.isShowSelfDestructionUnitsCheckBox();
        boolean focusArmyFromFilter = settings.isShowFocusArmyFromCheckBox();
        boolean textMarkerTypeFilter = settings.isTextMarkerTypeFilterCheckBox();
        int thresholdToShowSelfDestructionUnitsEvent = Integer.parseInt(settings.getThresholdToShowSelfDestructionUnitsEventTextField());

        String moderatorEventsLog = moderatorEvents.stream()
                .filter(event -> {
                    if (event.playerNameFromCommandSource() == null) return false;

                    String message = event.message();
                    boolean filterOut = false;

                    if (!enforceRating && message.contains("command 'EnforceRating' and data")) filterOut = true;
                    if (!gameEnded && message.contains("command 'GameEnded' and data")) filterOut = true;
                    if (!gameResult && message.contains("command 'GameResult' and data")) filterOut = true;
                    if (!jsonStats && message.contains("command 'JsonStats' and data")) filterOut = true;
                    if (!pingOfTypeMoveFilter && message.contains("Created a ping of type 'Move'")) filterOut = true;
                    if (!pingOfTypeAttackFilter && message.contains("Created a ping of type 'Attack'"))
                        filterOut = true;
                    if (!pingOfTypeAlertFilter && message.contains("Created a ping of type 'Alert'")) filterOut = true;
                    if (!focusArmyFromFilter && message.contains("focus army from")) filterOut = true;
                    if (!textMarkerTypeFilter && message.contains("Created a marker with the text")) filterOut = true;

                    if (!selfDestructionFilter && message.contains("Self-destructed")) {
                        Pattern pattern = Pattern.compile("Self-destructed (\\d+) units");
                        Matcher matcher = pattern.matcher(message);
                        if (matcher.find()) {
                            int unitsDestroyed = Integer.parseInt(matcher.group(1));
                            if (unitsDestroyed < thresholdToShowSelfDestructionUnitsEvent) {
                                filterOut = true;
                            }
                        }
                    }

                    return !filterOut;
                })
                .map(event -> {
                    long timeMillis = event.time().toMillis();
                    String formattedChatMessageTime = formatChatMessageTime(timeMillis);

                    String formattedMessage = event.message();
                    if (formattedMessage.contains("focus army from")) {
                        if (formattedMessage.contains("via ConExecute")) {
                            Pattern pattern = Pattern.compile("focus army from (\\d+) to (\\d+) via ConExecute");
                            Matcher matcher = pattern.matcher(formattedMessage);
                            if (matcher.find()) {
                                int fromArmy = Integer.parseInt(matcher.group(1));
                                int toArmy = Integer.parseInt(matcher.group(2));
                                String fromPlayer = playerInfoMap.getOrDefault(fromArmy, new PlayerInfo(-1, "Unknown Player")).getPlayerName();
                                String toPlayer = playerInfoMap.getOrDefault(toArmy, new PlayerInfo(-1, "Unknown Player")).getPlayerName();

                                if (fromPlayer == null) return null; // Filter out events with null source TODO Fallback Value (Spectator Slot Probably)

                                formattedMessage = String.format(
                                        "focus army from %d (%s) to %d (%s) via ConExecute",
                                        fromArmy, fromPlayer, toArmy, toPlayer
                                );
                            }
                        } else {
                            Pattern pattern = Pattern.compile("focus army from (\\d+) to (\\d+)");
                            Matcher matcher = pattern.matcher(formattedMessage);
                            if (matcher.find()) {
                                int fromArmy = Integer.parseInt(matcher.group(1));
                                int toArmy = Integer.parseInt(matcher.group(2));
                                String fromPlayer = playerInfoMap.getOrDefault(fromArmy, new PlayerInfo(-1, "Unknown Player")).getPlayerName();
                                String toPlayer = playerInfoMap.getOrDefault(toArmy, new PlayerInfo(-1, "Unknown Player")).getPlayerName();

                                if ("null".equals(fromPlayer)) return null;

                                formattedMessage = String.format(
                                        "focus army from %d (%s) to %d (%s)",
                                        fromArmy, fromPlayer, toArmy, toPlayer
                                );
                            }
                        }
                    }

                    return String.format("%s from %s : %s",
                            formattedChatMessageTime,
                            event.playerNameFromCommandSource(),
                            formattedMessage
                    );
                })
                .filter(Objects::nonNull)
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
            FafApiCommunicationService.checkRateLimit();

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

    @Setter
    @Getter
    private static Map<Integer, PlayerInfo> playerInfoMap;

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

    private static final Font MONO = Font.font("Courier New", 12);

    private void addColoredTextForPointOfInterest(String line, TextFlow textFlow) {
        textFlow.getChildren().add(styledText(line, Color.ORANGE));
    }

    List<String> keywordsPointOfInterestReplay = List.of(
            "Desynced Replay at Game Time:",
            "does not exist in any of the team names.",
            "Non-Default Common Army:",
            "Cheats Enabled:"
    );

    // Matches: #N [mm:ss] or [HH:mm:ss] sender → receiver: message
    private static final Pattern CHAT_LINE_PATTERN =
            Pattern.compile("^(#\\d+) (\\[[^\\]]+\\]) (.+?) → (.+?): (.*)$");

    public void updateChatLogToColorTextFlow(TextFlow textFlow, String filteredLog, String reporterName, String offenderName) {
        if (textFlow == null) {
            log.debug("TextFlow is not initialized in updateChatLogToColorTextFlow");
            return;
        }

        textFlow.getChildren().clear();
        String[] lines = filteredLog.split("\n");

        // First pass: compute column widths from chat lines
        int maxLineNumLen  = 2;
        int maxSenderLen   = 1;
        int maxReceiverLen = 1;
        for (String line : lines) {
            Matcher m = CHAT_LINE_PATTERN.matcher(line);
            if (m.matches()) {
                maxLineNumLen  = Math.max(maxLineNumLen,  m.group(1).length());
                maxSenderLen   = Math.max(maxSenderLen,   m.group(3).length());
                maxReceiverLen = Math.max(maxReceiverLen, m.group(4).length());
            }
        }
        final int lineNumW   = maxLineNumLen;
        final int senderW    = maxSenderLen;
        final int receiverW  = maxReceiverLen;

        // Second pass: render
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                textFlow.getChildren().add(newline());
                continue;
            }
            if (line.contains("boundsType=LOGICAL")) {
                continue;
            }

            boolean isPoi = false;
            for (String keyword : keywordsPointOfInterestReplay) {
                if (line.contains(keyword)) {
                    addColoredTextForPointOfInterest(line, textFlow);
                    textFlow.getChildren().add(newline());
                    isPoi = true;
                    break;
                }
            }
            if (isPoi) continue;

            Matcher m = CHAT_LINE_PATTERN.matcher(line);
            if (m.matches()) {
                String rawSender   = m.group(3);
                String rawReceiver = m.group(4);

                String lineNumPad  = String.format("%-" + lineNumW  + "s", m.group(1));
                String senderPad   = String.format("%-" + senderW   + "s", rawSender);
                String receiverPad = String.format("%-" + receiverW + "s", rawReceiver);

                Color senderColor;
                if (!offenderName.isEmpty() && rawSender.equals(offenderName)) {
                    senderColor = Color.LIGHTCORAL;
                } else if (!reporterName.isEmpty() && rawSender.equals(reporterName)) {
                    senderColor = Color.LIGHTBLUE;
                } else {
                    senderColor = Color.LIGHTYELLOW;
                }

                textFlow.getChildren().addAll(
                        styledText(lineNumPad + " ", Color.DIMGRAY),
                        styledText(m.group(2) + "  ", Color.GRAY),
                        styledText(senderPad + "  ", senderColor),
                        styledText(receiverPad + "  ", Color.DIMGRAY),
                        styledText(m.group(5), Color.WHITE),
                        newline()
                );
            } else {
                appendHighlightedLine(textFlow, line, offenderName, reporterName);
                textFlow.getChildren().add(newline());
            }
        }
    }

    private void appendHighlightedLine(TextFlow textFlow, String line, String offenderName, String reporterName) {
        record Span(int start, int end, Color color) {}
        List<Span> spans = new ArrayList<>();

        for (String name : new String[]{offenderName, reporterName}) {
            if (name == null || name.isEmpty()) continue;
            Color color = name.equals(offenderName) ? Color.LIGHTCORAL : Color.LIGHTBLUE;
            int idx = 0;
            while ((idx = line.indexOf(name, idx)) != -1) {
                spans.add(new Span(idx, idx + name.length(), color));
                idx += name.length();
            }
        }

        if (spans.isEmpty()) {
            textFlow.getChildren().add(styledText(line, Color.WHITE));
            return;
        }

        spans.sort(Comparator.comparingInt(Span::start));

        int pos = 0;
        for (Span span : spans) {
            if (span.start() < pos) continue; // skip overlaps
            if (span.start() > pos) {
                textFlow.getChildren().add(styledText(line.substring(pos, span.start()), Color.WHITE));
            }
            textFlow.getChildren().add(styledText(line.substring(span.start(), span.end()), span.color()));
            pos = span.end();
        }
        if (pos < line.length()) {
            textFlow.getChildren().add(styledText(line.substring(pos), Color.WHITE));
        }
    }

    private static Text styledText(String content, Color color) {
        Text t = new Text(content);
        t.setFill(color);
        t.setFont(MONO);
        return t;
    }

    private static Text newline() {
        Text t = new Text("\n");
        t.setFont(MONO);
        return t;
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
        String formattedTime;
        if (timeMillis < 0) {
            formattedTime = "N/A";
        } else if (timeMillis < 3_600_000) {
            formattedTime = DurationFormatUtils.formatDuration(timeMillis, "mm:ss");
        } else {
            formattedTime = DurationFormatUtils.formatDuration(timeMillis, "HH:mm:ss");
        }

        return format("[{0}] {1} → {2}: {3}",
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
    public static class PlayerInfo {
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
                        Map.Entry::getKey,
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

    public record DesyncResult(boolean desync, int tick) {}

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

        final String CHEATS_ENABLED_KEY = "CheatsEnabled";
        final String VICTORY_KEY = "Victory";
        final String SHARE_KEY = "Share";
        final String COMMON_ARMY_KEY = "CommonArmy";
        final String DEMORALIZATION = "demoralization";
        final String ASSASSINATION = "Assassination (default)";
        final String OFF_STRING = "Off";

        String commonArmy = getValueForKey(gameOptions, COMMON_ARMY_KEY);
        String cheatsEnabled = getValueForKey(gameOptions, CHEATS_ENABLED_KEY);
        String victoryCondition = getValueForKey(gameOptions, VICTORY_KEY);
        String shareCondition = getValueForKey(gameOptions, SHARE_KEY);

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
                .append("Victory Condition: ").append(DEMORALIZATION.equalsIgnoreCase(String.valueOf(victoryCondition)) ? ASSASSINATION : victoryCondition).append("\n")
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
                "Can you give me one Engineer, |→ notify: |→ allies: Sent Mass |→ allies: Sent Energy |" +
                "→ allies: sent |give me Mass";

        Pattern pattern = Pattern.compile(compileSentences);
        String chatLine;
        int lineNum = 0;

        while ((chatLine = bufReader.readLine()) != null) {
            boolean matchFound = pattern.matcher(chatLine).find();
            if (!localPreferences.getTabReports().isShowNotifyChatMessages() && matchFound) {
                continue;
            }
            lineNum++;
            filteredChatLog.append("#").append(lineNum).append(" ").append(chatLine).append("\n");
        }

        return filteredChatLog.toString();
    }

    public void onCreateReportForumButton() throws IOException {
        String reportedUserId = createReportForumButton.getId();
        String url = "https://forum.faforever.com/search?term=" + reportedUserId +
                "&in=titlesposts&matchWords=all&sortBy=relevance&sortDirection=desc&showAs=posts";

        String browser = String.valueOf(localPreferences.getUi().getBrowserComboBox());

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

    public record ModeratorActivity(String moderator, long completedReports, long discardedReports) {
    }

    private record DateRange(OffsetDateTime start, OffsetDateTime end) {
    }

    private DateRange getDateRange(String period) {
        LocalDate now = LocalDate.now();
        OffsetDateTime start;
        OffsetDateTime end;

        switch (period) {
            case "THIS_WEEK" -> {
                start = now.with(DayOfWeek.MONDAY).atStartOfDay().atOffset(ZoneOffset.UTC);
                end = now.with(DayOfWeek.SUNDAY).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
            }
            case "LAST_WEEK" -> {
                start = now.minusWeeks(1).with(DayOfWeek.MONDAY).atStartOfDay().atOffset(ZoneOffset.UTC);
                end = now.minusWeeks(1).with(DayOfWeek.SUNDAY).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
            }
            case "THIS_MONTH" -> {
                start = now.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                end = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
            }
            case "LAST_MONTH" -> {
                LocalDate lastMonth = now.minusMonths(1);
                start = lastMonth.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
            }
            default -> throw new IllegalArgumentException("Invalid period: " + period);
        }
        return new DateRange(start, end);
    }

    private void updateModeratorStatistics() {
        log.trace("Updating moderator statistics...");
        DateRange lastWeek = getDateRange("LAST_WEEK");
        DateRange thisWeek = getDateRange("THIS_WEEK");
        DateRange lastMonth = getDateRange("LAST_MONTH");
        DateRange thisMonth = getDateRange("THIS_MONTH");

        List<ModeratorActivity> lastWeekActivity = getModeratorActivityForDateRange(lastWeek.start(), lastWeek.end());
        List<ModeratorActivity> thisWeekActivity = getModeratorActivityForDateRange(thisWeek.start(), thisWeek.end());
        List<ModeratorActivity> lastMonthActivity = getModeratorActivityForDateRange(lastMonth.start(), lastMonth.end());
        List<ModeratorActivity> thisMonthActivity = getModeratorActivityForDateRange(thisMonth.start(), thisMonth.end());

        updateText(moderatorStatisticsLastWeekText, "Last Week", lastWeekActivity);
        updateText(moderatorStatisticsThisWeekText, "This Week", thisWeekActivity);
        updateText(moderatorStatisticsLastMonthText, "Last Month", lastMonthActivity);
        updateText(moderatorStatisticsThisMonthText, "This Month", thisMonthActivity);
    }

    private void updateText(Text textPane, String period, List<ModeratorActivity> activities) {
        log.trace("Updating {} moderator statistics...", period);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Moderator Statistics for " + period));
        sb.append("\n\n");

        long totalCompleted = activities.stream().mapToLong(ModeratorActivity::completedReports).sum();
        long totalDiscarded = activities.stream().mapToLong(ModeratorActivity::discardedReports).sum();
        long grandTotal = totalCompleted + totalDiscarded;

        activities.stream()
                .sorted((a1, a2) -> Long.compare(
                        a2.completedReports() + a2.discardedReports(),
                        a1.completedReports() + a1.discardedReports()))
                .forEach(activity -> {
                    long individualTotal = activity.completedReports() + activity.discardedReports();
                    sb.append(String.format("%-15s - Completed: %3d, Discarded: %3d, Total: %4d\n",
                            activity.moderator(),
                            activity.completedReports(),
                            activity.discardedReports(),
                            individualTotal));
                });

        sb.append("\n");
        sb.append(String.format("Overall - Completed: %3d, Discarded: %3d, Total: %4d\n",
                totalCompleted, totalDiscarded, grandTotal));

        textPane.setText(sb.toString());
    }

    private List<ModeratorActivity> getModeratorActivityForDateRange(OffsetDateTime start, OffsetDateTime end) {
        Map<String, Long> completedReports = cachedReports.stream()
                .filter(report -> report.getUpdateTime() != null &&
                        report.getUpdateTime().isAfter(start) &&
                        report.getUpdateTime().isBefore(end) &&
                        report.getReportStatus() == ModerationReportStatus.COMPLETED &&
                        report.getLastModerator() != null)
                .collect(Collectors.groupingBy(
                        report -> report.getLastModerator().getRepresentation(),
                        Collectors.counting()
                ));

        Map<String, Long> discardedReports = cachedReports.stream()
                .filter(report -> report.getUpdateTime() != null &&
                        report.getUpdateTime().isAfter(start) &&
                        report.getUpdateTime().isBefore(end) &&
                        report.getReportStatus() == ModerationReportStatus.DISCARDED &&
                        report.getLastModerator() != null)
                .collect(Collectors.groupingBy(
                        report -> report.getLastModerator().getRepresentation(),
                        Collectors.counting()
                ));

        Set<String> allModerators = new HashSet<>();
        allModerators.addAll(completedReports.keySet());
        allModerators.addAll(discardedReports.keySet());

        return allModerators.stream()
                .map(moderator -> new ModeratorActivity(
                        moderator,
                        completedReports.getOrDefault(moderator, 0L),
                        discardedReports.getOrDefault(moderator, 0L)
                ))
                .collect(Collectors.toList());
    }

    @FXML
    private void updateModeratorQuotas() {
        DateRange lastWeek = getDateRange("LAST_WEEK");
        DateRange thisWeek = getDateRange("THIS_WEEK");
        String quotaInput = quotaUserInputTextField.getText();
        int quota;
        try {
            quota = Integer.parseInt(quotaInput);
        } catch (NumberFormatException e) {
            quotaResultModeratorsText.setText("Please enter a valid number for the quota.");
            return;
        }

        List<ModeratorActivity> lastWeekActivity = getModeratorActivityForDateRange(lastWeek.start(), lastWeek.end());
        List<ModeratorActivity> thisWeekActivity = getModeratorActivityForDateRange(thisWeek.start(), thisWeek.end());

        StringBuilder sb = new StringBuilder();
        sb.append("Moderator Quotas\n");
        sb.append("------------------------------------\n\n");

        Map<String, ModeratorQuotaStatus> moderatorStatus = new HashMap<>();

        sb.append("Last Week Activity:\n\n");
        lastWeekActivity.stream()
                .sorted(Comparator.comparing(ModeratorActivity::moderator))
                .forEach(activity -> {
                    long totalReports = activity.completedReports() + activity.discardedReports();
                    boolean quotaReached = totalReports >= quota;
                    String statusSymbol = quotaReached ? "✅" : "❌";
                    sb.append(String.format("%-15s - Total Reports: %4d %s\n",
                            activity.moderator(),
                            totalReports,
                            statusSymbol));
                    moderatorStatus.put(activity.moderator(), new ModeratorQuotaStatus(totalReports, quotaReached));
                });
        sb.append("\n");

        sb.append("This Week Activity:\n\n");
        thisWeekActivity.stream()
                .sorted(Comparator.comparing(ModeratorActivity::moderator))
                .forEach(activity -> {
                    long totalReports = activity.completedReports() + activity.discardedReports();
                    boolean quotaReached = totalReports >= quota;
                    String statusSymbol = quotaReached ? "✅" : "❌";
                    String lastWeekStatus = moderatorStatus.containsKey(activity.moderator()) ?
                            (moderatorStatus.get(activity.moderator()).quotaReached() ? "✅" : "❌") : "N/A";
                    sb.append(String.format("%-15s - Total Reports: %4d %s (Last Week: %s)\n",
                            activity.moderator(),
                            totalReports,
                            statusSymbol,
                            lastWeekStatus));
                });

        sb.append("\n------------------------------------\n");
        sb.append(String.format("Required Quota: %d\n", quota));

        quotaResultModeratorsText.setText(sb.toString());
    }

    private record ModeratorQuotaStatus(long totalReports, boolean quotaReached) {}

    private void bindUIElementsToPreferences() {
        LocalPreferences.TabReports tabReports = localPreferences.getTabReports();

        for (Field fxField : this.getClass().getDeclaredFields()) {
            fxField.setAccessible(true);
            String fieldName = fxField.getName();

            try {
                // Try to find a matching field in TabReports
                Field prefField = LocalPreferences.TabReports.class.getDeclaredField(fieldName);
                prefField.setAccessible(true);

                Object node = fxField.get(this);
                Object value = prefField.get(tabReports);

                if (node instanceof TextField textField && value instanceof String) {
                    textField.setText((String) value);
                    textField.textProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof TextArea textArea && value instanceof String) {
                    textArea.setText((String) value);
                    textArea.textProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof CheckBox checkBox && value instanceof Boolean) {
                    checkBox.setSelected((Boolean) value);
                    checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof TitledPane titledPane && value instanceof Boolean) {
                    titledPane.setExpanded((Boolean) value);
                    titledPane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                }

            } catch (NoSuchFieldException e) {
                // Controller field has no matching preference field, skip it
            } catch (IllegalAccessException e) {
                log.warn("Cannot access field {}", fieldName, e);
            }
        }
    }

    public static void saveSplitPanePositions(SplitPane splitPane, LocalPreferences localPreferences) {
        if (splitPane == null || localPreferences == null) return;

        List<Double> positions = splitPane.getDividers().stream()
                .map(SplitPane.Divider::getPosition)
                .collect(Collectors.toList());

        localPreferences.getTabReports().setRootSplitPaneDividerPositionsTabReports(positions);
        log.debug("Saved SplitPane positions: {}", positions);
    }

    public static void loadSplitPanePositions(SplitPane splitPane, LocalPreferences localPreferences) {
        if (splitPane == null || localPreferences == null) return;

        List<Double> positions = localPreferences.getTabReports().getRootSplitPaneDividerPositionsTabReports();
        if (positions != null && !positions.isEmpty()) {
            ObservableList<SplitPane.Divider> dividers = splitPane.getDividers();
            for (int i = 0; i < Math.min(dividers.size(), positions.size()); i++) {
                dividers.get(i).setPosition(positions.get(i));
            }
            log.debug("Loaded SplitPane positions: {}", positions);
        } else {
            log.debug("No saved SplitPane positions to load.");
        }
    }

}
