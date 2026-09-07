package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.moderatorclient.ui.moderation_reports.ModeratorStatisticsFormatter.ModeratorActivity;
import com.faforever.moderatorclient.ui.main_window.ReportStatisticsController;
import com.faforever.moderatorclient.ui.main_window.UserManagementController;
import com.faforever.commons.api.dto.BanDurationType;
import com.faforever.commons.api.dto.BanStatus;
import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.commons.replay.ChatMessage;
import com.faforever.commons.replay.ModeratorEvent;
import com.faforever.commons.replay.ReplayDataParser;
import com.faforever.commons.replay.ReplayMetadata;
import com.faforever.commons.replay.GameOption;
import com.faforever.commons.replay.body.Event;
import com.faforever.commons.replay.body.EventCommandType;
import com.faforever.commons.replay.shared.LuaData;
import com.faforever.commons.map.PreviewGenerator;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.BanService;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.config.ApplicationPaths;
import com.faforever.moderatorclient.config.TemplateAndReasonConfig;
import com.faforever.moderatorclient.replay.ReplayStorageService;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.GameFX;
import com.faforever.moderatorclient.ui.domain.MapVersionFX;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import javafx.animation.KeyFrame;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
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
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.AccessLevel;
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
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.scene.paint.Color;

import static java.text.MessageFormat.format;



@Component
@Slf4j
@RequiredArgsConstructor
public class ModerationReportController implements Controller<Region> {
    static final String WARNING_TEMPLATE_NAME = "Warning";
    static final String WARNING_TEMPLATE_FORMAT = "{reportIds}\n\nWarning - ReplayID {gameIds} - {reason}";
    private static final String CHEATS_ENABLED_KEY = "CheatsEnabled";
    private static final String VICTORY_KEY = "Victory";
    private static final String SHARE_KEY = "Share";
    private static final String COMMON_ARMY_KEY = "CommonArmy";
    private static final String DEMORALIZATION = "demoralization";
    private static final String ASSASSINATION = "Assassination (default)";
    private static final String OFF_STRING = "Off";
    private static final String REPORTER_TEXT_STYLE = "-fx-text-fill: lightblue;";
    private static final String OFFENDER_TEXT_STYLE = "-fx-text-fill: lightcoral;";
    private static final double RECENT_BANS_DIALOG_DEFAULT_WIDTH = 1400;
    private static final double RECENT_BANS_DIALOG_DEFAULT_HEIGHT = 640;
    private static final double RECENT_BANS_DIALOG_MIN_WIDTH = 800;
    private static final double RECENT_BANS_DIALOG_MIN_HEIGHT = 300;
    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final ObjectMapper objectMapper;
    private final ModerationReportService moderationReportService;
    private final UiService uiService;
    private final FafApiCommunicationService fafApiCommunicationService;
    private final PlatformService platformService;
    private final ObservableList<PlayerFX> accountPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();
    private final ObservableList<PlayerFX> reportedPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final BanService banService;
    private final UserService userService;
    private final ReplayStorageService replayStorageService;
    public Button createReportForumReporterButton;
    public Button createReportForumOffenderButton;
    public TableColumn lastActivity;
    @FXML
    public Button copyModeratorEventsButton;
    @FXML
    public Button copyChatLogButtonOffenderOnly;
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
    public Canvas paintingCanvas;
    @FXML
    public Slider paintingTimeSlider;
    @FXML
    public TextField replayTimeTextField;
    @FXML
    public TextField replayDrawingFadeSecondsTextField;
    @FXML
    public TextField replayMarkerFadeSecondsTextField;
    @FXML
    public TextField replayPlaybackSpeedTextField;
    @FXML
    public Button replayPlayPauseButton;
    @FXML
    public Label replayEventDetailLabel;
    @FXML
    public ListView<ReplayTimelineEntry> replayTimelineListView;
    @FXML
    public VBox replayPlayersLegend;
    @FXML
    public ListView<ReplayDetailEntry> replayDetailListView;
    @FXML
    public Label replayDetailSummaryLabel;
    private List<PaintingBrushStroke> currentPaintingStrokes = new ArrayList<>();
    private List<ReplayMapMarker> currentReplayMapMarkers = new ArrayList<>();
    private List<ReplayTimelineEntry> currentReplayTimeline = new ArrayList<>();
    private Image replayMapPreview;
    private Map<String, Color> currentReplayPlayerColors = new LinkedHashMap<>();
    private Timeline replayPlaybackTimeline;
    private boolean synchronizingReplayTimelineSelection;
    private int currentReplayTimelineIndex = -1;
    private float replayMapWidth;
    private float replayMapHeight;
    private float paintingMinX, paintingMinZ, paintingMaxX, paintingMaxZ;
    @FXML
    public TextField getModeratorEventsForReplayIdTextField;
    @FXML
    public Button getModeratorEventsReplayIdButton;
    @FXML
    public Text manualReplayLookupInfoText;
    @FXML
    public CheckBox pingOfTypeMoveFilterCheckBox;
    @FXML
    public CheckBox pingOfTypeAttackFilterCheckBox;
    @FXML
    public CheckBox pingOfTypeAlertFilterCheckBox;
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
    public CheckBox autoSearchReportedAccountInUserManagementCheckBox;
    @FXML
    public CheckBox enableManualReplayLookupCheckBox;
    @FXML
    public CheckBox showOpenLogsInNotepadPlusPlusButtonCheckBox;
    @FXML
    public CheckBox showReportPlayerRoleLabelsCheckBox;
    @FXML
    public TabPane reportDetailsTabPane;
    @FXML
    public Tab reportSettingsTab;
    @FXML
    public TextFlow chatLogTextFlow;
    public Button copyReportedUserIdButton;
    public Button copyChatLogButton;
    public Button openLogsInNotepadPlusPlusButton;
    public Button copyReportIdButton;
    public Button copyGameIdButton;
    public Button startReplayButton;

    private final LocalPreferences localPreferences;

    @Autowired
    public ReportStatisticsController reportStatisticsController;

    @Autowired
    public UserManagementController userManagementController;

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

    public void onOpenLogsInNotepadPlusPlus() {
        String chatLog = copyChatLogButton.getId();
        String moderatorEvents = copyModeratorEventsButton.getId();
        if (chatLog == null || chatLog.isBlank()) {
            ViewHelper.errorDialog("Logs unavailable", "Load a report chat log before opening logs in Notepad++.");
            return;
        }
        if (moderatorEvents == null || moderatorEvents.isBlank()) {
            ViewHelper.errorDialog("Logs unavailable", "Load moderator events before opening logs in Notepad++.");
            return;
        }

        try {
            Path chatLogFile = Files.createTempFile("faf-chat-log-", ".txt");
            Path moderatorEventsFile = Files.createTempFile("faf-moderator-events-", ".txt");
            chatLogFile.toFile().deleteOnExit();
            moderatorEventsFile.toFile().deleteOnExit();
            Files.writeString(chatLogFile, chatLog, StandardCharsets.UTF_8);
            Files.writeString(moderatorEventsFile, moderatorEvents, StandardCharsets.UTF_8);
            new ProcessBuilder(resolveNotepadPlusPlusExecutable(),
                    chatLogFile.toAbsolutePath().toString(),
                    moderatorEventsFile.toAbsolutePath().toString()).start();
        } catch (IOException e) {
            log.error("Failed to open logs in Notepad++", e);
            ViewHelper.errorDialog("Notepad++ required",
                    "Install Notepad++ in the standard location or make sure notepad++ is available on PATH, then try again.");
        }
    }

