package com.faforever.moderatorclient.replay;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void purgeReplayFilesOlderThanOneDayDeletesOnlyOldReplayFiles(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            ReplayStorageService service = new ReplayStorageService(new LocalPreferences());
            service.ensureReplayDirectoryExists();

            Path oldReplay = service.resolveReplayDirectory().resolve("old.fafreplay");
            Path newReplay = service.resolveReplayDirectory().resolve("new.fafreplay");
            Files.writeString(oldReplay, "old");
            Files.writeString(newReplay, "new");
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
}
