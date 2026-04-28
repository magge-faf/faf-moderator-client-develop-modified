package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.replay.GameOption;
import com.faforever.commons.replay.ModeratorEvent;
import com.faforever.commons.replay.ReplayDataParser;
import com.faforever.commons.replay.body.Event;
import com.faforever.moderatorclient.api.TokenService;
import com.faforever.moderatorclient.config.ApplicationVersion;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Getter
@Component
@Slf4j
@RequiredArgsConstructor
public class ReplayAnalysisController implements Controller<VBox> {

    @FXML
    public VBox root;

    @FXML
    public Button checkLatestReplaysButton;

    @FXML
    public TextArea replayAnalysisTextArea;

    @FXML
    public TextField replayIDTextField;

    @FXML
    public TextField numberOfReplaysTextField;

    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final RestTemplate restTemplate = new RestTemplate();
    private static String REPLAY_API_URL;
    private static final String REPLAY_SAVE_DIR = "data/replays/";
    private static final String CHECKED_REPLAYS_FILE = "data/checked_replays.json";

    @Override
    public VBox getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        root.setDisable(true);
    }

    public void handleCheckLatestReplaysButton() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                downloadLatestReplays(null);
                return null;
            }
        };
        task.setOnFailed(event -> log.error("Replay check task failed", task.getException()));
        new Thread(task).start();
    }

    public void downloadLatestReplays(String replayIdParam) {
        if (replayIdParam != null && !replayIdParam.isEmpty()) {
            try {
                int replayId = Integer.parseInt(replayIdParam);
                Map<Integer, ReplayStatus> checkedReplays = loadCheckedReplays();
                if (!checkedReplays.containsKey(replayId)) {
                    downloadReplay(replayId, checkedReplays);
                } else {
                    log.info("Replay {} already checked, skipping.", replayId);
                }
                saveCheckedReplays(checkedReplays);
            } catch (NumberFormatException e) {
                log.error("Invalid replay ID format: {}", replayIdParam);
            }
            return;
        }
        String jsonResponse = fetchLatestReplays();
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            log.error("No replay data received.");
            return;
        }
        List<Integer> replayIds = parseReplayIds(jsonResponse);
        if (replayIds.isEmpty()) {
            log.error("No replays found.");
            return;
        }
        ensureReplayDirectoryExists();
        Map<Integer, ReplayStatus> checkedReplays = loadCheckedReplays();
        int totalToCheck = (int) replayIds
            .stream()
            .filter(id -> !checkedReplays.containsKey(id))
            .count();
        int checkedSoFar = 0;
        for (int replayId : replayIds) {
            if (!checkedReplays.containsKey(replayId)) {
                log.debug(
                    "Checking replay {} ({} remaining)",
                    replayId,
                    totalToCheck - checkedSoFar
                );
                downloadReplay(replayId, checkedReplays);
                checkedSoFar++;
            } else {
                log.info("Replay {} already checked, skipping.", replayId);
            }
        }
        saveCheckedReplays(checkedReplays);
    }

    public String fetchLatestReplays() {
        String accessToken = tokenService.getRefreshedTokenValue();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            REPLAY_API_URL =
                "https://api.faforever.com/data/game?filter=replayAvailable==true;validity==VALID&sort=-id&page[limit]=" +
                numberOfReplaysTextField.getText();
            ResponseEntity<String> response = restTemplate.exchange(
                REPLAY_API_URL,
                HttpMethod.GET,
                entity,
                String.class
            );
            return response.getBody();
        } catch (HttpServerErrorException e) {
            System.err.println("Error fetching replays: " + e.getMessage());
            System.err.println("Response body: " + e.getResponseBodyAsString());
            return null;
        }
    }

    public void ensureReplayDirectoryExists() {
        Path replayDir = Path.of(REPLAY_SAVE_DIR);
        if (!Files.exists(replayDir)) {
            try {
                Files.createDirectories(replayDir);
                log.info("Created directory: " + REPLAY_SAVE_DIR);
            } catch (IOException e) {
                log.error("Failed to create replay directory: {}", e.getMessage());
            }
        }
    }

    public void downloadReplay(int replayId, Map<Integer, ReplayStatus> checkedReplays) {
        String replayUrl = "https://replay.faforever.com/" + replayId;
        Path savePath = Path.of(REPLAY_SAVE_DIR, replayId + ".fafreplay");
        int retryCount = 0;
        boolean success = false;

        while (!success && retryCount < 10) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(replayUrl))
                        .header("User-Agent", "Modified Mordor/" + ApplicationVersion.CURRENT_VERSION)
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                int status = response.statusCode();
                log.info("Replay {} download status: {}", replayId, status);

                if (status == 200) {
                    // Check Content-Type before saving
                    String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
                    if (!contentType.contains("application") && !contentType.contains("octet-stream")) {
                        log.warn("Replay {}: Unexpected content type: {}", replayId, contentType);
                        checkedReplays.put(replayId, ReplayStatus.DOWNLOAD_FAILED);
                        success = true;
                        continue;
                    }

                    Files.createDirectories(savePath.getParent());
                    try (InputStream in = response.body()) {
                        Files.copy(in, savePath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    log.info("Replay {} saved to {}", replayId, savePath);
                    checkedReplays.put(replayId, ReplayStatus.DOWNLOADED);
                    processReplayFile(savePath, checkedReplays);
                    success = true;

                } else if (status == 429) {
                    retryCount++;
                    long sleepMs = (long) Math.pow(2, retryCount) * 1000;
                    log.warn("Rate limit hit for replay {}. Retrying in {} ms", replayId, sleepMs);
                    Thread.sleep(sleepMs);

                } else {
                    log.warn("Failed to download replay {}: HTTP {}", replayId, status);
                    checkedReplays.put(replayId, ReplayStatus.DOWNLOAD_FAILED);
                    success = true;
                }

            } catch (IOException | InterruptedException e) {
                log.warn("Replay {} download exception: {}", replayId, e.getMessage());
                checkedReplays.put(replayId, ReplayStatus.DOWNLOAD_FAILED);
                success = true;
            }
        }
    }

    public static List<Integer> parseReplayIds(String jsonResponse) {
        List<Integer> replayIds = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode dataNode = rootNode.path("data");
            for (JsonNode gameNode : dataNode) {
                replayIds.add(gameNode.path("id").asInt());
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON: {}", e.getMessage());
        }
        return replayIds;
    }

    @FXML
    public void handleReplaysInFolderButton() {
        replayAnalysisTextArea.appendText("Checking replays in folder...\n");
        Map<Integer, ReplayStatus> checkedReplays = loadCheckedReplays();
        checkReplaysForFocusArmySwitch(checkedReplays);
        saveCheckedReplays(checkedReplays);
    }

    public void checkReplaysForFocusArmySwitch(Map<Integer, ReplayStatus> checkedReplays) {
        try (Stream<Path> paths = Files.walk(Paths.get(REPLAY_SAVE_DIR))) {
            long detectedReplays = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".fafreplay"))
                .count();
            log.info("Total replays in folder: {}", detectedReplays);
            try (Stream<Path> newPaths = Files.walk(Paths.get(REPLAY_SAVE_DIR))) {
                newPaths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".fafreplay"))
                    .forEach(path -> processReplayFile(path, checkedReplays));
            }
        } catch (IOException e) {
            log.error("Error accessing replay directory: {}", e.getMessage());
        }
    }

    private void processReplayFile(Path replayPath, Map<Integer, ReplayStatus> checkedReplays) {
        try {
            int replayId = Integer.parseInt(
                replayPath.getFileName().toString().replace(".fafreplay", "")
            );
            ReplayDataParser replayDataParser = new ReplayDataParser(replayPath, objectMapper);
            List<GameOption> gameOptions = replayDataParser.getGameOptions();
            String commonArmy = getValueForKey(gameOptions);
            if ("Union".equals(commonArmy)) {
                checkedReplays.put(replayId, ReplayStatus.UNION);
                return;
            }
            List<Event> events = replayDataParser.getEvents();
            ModerationReportController.DesyncResult desyncResult = checkReplayEventsForDesync(
                events
            );
            if (desyncResult.desync()) {
                checkedReplays.put(replayId, ReplayStatus.DESYNCED);
                return;
            }
            List<ModeratorEvent> moderatorEvents = replayDataParser.getModeratorEvents();
            List<PlayerInfo> playerInfoList = replayDataParser
                .getArmies()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().containsKey("PlayerName"))
                .map(
                    entry ->
                        new PlayerInfo(
                            entry.getKey() + 1,
                            (String) entry.getValue().get("PlayerName")
                        )
                )
                .toList();
            if (
                checkModeratorEventsForFocusSwitch(
                    moderatorEvents,
                    replayPath.getFileName().toString(),
                    playerInfoList
                )
            ) {
                checkedReplays.put(replayId, ReplayStatus.DETECTED);
                log.debug("Detected: {}", replayId);
                return;
            }
            checkedReplays.put(replayId, ReplayStatus.CHECKED);
        } catch (Exception e) {
            int replayId = 0;
            try {
                replayId = Integer.parseInt(
                    replayPath.getFileName().toString().replace(".fafreplay", "")
                );
            } catch (NumberFormatException ex) {
                log.error(
                    "Error processing replay file {}: {}",
                    replayPath.getFileName(),
                    e.getMessage()
                );
                checkedReplays.put(replayId, ReplayStatus.PROCESSING_FAILED);
                return;
            }
            log.error(
                "Error processing replay file {}: {}",
                replayPath.getFileName(),
                e.getMessage()
            );
            checkedReplays.put(replayId, ReplayStatus.PROCESSING_FAILED);
        }
    }

    private static String getValueForKey(List<GameOption> gameOptions) {
        for (GameOption option : gameOptions) {
            if (option.getKey().equals("CommonArmy")) {
                return (String) option.getValue();
            }
        }
        return "Not Found";
    }

    private boolean checkModeratorEventsForFocusSwitch(
        List<ModeratorEvent> moderatorEvents,
        String fileName,
        List<PlayerInfo> playerInfoList
    ) {
        boolean foundSwitchToMinus1 = false;
        boolean foundSwitchFromMinus1 = false;
        int switchCount = 0;
        String fromPlayer = "";
        String toPlayer = "";
        int eventsToSkipAtEnd = 30; // Players switch to observer when the game has ended; avoid false triggers
        int limit = Math.max(0, moderatorEvents.size() - eventsToSkipAtEnd);
        for (int i = 0; i < limit; i++) {
            ModeratorEvent event = moderatorEvents.get(i);
            if ("null".equals(event.playerNameFromCommandSource())) continue;
            String message = event.message().replaceAll("!", "");
            if (message.contains("Switched focus army from")) {
                String[] parts = message.split(" ");
                if (parts.length >= 7 && parts[3].equals("from")) {
                    Integer fromIndex = Integer.valueOf(parts[4]);
                    Integer toIndex = Integer.valueOf(parts[6]);
                    fromPlayer = fromIndex == -1
                        ? "Observer"
                        : playerInfoList
                            .stream()
                            .filter(player -> player.getIndex().equals(fromIndex))
                            .map(PlayerInfo::getPlayerName)
                            .findFirst()
                            .orElse("Unknown Player");
                    toPlayer = toIndex == -1
                        ? "Observer"
                        : playerInfoList
                            .stream()
                            .filter(player -> player.getIndex().equals(toIndex))
                            .map(PlayerInfo::getPlayerName)
                            .findFirst()
                            .orElse("Unknown Player");
                    if (fromPlayer.equals("Unknown Player") || toPlayer.equals("Unknown Player")) {
                        log.debug("Unknown Player"); // Perhaps spectator if not in index army
                        return false;
                    }
                    if (toIndex.equals(-1) && !message.contains("via ConExecute")) {
                        foundSwitchToMinus1 = true;
                    }
                    if (fromIndex.equals(-1) && !message.contains("via ConExecute")) {
                        foundSwitchFromMinus1 = true;
                    }
                    if (foundSwitchToMinus1 && foundSwitchFromMinus1) {
                        switchCount++;
                        foundSwitchToMinus1 = false;
                        foundSwitchFromMinus1 = false;
                    }
                }
            }
        }
        if (switchCount > 2) {
            String logMessage = String.format(
                "Replay %s contains %d focus army switches to -1 and back from -1 without ConExecute. Players involved: From '%s' to '%s'.\n",
                fileName,
                switchCount,
                fromPlayer,
                toPlayer
            );
            File logFile = new File("log.txt");
            try (FileWriter fileWriter = new FileWriter(logFile, true)) {
                fileWriter.write(logMessage);
            } catch (IOException e) {
                System.err.println("Error while writing log data to text file: " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    public void handleReplayIdCheckButton() {
        String replayId = replayIDTextField.getText();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                downloadLatestReplays(replayId);
                return null;
            }
        };
        task.setOnFailed(event -> log.error("Replay ID check task failed", task.getException()));
        new Thread(task).start();
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

    private Map<Integer, ReplayStatus> loadCheckedReplays() {
        Path checkedReplaysPath = Paths.get(CHECKED_REPLAYS_FILE);
        if (Files.exists(checkedReplaysPath)) {
            try {
                return objectMapper.readValue(
                    checkedReplaysPath.toFile(),
                    new TypeReference<>() {}
                );
            } catch (IOException e) {
                log.error("Failed to load checked replays: {}", e.getMessage());
            }
        }
        return new HashMap<>();
    }

    private void saveCheckedReplays(Map<Integer, ReplayStatus> checkedReplays) {
        Path checkedReplaysPath = Paths.get(CHECKED_REPLAYS_FILE);
        try {
            objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(checkedReplaysPath.toFile(), checkedReplays);
        } catch (IOException e) {
            log.error("Failed to save checked replays: {}", e.getMessage());
        }
    }

    public enum ReplayStatus {
        DOWNLOADED,
        CHECKED,
        DESYNCED,
        SANDBOX,
        DOWNLOAD_FAILED,
        PROCESSING_FAILED,
        UNION,
        DETECTED
    }

    public ModerationReportController.DesyncResult checkReplayEventsForDesync(List<Event> events) {
        String previousChecksum = null;
        int previousTick = -1;
        for (Event event : events) {
            if (event instanceof Event.VerifyChecksum(String hash, int tick)) {
                if (tick == previousTick && !Objects.equals(previousChecksum, hash)) {
                    return new ModerationReportController.DesyncResult(true, previousTick);
                }
                previousChecksum = hash;
                previousTick = tick;
            }
        }
        return new ModerationReportController.DesyncResult(false, -1);
    }
}
