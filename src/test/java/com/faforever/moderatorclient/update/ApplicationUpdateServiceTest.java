package com.faforever.moderatorclient.update;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class ApplicationUpdateServiceTest {

    private final ApplicationUpdateService service = new ApplicationUpdateService(new ObjectMapper(), new LocalPreferences());

    @Test
    void newerReleaseOverridesFutureSnoozeForOlderRelease() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        reminder.setReminderVersionTag("v2026-07-01");
        reminder.setNextReminderEpoch(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(14));

        GithubRelease release = new GithubRelease("v2026-07-08", "v2026-07-08", "https://example.invalid", "", null, List.of());

        assertThat(service.shouldShowUpdate(release, reminder), is(true));
    }

    @Test
    void legacyReminderTimestampStillSuppressesPopupForThreeDays() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        reminder.setLastReminderEpoch(System.currentTimeMillis());

        GithubRelease release = new GithubRelease("v9999-01-01", "v9999-01-01", "https://example.invalid", "", null, List.of());

        assertThat(service.shouldShowUpdate(release, reminder), is(false));
    }

    @Test
    void singleZipAssetIsUsedAsFallback() {
        GithubReleaseAsset asset = new GithubReleaseAsset(
                "faf-moderator-client-v2026-07-08-custom.zip",
                "https://example.invalid/download.zip",
                "application/zip",
                123L
        );
        GithubRelease release = new GithubRelease("v2026-07-08", "v2026-07-08", "https://example.invalid", "", null, List.of(asset));

        Optional<GithubReleaseAsset> selected = service.findMatchingAsset(release);

        assertThat(selected.isPresent(), is(true));
        assertThat(selected.get(), is(asset));
    }

    @Test
    void defaultBackupDirectoryUsesBackupFolder() {
        assertThat(service.resolveDefaultBackupDirectory().getFileName().toString(), is("backup"));
    }

    @Test
    void purgeBackupFilesOlderThanDeletesOnlyOldFiles(@TempDir Path tempDir) throws Exception {
        LocalPreferences localPreferences = new LocalPreferences();
        localPreferences.getTabSettings().setUpdateBackupFolder(tempDir.toString());
        ApplicationUpdateService localService = new ApplicationUpdateService(new ObjectMapper(), localPreferences);

        Path oldFile = tempDir.resolve("old-backup.zip");
        Path newFile = tempDir.resolve("new-backup.zip");
        Files.writeString(oldFile, "old");
        Files.writeString(newFile, "new");
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS)));

        ApplicationUpdateService.BackupPurgeResult result = localService.purgeBackupFilesOlderThan(5);

        assertThat(result.deletedFileCount(), is(1L));
        assertThat(Files.exists(oldFile), is(false));
        assertThat(Files.exists(newFile), is(true));
    }

    @Test
    void createConfigurationBackupArchiveWritesCurrentConfigFolder(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            Path configDir = tempDir.resolve("config");
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("templatesAndReasons.json"), "{}");

            LocalPreferences localPreferences = new LocalPreferences();
            localPreferences.getTabSettings().setUpdateBackupFolder(tempDir.resolve("backup").toString());
            ApplicationUpdateService localService = new ApplicationUpdateService(new ObjectMapper(), localPreferences);

            Path archive = localService.createConfigurationBackupArchive();

            assertThat(Files.exists(archive), is(true));
            try (ZipFile zipFile = new ZipFile(archive.toFile())) {
                assertThat(zipFile.size(), greaterThan(0));
                assertThat(zipFile.getEntry("templatesAndReasons.json") != null, is(true));
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
