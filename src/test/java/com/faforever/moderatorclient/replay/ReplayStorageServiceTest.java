package com.faforever.moderatorclient.replay;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class ReplayStorageServiceTest {

    @Test
    void resolveReplayFileUsesApplicationRootReplayFolder(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());

            Path replayFile = service.resolveReplayFile(12345);

            assertThat(replayFile.getParent(), is(tempDir.resolve("replay")));
            assertThat(replayFile.getFileName().toString(), is("12345.fafreplay"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void purgeReplayFilesOlderThanOneDayDeletesOnlyOldTemporaryReplayFiles(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            Files.createDirectories(service.resolveTemporaryReplayDirectory());

            Path oldReplay = service.resolveTemporaryReplayDirectory().resolve("old.fafreplay");
            Path newReplay = service.resolveTemporaryReplayDirectory().resolve("new.fafreplay");
            Files.writeString(oldReplay, "{\"game_type\":0,\"compression\":\"zstd\"}\nold");
            Files.writeString(newReplay, "{\"game_type\":0,\"compression\":\"zstd\"}\nnew");
            Files.setLastModifiedTime(oldReplay, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

            ReplayStorageService.ReplayCleanupResult result = service.purgeReplayFilesOlderThanOneDay();

            assertThat(result.deletedFileCount(), is(1L));
            assertThat(Files.exists(oldReplay), is(false));
            assertThat(Files.exists(newReplay), is(true));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void purgeReplayFilesOlderThanOneDayKeepsInvalidReplayFiles(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            Files.createDirectories(service.resolveTemporaryReplayDirectory());

            Path invalidReplay = service.resolveTemporaryReplayDirectory().resolve("invalid.fafreplay");
            Files.writeString(invalidReplay, "not a replay");
            Files.setLastModifiedTime(invalidReplay, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

            ReplayStorageService.ReplayCleanupResult result = service.purgeReplayFilesOlderThanOneDay();

            assertThat(result.deletedFileCount(), is(0L));
            assertThat(Files.exists(invalidReplay), is(true));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void purgeReplayFilesOlderThanOneDayKeepsRetainedReplayFilesRegardlessOfAge(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            service.ensureReplayDirectoryExists();

            Path retainedReplay = service.resolveReplayFile(999);
            Files.writeString(retainedReplay, "{\"game_type\":0,\"compression\":\"zstd\"}\nold");
            Files.setLastModifiedTime(retainedReplay, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

            ReplayStorageService.ReplayCleanupResult result = service.purgeReplayFilesOlderThanOneDay();

            assertThat(result.deletedFileCount(), is(0L));
            assertThat(Files.exists(retainedReplay), is(true));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void purgeAllReplayFilesDeletesReplayFilesRegardlessOfAge(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            service.ensureReplayDirectoryExists();

            Path replay = service.resolveReplayDirectory().resolve("new.fafreplay");
            Path invalidReplay = service.resolveReplayDirectory().resolve("invalid.fafreplay");
            Files.writeString(replay, "{\"game_type\":0,\"compression\":\"zstd\"}\nnew");
            Files.writeString(invalidReplay, "not a replay");

            ReplayStorageService.ReplayCleanupResult result = service.purgeAllReplayFiles();

            assertThat(result.deletedFileCount(), is(1L));
            assertThat(Files.exists(replay), is(false));
            assertThat(Files.exists(invalidReplay), is(true));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void describeReplayFolderReturnsStoredReplaySize(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            service.ensureReplayDirectoryExists();
            Files.writeString(service.resolveReplayDirectory().resolve("sample.fafreplay"), "1234567890");

            ReplayStorageService.ReplayFolderStats stats = service.describeReplayFolder();

            assertThat(stats.fileCount(), is(1L));
            assertThat(stats.totalBytes(), greaterThan(0L));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void prepareReplayForParsingNormalizesTextualGameType(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            Path replay = service.resolveReplayFile(555);
            Files.write(replay, ("{\"game_type\":\"DEMORALIZATION\",\"compression\":\"zstd\"}\nbody").getBytes(StandardCharsets.UTF_8));

            Path preparedPath;
            try (ReplayStorageService.PreparedReplay preparedReplay = service.prepareReplayForParsing(replay)) {
                preparedPath = preparedReplay.path();
                String preparedContent = Files.readString(preparedPath, StandardCharsets.UTF_8);
                assertThat(preparedReplay.temporary(), is(true));
                assertThat(preparedContent, is("{\"game_type\":0,\"compression\":\"zstd\"}\nbody"));
            }

            assertThat(Files.exists(preparedPath), is(false));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void prepareReplayForParsingReusesReplayWhenHeaderIsAlreadyNumeric(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            Path replay = service.resolveReplayFile(556);
            Files.write(replay, ("{\"game_type\":0,\"compression\":\"zstd\"}\nbody").getBytes(StandardCharsets.UTF_8));

            try (ReplayStorageService.PreparedReplay preparedReplay = service.prepareReplayForParsing(replay)) {
                assertThat(preparedReplay.temporary(), is(false));
                assertThat(preparedReplay.path(), is(replay));
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
