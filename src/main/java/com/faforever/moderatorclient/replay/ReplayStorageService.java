package com.faforever.moderatorclient.replay;

import com.faforever.moderatorclient.config.ApplicationPaths;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayStorageService {
    private static final Duration TEMP_REPLAY_MAX_AGE = Duration.ofDays(1);
    private static final int MAX_REPLAY_HEADER_BYTES = 8192;
    private static final Pattern TEXTUAL_GAME_TYPE_PATTERN =
            Pattern.compile("\"game_type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Map<String, Integer> GAME_TYPE_BY_NAME = Map.of(
            "DEMORALIZATION", 0,
            "DOMINATION", 1,
            "ERADICATION", 2,
            "SANDBOX", 3
    );

    private final LocalPreferences localPreferences;

    public Path resolveReplayDirectory() {
        return ApplicationPaths.resolveReplayDirectory();
    }

    public Path resolveCheckedReplaysFile() {
        return ApplicationPaths.resolveConfigurationDirectory().resolve("checked_replays.json");
    }

    public void ensureReplayDirectoryExists() throws IOException {
        Files.createDirectories(resolveReplayDirectory());
    }

    public Path resolveReplayFile(int replayId) throws IOException {
        ensureReplayDirectoryExists();
        return resolveReplayDirectory().resolve(replayId + ".fafreplay");
    }

    /**
     * Scratch files (temp downloads, header-normalized copies) live under a dedicated subdirectory so
     * age-based cleanup never touches replays a moderator deliberately downloaded and kept.
     */
    public Path resolveTemporaryReplayDirectory() {
        return resolveReplayDirectory().resolve("tmp");
    }

    private void ensureTemporaryReplayDirectoryExists() throws IOException {
        Files.createDirectories(resolveTemporaryReplayDirectory());
    }

    public Path createTemporaryReplayFile(String prefix) throws IOException {
        ensureTemporaryReplayDirectoryExists();
        String sanitizedPrefix = prefix == null ? "faf_replay_" : prefix.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitizedPrefix.length() < 3) {
            sanitizedPrefix = "faf_replay_";
        }
        return Files.createTempFile(resolveTemporaryReplayDirectory(), sanitizedPrefix, ".fafreplay");
    }

    public PreparedReplay prepareReplayForParsing(Path replayFile) throws IOException {
        HeaderProbe probe = readReplayHeader(replayFile);
        String normalizedHeader = normalizeReplayHeader(probe.header());

        if (probe.header().equals(normalizedHeader)) {
            return new PreparedReplay(replayFile, false);
        }

        Path normalizedReplay = createTemporaryReplayFile("normalized_replay_");
        try (var input = Files.newInputStream(replayFile);
             var output = Files.newOutputStream(normalizedReplay)) {
            output.write(normalizedHeader.getBytes(StandardCharsets.UTF_8));
            output.write('\n');
            input.skipNBytes(probe.headerEnd() + 1L);
            input.transferTo(output);
        }
        return new PreparedReplay(normalizedReplay, true);
    }

    private HeaderProbe readReplayHeader(Path replayFile) throws IOException {
        byte[] buffer = new byte[MAX_REPLAY_HEADER_BYTES];
        int bytesRead;
        try (var inputStream = Files.newInputStream(replayFile)) {
            bytesRead = inputStream.readNBytes(buffer, 0, buffer.length);
        }
        int headerEnd = findReplayHeaderEnd(buffer, bytesRead);
        String header = new String(buffer, 0, headerEnd, StandardCharsets.UTF_8);
        return new HeaderProbe(header, headerEnd);
    }

    private record HeaderProbe(String header, int headerEnd) {
    }

    public ReplayFolderStats describeReplayFolder() throws IOException {
        Path replayDir = resolveReplayDirectory();
        if (!Files.isDirectory(replayDir)) {
            return new ReplayFolderStats(replayDir, 0L, 0L);
        }

        try (var paths = Files.walk(replayDir)) {
            long[] totals = paths
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return new long[]{1L, Files.size(path)};
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .reduce(new long[]{0L, 0L}, (left, right) -> new long[]{left[0] + right[0], left[1] + right[1]});
            return new ReplayFolderStats(replayDir, totals[0], totals[1]);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    public ReplayCleanupResult purgeReplayFilesOlderThanOneDay() throws IOException {
        return purgeReplayFilesOlderThan(TEMP_REPLAY_MAX_AGE);
    }

    public ReplayCleanupResult purgeAllReplayFiles() throws IOException {
        return purgeReplayFiles(null);
    }

    public ReplayCleanupResult purgeReplayFilesOlderThan(Duration maxAge) throws IOException {
        return purgeReplayFiles(maxAge);
    }

    private ReplayCleanupResult purgeReplayFiles(Duration maxAge) throws IOException {
        Path replayDir = resolveReplayDirectory().toAbsolutePath().normalize();

        // Age-based cleanup only ever touches scratch files in the temp subdirectory, never replays a
        // moderator deliberately downloaded and kept. A full purge (maxAge == null) is an explicit,
        // user-confirmed action and clears the whole replay directory, temp files included.
        Path scanRoot = maxAge == null ? replayDir : resolveTemporaryReplayDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(scanRoot)) {
            return new ReplayCleanupResult(replayDir, maxAge, 0L, 0L);
        }

        Instant cutoff = maxAge == null ? null : Instant.now().minus(maxAge);
        long deletedFiles = 0L;
        long deletedBytes = 0L;

        try (var paths = Files.walk(scanRoot)) {
            for (Path path : paths.filter(file -> isReplayFile(file, cutoff)).toList()) {
                long size = Files.size(path);
                Files.deleteIfExists(path);
                deletedFiles++;
                deletedBytes += size;
            }
        }

        return new ReplayCleanupResult(replayDir, maxAge, deletedFiles, deletedBytes);
    }

    public void purgeReplayFilesIfEnabled() {
        if (!localPreferences.getTabSettings().isAutoPurgeTempReplaysOlderThanOneDayCheckBox()) {
            return;
        }

        try {
            ReplayCleanupResult result = purgeReplayFilesOlderThanOneDay();
            if (result.deletedFileCount() > 0) {
                log.info("Auto-purged {} replay files ({} bytes) older than {} from {}",
                        result.deletedFileCount(),
                        result.deletedBytes(),
                        result.maxAge(),
                        result.directory());
            }
        } catch (IOException e) {
            log.warn("Failed to auto-purge replay folder {}", resolveReplayDirectory(), e);
        }
    }

    private boolean isReplayFile(Path path, Instant cutoff) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".fafreplay")) {
            return false;
        }

        try {
            if (cutoff != null) {
                FileTime lastModifiedTime = Files.getLastModifiedTime(normalizedPath);
                if (!lastModifiedTime.toInstant().isBefore(cutoff)) {
                    return false;
                }
            }
            return hasReplayHeader(normalizedPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasReplayHeader(Path path) throws IOException {
        byte[] headerBytes = new byte[MAX_REPLAY_HEADER_BYTES];
        int bytesRead;
        try (var inputStream = Files.newInputStream(path)) {
            bytesRead = inputStream.read(headerBytes);
        }
        if (bytesRead <= 0) {
            return false;
        }

        int headerEnd = -1;
        for (int i = 0; i < bytesRead; i++) {
            if (headerBytes[i] == '\n') {
                headerEnd = i;
                break;
            }
        }
        if (headerEnd <= 0) {
            return false;
        }

        String header = new String(headerBytes, 0, headerEnd, StandardCharsets.UTF_8).trim();
        return header.startsWith("{")
                && header.endsWith("}")
                && (header.contains("\"compression\"") || header.contains("\"game_type\""));
    }

    private int findReplayHeaderEnd(byte[] replayBytes, int length) {
        for (int i = 0; i < length; i++) {
            if (replayBytes[i] == '\n') {
                return i;
            }
        }
        throw new IllegalArgumentException("Missing separator between replay header and body");
    }

    private String normalizeReplayHeader(String header) {
        Matcher matcher = TEXTUAL_GAME_TYPE_PATTERN.matcher(header);
        if (!matcher.find()) {
            return header;
        }

        String rawGameType = matcher.group(1).trim();
        Integer normalizedGameType = GAME_TYPE_BY_NAME.get(rawGameType.toUpperCase());
        if (normalizedGameType == null) {
            return header;
        }

        return matcher.replaceFirst("\"game_type\":" + normalizedGameType);
    }

    public record ReplayFolderStats(Path directory, long fileCount, long totalBytes) {
    }

    public record ReplayCleanupResult(Path directory, Duration maxAge, long deletedFileCount, long deletedBytes) {
    }

    public record PreparedReplay(Path path, boolean temporary) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (temporary) {
                Files.deleteIfExists(path);
            }
        }
    }
}
