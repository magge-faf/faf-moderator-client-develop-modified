package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.commons.replay.ChatMessage;
import com.faforever.commons.replay.ModeratorEvent;
import com.faforever.commons.replay.ReplayDataParser;
import com.faforever.commons.replay.ReplayMetadata;
import com.faforever.commons.replay.GameOption;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.GameFX;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;
import static java.text.MessageFormat.format;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModerationReportController implements Controller<Region> {
    public TableView mostReportedAccountsTableView;
    public TextArea moderatorStatisticsTextArea;
    private final ObjectMapper objectMapper;
    private final ModerationReportService moderationReportService;
    private final UiService uiService;
    private final FafApiCommunicationService communicationService;
    private final PlatformService platformService;
    private final ObservableList<PlayerFX> reportedPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    public CheckBox FilterLogCheckBox;
    public CheckBox AutomaticallyLoadChatLogCheckBox;
    public Button CreateReportButton;
    public Button UseTemplateWithoutReasonsButton;
    public TableView moderatorStatisticsTableView;
    public CheckBox reasonsCheckBox;
    public Button UseTemplateWithReasonsButton;
    public TextArea moderatorEventTextArea;
    public TextField getModeratorEventsForReplayIdTextField;
    public Button getModeratorEventsReplayIdButton;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;

    public Region root;
    public ChoiceBox<ChooseableStatus> statusChoiceBox;
    public TextField playerNameFilterTextField;
    public TableView<ModerationReportFX> reportTableView;
    public Button editReportButton;
    public TableView<PlayerFX> reportedPlayerTableView;
    public TextArea chatLogTextArea;
    public Button CopyReportedUserIDButton;
    public Button CopyChatLogButton;
    public Button CopyReportIDButton;
    public Button CopyGameIDButton;
    public Button StartReplayButton;

    private FilteredList<ModerationReportFX> filteredItemList;
    private ObservableMap<Integer, ModerationReportFX> itemMap;
    private ObservableList<ModerationReportFX> itemList;
    private ModerationReportFX currentlySelectedItemNotNull;

    private boolean isLoading = false;

    @Override
    public SplitPane getRoot() {
        return (SplitPane) root;
    }

    public void onCopyReportedUserID() {
        setSysClipboardText(CopyReportedUserIDButton.getId());
    }

    public void onCopyChatLog() {
        setSysClipboardText(CopyChatLogButton.getId());
    }

    public void onCopyReportID() {
        setSysClipboardText(CopyReportIDButton.getId() + ",");
    }

    public void onCopyGameID() {
        setSysClipboardText(CopyGameIDButton.getId());
    }

    public void onCreateReportButton() throws IOException {
        String reportedUserId = CreateReportButton.getId();
        String url = "https://forum.faforever.com/search?term=" + reportedUserId + "&in=titles";
        String cmd = "cmd /c start chrome " + url;
        Runtime.getRuntime().exec(cmd);
    }

    public void onStartReplay() throws IOException, InterruptedException {
        String replayId = StartReplayButton.getId();
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
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    public void onUseTemplateWithoutReasonsButton() {
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

            removeTrailingComma(new StringBuilder(selectedIds));
            removeTrailingComma(new StringBuilder(selectedGameIds));

            String result;
            result = selectedIds + "\n\n" + "DAY_NUMBER day ban - ReplayID " + selectedGameIds + " - SOME_REASON";
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(result);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);

            UseTemplateWithoutReasonsButton.setText("Copied");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> UseTemplateWithoutReasonsButton.setText("Use template"));
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

            removeTrailingComma(new StringBuilder(selectedIds));
            removeTrailingComma(new StringBuilder(selectedGameIds));

            Stage stage = new Stage();
            final String selectedReasons = "";
            stage.setTitle("Select reasons:");

            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);
            gridPane.setPadding(new Insets(120, 120, 120, 120));

            File file = new File(CONFIGURATION_FOLDER + File.separator + "templateReasonsCheckBox.txt");
            List<String> checkBoxLabels = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    checkBoxLabels.add(line);
                }
            } catch (IOException e) {
                log.debug(String.valueOf(e));
            }

            List<CheckBox> checkBoxes = checkBoxLabels.stream().map(CheckBox::new).toList();

            for (int i = 0; i < checkBoxes.size(); i++) {
                gridPane.add(checkBoxes.get(i), 0, i);
            }

            Button okButton = new Button("OK");
            gridPane.add(okButton, 0, checkBoxLabels.size());

            Scene scene = new Scene(gridPane);
            stage.setScene(scene);
            stage.show();

            final String[] updatedReasons = {selectedReasons};
            okButton.setOnAction(event -> {
                for (CheckBox checkBox : checkBoxes) {
                    if (checkBox.isSelected()) {
                        updatedReasons[0] += checkBox.getText() + ", ";
                    }
                }

                String updatedReasonsString = Arrays.toString(updatedReasons);
                updatedReasonsString = updatedReasonsString.substring(1, updatedReasonsString.length() - 1); // Remove brackets
                updatedReasonsString = updatedReasonsString.substring(0, updatedReasonsString.length() - 2); // Remove latest two char

                String result;
                result = selectedIds + "\n\n" + "DAY_NUMBER day ban - ReplayID " + selectedGameIds + " - " + updatedReasonsString;

                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(result);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);
                stage.close();
            });

            UseTemplateWithReasonsButton.setText("Copied");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> UseTemplateWithReasonsButton.setText("Use template, with reasons"));
                }
            }, 750);
        } catch (NullPointerException e) {
            log.debug(String.valueOf(e));
        }
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
                            Long offenderTotalReportCountCompleted = offendersCompletedReports.getOrDefault(offenderUsername, 0L); // Get total report count for this offender
                            Long offenderTotalReportCountDiscarded = offendersDiscardedReports.getOrDefault(offenderUsername, 0L); // Get total report count for this offender
                            Long offenderTotalReportCountProcessing = offendersProcessingReports.getOrDefault(offenderUsername, 0L); // Get total report count for this offender

                            Optional<OffsetDateTime> maxCreateTime = reports.stream()
                                    .filter(report -> report.getReportedUsers().stream()
                                            .anyMatch(user -> user.getRepresentation().equals(offenderUsername)))
                                    .map(ModerationReportFX::getCreateTime)
                                    .max(Comparator.naturalOrder());

                            if (maxCreateTime.isPresent()) {
                                LocalDateTime lastReported = maxCreateTime.get().toLocalDateTime();
                                return new Offender(offenderUsername, offenderReportCount,
                                        offenderTotalReportCountCompleted,
                                        offenderTotalReportCountDiscarded,
                                        offenderTotalReportCountProcessing,
                                        lastReported); // Pass total report count for this offender
                            } else {
                                log.debug("MaxCreateTime is not present.");
                                return null;
                            }
                        }).filter(Objects::nonNull)
                        .collect(Collectors.toList());
                Platform.runLater(() -> mostReportedAccountsTableView.setItems(FXCollections.observableArrayList(offenders)));

                mostReportedAccountsTableView.setOnKeyPressed(event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.C) {
                        Offender selectedOffender = (Offender) mostReportedAccountsTableView.getSelectionModel().getSelectedItem();
                        if (selectedOffender != null) {
                            StringSelection stringSelection = new StringSelection(selectedOffender.getPlayer());
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
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

        playerFX.setLogin("TemporaryTestUser");
        fakeReport.setReporter(playerFX);
        gameFX.setId(replayID);
        fakeReport.setGame(gameFX);

        showChatLog(fakeReport);
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
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
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
                    moderatorStatisticsTextArea.setText(sb.toString());
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    public static class Offender {
        private final StringProperty player;
        private final LongProperty currentOffenseCount;
        private final LongProperty totalOffenseCountCompleted;
        private final LongProperty totalOffenseCountDiscarded;
        private final LongProperty totalOffenseCountProcessing;
        @Getter
        private LocalDateTime lastReported;

        public Offender(String player,
                        long offenseCount,
                        long totalOffenseCountCompleted,
                        long totalOffenseCountDiscarded,
                        long totalOffenseCountProcessing,
                        LocalDateTime lastReported) {

            this.player = new SimpleStringProperty(player);
            this.currentOffenseCount = new SimpleLongProperty(offenseCount);
            this.totalOffenseCountCompleted = new SimpleLongProperty(totalOffenseCountCompleted);
            this.totalOffenseCountProcessing = new SimpleLongProperty(totalOffenseCountProcessing);
            this.totalOffenseCountDiscarded = new SimpleLongProperty(totalOffenseCountDiscarded);
            this.lastReported = lastReported;
        }
        public long getTotalOffenseCountCompleted() {
            return totalOffenseCountCompleted.get();
        }

        public LongProperty totalOffenseCountCompletedProperty() {
            return totalOffenseCountCompleted;
        }

        public long getTotalOffenseCountProcessing() {
            return totalOffenseCountProcessing.get();
        }

        public LongProperty totalOffenseCountProcessingroperty() {
            return totalOffenseCountProcessing;
        }

        public long getTotalOffenseCountDiscarded() {
            return totalOffenseCountDiscarded.get();
        }

        public LongProperty totalOffenseCountDiscardedgroperty() {
            return totalOffenseCountDiscarded;
        }

        public void lastReported(LocalDateTime lastReported) {
            this.lastReported = lastReported;
        }

        public String getPlayer() {
            return player.get();
        }

        public StringProperty playerProperty() {
            return player;
        }

        public long getCurrentOffenseCount() {
            return currentOffenseCount.get();
        }

        public LongProperty currentOffenseCountProperty() {
            return currentOffenseCount;
        }

    }

    @FXML
    public void initialize() {
        mostReportedAccountsTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                ModerationReportController.Offender selectedAccount = (ModerationReportController.Offender) newValue;
                String selectedAccountString = String.valueOf(selectedAccount.getPlayer());
                int startIndex = 0;
                int endIndex = selectedAccountString.indexOf(" [id");
                String accountName = selectedAccountString.substring(startIndex, endIndex);
                playerNameFilterTextField.setText(accountName);
            } catch (ClassCastException e) {
                // Do nothing if no account is selected in table
            }
        });
        statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        statusChoiceBox.getSelectionModel().select(ChooseableStatus.AWAITING_PROCESSING); // Set Default Selection
        editReportButton.disableProperty().bind(reportTableView.getSelectionModel().selectedItemProperty().isNull());
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

        filteredItemList = new FilteredList<>(itemList);

        renewFilter();
        SortedList<ModerationReportFX> sortedItemList = new SortedList<>(filteredItemList);
        sortedItemList.comparatorProperty().bind(reportTableView.comparatorProperty());
        ViewHelper.buildModerationReportTableView(reportTableView, sortedItemList, this::showChatLog);
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener(observable -> renewFilter());
        playerNameFilterTextField.textProperty().addListener(observable -> renewFilter());
        reportTableView.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    try {
                        reportedPlayersOfCurrentlySelectedReport.setAll(newValue.getReportedUsers());
                        currentlySelectedItemNotNull = newValue;
                        CopyReportIDButton.setId(newValue.getId());
                        CopyReportIDButton.setText("Report ID: " + newValue.getId());
                        if (newValue.getGame() != null) {
                            CopyGameIDButton.setId(newValue.getGame().getId());
                            CopyGameIDButton.setText("Game ID: " + newValue.getGame().getId());

                            StartReplayButton.setId(CopyGameIDButton.getId());
                            StartReplayButton.setText("Start Replay: " + CopyGameIDButton.getId());

                            if (AutomaticallyLoadChatLogCheckBox.isSelected()) {
                                showChatLog(newValue);
                                log.debug("[LoadChatLog] log automatically loaded");
                            }
                        }

                        for (PlayerFX item : reportedPlayersOfCurrentlySelectedReport) {
                            log.debug("Selected report id - offenders: " + item.getRepresentation());
                            CopyReportedUserIDButton.setId(item.getRepresentation());
                            CopyReportedUserIDButton.setText(item.getRepresentation());
                            CreateReportButton.setId(StringUtils.substringBetween(item.getRepresentation(), " [id ", "]"));
                            CreateReportButton.setText("Create report for " + StringUtils.substringBetween(item.getRepresentation(), " [id ", "]"));
                        }
                    } catch (Exception ErrorSelectedReport) {
                        log.debug("Exception for selected report: ");
                        log.debug(String.valueOf(ErrorSelectedReport));
                        chatLogTextArea.setText("Game ID is invalid or missing.");
                        CopyChatLogButton.setText("Chat does not exist");
                        CopyChatLogButton.setId("");
                        CopyGameIDButton.setText("Game ID does not exist");
                        CopyGameIDButton.setId("");
                        StartReplayButton.setText("Replay does not exist");
                        StartReplayButton.setId("");
                        CreateReportButton.setText("no value / missing Game ID");
                        CreateReportButton.setId("");
                        CopyReportedUserIDButton.setText("no value / missing Game ID");
                        CopyReportedUserIDButton.setId("");
                    }
                });
        chatLogTextArea.setText("select a report first");
        ViewHelper.buildUserTableView(platformService, reportedPlayerTableView, reportedPlayersOfCurrentlySelectedReport, this::addBan,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), communicationService);
    }

    public static void setSysClipboardText(String writeMe) {
        System.setProperty("java.awt.headless", "false");
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable tText = new StringSelection(writeMe);
        clip.setContents(tText, null);
    }

    private void addBan(PlayerFX accountFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX ban = new BanInfoFX();
        ban.setPlayer(accountFX);
        banInfoController.setBanInfo(ban);
        banInfoController.addPostedListener(banInfoFX -> onRefreshAllReports());
        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle("Apply new ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoController.preSetReportId(currentlySelectedItemNotNull.getId());
        banInfoDialog.showAndWait();
    }

    private void renewFilter() {
        filteredItemList.setPredicate(moderationReportFx -> {
            String playerFilter = playerNameFilterTextField.getText().toLowerCase();
            if (!Strings.isNullOrEmpty(playerFilter)) {
                boolean reportedPlayerPositive = moderationReportFx.getReportedUsers().stream().anyMatch(accountFX -> accountFX.getLogin().toLowerCase().contains(playerFilter));
                boolean reporterPositive = moderationReportFx.getReporter().getLogin().toLowerCase().contains(playerFilter);
                if (!(reportedPlayerPositive || reporterPositive)) {
                    return false;
                }
            }
            ChooseableStatus selectedItemChoiceBox = statusChoiceBox.getSelectionModel().getSelectedItem();
            if (selectedItemChoiceBox.toString().equals("ALL")) return true;

            if (selectedItemChoiceBox == ChooseableStatus.AWAITING_PROCESSING) {
                return moderationReportFx.getReportStatus() == ModerationReportStatus.AWAITING ||
                        moderationReportFx.getReportStatus() == ModerationReportStatus.PROCESSING;
            }

            ModerationReportStatus moderationReportStatus = selectedItemChoiceBox.getModerationReportStatus();
            return moderationReportFx.getReportStatus() == moderationReportStatus;
        });
    }

    public void onRefreshAllReports() {
        if (isLoading) {
            log.debug("onRefreshAllReports is already updating");
            return;
        }
        isLoading = true;
        createNewApiRequestThread(1, true);
    }

    private void createNewApiRequestThread(int x, boolean recursive) {
        int pageSize = 100;
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {

                moderationReportService.getPageOfReports(x, pageSize).thenAccept(reportFxes -> {
                    Platform.runLater(() -> {
                        reportFxes.forEach((report -> {
                            itemMap.put(Integer.valueOf(report.getId()), report);
                        }));
                    });
                    if (reportFxes.size() == pageSize || x < 2) {
                        if (recursive) {
                            createNewApiRequestThread(x + 5, true);
                            for (int i = 1; i < 5; i++) {
                                createNewApiRequestThread(x + i, false);
                            }
                        }

                    } else {
                        isLoading = false;
                        processStatisticsModerator(itemList);
                        showInTableRepeatedOffenders(itemList);
                    }
                }).exceptionally(throwable -> {
                    log.error("error loading reports", throwable);
                    return null;
                });

                return null;
            }
        };
        new Thread(task).start();
    }

    public void onEdit() {
        EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
        editModerationReportController.setModerationReportFx(reportTableView.getSelectionModel().getSelectedItem());
        editModerationReportController.setOnSaveRunnable(() -> Platform.runLater(() -> {
            renewFilter();
            this.onRefreshAllReports();
        }));
        //statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        Stage newCategoryDialog = new Stage();
        newCategoryDialog.setTitle("Edit Report");
        newCategoryDialog.setScene(new Scene(editModerationReportController.getRoot()));
        newCategoryDialog.showAndWait();
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

    @SneakyThrows
    private void showChatLog(ModerationReportFX report) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                GameFX game = report.getGame();
                String header = formatChatHeader(report, game);
                Path tempFilePath = createTempFile(game);

                if (tempFilePath == null) {
                    updateUIUnavailable(header, "An error occurred while creating a temporary file.");
                    return null;
                }

                HttpResponse<Path> response = downloadReplay(game, tempFilePath);

                if (response == null || response.statusCode() == 404) {
                    updateUIUnavailable(header, "Replay not available");
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

                deleteTempFile(tempFilePath);
                return null;
            }
        };
        new Thread(task).start();
    }

    private void showModeratorEvent(List<ModeratorEvent> moderatorEvents){
        /* Current Events:
        Created a marker with the text
        Created a ping of type
        Self-destructed X units
        Switched focus army from
        Is changing focus army from
        Paused
        Unpaused
         */

        String moderatorEventsLog = moderatorEvents.stream()
                .filter(event -> !event.message().contains("focus army from"))
                .map(event -> {
                    long timeMillis = event.time().toMillis();
                    String formattedChatMessageTime = formatChatMessageTime(timeMillis);

                    return String.format("[%s] from %s: %s",
                            formattedChatMessageTime,
                            event.playerNameFromCommandSource(),
                            event.message()
                    );
                })
                .collect(Collectors.joining("\n"));


        moderatorEventsLog += "\n";
        moderatorEventTextArea.setText(moderatorEventsLog);
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
            StartReplayButton.setText(message);
            CopyChatLogButton.setText(message);
            chatLogTextArea.setText(header + message);
        });
    }

    public static String getPlayerAPMs(Map<Integer, Map<Integer, AtomicInteger>> commandsPerMinuteByPlayer, double totalTimeInMinutes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Map<Integer, AtomicInteger>> entry : commandsPerMinuteByPlayer.entrySet()) {
            int player = entry.getKey();
            Map<Integer, AtomicInteger> commandsPerMinute = entry.getValue();

            int totalCommands = 0;
            for (AtomicInteger commands : commandsPerMinute.values()) {
                totalCommands += commands.get();
            }

            double apm = (double) totalCommands / totalTimeInMinutes;
            log.debug("totalCommands: " + totalCommands);
            log.debug("totalTimeInMinutes: " + totalTimeInMinutes);
            sb.append("\nPlayer ").append(player).append(" has an APM of ").append(apm);
        }
        return sb.toString();
    }

    private void processAndDisplayReplay(String header, Path tempFilePath, String promptAI) {
        try {
            ReplayDataParser replayDataParser = new ReplayDataParser(tempFilePath, objectMapper);
            Map<Integer, Map<Integer, AtomicInteger>> commandsPerMinuteByPlayer = replayDataParser.getCommandsPerMinuteByPlayer();
            String chatLog = generateChatLog(replayDataParser);

            StringBuilder chatLogFiltered = new StringBuilder();
            chatLogFiltered.append(header);

            String metadataInfo = generateMetadataInfo(replayDataParser);
            chatLogFiltered.append(metadataInfo);

            String filteredChatLog = filterAndAppendChatLog(chatLog);
            chatLogFiltered.append(filteredChatLog);

            List<ModeratorEvent> moderatorEvents = replayDataParser.getModeratorEvents();
            showModeratorEvent(moderatorEvents);

            chatLogFiltered.append("\n").append(promptAI);

            Platform.runLater(() -> {
                CopyChatLogButton.setId(chatLogFiltered.toString());
                CopyChatLogButton.setText("Copy Chat Log");
                chatLogTextArea.setText(chatLogFiltered.toString());
            });
        } catch (Exception e) {
            log.error("An error occurred while parsing replay data.", e);
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

    private String generateMetadataInfo(ReplayDataParser replayDataParser) {
        ReplayMetadata metadata = replayDataParser.getMetadata();
        List<GameOption> gameOptions = replayDataParser.getGameOptions();
        Map<String, Map<String, ?>> mods = replayDataParser.getMods();

        log.debug("Mods: {}", mods);
        log.debug("Metadata: {}", metadata);
        log.debug("Game Options: {}", gameOptions);

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

        if (Boolean.parseBoolean(cheatsEnabled)) {
            report.append("[!] Cheats Enabled: ").append(cheatsEnabled).append("\n");
        }

        if (!commonArmy.equalsIgnoreCase(OFF_STRING) && !commonArmy.equalsIgnoreCase("Not Found")) {
            report.append("[!] Non-Default Common Army: ").append(commonArmy).append("\n");
        }

        String reporterLoginName = String.valueOf(currentlySelectedItemNotNull.getReporter().getLogin());

        boolean found = false;
        for (Map.Entry<String, List<String>> entry : metadata.getTeams().entrySet()) {
            List<String> playerNames = entry.getValue();
            if (playerNames.contains(reporterLoginName)) {
                found = true;
                break;
            }
        }

        if (!found) {
            report.append(String.format("[!] Reporter '%s' does not exist in any of the team names.\n", reporterLoginName));
        }

        report.append("Host: ").append(metadata.getHost()).append("\n")
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
            if (FilterLogCheckBox.isSelected() && matchFound) {
                continue;
            }
            filteredChatLog.append(chatLine).append("\n");
        }

        return filteredChatLog.toString();
    }
}