    private String resolveNotepadPlusPlusExecutable() {
        Path path = ViewHelper.resolveNotepadPlusPlusPath();
        return path != null ? path.toString() : "notepad++";
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

    public void onStartReplay() {
        String replayId = startReplayButton.getId();
        String replayUrl = String.format(replayDownLoadFormat, replayId);
        Path stagingFilePath = null;
        try {
            Path resolvedReplayPath = replayStorageService.resolveReplayFile(Integer.parseInt(replayId));
            stagingFilePath = replayStorageService.createTemporaryReplayFile("download_" + replayId + "_");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(replayUrl))
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(stagingFilePath));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Files.move(stagingFilePath, resolvedReplayPath, StandardCopyOption.REPLACE_EXISTING);
                if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(resolvedReplayPath.toFile());
                } else {
                    new ProcessBuilder("cmd", "/c", "start", "", resolvedReplayPath.toAbsolutePath().toString()).start();
                }
            } else {
                log.error("Failed to download replay {}: HTTP {}", replayId, response.statusCode());
                Files.deleteIfExists(stagingFilePath);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to start replay {} from {}", replayId, replayUrl, e);
            if (stagingFilePath != null) {
                try {
                    Files.deleteIfExists(stagingFilePath);
                } catch (IOException cleanupException) {
                    log.warn("Failed to delete temp file {}", stagingFilePath, cleanupException);
                }
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void removeTrailingComma(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    public void onUseTemplateWithoutReasonsButton() {
        ObservableList<ModerationReportFX> selectedItems = reportTableView.getSelectionModel().getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }

        String selectedReportIds = selectedItems.stream()
                .map(item -> String.valueOf(item.getId()))
                .collect(Collectors.joining(","));

        String selectedGameIds = selectedItems.stream()
                .map(ModerationReportFX::getGame)
                .filter(Objects::nonNull)
                .map(game -> String.valueOf(game.getId()))
                .distinct()
                .collect(Collectors.joining(","));

        TemplateAndReasonConfig templateAndReasonConfig = loadTemplateAndReasonConfig();
        if (templateAndReasonConfig == null || templateAndReasonConfig.getTemplates() == null || templateAndReasonConfig.getTemplates().isEmpty()) {
            log.warn("No ban template found in templatesAndReasons.json");
            return;
        }

        String result = formatTemplate(templateAndReasonConfig.getTemplates().getFirst(), selectedReportIds, selectedGameIds, "SOME_REASON");
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(result);
        Clipboard.getSystemClipboard().setContent(clipboardContent);

        useTemplateWithoutReasonsButton.setText("Copied");
        PauseTransition pause = new PauseTransition(Duration.millis(750));
        pause.setOnFinished(e -> useTemplateWithoutReasonsButton.setText("Template No Reasons"));
        pause.play();
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

                Button okButton = new Button("Copy Ban Template");
                Button warningOkButton = new Button("Copy Warning Template");
                Button referenceOkButton = new Button("Copy Reference Template");
                HBox buttonRow = new HBox(10, okButton, warningOkButton, referenceOkButton);
                for (Button button : List.of(okButton, warningOkButton, referenceOkButton)) {
                    button.setMinWidth(150);
                    button.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(button, javafx.scene.layout.Priority.ALWAYS);
                }
                gridPane.add(buttonRow, 0, reasonCheckBoxes.size() + 1, 2, 1);

                Scene scene = new Scene(gridPane);
                templateStage.setScene(scene);
                templateStage.show();

                okButton.setOnAction(event -> {
                    TemplateAndReasonConfig selectedTemplate = templateComboBox.getValue();
                    if (selectedTemplate == null) {
                        return;
                    }

                    copyTemplateToClipboard(selectedTemplate, reasonCheckBoxes, selectedIds, selectedGameIds);
                    closeTemplateStageAfterCopy(templateStage);
                });

                warningOkButton.setOnAction(event -> {
                    TemplateAndReasonConfig warningTemplate = findWarningTemplate(templates);
                    copyTemplateToClipboard(warningTemplate, reasonCheckBoxes, selectedIds, selectedGameIds);
                    closeTemplateStageAfterCopy(templateStage);
                });

                referenceOkButton.setOnAction(event -> {
                    copyReferenceTemplateToClipboard(reasonCheckBoxes, selectedIds, selectedGameIds);
                    closeTemplateStageAfterCopy(templateStage);
                });
            });
        } catch (Exception e) {
            log.warn("Error in template-with-reasons button", e);
        }
    }

    private void copyTemplateToClipboard(TemplateAndReasonConfig selectedTemplate, List<CheckBox> reasonCheckBoxes, String selectedIds, String selectedGameIds) {
        String selectedReasons = reasonCheckBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .collect(Collectors.joining(", "));

        String result = formatTemplate(selectedTemplate, selectedIds, selectedGameIds, selectedReasons);

        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(result);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private String formatTemplate(TemplateAndReasonConfig selectedTemplate, String selectedIds, String selectedGameIds, String selectedReasons) {
        String result = selectedTemplate.getFormat()
                .replace("{reportIds}", selectedIds)
                .replace("{reason}", selectedReasons);

        if (selectedGameIds.isEmpty()) {
            result = result.replace("ReplayID", "");
            result = result.replace(" -  {gameIds}", "");
            result = result.replace("{gameIds}", "");
        } else {
            result = result.replace("{gameIds}", selectedGameIds);
        }

        return result;
    }

    private void copyReferenceTemplateToClipboard(List<CheckBox> reasonCheckBoxes, String selectedIds, String selectedGameIds) {
        String selectedReasons = reasonCheckBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .collect(Collectors.joining(", "));

        String result;
        if (selectedGameIds.isEmpty()) {
            result = selectedIds + "\n\n" + "Reference - " + selectedReasons;
        } else {
            result = selectedIds + "\n\n" + "Reference - ReplayID " + selectedGameIds + " - " + selectedReasons;
        }

        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(result);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private void closeTemplateStageAfterCopy(Stage templateStage) {
        useTemplateWithReasonsButton.setText("Copied");
        templateStage.close();

        PauseTransition pause = new PauseTransition(Duration.millis(750));
        pause.setOnFinished(e -> useTemplateWithReasonsButton.setText("Template With Reasons"));
        pause.play();
    }

    private TemplateAndReasonConfig loadTemplateAndReasonConfig() {
        File templatesAndReasonsFile = ApplicationPaths.resolveConfigurationFile("templatesAndReasons.json").toFile();
        try {
            return objectMapper.readValue(templatesAndReasonsFile, TemplateAndReasonConfig.class);
        } catch (IOException e) {
            log.warn("Failed to load templates and reasons config", e);
        }
        return null;
    }

    static TemplateAndReasonConfig findWarningTemplate(List<TemplateAndReasonConfig> templates) {
        if (templates != null) {
            Optional<TemplateAndReasonConfig> warningTemplate = templates.stream()
                    .filter(template -> WARNING_TEMPLATE_NAME.equalsIgnoreCase(template.getName()))
                    .findFirst();
            if (warningTemplate.isPresent()) {
                return warningTemplate.get();
            }
        }

        TemplateAndReasonConfig warningTemplate = new TemplateAndReasonConfig();
        warningTemplate.setName(WARNING_TEMPLATE_NAME);
        warningTemplate.setFormat(WARNING_TEMPLATE_FORMAT);
        return warningTemplate;
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
        BACKGROUND_EXECUTOR.submit(task);
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

    public void handleCopyBanStatsButtonAction() {
        String stats = moderatorStatisticsTextArea == null ? "" : moderatorStatisticsTextArea.getText();
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(stats == null ? "" : stats);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

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
            @SuppressWarnings("unchecked")
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
        BACKGROUND_EXECUTOR.submit(task);
    }

    @Getter
    @Setter
    public static class Offender {
        private static final DateTimeFormatter LAST_REPORTED_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private final StringProperty player;
        private final Integer currentOffenseCount;
        private final Integer totalOffenseCountCompleted;
        private final Integer totalOffenseCountDiscarded;
        private final Integer totalOffenseCountProcessing;
        @Getter(AccessLevel.NONE)
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

        public String getLastReported() {
            if (lastReported == null) {
                return "";
            }
            return lastReported.format(LAST_REPORTED_FORMATTER);
        }

    }

    private void resetButtonsToInvalidState() {
        resetGameButtonsToInvalidState();
        copyReporterIdButton.setText(formatPlayerRoleButtonText("Reporter", "Reporter n/a"));
        styleReportAccountButton(copyReporterIdButton, "Reporter");
        copyReporterIdButton.setId("");
        copyReportedUserIdButton.setText(formatPlayerRoleButtonText("Offender", "Reported User n/a"));
        styleReportAccountButton(copyReportedUserIdButton, "Offender");
        copyReportedUserIdButton.setId("");
        createReportForumReporterButton.setId("");
        createReportForumReporterButton.setText("Search Forum Reporter:\nn/a");
        styleReportAccountButton(createReportForumReporterButton, "Reporter");
        createReportForumOffenderButton.setId("");
        createReportForumOffenderButton.setText("Search Forum Offender:\nn/a");
        styleReportAccountButton(createReportForumOffenderButton, "Offender");
        accountPlayersOfCurrentlySelectedReport.clear();
        reportedPlayersOfCurrentlySelectedReport.clear();
    }

    private void resetGameButtonsToInvalidState() {
        copyChatLogButton.setText("Chat Log n/a");
        copyChatLogButton.setId("");
        openLogsInNotepadPlusPlusButton.setDisable(true);
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
        ViewHelper.buildUserTableView(platformService, reportedPlayerTableView, accountPlayersOfCurrentlySelectedReport, null,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), false, fafApiCommunicationService, userService, uiService, null);
        TableColumn<PlayerFX, String> roleColumn = new TableColumn<>("Role");
        roleColumn.setId("Role");
        roleColumn.setCellValueFactory(param -> new SimpleStringProperty(getReportAccountRole(param.getValue())));
        roleColumn.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(role);
                setStyle(getReportAccountRoleTextStyle(role));
            }
        });
        roleColumn.setPrefWidth(90);
        reportedPlayerTableView.getColumns().addFirst(roleColumn);
        reportedPlayerTableView.getColumns().add(createReportAccountBanColumn());
        reportedPlayerTableView.getColumns().add(createReportAccountRecentBansColumn());
        reportedPlayerTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> autoSearchReportedAccountInUserManagement(newValue));
        Platform.runLater(() -> {
            loadColumnLayout(reportTableView, localPreferences);
            loadSplitPanePositions(root, localPreferences);
        });
    }

    private TableColumn<PlayerFX, PlayerFX> createReportAccountBanColumn() {
        TableColumn<PlayerFX, PlayerFX> banColumn = new TableColumn<>("Ban");
        banColumn.setId("Ban");
        banColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        banColumn.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(PlayerFX item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Button button = new Button("Add ban");
                button.setStyle(getReportAccountRoleTextStyle(getReportAccountRole(item)));
                button.setOnAction(event -> addBan(item));
                setGraphic(button);
            }
        });
        return banColumn;
    }

    private TableColumn<PlayerFX, PlayerFX> createReportAccountRecentBansColumn() {
        TableColumn<PlayerFX, PlayerFX> bansColumn = new TableColumn<>("Recent Bans");
        bansColumn.setId("RecentBans");
        bansColumn.setMinWidth(130);
        bansColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        bansColumn.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(PlayerFX item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                int banEventCount = item.getBans().size();
                Button button = new Button("Show (" + banEventCount + " events)");
                button.setMaxWidth(Double.MAX_VALUE);
                button.setDisable(countRecentBanEventsForSelectedReport() == 0);
                button.setOnAction(event -> showRecentBansForSelectedReport());
                setGraphic(button);
            }
        });
        return bansColumn;
    }

    private int countRecentBanEventsForSelectedReport() {
        return accountPlayersOfCurrentlySelectedReport.stream()
                .map(PlayerFX::getBans)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();
    }

    private void showRecentBansForSelectedReport() {
        PlayerFX reporter = currentlySelectedItemNotNull == null ? null : currentlySelectedItemNotNull.getReporter();
        ObservableList<BanInfoFX> reporterBans = createSortedRecentBans(reporter == null ? List.of() : reporter.getBans());
        ObservableList<BanInfoFX> offenderBans = createSortedRecentBans(reportedPlayersOfCurrentlySelectedReport.stream()
                .map(PlayerFX::getBans)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList());

        HBox content = new HBox(8,
                createRecentBansPane("Reporter", getPlayerRepresentationOrNA(reporter), Color.LIGHTBLUE, reporterBans),
                createRecentBansPane("Offender", getOffenderRecentBansTitle(), Color.LIGHTCORAL, offenderBans));
        content.setPadding(new Insets(8));

        Stage dialog = new Stage();
        dialog.initModality(Modality.NONE);
        dialog.setTitle("Recent bans - selected report");
        LocalPreferences.TabReports tabReports = localPreferences.getTabReports();
        Scene scene = new Scene(content);
        applyCurrentTheme(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });
        dialog.setScene(scene);
        dialog.setMinWidth(RECENT_BANS_DIALOG_MIN_WIDTH);
        dialog.setMinHeight(RECENT_BANS_DIALOG_MIN_HEIGHT);
        dialog.setWidth(validSavedDialogSize(tabReports.getRecentBansDialogWidth(), RECENT_BANS_DIALOG_MIN_WIDTH)
                ? tabReports.getRecentBansDialogWidth()
                : RECENT_BANS_DIALOG_DEFAULT_WIDTH);
        dialog.setHeight(validSavedDialogSize(tabReports.getRecentBansDialogHeight(), RECENT_BANS_DIALOG_MIN_HEIGHT)
                ? tabReports.getRecentBansDialogHeight()
                : RECENT_BANS_DIALOG_DEFAULT_HEIGHT);
        restoreRecentBansDialogPosition(dialog, tabReports);
        dialog.setOnHidden(event -> saveRecentBansDialogBounds(dialog));
        dialog.show();
    }

    private static boolean validSavedDialogSize(double size, double minimum) {
        return Double.isFinite(size) && size >= minimum;
    }

    private static void restoreRecentBansDialogPosition(Stage dialog, LocalPreferences.TabReports tabReports) {
        if (Double.isFinite(tabReports.getRecentBansDialogX()) && tabReports.getRecentBansDialogX() >= 0) {
            dialog.setX(tabReports.getRecentBansDialogX());
        }
        if (Double.isFinite(tabReports.getRecentBansDialogY()) && tabReports.getRecentBansDialogY() >= 0) {
            dialog.setY(tabReports.getRecentBansDialogY());
        }
    }

    private void saveRecentBansDialogBounds(Stage dialog) {
        LocalPreferences.TabReports tabReports = localPreferences.getTabReports();
        tabReports.setRecentBansDialogX(dialog.getX());
        tabReports.setRecentBansDialogY(dialog.getY());
        tabReports.setRecentBansDialogWidth(dialog.getWidth());
        tabReports.setRecentBansDialogHeight(dialog.getHeight());
    }

    private void applyCurrentTheme(Scene scene) {
        String stylesheet = localPreferences.getTabSettings().isDarkModeCheckBox()
                ? "/style/main-dark.css"
                : "/style/main-light.css";
        Optional.ofNullable(getClass().getResource(stylesheet))
                .ifPresent(resource -> scene.getStylesheets().add(resource.toExternalForm()));
    }

    private ObservableList<BanInfoFX> createSortedRecentBans(Collection<BanInfoFX> bans) {
        return FXCollections.observableArrayList(bans.stream()
                .sorted(Comparator.comparing(BanInfoFX::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList());
    }

    private VBox createRecentBansPane(String prefixLabel, String name, Color color, ObservableList<BanInfoFX> bans) {
        TableView<BanInfoFX> tableView = new TableView<>();
        ViewHelper.buildBanTableView(tableView, bans, true, localPreferences, userService, uiService);
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        configureRecentBansTableColumns(tableView);

        Color plainTextColor = localPreferences.getTabSettings().isDarkModeCheckBox() ? Color.LIGHTGRAY : Color.BLACK;
        Text prefixText = new Text(prefixLabel);
        prefixText.setFill(color);
        prefixText.setStyle("-fx-font-weight: bold;");
        Text nameText = new Text(name);
        nameText.setFill(color);
        nameText.setStyle("-fx-font-weight: bold;");
        TextFlow titleFlow = new TextFlow(
                prefixText,
                styledPlainText(" - ", plainTextColor),
                nameText,
                styledPlainText(" (" + bans.size() + " events)", plainTextColor));

        VBox pane = new VBox(6, titleFlow, tableView);
        pane.setPrefWidth(730);
        pane.setMinWidth(520);
        pane.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pane, Priority.ALWAYS);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        return pane;
    }

    private void configureRecentBansTableColumns(TableView<BanInfoFX> tableView) {
        tableView.getColumns().removeIf(column ->
                !List.of("ID", "Duration", "Reason").contains(column.getText()));
        TableColumn<BanInfoFX, String> expiredColumn = new TableColumn<>("Expired");
        expiredColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                formatRecentBanExpiredAgo(cellData.getValue())));
        tableView.getColumns().add(2, expiredColumn);

        setFixedRecentBansColumnWidth(tableView, "ID", 70);
        setFixedRecentBansColumnWidth(tableView, "Duration", 90);
        setFixedRecentBansColumnWidth(tableView, "Expired", 170);
        bindRecentBansReasonColumnWidth(tableView);
    }

    private void setFixedRecentBansColumnWidth(TableView<BanInfoFX> tableView, String columnText, double width) {
        tableView.getColumns().stream()
                .filter(column -> Objects.equals(column.getText(), columnText))
                .findFirst()
                .ifPresent(column -> {
                    column.setMinWidth(width);
                    column.setPrefWidth(width);
                    column.setMaxWidth(width);
                    column.setResizable(false);
                });
    }

    private void bindRecentBansReasonColumnWidth(TableView<BanInfoFX> tableView) {
        tableView.getColumns().stream()
                .filter(column -> Objects.equals(column.getText(), "Reason"))
                .findFirst()
                .ifPresent(column -> {
                    column.setMinWidth(260);
                    column.prefWidthProperty().bind(tableView.widthProperty()
                            .subtract(70)
                            .subtract(90)
                            .subtract(170)
                            .subtract(24));
                });
    }

    private String formatRecentBanExpiredAgo(BanInfoFX banInfo) {
        if (banInfo.getBanStatus() == BanStatus.DISABLED) {
            if (banInfo.getRevokeTime() == null) {
                return "disabled/revoked";
            }

            java.time.Duration revokedAgo = java.time.Duration.between(banInfo.getRevokeTime(), java.time.OffsetDateTime.now());
            return revokedAgo.isNegative()
                    ? "disabled/revoked"
                    : "disabled/revoked " + formatRecentBanRelativeDuration(revokedAgo) + " ago";
        }

        if (banInfo.getBanStatus() == BanStatus.BANNED) {
            if (banInfo.getDuration() == BanDurationType.PERMANENT) {
                return "active, permanent";
            }
            if (banInfo.getExpiresAt() == null) {
                return "active";
            }

            java.time.Duration remaining = java.time.Duration.between(java.time.OffsetDateTime.now(), banInfo.getExpiresAt());
            return remaining.isNegative()
                    ? "active"
                    : "active, " + formatRecentBanRelativeDuration(remaining) + " left";
        }

        if (banInfo.getExpiresAt() == null) {
            return "";
        }

        java.time.Duration delta = java.time.Duration.between(banInfo.getExpiresAt(), java.time.OffsetDateTime.now());
        if (delta.isNegative()) {
            return "";
        }

        return formatRecentBanRelativeDuration(delta) + " ago";
    }

    private String formatRecentBanRelativeDuration(java.time.Duration duration) {
        if (duration.toDays() == 0 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0) {
            return "now";
        }
        return ViewHelper.formatCompactDuration(duration);
    }

    private String getPlayerRepresentationOrNA(PlayerFX player) {
        return player == null ? "n/a" : player.getRepresentation();
    }

    private String getOffenderRecentBansTitle() {
        if (reportedPlayersOfCurrentlySelectedReport.isEmpty()) {
            return "n/a";
        }
        if (reportedPlayersOfCurrentlySelectedReport.size() == 1) {
            return reportedPlayersOfCurrentlySelectedReport.getFirst().getRepresentation();
        }
        return reportedPlayersOfCurrentlySelectedReport.size() + " offenders";
    }

    public void onSave() {
        saveColumnLayout(reportTableView, localPreferences);
        saveSplitPanePositions(root, localPreferences);
        Tab selectedSubTab = reportDetailsTabPane.getSelectionModel().getSelectedItem();
        if (selectedSubTab != null && selectedSubTab.getId() != null) {
            localPreferences.getTabReports().setSelectedSubTabId(selectedSubTab.getId());
        }
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
            if (newValue == null) {
                resetButtonsToInvalidState();
                return;
            }
            try {
                updateReportDetails(newValue);
            } catch (Exception e) {
                log.debug("Exception for selected report: ", e);
                resetButtonsToInvalidState();
            }
        });
    }

    private void updateReportDetails(ModerationReportFX newValue) {
        Collection<PlayerFX> reportedUsers = newValue.getReportedUsers() == null
                ? Collections.emptySet()
                : newValue.getReportedUsers();
        reportedPlayersOfCurrentlySelectedReport.setAll(reportedUsers);
        accountPlayersOfCurrentlySelectedReport.setAll(getReportAccountRows(newValue));
        currentlySelectedItemNotNull = newValue;

        copyReportIdButton.setText("Report ID: " + newValue.getId());
        copyReportIdButton.setId(newValue.getId());

        String reporterRepresentation = newValue.getReporter() == null
                ? "Reporter n/a"
                : newValue.getReporter().getRepresentation();
        copyReporterIdButton.setText(formatPlayerRoleButtonText("Reporter", reporterRepresentation));
        styleReportAccountButton(copyReporterIdButton, "Reporter");
        copyReporterIdButton.setId(newValue.getReporter() == null ? "" : reporterRepresentation);
        createReportForumReporterButton.setId(newValue.getReporter() == null ? "" : newValue.getReporter().getId());
        createReportForumReporterButton.setText("Search Forum Reporter:\n" + reporterRepresentation);
        styleReportAccountButton(createReportForumReporterButton, "Reporter");

        // Since ~2023, reports have only one offender; legacy reports may have several.
        for (PlayerFX offender : reportedPlayersOfCurrentlySelectedReport) {
            copyReportedUserIdButton.setId(offender.getRepresentation());
            copyReportedUserIdButton.setText(formatPlayerRoleButtonText("Offender", offender.getRepresentation()));
            styleReportAccountButton(copyReportedUserIdButton, "Offender");
            createReportForumOffenderButton.setId(offender.getId());
            createReportForumOffenderButton.setText("Search Forum Offender:\n" + offender.getRepresentation());
            styleReportAccountButton(createReportForumOffenderButton, "Offender");
        }
        if (reportedPlayersOfCurrentlySelectedReport.isEmpty()) {
            copyReportedUserIdButton.setId("");
            copyReportedUserIdButton.setText(formatPlayerRoleButtonText("Offender", "Reported User n/a"));
            styleReportAccountButton(copyReportedUserIdButton, "Offender");
            createReportForumOffenderButton.setId("");
            createReportForumOffenderButton.setText("Search Forum Offender:\nn/a");
            styleReportAccountButton(createReportForumOffenderButton, "Offender");
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
            resetGameButtonsToInvalidState();
        }
    }

    private static List<PlayerFX> getReportAccountRows(ModerationReportFX report) {
        List<PlayerFX> accountRows = new ArrayList<>();
        if (report.getReporter() != null) {
            accountRows.add(report.getReporter());
        }
        if (report.getReportedUsers() != null) {
            accountRows.addAll(report.getReportedUsers());
        }
        return accountRows;
    }

    private String getReportAccountRole(PlayerFX player) {
        if (player == null || currentlySelectedItemNotNull == null) {
            return "";
        }

        PlayerFX reporter = currentlySelectedItemNotNull.getReporter();
        if (reporter != null && Objects.equals(reporter.getId(), player.getId())) {
            return "Reporter";
        }

        return "Offender";
    }

    private String getReportAccountRoleTextStyle(String role) {
        return switch (role) {
            case "Reporter" -> REPORTER_TEXT_STYLE;
            case "Offender" -> OFFENDER_TEXT_STYLE;
            default -> "";
        };
    }

    private void autoSearchReportedAccountInUserManagement(PlayerFX player) {
        if (!autoSearchReportedAccountInUserManagementCheckBox.isSelected() || player == null) {
            return;
        }

        userManagementController.searchUserByLogin(player.getLogin());
    }

    public void loadCheckboxStates() {
        LocalPreferences.TabReports tabReports = localPreferences.getTabReports();

        showEnforceRatingCheckBox.setSelected(tabReports.isShowEnforceRatingCheckBox());
        showGameResultCheckBox.setSelected(tabReports.isShowGameResultCheckBox());
        showJsonStatsCheckBox.setSelected(tabReports.isShowJsonStatsCheckBox());
        showGameEndedCheckBox.setSelected(tabReports.isShowGameEndedCheckBox());
        showNotifyChatMessages.setSelected(tabReports.isShowNotifyChatMessages());
        autoLoadChatLogCheckBox.setSelected(tabReports.isAutoLoadChatLogCheckBox());
        showFocusArmyFromCheckBox.setSelected(tabReports.isShowFocusArmyFromCheckBox());
        pingOfTypeAlertFilterCheckBox.setSelected(tabReports.isPingOfTypeAlertFilterCheckBox());
        pingOfTypeMoveFilterCheckBox.setSelected(tabReports.isPingOfTypeMoveFilterCheckBox());
        pingOfTypeAttackFilterCheckBox.setSelected(tabReports.isPingOfTypeAttackFilterCheckBox());
        showSelfDestructionUnitsCheckBox.setSelected(tabReports.isShowSelfDestructionUnitsCheckBox());
        showTextMarkersCheckBox.setSelected(tabReports.isTextMarkerTypeFilterCheckBox());
        thresholdToShowSelfDestructionUnitsEventTextField.setText(tabReports.getThresholdToShowSelfDestructionUnitsEventTextField());
        fetchReportsOnStartupCheckBox.setSelected(tabReports.isFetchReportsOnStartupCheckBox());
        enableManualReplayLookupCheckBox.setSelected(tabReports.isEnableManualReplayLookupCheckBox());
        showOpenLogsInNotepadPlusPlusButtonCheckBox.setSelected(
                tabReports.isShowOpenLogsInNotepadPlusPlusButtonCheckBox());
        showReportPlayerRoleLabelsCheckBox.setSelected(tabReports.isShowReportPlayerRoleLabelsCheckBox());
        autoSearchReportedAccountInUserManagementCheckBox.setSelected(
                tabReports.isAutoSearchReportedAccountInUserManagementCheckBox());
    }

    @FXML
    public void initialize() {
        loadCheckboxStates();
        restoreAndTrackSelectedSubTab();
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
        addReportToolPreferenceListeners();
        refreshManualReplayLookupVisibility();
        refreshOpenLogsInNotepadPlusPlusVisibility();
        refreshReportPlayerRoleButtonText();

        if (fetchReportsOnStartupCheckBox.isSelected()) {
            onRefreshInitialReports();
        }

        reportedPlayerTableView.getColumns().forEach(column -> {
            if (column.getId() == null) {
                column.setId(column.getText().replaceAll("\\s+", "")); // e.g., "Last Login" -> "LastLogin"
            }
        });

        paintingTimeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            long milliseconds = Math.round(newVal.doubleValue() * 1_000);
            replayTimeTextField.setText(formatReplayTime(java.time.Duration.ofMillis(milliseconds)));
            java.time.Duration limit = java.time.Duration.ofMillis(milliseconds);
            renderReplayMap(visiblePaintingStrokes(limit), limit);
            synchronizeReplayTimeline(limit);
        });
        replayPlaybackTimeline = new Timeline(new KeyFrame(Duration.millis(100), event -> advanceReplayPlayback()));
        replayPlaybackTimeline.setCycleCount(Timeline.INDEFINITE);
        replayTimelineListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(ReplayTimelineEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label time = new Label(formatReplayTime(entry.time()));
                time.setMinWidth(54);
                Label category = new Label(entry.category());
                category.setMinWidth(110);
                Label player = new Label(entry.playerName());
                player.setMinWidth(120);
                player.setStyle("-fx-text-fill: " + colorCss(currentReplayPlayerColors
                        .getOrDefault(entry.playerName(), Color.WHITE)) + ";");
                Label details = new Label(entry.details());
                details.setWrapText(true);
                HBox.setHgrow(details, Priority.ALWAYS);
                setText(null);
                setGraphic(new HBox(8, time, category, player, details));
            }
        });
        replayTimelineListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, entry) -> {
            if (entry != null && !synchronizingReplayTimelineSelection) {
                paintingTimeSlider.setValue(entry.time().toMillis() / 1_000d);
            }
        });
        replayDetailListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(ReplayDetailEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label time = new Label(formatReplayTime(entry.time()));
                time.setMinWidth(54);
                Label category = new Label(entry.category());
                category.setMinWidth(104);
                Label player = new Label(entry.playerName());
                player.setMinWidth(120);
                player.setStyle("-fx-text-fill: " + colorCss(currentReplayPlayerColors
                        .getOrDefault(entry.playerName(), Color.WHITE)) + ";");
                Label details = new Label(entry.details());
                details.setWrapText(true);
                HBox.setHgrow(details, Priority.ALWAYS);
                setText(null);
                setGraphic(new HBox(8, time, category, player, details));
            }
        });
        replayDetailListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, entry) -> {
            if (entry != null) {
                paintingTimeSlider.setValue(entry.time().toMillis() / 1_000d);
            }
        });
    }

    private void addReportToolPreferenceListeners() {
        enableManualReplayLookupCheckBox.selectedProperty().addListener((obs, oldValue, newValue) ->
                refreshManualReplayLookupVisibility());
        showOpenLogsInNotepadPlusPlusButtonCheckBox.selectedProperty().addListener((obs, oldValue, newValue) ->
                refreshOpenLogsInNotepadPlusPlusVisibility());
        showReportPlayerRoleLabelsCheckBox.selectedProperty().addListener((obs, oldValue, newValue) ->
                refreshReportPlayerRoleButtonText());
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
        ViewHelper.buildModerationReportTableView(reportTableView, sortedItemList, this::showChatLog, userService, uiService, localPreferences);
        refreshReportTableRoleHeaderColors();
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener(observable -> renewFilter());
        playerNameFilterTextField.textProperty().addListener(observable -> renewFilter());
    }

    private void refreshReportTableRoleHeaderColors() {
        colorReportTableColumnHeader("reporterColumn", "Reporter");
        colorReportTableColumnHeader("reportedUsersColumn", "Offender");
    }

    private void colorReportTableColumnHeader(String columnId, String role) {
        reportTableView.getColumns().stream()
                .filter(column -> Objects.equals(column.getId(), columnId))
                .findFirst()
                .ifPresent(column -> {
                    Label label = new Label(role);
                    label.setStyle(getReportAccountRoleTextStyle(role));
                    column.setText("");
                    column.setGraphic(label);
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

        // Snapshot before any dialogs — showAndWait() pumps the event loop and a
        // background refresh could clear itemList and reset the selection mid-wait.
        List<ModerationReportFX> snapshot = new ArrayList<>(selectedItems);

        if (snapshot.size() > 5) {
            if (!showConfirmationDialog(snapshot.size())) {
                return;
            }
        }

        openEditDialog(snapshot);
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

    private void openEditDialog(List<ModerationReportFX> selectedItems) {
        try {
            EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
            editModerationReportController.setSelectedReports(selectedItems);

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

    private void restoreAndTrackSelectedSubTab() {
        String savedTabId = localPreferences.getTabReports().getSelectedSubTabId();
        reportDetailsTabPane.getTabs().stream()
                .filter(tab -> Objects.equals(tab.getId(), savedTabId))
                .findFirst()
                .ifPresent(tab -> reportDetailsTabPane.getSelectionModel().select(tab));
        reportDetailsTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null && newTab.getId() != null) {
                localPreferences.getTabReports().setSelectedSubTabId(newTab.getId());
            }
        });
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

    private volatile boolean isTaskRunning = false;

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
                        String filePathGamingModeratorTask = ApplicationPaths.resolveConfigurationFile("templateGamingModeratorTask.txt").toString();
                        StringBuilder contentGamingModeratorTask = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new FileReader(filePathGamingModeratorTask))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.replace("%offenderNames%", offenderNames);
                                line = line.replace("%reporter%", reporter);

                                contentGamingModeratorTask.append(line).append("\n");
                            }
                        } catch (IOException e) {
                            log.warn("Failed to read gaming moderator task template", e);
                        }

                        try {
                            processAndDisplayReplay(header, tempFilePath, String.valueOf(contentGamingModeratorTask), game);
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
        BACKGROUND_EXECUTOR.submit(task);
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
        int thresholdRaw;
        try {
            thresholdRaw = Integer.parseInt(settings.getThresholdToShowSelfDestructionUnitsEventTextField());
        } catch (NumberFormatException e) {
            thresholdRaw = Integer.MAX_VALUE;
        }
        final int thresholdToShowSelfDestructionUnitsEvent = thresholdRaw;

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

                    if (message.contains("Self-destructed")) {
                        if (!selfDestructionFilter) {
                            filterOut = true;
                        } else {
                            Pattern pattern = Pattern.compile("Self-destructed (\\d+) units");
                            Matcher matcher = pattern.matcher(message);
                            if (matcher.find()) {
                                int unitsDestroyed = Integer.parseInt(matcher.group(1));
                                if (unitsDestroyed < thresholdToShowSelfDestructionUnitsEvent) {
                                    filterOut = true;
                                }
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

                                if (fromPlayer == null) return null;

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
            refreshOpenLogsInNotepadPlusPlusVisibility();
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
        String validity = game.getValidity() != null ? game.getValidity().name() : "UNKNOWN";
        OffsetDateTime playedAt = game.getStartTime() != null ? game.getStartTime() : game.getEndTime();
        return format("CHAT LOG -- Report ID {0} -- Replay ID {1} -- Title \"{2}\" -- Rank Status: {3} -- Played: {4}\n\n",
                report.getId(), game.getId(), game.getName(), validity, formatGameAge(playedAt, OffsetDateTime.now()));
    }

    private Path createTempFile(GameFX game) {
        try {
            return replayStorageService.createTemporaryReplayFile("analysis-" + game.getId() + "-");
        } catch (IOException e) {
            log.error("An error occurred while creating a temporary file.", e);
            return null;
        }
    }

    private HttpResponse<Path> downloadReplay(GameFX game, Path tempFilePath) {
        try {
            FafApiCommunicationService.checkRateLimit();

            String replayUrl = game.getReplayUrl(replayDownLoadFormat);
            log.debug("Downloading replay from {} to {}", replayUrl, tempFilePath);

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

    private void processAndDisplayReplay(String header, Path tempFilePath, String promptAI, GameFX game) {
        try {
            try (ReplayStorageService.PreparedReplay preparedReplay = replayStorageService.prepareReplayForParsing(tempFilePath)) {
                ReplayDataParser replayDataParser = new ReplayDataParser(preparedReplay.path(), objectMapper);
                String chatLog = generateChatLog(replayDataParser);

                StringBuilder chatLogFiltered = new StringBuilder();
                StringBuilder chatLogFilteredOffenderOnly = new StringBuilder();
                String reportedUser = extractName(copyReportedUserIdButton.getText());

                chatLogFiltered.append(header);

                String metadataInfo = generateMetadataInfo(replayDataParser);
                chatLogFiltered.append(metadataInfo);

                String filteredChatLog = filterAndAppendChatLog(chatLog);
                chatLogFiltered.append(filteredChatLog);

                Map<Integer, PlayerInfo> playerInfoMap = replayDataParser.getArmies().entrySet().stream()
                        .filter(entry -> entry.getValue().containsKey("PlayerName"))
                        .collect(Collectors.toMap(
                                entry -> entry.getKey() + 1,
                                entry -> new PlayerInfo(entry.getKey() + 1, (String) entry.getValue().get("PlayerName"))
                        ));

                List<ModeratorEvent> moderatorEvents = replayDataParser.getModeratorEvents();
                showModeratorEvent(moderatorEvents, playerInfoMap);

                List<PaintingBrushStroke> paintingStrokes = extractPaintingStrokes(replayDataParser);
                ReplayTimelineData replayTimelineData = extractReplayTimeline(replayDataParser);
                List<ReplayDetailEntry> replayDetailEntries = extractReplayDetailEntries(replayDataParser);
                ReplayMapPreview mapPreview = loadMapPreview(game, replayDataParser);
                Platform.runLater(() -> {
                    currentPaintingStrokes = paintingStrokes;
                    currentReplayMapMarkers = replayTimelineData.mapMarkers();
                    currentReplayTimeline = replayTimelineData.timelineEntries();
                    renderReplayDetailEntries(replayDetailEntries);
                    stopReplayPlayback();
                    currentReplayPlayerColors = extractReplayPlayerColors(replayDataParser);
                    replayMapPreview = mapPreview.image();
                    replayMapWidth = mapPreview.width();
                    replayMapHeight = mapPreview.height();
                    renderReplayPlayerLegend(extractReplayPlayers(replayDataParser, replayTimelineData.mapMarkers(),
                            currentReplayPlayerColors));
                    if (replayMapWidth > 0 && replayMapHeight > 0) {
                        paintingMinX = 0;
                        paintingMaxX = replayMapWidth;
                        paintingMinZ = 0;
                        paintingMaxZ = replayMapHeight;
                    } else {
                        paintingMinX = Float.MAX_VALUE; paintingMaxX = -Float.MAX_VALUE;
                        paintingMinZ = Float.MAX_VALUE; paintingMaxZ = -Float.MAX_VALUE;
                        for (PaintingBrushStroke s : paintingStrokes) {
                            for (float[] pt : s.points()) {
                                if (pt[0] < paintingMinX) paintingMinX = pt[0];
                                if (pt[0] > paintingMaxX) paintingMaxX = pt[0];
                                if (pt[1] < paintingMinZ) paintingMinZ = pt[1];
                                if (pt[1] > paintingMaxZ) paintingMaxZ = pt[1];
                            }
                        }
                        for (ReplayMapMarker marker : currentReplayMapMarkers) {
                            paintingMinX = Math.min(paintingMinX, marker.x());
                            paintingMaxX = Math.max(paintingMaxX, marker.x());
                            paintingMinZ = Math.min(paintingMinZ, marker.z());
                            paintingMaxZ = Math.max(paintingMaxZ, marker.z());
                        }
                    }
                    long maxSec = Math.max(
                            currentReplayTimeline.stream().mapToLong(entry -> entry.time().getSeconds()).max().orElse(0),
                            Math.max(
                                    paintingStrokes.stream().mapToLong(stroke -> stroke.time().getSeconds()).max().orElse(0),
                                    currentReplayMapMarkers.stream().mapToLong(marker -> marker.time().getSeconds()).max().orElse(0)
                            )
                    );
                    paintingTimeSlider.setMin(0);
                    paintingTimeSlider.setMax(Math.max(maxSec, 1));
                    paintingTimeSlider.setValue(0);
                    renderReplayMap(visiblePaintingStrokes(java.time.Duration.ZERO), java.time.Duration.ZERO);
                    renderReplayTimeline();
                });

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
                    refreshOpenLogsInNotepadPlusPlusVisibility();

                    copyChatLogButtonOffenderOnly.setText("Copy Chat Offender");
                    copyChatLogButtonOffenderOnly.setId(chatLogFilteredOffenderOnly.toString());

                    updateChatLogToColorTextFlow(chatLogTextFlow, String.valueOf(chatLogFiltered),
                            extractName(copyReporterIdButton.getText()),
                            extractName(copyReportedUserIdButton.getText()));
                });
            }
        } catch (Exception e) {
            log.error("An error occurred while parsing replay data.", e);
        }
    }

    static String extractName(String fullNameWithId) {
        if (fullNameWithId == null) {
            return "";
        }

        String name = fullNameWithId.trim();
        int rolePrefixEnd = name.lastIndexOf('\n');
        if (rolePrefixEnd >= 0) {
            name = name.substring(rolePrefixEnd + 1).trim();
        }

        if (name.contains("[")) {
            return name.split("\\[")[0].trim();
        }
        return name;
    }

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
    private static final Pattern MODERATOR_EVENT_LINE_PATTERN =
            Pattern.compile("^(\\S+) from (.+?)\\s*:\\s*(.*)$");

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
                Color messageColor;
                if (!offenderName.isEmpty() && rawSender.equals(offenderName)) {
                    senderColor = Color.LIGHTCORAL;
                    messageColor = Color.LIGHTCORAL;
                } else if (!reporterName.isEmpty() && rawSender.equals(reporterName)) {
                    senderColor = Color.LIGHTBLUE;
                    messageColor = Color.LIGHTBLUE;
                } else {
                    senderColor = Color.LIGHTYELLOW;
                    messageColor = Color.WHITE;
                }

                textFlow.getChildren().addAll(
                        styledText(lineNumPad + " ", Color.DIMGRAY),
                        styledText(m.group(2) + "  ", Color.GRAY),
                        styledText(senderPad + "  ", senderColor),
                        styledText(receiverPad + "  ", Color.DIMGRAY),
                        styledText(m.group(5), messageColor),
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

    private static Text styledPlainText(String content, Color color) {
        Text t = new Text(content);
        t.setFill(color);
        return t;
    }

    private static Text styledText(String content, Color color) {
        Text t = new Text(content);
        t.setFill(color);
        t.setFont(monospaceFont());
        return t;
    }

    private static Text newline() {
        Text t = new Text("\n");
        t.setFont(monospaceFont());
        return t;
    }

    private static Font monospaceFont() {
        return Font.font("Courier New", 12);
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
        if (textFlow == null) {
            return;
        }

        textFlow.getChildren().clear();
        String[] events = moderatorEvents.split("\n");

        int visibleEventCount = (int) Arrays.stream(events)
                .filter(event -> event != null && !event.trim().isEmpty())
                .count();
        int rowNumberW = Math.max(2, ("#" + visibleEventCount).length());
        int maxTimeLen = 10;
        int maxNameLen = 1;
        for (String event : events) {
            Matcher matcher = MODERATOR_EVENT_LINE_PATTERN.matcher(event);
            if (matcher.matches()) {
                maxTimeLen = Math.max(maxTimeLen, bracketedTime(matcher.group(1)).length());
                maxNameLen = Math.max(maxNameLen, matcher.group(2).trim().length());
            }
        }
        final int rowW = rowNumberW;
        final int timeW = maxTimeLen;
        final int nameW = maxNameLen;

        int rowNumber = 1;
        for (String event : events) {
            if (event == null || event.trim().isEmpty()) {
                textFlow.getChildren().add(newline());
                continue;
            }

            String rowNumberPad = String.format("%-" + rowW + "s", "#" + rowNumber++);

            Matcher matcher = MODERATOR_EVENT_LINE_PATTERN.matcher(event);
            if (matcher.matches()) {
                String timestamp = String.format("%-" + timeW + "s", bracketedTime(matcher.group(1)));
                String name = matcher.group(2).trim();
                String paddedName = String.format("%-" + nameW + "s", name);
                String eventMessage = matcher.group(3).trim();

                Color nameColor;
                if (name.equalsIgnoreCase(reporterName)) {
                    nameColor = Color.LIGHTBLUE;
                } else if (name.equalsIgnoreCase(offenderName)) {
                    nameColor = Color.LIGHTCORAL;
                } else {
                    nameColor = Color.LIGHTYELLOW;
                }

                textFlow.getChildren().addAll(
                        styledText(rowNumberPad + " ", Color.DIMGRAY),
                        styledText(timestamp + "  ", Color.GRAY),
                        styledText(paddedName + "  ", nameColor),
                        styledText(eventMessage, Color.WHITE),
                        newline()
                );
            } else {
                textFlow.getChildren().add(styledText(rowNumberPad + " ", Color.DIMGRAY));
                appendHighlightedLine(textFlow, event, offenderName, reporterName);
                textFlow.getChildren().add(newline());
            }
        }
    }

    private static String bracketedTime(String timestamp) {
        return "[" + timestamp + "]";
    }

    // -------------------------------------------------------------------------
    // Painting brush stroke visualization
    // -------------------------------------------------------------------------

    private record PaintingBrushStroke(
            java.time.Duration time, String playerName, String adapterIdentifier, int shareId, List<float[]> points) {}

    private enum ReplayMapMarkerType { START_POSITION, ATTACK_ORDER, PING }

    private record ReplayMapMarker(java.time.Duration time, ReplayMapMarkerType type, String playerName, float x, float z,
                                   String label) {}

    private record ReplayTimelineEntry(java.time.Duration time, String category, String playerName, String details,
                                       ReplayMapMarker mapMarker) {}

    private record ReplayPlayerEntry(String name, boolean startRecorded, Color color, String team) {}

    private record ReplayTimelineData(List<ReplayMapMarker> mapMarkers, List<ReplayTimelineEntry> timelineEntries) {}

    private record ReplayDetailEntry(java.time.Duration time, String category, String playerName, String details) {}

    private record ReplayMapPreview(Image image, float width, float height) {
        private static ReplayMapPreview unavailable() {
            return new ReplayMapPreview(null, 0, 0);
        }
    }

    static String formatGameAge(OffsetDateTime playedAt, OffsetDateTime now) {
        if (playedAt == null) {
            return "time unavailable";
        }

        long minutes = Math.max(0, java.time.Duration.between(playedAt, now).toMinutes());
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        if (days < 14) {
            return days + "d ago";
        }
        if (days < 60) {
            return days / 7 + "w ago";
        }
        if (days < 730) {
            return days / 30 + "mo ago";
        }
        return days / 365 + "y ago";
    }

    private record MapDimensions(float width, float height) {}

    private static final Color[] PAINTING_PLAYER_COLORS = {
        Color.CORNFLOWERBLUE, Color.TOMATO,       Color.LIMEGREEN,    Color.GOLD,
        Color.ORANGE,         Color.MEDIUMPURPLE,  Color.CYAN,         Color.HOTPINK,
        Color.WHITE,          Color.SPRINGGREEN,   Color.DEEPSKYBLUE,  Color.CRIMSON,
        Color.ORCHID,         Color.TURQUOISE,     Color.YELLOWGREEN,  Color.KHAKI
    };

    private List<PaintingBrushStroke> extractPaintingStrokes(ReplayDataParser parser) {
        int tick = 0;
        int commandSource = -1;
        List<PaintingBrushStroke> strokes = new ArrayList<>();
        Map<Integer, Map<String, Object>> armies = parser.getArmies();

        for (Event event : parser.getEvents()) {
            switch (event) {
                case Event.Advance(int n) -> tick += n;
                case Event.SetCommandSource(int p) -> commandSource = p;
                case Event.LuaSimCallback(String func, LuaData.Table params, Event.CommandUnits cu)
                        when func.equals("SharePaintingBrushStroke") -> {

                    LuaData.Table shareable = params.getTable("ShareablePainting");
                    if (shareable == null) break;

                    String adapterId = shareable.getString("PaintingAdapterIdentifier");
                    LuaData.Table samples = shareable.getTable("Samples");
                    Integer shareId = shareable.getInteger("ShareId");
                    String peerName = shareable.getString("PeerName");

                    // Samples are interleaved triplets: x1,y1,z1, x2,y2,z2, ...
                    List<float[]> points = new ArrayList<>();
                    if (samples != null) {
                        int count = samples.value().size();
                        for (int i = 1; i + 2 <= count; i += 3) {
                            Float x = floatByLuaIndex(samples, i);
                            Float z = floatByLuaIndex(samples, i + 2);
                            if (x != null && z != null) points.add(new float[]{x, z});
                        }
                    }

                    String playerName = "Unknown";
                    if (peerName != null && !peerName.isEmpty()) {
                        playerName = peerName;
                    } else {
                        Map<String, Object> army = armies.get(commandSource);
                        if (army != null && army.get("PlayerName") instanceof String s) playerName = s;
                    }

                    strokes.add(new PaintingBrushStroke(
                            java.time.Duration.ofSeconds(tick / 10L),
                            playerName,
                            adapterId != null ? adapterId : "default",
                            shareId != null ? shareId : 0,
                            points
                    ));
                }
                default -> {}
            }
        }
        return strokes;
    }

    private ReplayTimelineData extractReplayTimeline(ReplayDataParser parser) {
        int tick = 0;
        int commandSource = -1;
        Map<Integer, Map<String, Object>> armies = parser.getArmies();
        Set<Integer> commanderStartRecorded = new HashSet<>();
        List<ReplayMapMarker> mapMarkers = new ArrayList<>();
        List<ReplayTimelineEntry> timelineEntries = new ArrayList<>();

        for (Event event : parser.getEvents()) {
            if (event instanceof Event.Advance advance) {
                tick += advance.ticksToAdvance();
                continue;
            }
            if (event instanceof Event.SetCommandSource source) {
                commandSource = source.playerIndex();
                continue;
            }

            java.time.Duration time = java.time.Duration.ofSeconds(tick / 10L);
            String playerName = playerNameForArmy(armies, commandSource);
            if (event instanceof Event.CommandSourceTerminated) {
                timelineEntries.add(new ReplayTimelineEntry(time, "Player left", playerName, "Command source terminated", null));
            } else if (event instanceof Event.CreateUnit createdUnit
                    && createdUnit.playerIndex() >= 0
                    && time.compareTo(java.time.Duration.ofMinutes(1)) <= 0
                    && commanderStartRecorded.add(createdUnit.playerIndex())) {
                String commanderPlayer = playerNameForArmy(armies, createdUnit.playerIndex());
                mapMarkers.add(new ReplayMapMarker(time, ReplayMapMarkerType.START_POSITION, commanderPlayer,
                        createdUnit.px(), createdUnit.pz(), "Start position — " + commanderPlayer));
                timelineEntries.add(new ReplayTimelineEntry(time, "Start position", commanderPlayer,
                        "Initial unit created: " + createdUnit.blueprintId(), mapMarkers.getLast()));
            } else if (event instanceof Event.IssueCommand issueCommand
                    && isAttackCommand(issueCommand.commandData().commandType())
                    && issueCommand.commandData().commandTarget() instanceof Event.CommandTarget.Position target) {
                mapMarkers.add(new ReplayMapMarker(time, ReplayMapMarkerType.ATTACK_ORDER, playerName,
                        target.px(), target.pz(), "Attack order — " + playerName));
                timelineEntries.add(new ReplayTimelineEntry(time, "Attack order", playerName,
                        "Attack target marked on map", mapMarkers.getLast()));
            } else if (event instanceof Event.LuaSimCallback callback
                    && "SpawnPing".equals(callback.func())
                    && callback.parametersLua() instanceof LuaData.Table parameters) {
                ReplayMapMarker pingMarker = extractPingMarker(time, playerName, parameters);
                if (pingMarker != null) {
                    mapMarkers.add(pingMarker);
                    timelineEntries.add(new ReplayTimelineEntry(time,
                            parameters.getBool("Marker") == Boolean.TRUE ? "Text marker" : "Ping",
                            playerName, pingMarker.label(), pingMarker));
                }
            }
        }

        for (ModeratorEvent event : parser.getModeratorEvents()) {
            if (isMapPingEvent(event.message())) {
                continue;
            }
            String category = event.message().contains("Created a ping of type 'Attack'") ? "Attack ping"
                    : event.message().contains("Created a marker with the text") ? "Text marker"
                    : "Moderator event";
            timelineEntries.add(new ReplayTimelineEntry(event.time(), category,
                    event.playerNameFromCommandSource() == null ? "Unknown" : event.playerNameFromCommandSource(),
                    event.message(), null));
        }
        for (ChatMessage message : parser.getChatMessages()) {
            if (isDuplicateTextMarkerChatMessage(message, parser.getModeratorEvents())) {
                continue;
            }
            if (!localPreferences.getTabReports().isShowNotifyChatMessages()
                    && isNotifyChatMessage(message.getMessage())) {
                continue;
            }
            timelineEntries.add(new ReplayTimelineEntry(message.getTime(), "Chat", message.getSender(), message.getMessage(), null));
        }

        timelineEntries.sort(Comparator.comparing(ReplayTimelineEntry::time));
        return new ReplayTimelineData(mapMarkers, timelineEntries);
    }

    private List<ReplayDetailEntry> extractReplayDetailEntries(ReplayDataParser parser) {
        final int maximumEntries = 25_000;
        int tick = 0;
        int commandSource = -1;
        List<ReplayDetailEntry> entries = new ArrayList<>();
        Map<Integer, Map<String, Object>> armies = parser.getArmies();
        for (Event event : parser.getEvents()) {
            if (event instanceof Event.Advance advance) {
                tick += advance.ticksToAdvance();
                continue;
            }
            if (event instanceof Event.SetCommandSource source) {
                commandSource = source.playerIndex();
                continue;
            }
            if (entries.size() >= maximumEntries) {
                break;
            }
            java.time.Duration time = java.time.Duration.ofSeconds(tick / 10L);
            String playerName = playerNameForArmy(armies, commandSource);
            if (event instanceof Event.IssueCommand command) {
                entries.add(new ReplayDetailEntry(time, "Command", playerName,
                        command.commandData().commandType() + " — " + command.commandUnits().count() + " selected; "
                                + describeCommandTarget(command.commandData().commandTarget())));
            } else if (event instanceof Event.IssueFactoryCommand command) {
                entries.add(new ReplayDetailEntry(time, "Factory command", playerName,
                        command.commandData().commandType() + " — " + command.commandUnits().count() + " selected; "
                                + describeCommandTarget(command.commandData().commandTarget())));
            } else if (event instanceof Event.CreateUnit unit) {
                entries.add(new ReplayDetailEntry(time, "Unit created", playerNameForArmy(armies, unit.playerIndex()),
                        shortBlueprintName(unit.blueprintId()) + " at " + formatReplayPosition(unit.px(), unit.pz())));
            } else if (event instanceof Event.DestroyEntity destroyed) {
                entries.add(new ReplayDetailEntry(time, "Entity destroyed", playerName,
                        "Entity " + destroyed.entityId()));
            } else if (event instanceof Event.WarpEntity warped) {
                entries.add(new ReplayDetailEntry(time, "Entity warped", playerName,
                        "Entity " + warped.entityId() + " to " + formatReplayPosition(warped.px(), warped.pz())));
            } else if (event instanceof Event.CommandSourceTerminated) {
                entries.add(new ReplayDetailEntry(time, "Player left", playerName, "Command source terminated"));
            } else if (event instanceof Event.RequestPause) {
                entries.add(new ReplayDetailEntry(time, "Pause", playerName, "Pause requested"));
            } else if (event instanceof Event.RequestResume) {
                entries.add(new ReplayDetailEntry(time, "Resume", playerName, "Resume requested"));
            } else if (event instanceof Event.EndGame) {
                entries.add(new ReplayDetailEntry(time, "Game ended", playerName, "End game event"));
            }
        }
        return entries;
    }

    private static String describeCommandTarget(Event.CommandTarget target) {
        if (target instanceof Event.CommandTarget.Position position) {
            return "target " + formatReplayPosition(position.px(), position.pz());
        }
        if (target instanceof Event.CommandTarget.Entity entity) {
            return "target entity " + entity.unitId();
        }
        return "no explicit target";
    }

    private static String formatReplayPosition(float x, float z) {
        return String.format(Locale.ROOT, "(%.0f, %.0f)", x, z);
    }

    private static String shortBlueprintName(String blueprintId) {
        if (blueprintId == null || blueprintId.isBlank()) {
            return "Unknown blueprint";
        }
        int separator = Math.max(blueprintId.lastIndexOf('/'), blueprintId.lastIndexOf('\\'));
        return separator >= 0 ? blueprintId.substring(separator + 1) : blueprintId;
    }

    private static boolean isAttackCommand(EventCommandType commandType) {
        return commandType == EventCommandType.ATTACK || commandType == EventCommandType.FORM_ATTACK;
    }

    private static boolean isCommanderBlueprint(String blueprintId) {
        if (blueprintId == null) return false;
        String normalized = blueprintId.toLowerCase(Locale.ROOT);
        return normalized.contains("uel0001") || normalized.contains("ual0001") || normalized.contains("url0001")
                || normalized.contains("xsl0001") || normalized.contains("xnl0001");
    }

    private static boolean isDuplicateTextMarkerChatMessage(ChatMessage chatMessage, List<ModeratorEvent> moderatorEvents) {
        return moderatorEvents.stream()
                .filter(event -> event.message() != null && event.message().contains("Created a marker with the text"))
                .filter(event -> event.time().equals(chatMessage.getTime()))
                .anyMatch(event -> event.playerNameFromCommandSource() == null
                        || chatMessage.getSender() == null
                        || event.playerNameFromCommandSource().equalsIgnoreCase(chatMessage.getSender()));
    }

    private ReplayMapMarker extractPingMarker(java.time.Duration time, String playerName, LuaData.Table parameters) {
        LuaData.Table location = parameters.getTable("Location");
        if (location == null) {
            return null;
        }
        Float x = floatByLuaIndex(location, 1);
        Float z = floatByLuaIndex(location, 3);
        if (x == null || z == null) {
            return null;
        }
        boolean textMarker = parameters.getBool("Marker") == Boolean.TRUE;
        String type = textMarker ? parameters.getString("Name") : parameters.getString("Type");
        String label = (textMarker ? "Text marker" : "Ping") + (type == null ? "" : ": " + type)
                + " — " + playerName;
        return new ReplayMapMarker(time, ReplayMapMarkerType.PING, playerName, x, z, label);
    }

    private static boolean isMapPingEvent(String message) {
        return message != null && (message.contains("Created a ping of type")
                || message.contains("Created a marker with the text"));
    }

    private static List<ReplayPlayerEntry> extractReplayPlayers(ReplayDataParser parser, List<ReplayMapMarker> markers,
                                                                 Map<String, Color> playerColors) {
        Set<String> playersWithRecordedStart = markers.stream()
                .filter(marker -> marker.type() == ReplayMapMarkerType.START_POSITION)
                .map(ReplayMapMarker::playerName)
                .collect(Collectors.toSet());
        List<Integer> rawTeams = parser.getArmies().values().stream()
                .map(army -> army.get("Team"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .filter(team -> team >= 0)
                .distinct()
                .sorted()
                .toList();
        Map<Integer, String> displayTeams = new LinkedHashMap<>();
        for (int index = 0; index < rawTeams.size(); index++) {
            displayTeams.put(rawTeams.get(index), "Team " + (index + 1));
        }
        return parser.getArmies().values().stream()
                .filter(army -> army.get("PlayerName") instanceof String player && !"civilian".equalsIgnoreCase(player))
                .sorted(Comparator.<Map<String, Object>, String>comparing(army -> teamForArmy(army, displayTeams))
                        .thenComparing(army -> (String) army.get("PlayerName"), String.CASE_INSENSITIVE_ORDER))
                .map(army -> {
                    String player = (String) army.get("PlayerName");
                    return new ReplayPlayerEntry(player, playersWithRecordedStart.contains(player),
                            playerColors.getOrDefault(player, Color.WHITE), teamForArmy(army, displayTeams));
                })
                .collect(Collectors.toList());
    }

    private static String teamForArmy(Map<String, Object> army, Map<Integer, String> displayTeams) {
        Object team = army.get("Team");
        if (team instanceof Number number && number.intValue() >= 0) {
            return displayTeams.getOrDefault(number.intValue(), "Team " + number.intValue());
        }
        if (team instanceof String name && !name.isBlank()) {
            return name;
        }
        return "Spectator";
    }

    private static Map<String, Color> extractReplayPlayerColors(ReplayDataParser parser) {
        Map<String, Color> colors = new LinkedHashMap<>();
        parser.getArmies().values().stream()
                .map(army -> army.get("PlayerName"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(player -> colors.computeIfAbsent(player,
                        ignored -> PAINTING_PLAYER_COLORS[colors.size() % PAINTING_PLAYER_COLORS.length]));
        return colors;
    }

    private void renderReplayPlayerLegend(List<ReplayPlayerEntry> players) {
        List<javafx.scene.Node> legendRows = new ArrayList<>();
        players.stream().collect(Collectors.groupingBy(ReplayPlayerEntry::team, LinkedHashMap::new, Collectors.toList()))
                .forEach((team, teammates) -> {
                    Label teamLabel = new Label(team);
                    teamLabel.setStyle("-fx-font-weight: bold;");
                    legendRows.add(teamLabel);
                    teammates.forEach(player -> {
                        Circle color = new Circle(6, player.color());
                        Label text = new Label(player.name() + (player.startRecorded() ? " — start position recorded" : ""));
                        text.setStyle("-fx-text-fill: " + colorCss(player.color()) + ";");
                        text.setWrapText(true);
                        text.setMaxWidth(Double.MAX_VALUE);
                        HBox row = new HBox(7, color, text);
                        HBox.setHgrow(text, Priority.ALWAYS);
                        legendRows.add(row);
                    });
                });
        replayPlayersLegend.getChildren().setAll(legendRows);
    }

    private ReplayMapPreview loadMapPreview(GameFX game, ReplayDataParser replayDataParser) {
        MapVersionFX mapVersion = game == null ? null : game.getMapVersion();
        if (mapVersion != null && mapVersion.getThumbnailUrlLarge() != null) {
            Image preview = new Image(mapVersion.getThumbnailUrlLarge().toExternalForm(), true);
            preview.progressProperty().addListener((observable, oldProgress, progress) -> {
                if (progress.doubleValue() >= 1 && !preview.isError()) {
                    Platform.runLater(() -> {
                        java.time.Duration currentTime = java.time.Duration.ofMillis(
                                Math.round(paintingTimeSlider.getValue() * 1_000));
                        renderReplayMap(visiblePaintingStrokes(currentTime), currentTime);
                    });
                }
            });
            return new ReplayMapPreview(preview, mapVersion.getWidth(), mapVersion.getHeight());
        }

        Optional<Path> installedMapDirectory = resolveInstalledReplayMapDirectory(replayDataParser.getMap());
        if (installedMapDirectory.isPresent()) {
            return loadInstalledMapPreview(installedMapDirectory.get(), replayDataParser.getMap());
        }
        return generateNeroxisMapPreview(replayDataParser.getMap()).orElseGet(() -> {
            log.info("No local map directory or generator preview available for replay map '{}'", replayDataParser.getMap());
            return ReplayMapPreview.unavailable();
        });
    }

    private ReplayMapPreview loadInstalledMapPreview(Path installedMapDirectory, String replayMap) {
        try {
            MapDimensions dimensions = readMapDimensions(installedMapDirectory);
            Optional<Path> existingPreview = findInstalledMapPreview(installedMapDirectory);
            if (existingPreview.isPresent()) {
                return new ReplayMapPreview(new Image(existingPreview.get().toUri().toString(), false),
                        dimensions.width(), dimensions.height());
            }
            BufferedImage preview = PreviewGenerator.generatePreview(installedMapDirectory, 1024, 1024);
            return new ReplayMapPreview(SwingFXUtils.toFXImage(preview, null), dimensions.width(), dimensions.height());
        } catch (IOException | RuntimeException e) {
            log.warn("Could not render installed replay map '{}' from {}", replayMap, installedMapDirectory, e);
            return ReplayMapPreview.unavailable();
        }
    }

    private Optional<ReplayMapPreview> generateNeroxisMapPreview(String replayMap) {
        Optional<String> mapName = generatedMapName(replayMap);
        if (mapName.isEmpty()) {
            return Optional.empty();
        }
        Optional<Path> generatorJar = resolveNeroxisGeneratorJar(mapName.get());
        Optional<Path> javaExecutable = resolveFafJavaExecutable();
        if (generatorJar.isEmpty() || javaExecutable.isEmpty()) {
            log.warn("Cannot generate replay map '{}': FAF generator or its Java runtime is unavailable", mapName.get());
            return Optional.empty();
        }

        Path cacheDirectory = ApplicationPaths.resolveConfigurationDirectory().resolve("generated-map-previews");
        Path cachedPreview = cacheDirectory.resolve(mapName.get() + "_preview.png");
        Path cachedDimensions = cacheDirectory.resolve(mapName.get() + ".dimensions");
        try {
            Files.createDirectories(cacheDirectory);
            if (Files.isRegularFile(cachedPreview) && Files.isRegularFile(cachedDimensions)) {
                MapDimensions dimensions = readCachedMapDimensions(cachedDimensions);
                return Optional.of(new ReplayMapPreview(new Image(cachedPreview.toUri().toString(), false),
                        dimensions.width(), dimensions.height()));
            }

            Path generationDirectory = Files.createTempDirectory(cacheDirectory, "neroxis-");
            try {
                Process process = new ProcessBuilder(javaExecutable.get().toString(), "-jar", generatorJar.get().toString(),
                        "--map-name", mapName.get(), "--out-path", generationDirectory.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (!process.waitFor(2, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    log.warn("Timed out generating replay map '{}'", mapName.get());
                    return Optional.empty();
                }
                if (process.exitValue() != 0) {
                    log.warn("Map generator exited with {} for replay map '{}'", process.exitValue(), mapName.get());
                    return Optional.empty();
                }

                Path generatedMapDirectory = generationDirectory.resolve(mapName.get());
                Path generatedPreview = findInstalledMapPreview(generatedMapDirectory)
                        .orElseThrow(() -> new FileNotFoundException("Generated map preview is missing"));
                MapDimensions dimensions = readMapDimensions(generatedMapDirectory);
                Files.copy(generatedPreview, cachedPreview, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(cachedDimensions, dimensions.width() + "," + dimensions.height(), StandardCharsets.UTF_8);
                return Optional.of(new ReplayMapPreview(new Image(cachedPreview.toUri().toString(), false),
                        dimensions.width(), dimensions.height()));
            } finally {
                deleteGeneratedMapDirectory(generationDirectory);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Could not generate preview for replay map '{}'", mapName.get(), e);
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Could not generate preview for replay map '{}'", mapName.get(), e);
            return Optional.empty();
        }
    }

    private Optional<String> generatedMapName(String replayMap) {
        if (replayMap == null) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("(?i)/maps/(neroxis_map_generator_[^/]+)/").matcher(replayMap.replace('\\', '/'));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<Path> resolveNeroxisGeneratorJar(String mapName) {
        String[] segments = mapName.split("_");
        if (segments.length < 4) {
            return Optional.empty();
        }
        String programData = System.getenv("ProgramData");
        if (programData == null || programData.isBlank()) {
            return Optional.empty();
        }
        Path generator = Path.of(programData, "FAForever", "map_generator", "MapGenerator_" + segments[3] + ".jar");
        return Files.isRegularFile(generator) ? Optional.of(generator) : Optional.empty();
    }

    private Optional<Path> resolveFafJavaExecutable() {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null || programFiles.isBlank()) {
            return Optional.empty();
        }
        Path javaExecutable = Path.of(programFiles, "FAF Client", "jre", "bin", "java.exe");
        return Files.isRegularFile(javaExecutable) ? Optional.of(javaExecutable) : Optional.empty();
    }

    private MapDimensions readCachedMapDimensions(Path dimensionsFile) throws IOException {
        String[] values = Files.readString(dimensionsFile, StandardCharsets.UTF_8).split(",");
        if (values.length != 2) {
            throw new IOException("Invalid cached map dimensions");
        }
        return new MapDimensions(Float.parseFloat(values[0]), Float.parseFloat(values[1]));
    }

    private void deleteGeneratedMapDirectory(Path generationDirectory) {
        try (var paths = Files.walk(generationDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Could not remove temporary map-generation file {}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("Could not remove temporary map-generation directory {}", generationDirectory, e);
        }
    }

    private Optional<Path> resolveInstalledReplayMapDirectory(String replayMap) {
        if (replayMap == null || replayMap.isBlank()) {
            return Optional.empty();
        }
        String normalizedMap = replayMap.replace('\\', '/').toLowerCase(Locale.ROOT);
        List<String> pathParts = Arrays.stream(normalizedMap.split("/"))
                .filter(part -> !part.isBlank() && !part.endsWith(".scmap"))
                .toList();
        for (Path mapsDirectory : localFafMapsDirectories()) {
            for (String pathPart : pathParts) {
                Path candidate = mapsDirectory.resolve(pathPart);
                if (containsScmap(candidate)) {
                    return Optional.of(candidate);
                }
            }
            try (var directories = Files.list(mapsDirectory)) {
                Optional<Path> matchingDirectory = directories
                        .filter(Files::isDirectory)
                        .filter(directory -> normalizedMap.contains("/" + directory.getFileName().toString().toLowerCase(Locale.ROOT) + "/"))
                        .filter(this::containsScmap)
                        .findFirst();
                if (matchingDirectory.isPresent()) {
                    return matchingDirectory;
                }
            } catch (IOException e) {
                log.debug("Could not inspect FAF maps directory {}", mapsDirectory, e);
            }
        }
        return Optional.empty();
    }

    private List<Path> localFafMapsDirectories() {
        List<Path> directories = new ArrayList<>();
        String oneDrive = System.getenv("OneDrive");
        if (oneDrive != null && !oneDrive.isBlank()) {
            directories.add(Path.of(oneDrive, "Documents", "My Games", "Gas Powered Games",
                    "Supreme Commander Forged Alliance", "maps"));
        }
        directories.add(Path.of(System.getProperty("user.home"), "Documents", "My Games", "Gas Powered Games",
                "Supreme Commander Forged Alliance", "maps"));
        return directories.stream().filter(Files::isDirectory).distinct().toList();
    }

    private boolean containsScmap(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".scmap"));
        } catch (IOException e) {
            return false;
        }
    }

    private Optional<Path> findInstalledMapPreview(Path mapDirectory) throws IOException {
        try (var files = Files.list(mapDirectory)) {
            return files
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_preview.png"))
                    .findFirst();
        }
    }

    private MapDimensions readMapDimensions(Path mapDirectory) throws IOException {
        Path scmapFile;
        try (var files = Files.list(mapDirectory)) {
            scmapFile = files
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".scmap"))
                    .findFirst()
                    .orElseThrow(() -> new FileNotFoundException("No .scmap file in " + mapDirectory));
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(scmapFile))) {
            input.skipNBytes(16);
            float width = Float.intBitsToFloat(Integer.reverseBytes(input.readInt()));
            float height = Float.intBitsToFloat(Integer.reverseBytes(input.readInt()));
            return new MapDimensions(width, height);
        }
    }

    private static String playerNameForArmy(Map<Integer, Map<String, Object>> armies, int armyIndex) {
        Map<String, Object> army = armies.get(armyIndex);
        if (army != null && army.get("PlayerName") instanceof String playerName) {
            return playerName;
        }
        return armies.values().stream()
                .filter(candidate -> candidate.get("ArmyIndex") instanceof Number index
                        && (index.intValue() == armyIndex || index.intValue() - 1 == armyIndex))
                .map(candidate -> candidate.get("PlayerName"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse("Unknown");
    }

    /** LoadUtils stores numeric Lua keys as String.valueOf(float), e.g. "1.0", "2.0". */
    private Float floatByLuaIndex(LuaData.Table table, int index) {
        Float v = table.getFloat(String.valueOf((float) index));
        if (v != null) return v;
        return table.getFloat(String.valueOf(index));
    }

    private void renderReplayMap(List<PaintingBrushStroke> strokes, java.time.Duration limit) {
        GraphicsContext gc = paintingCanvas.getGraphicsContext2D();
        double w = paintingCanvas.getWidth();
        double h = paintingCanvas.getHeight();

        // Generated previews contain transparent margins. Paint an opaque base on every frame so a
        // previous replay can never show through a later replay's preview.
        gc.setFill(Color.rgb(42, 48, 54));
        gc.fillRect(0, 0, w, h);
        if (replayMapPreview != null && !replayMapPreview.isError() && replayMapPreview.getProgress() >= 1) {
            gc.drawImage(replayMapPreview, 0, 0, w, h);
        } else {
            gc.setFill(Color.LIGHTGRAY);
            gc.setFont(javafx.scene.text.Font.font(15));
            gc.fillText("Map preview unavailable", 24, h / 2 - 10);
            gc.setFont(javafx.scene.text.Font.font(12));
            gc.fillText("The map is not installed locally, so its exact generated terrain cannot be rendered.",
                    24, h / 2 + 14);
        }

        if (currentPaintingStrokes.isEmpty() && currentReplayMapMarkers.isEmpty()) {
            gc.setFill(Color.GRAY);
            gc.setFont(javafx.scene.text.Font.font(14));
            gc.fillText("No replay map coordinates found in this replay.", 20, h / 2);
            return;
        }

        // Use globally-computed bounds so scale stays stable when filtering by time
        float range = Math.max(Math.max(paintingMaxX - paintingMinX, paintingMaxZ - paintingMinZ), 1f);
        double padding = 24;
        double scale = (Math.min(w, h) - 2 * padding) / range;

        // Assign one color per player using all strokes for a stable legend
        Map<String, Color> playerColors = new LinkedHashMap<>(currentReplayPlayerColors);
        for (PaintingBrushStroke s : currentPaintingStrokes) {
            playerColors.computeIfAbsent(s.playerName(),
                    name -> PAINTING_PLAYER_COLORS[playerColors.size() % PAINTING_PLAYER_COLORS.length]);
        }
        for (ReplayMapMarker marker : currentReplayMapMarkers) {
            playerColors.computeIfAbsent(marker.playerName(),
                    name -> PAINTING_PLAYER_COLORS[playerColors.size() % PAINTING_PLAYER_COLORS.length]);
        }

        // Draw the visible (filtered) strokes
        gc.setLineWidth(2.0);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        for (PaintingBrushStroke stroke : strokes) {
            List<float[]> pts = stroke.points();
            if (pts.isEmpty()) continue;
            Color c = playerColors.getOrDefault(stroke.playerName(), Color.WHITE);
            if (pts.size() == 1) {
                gc.setFill(c);
                double cx = padding + (pts.get(0)[0] - paintingMinX) * scale;
                double cy = padding + (pts.get(0)[1] - paintingMinZ) * scale;
                gc.fillOval(cx - 3, cy - 3, 6, 6);
                continue;
            }
            gc.setStroke(c);
            gc.beginPath();
            gc.moveTo(padding + (pts.get(0)[0] - paintingMinX) * scale,
                      padding + (pts.get(0)[1] - paintingMinZ) * scale);
            for (int i = 1; i < pts.size(); i++) {
                gc.lineTo(padding + (pts.get(i)[0] - paintingMinX) * scale,
                          padding + (pts.get(i)[1] - paintingMinZ) * scale);
            }
            gc.stroke();
        }

        for (ReplayMapMarker marker : currentReplayMapMarkers) {
            boolean visible = marker.type() == ReplayMapMarkerType.START_POSITION
                    || Math.abs(marker.time().minus(limit).toMillis()) <= replayMarkerFadeMillis();
            if (!visible) continue;
            double x = padding + (marker.x() - paintingMinX) * scale;
            double y = padding + (marker.z() - paintingMinZ) * scale;
            if (marker.type() == ReplayMapMarkerType.START_POSITION) {
                Color color = playerColors.getOrDefault(marker.playerName(), Color.WHITE);
                gc.setFill(color);
                gc.fillOval(x - 5, y - 5, 10, 10);
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(12));
                gc.fillText(marker.playerName(), x + 8, y - 8);
            } else {
                Color markerColor = marker.type() == ReplayMapMarkerType.PING ? Color.GOLD : Color.ORANGERED;
                gc.setStroke(markerColor);
                gc.setLineWidth(2.5);
                gc.strokeOval(x - 8, y - 8, 16, 16);
                gc.strokeLine(x - 12, y, x + 12, y);
                gc.strokeLine(x, y - 12, x, y + 12);
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(12));
                gc.fillText(marker.label(), x + 10, y - 10);
            }
        }
    }

    private void clearReplayMapTimeline() {
        stopReplayPlayback();
        currentPaintingStrokes = List.of();
        currentReplayMapMarkers = List.of();
        currentReplayTimeline = List.of();
        currentReplayPlayerColors = Map.of();
        replayMapPreview = null;
        replayMapWidth = 0;
        replayMapHeight = 0;
        replayPlayersLegend.getChildren().clear();
        replayTimelineListView.getItems().clear();
        replayDetailListView.getItems().clear();
        replayDetailSummaryLabel.setText("Load a replay to inspect recorded commands and unit events.");
        paintingTimeSlider.setMin(0);
        paintingTimeSlider.setMax(1);
        paintingTimeSlider.setValue(0);
        renderReplayMap(List.of(), java.time.Duration.ZERO);
    }

    private static String colorCss(Color color) {
        return String.format("#%02X%02X%02X", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    private void renderReplayTimeline() {
        replayTimelineListView.setItems(FXCollections.observableArrayList(currentReplayTimeline));
        currentReplayTimelineIndex = -1;
        synchronizeReplayTimeline(java.time.Duration.ofMillis(Math.round(paintingTimeSlider.getValue() * 1_000)));
    }

    private void renderReplayDetailEntries(List<ReplayDetailEntry> entries) {
        replayDetailListView.setItems(FXCollections.observableArrayList(entries));
        replayDetailSummaryLabel.setText(entries.size() + " recorded command and lifecycle event(s). "
                + "These are replay events, not continuous mouse or unit movement.");
    }

    private void synchronizeReplayTimeline(java.time.Duration currentTime) {
        int matchingIndex = -1;
        for (int index = 0; index < currentReplayTimeline.size(); index++) {
            if (currentReplayTimeline.get(index).time().compareTo(currentTime) <= 0) {
                matchingIndex = index;
            } else {
                break;
            }
        }
        if (matchingIndex == currentReplayTimelineIndex) {
            return;
        }
        currentReplayTimelineIndex = matchingIndex;
        synchronizingReplayTimelineSelection = true;
        try {
            if (matchingIndex < 0) {
                replayTimelineListView.getSelectionModel().clearSelection();
                replayTimelineListView.scrollTo(0);
                replayEventDetailLabel.setText("No recorded event yet at " + formatDurationHMS(currentTime.getSeconds()) + ".");
            } else {
                ReplayTimelineEntry entry = currentReplayTimeline.get(matchingIndex);
                replayTimelineListView.getSelectionModel().select(matchingIndex);
                replayTimelineListView.scrollTo(matchingIndex);
                replayEventDetailLabel.setText(entry.mapMarker() == null
                        ? "Current event: " + formatReplayTimelineEntry(entry)
                        : "Current event: " + formatReplayTimelineEntry(entry) + " — map marker shown.");
            }
        } finally {
            synchronizingReplayTimelineSelection = false;
        }
    }

    private static String formatReplayTimelineEntry(ReplayTimelineEntry entry) {
        return String.format("%s | %s | %s | %s", formatReplayTime(entry.time()),
                entry.category(), entry.playerName(), entry.details());
    }

    private static String formatReplayTime(java.time.Duration time) {
        long totalSeconds = time.getSeconds();
        return totalSeconds >= 3_600 ? formatDurationHMS(totalSeconds)
                : String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @FXML
    public void onReplayTimeEntered() {
        String value = replayTimeTextField.getText();
        try {
            String[] parts = value.trim().split(":");
            long seconds = parts.length == 3
                    ? Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2])
                    : parts.length == 2 ? Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])
                    : Long.parseLong(parts[0]);
            paintingTimeSlider.setValue(Math.max(paintingTimeSlider.getMin(),
                    Math.min(paintingTimeSlider.getMax(), seconds)));
        } catch (NumberFormatException ignored) {
            replayTimeTextField.setText(formatReplayTime(java.time.Duration.ofMillis(
                    Math.round(paintingTimeSlider.getValue() * 1_000))));
        }
    }

    @FXML
    public void onToggleReplayPlayback() {
        if (replayPlaybackTimeline.getStatus() == Animation.Status.RUNNING) {
            stopReplayPlayback();
        } else {
            replayPlaybackTimeline.play();
            replayPlayPauseButton.setText("Pause");
        }
    }

    private void advanceReplayPlayback() {
        double nextTime = paintingTimeSlider.getValue() + replayPlaybackSpeed() / 10d;
        if (nextTime >= paintingTimeSlider.getMax()) {
            paintingTimeSlider.setValue(paintingTimeSlider.getMax());
            stopReplayPlayback();
            return;
        }
        paintingTimeSlider.setValue(nextTime);
    }

    private void stopReplayPlayback() {
        if (replayPlaybackTimeline != null) {
            replayPlaybackTimeline.stop();
        }
        if (replayPlayPauseButton != null) {
            replayPlayPauseButton.setText("Play");
        }
    }

    private List<PaintingBrushStroke> visiblePaintingStrokes(java.time.Duration limit) {
        long earliestVisibleTime = limit.toMillis() - replayDrawingFadeMillis();
        return currentPaintingStrokes.stream()
                .filter(stroke -> stroke.time().toMillis() >= earliestVisibleTime
                        && stroke.time().compareTo(limit) <= 0)
                .collect(Collectors.toList());
    }

    private long replayDrawingFadeMillis() {
        return replayFadeSeconds(replayDrawingFadeSecondsTextField, 10) * 1_000L;
    }

    private long replayMarkerFadeMillis() {
        return replayFadeSeconds(replayMarkerFadeSecondsTextField, 10) * 1_000L;
    }

    private static long replayFadeSeconds(TextField field, long fallback) {
        try {
            return Math.max(0, Long.parseLong(field.getText().trim()));
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }

    private double replayPlaybackSpeed() {
        try {
            return Math.max(0.01, Double.parseDouble(replayPlaybackSpeedTextField.getText().trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String formatDurationHMS(long totalSeconds) {
        long hh = totalSeconds / 3600;
        long mm = (totalSeconds % 3600) / 60;
        long ss = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private String generateChatLog(ReplayDataParser replayDataParser) {
        return replayDataParser.getChatMessages().stream()
                .filter(message -> !isDuplicateTextMarkerChatMessage(message, replayDataParser.getModeratorEvents()))
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
        int playerIndexFromArmiesMap = -1;

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

        String chatLine;
        int lineNum = 0;

        while ((chatLine = bufReader.readLine()) != null) {
            if (!localPreferences.getTabReports().isShowNotifyChatMessages() && isNotifyChatMessage(chatLine)) {
                continue;
            }
            lineNum++;
            filteredChatLog.append("#").append(lineNum).append(" ").append(chatLine).append("\n");
        }

        return filteredChatLog.toString();
    }

    private static boolean isNotifyChatMessage(String message) {
        if (message == null) {
            return false;
        }
        return Pattern.compile("(?i)(?:can you )?give me (?:some )?(?:mass|energy)|give me one engineer|"
                        + "(?:→|to) notify:|(?:→|to) allies: sent (?:mass|energy)|sent (?:mass|energy) [0-9]|"
                        + "starting tech [123].*(?:upgrade|cancelled)|t[123] done!")
                .matcher(message)
                .find();
    }

    public void onCreateReportForumReporterButton() throws IOException {
        openForumSearch(createReportForumReporterButton.getId());
    }

    public void onCreateReportForumOffenderButton() throws IOException {
        openForumSearch(createReportForumOffenderButton.getId());
    }

    private void openForumSearch(String userId) throws IOException {
        if (userId == null || userId.isBlank()) {
            return;
        }

        String url = "https://forum.faforever.com/search?term=" + userId +
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

        // Use Desktop.browse() when available to avoid command injection risks
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                // Fallback to ProcessBuilder with strict validation for Windows-specific browser launch
                List<String> allowedBrowsers = Arrays.asList("chrome", "firefox", "Microsoft Edge", "edge", "msedge", "iexplore");
                String lowerBrowser = browser.toLowerCase();

                boolean isAllowed = allowedBrowsers.stream().anyMatch(allowed -> lowerBrowser.contains(allowed.toLowerCase()));

                if (!isAllowed) {
                    log.warn("Browser not in allow-list: {}", browser);
                    throw new SecurityException("Browser not permitted: " + browser);
                }

                ProcessBuilder pb;
                if ("Microsoft Edge".equalsIgnoreCase(browser)) {
                    pb = new ProcessBuilder("cmd", "/c", "start", "microsoft-edge:" + url);
                } else {
                    pb = new ProcessBuilder("cmd", "/c", "start", browser, url);
                }
                pb.start();
            }
        } catch (Exception e) {
            log.error("Failed to open forum URL in browser", e);
            throw new IOException("Failed to open forum URL", e);
        }
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
        textPane.setText(ModeratorStatisticsFormatter.formatStatistics(period, activities));
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
                .map(moderator -> new ModeratorStatisticsFormatter.ModeratorActivity(
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

        quotaResultModeratorsText.setText(ModeratorStatisticsFormatter.formatQuotas(lastWeekActivity, thisWeekActivity, quota));
    }

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

    public void refreshManualReplayLookupVisibility() {
        boolean visible = localPreferences.getTabReports().isEnableManualReplayLookupCheckBox();
        manualReplayLookupInfoText.setVisible(visible);
        manualReplayLookupInfoText.setManaged(visible);
        getModeratorEventsForReplayIdTextField.setVisible(visible);
        getModeratorEventsForReplayIdTextField.setManaged(visible);
        getModeratorEventsReplayIdButton.setVisible(visible);
        getModeratorEventsReplayIdButton.setManaged(visible);
    }

    private void refreshOpenLogsInNotepadPlusPlusVisibility() {
        boolean visible = localPreferences.getTabReports().isShowOpenLogsInNotepadPlusPlusButtonCheckBox();
        openLogsInNotepadPlusPlusButton.setVisible(visible);
        openLogsInNotepadPlusPlusButton.setManaged(visible);
        openLogsInNotepadPlusPlusButton.setDisable(
                copyChatLogButton.getId() == null || copyChatLogButton.getId().isBlank()
                        || copyModeratorEventsButton.getId() == null || copyModeratorEventsButton.getId().isBlank());
    }

    public void refreshReportPlayerRoleButtonText() {
        if (currentlySelectedItemNotNull == null) {
            copyReporterIdButton.setText(formatPlayerRoleButtonText("Reporter", "Reporter n/a"));
            styleReportAccountButton(copyReporterIdButton, "Reporter");
            copyReportedUserIdButton.setText(formatPlayerRoleButtonText("Offender", "Reported User n/a"));
            styleReportAccountButton(copyReportedUserIdButton, "Offender");
            refreshReportForumSearchRoleButtonStyles();
            return;
        }

        String reporterRepresentation = currentlySelectedItemNotNull.getReporter() == null
                ? "Reporter n/a"
                : currentlySelectedItemNotNull.getReporter().getRepresentation();
        copyReporterIdButton.setText(formatPlayerRoleButtonText("Reporter", reporterRepresentation));
        styleReportAccountButton(copyReporterIdButton, "Reporter");

        Collection<PlayerFX> reportedUsers = currentlySelectedItemNotNull.getReportedUsers();
        if (reportedUsers == null || reportedUsers.isEmpty()) {
            copyReportedUserIdButton.setText(formatPlayerRoleButtonText("Offender", "Reported User n/a"));
            styleReportAccountButton(copyReportedUserIdButton, "Offender");
        } else {
            for (PlayerFX offender : reportedUsers) {
                copyReportedUserIdButton.setText(formatPlayerRoleButtonText("Offender", offender.getRepresentation()));
                styleReportAccountButton(copyReportedUserIdButton, "Offender");
            }
        }
        refreshReportForumSearchRoleButtonStyles();
    }

    public void selectReportSettingsTab() {
        reportDetailsTabPane.getSelectionModel().select(reportSettingsTab);
    }

    private void refreshReportForumSearchRoleButtonStyles() {
        styleReportAccountButton(createReportForumReporterButton, "Reporter");
        styleReportAccountButton(createReportForumOffenderButton, "Offender");
    }

    private void styleReportAccountButton(Button button, String role) {
        button.setGraphic(null);
        button.setContentDisplay(ContentDisplay.TEXT_ONLY);
        button.setStyle(getReportAccountRoleTextStyle(role));
    }

    private String formatPlayerRoleButtonText(String role, String playerText) {
        if (!localPreferences.getTabReports().isShowReportPlayerRoleLabelsCheckBox()) {
            return playerText;
        }

        return role + ":\n" + playerText;
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
