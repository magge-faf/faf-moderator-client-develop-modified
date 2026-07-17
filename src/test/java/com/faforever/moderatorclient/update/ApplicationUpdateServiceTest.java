package com.faforever.moderatorclient.update;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
    void createConfigurationBackupArchiveUsesTenRotatingSlots(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        LocalPreferences localPreferences = new LocalPreferences();
        Path backupDir = tempDir.resolve("backup");
        localPreferences.getTabSettings().setUpdateBackupFolder(backupDir.toString());
        ApplicationUpdateService localService = new ApplicationUpdateService(new ObjectMapper(), localPreferences);

        try {
            System.setProperty("user.dir", tempDir.toString());
            Path configDir = tempDir.resolve("config");
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("templatesAndReasons.json"), "first");

            for (int index = 1; index <= 10; index++) {
                Path archive = localService.createConfigurationBackupArchive();

                assertThat(archive.getFileName().toString(), is("config-backup-%02d.zip".formatted(index)));
                assertThat(Files.exists(archive), is(true));
            }

            Path firstArchive = backupDir.resolve("config-backup-01.zip");
            long firstArchiveSize = Files.size(firstArchive);
            Files.writeString(configDir.resolve("templatesAndReasons.json"), "second");

            Path overwrittenArchive = localService.createConfigurationBackupArchive();

            assertThat(overwrittenArchive, is(firstArchive));
            try (var backupFiles = Files.list(backupDir)) {
                assertThat(backupFiles.filter(Files::isRegularFile).count(), is(10L));
            }
            assertThat(Files.size(firstArchive) >= firstArchiveSize, is(true));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
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

    @Test
    void createManualConfigurationBackupArchiveUsesTimestampedFileOutsideRotatingSlots(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            Path configDir = tempDir.resolve("config");
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("templatesAndReasons.json"), "{}");

            LocalPreferences localPreferences = new LocalPreferences();
            Path backupDir = tempDir.resolve("backup");
            localPreferences.getTabSettings().setUpdateBackupFolder(backupDir.toString());
            ApplicationUpdateService localService = new ApplicationUpdateService(new ObjectMapper(), localPreferences);

            Path archive = localService.createManualConfigurationBackupArchive();

            assertThat(archive.getParent(), is(backupDir));
            assertThat(archive.getFileName().toString().startsWith("config-manual-backup-"), is(true));
            assertThat(archive.getFileName().toString().endsWith(".zip"), is(true));
            assertThat(Files.exists(backupDir.resolve("config-backup-01.zip")), is(false));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void extractZipTrimsSharedRootDirectoryEntry(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("release.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOutputStream.putNextEntry(new ZipEntry("faf-moderator-client-win/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("faf-moderator-client-win/bin/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("faf-moderator-client-win/bin/faf-moderator-client.bat"));
            zipOutputStream.write("@echo off".getBytes());
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("faf-moderator-client-win/lib/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("faf-moderator-client-win/lib/faf-moderator-client.jar"));
            zipOutputStream.write("jar".getBytes());
            zipOutputStream.closeEntry();
        }

        Path stagedInstall = tempDir.resolve("staged");
        ReflectionTestUtils.invokeMethod(service, "extractZip", zip, stagedInstall);

        assertThat(Files.isRegularFile(stagedInstall.resolve("bin/faf-moderator-client.bat")), is(true));
        assertThat(Files.isRegularFile(stagedInstall.resolve("lib/faf-moderator-client.jar")), is(true));
        assertThat(Files.exists(stagedInstall.resolve("faf-moderator-client-win")), is(false));
    }

    @Test
    void purgeUpdaterFolderDeletesSessionFilesAndReportsDeletedSize(@TempDir Path tempDir) throws Exception {
        Path updaterDir = tempDir.resolve("updater");
        Path sessionDir = updaterDir.resolve("update-test");
        Files.createDirectories(sessionDir);
        Files.writeString(sessionDir.resolve("apply-update.log"), "log");
        Files.writeString(sessionDir.resolve("release.zip"), "archive");

        ApplicationUpdateService localService = new ApplicationUpdateService(new ObjectMapper(), new LocalPreferences()) {
            @Override
            public Path resolveUpdaterBaseDirectory() {
                return updaterDir;
            }
        };

        ApplicationUpdateService.UpdaterFolderStats before = localService.describeUpdaterFolder();
        ApplicationUpdateService.UpdaterFolderCleanupResult result = localService.purgeUpdaterFolder();
        ApplicationUpdateService.UpdaterFolderStats after = localService.describeUpdaterFolder();

        assertThat(before.fileCount(), is(2L));
        assertThat(before.totalBytes(), is(10L));
        assertThat(result.deletedFileCount(), is(2L));
        assertThat(result.deletedBytes(), is(10L));
        assertThat(Files.isDirectory(updaterDir), is(true));
        assertThat(Files.exists(sessionDir), is(false));
        assertThat(after.fileCount(), is(0L));
        assertThat(after.totalBytes(), is(0L));
    }

    @Test
    void installerHelperChecksAllConfigLocationsInPriorityOrder(@TempDir Path tempDir) {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");
        Path currentConfig = tempDir.resolve("current-config");

        List<Path> sources = ApplicationUpdateInstallerHelper.configSourceDirs(installRoot, currentWorkingDir, currentConfig);

        assertThat(sources.get(0), is(currentConfig.toAbsolutePath().normalize()));
        assertThat(sources.get(1), is(currentWorkingDir.resolve("config").toAbsolutePath().normalize()));
        assertThat(sources.get(2), is(installRoot.resolve("config").toAbsolutePath().normalize()));
        assertThat(sources.get(3), is(currentWorkingDir.resolve("data").toAbsolutePath().normalize()));
        assertThat(sources.get(4), is(installRoot.resolve("data").toAbsolutePath().normalize()));
    }

    @Test
    void installerHelperRestoresCurrentAndInstallRootConfigTargets(@TempDir Path tempDir) {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");
        Path currentConfig = tempDir.resolve("current-config");

        List<Path> targets = ApplicationUpdateInstallerHelper.configTargetDirs(installRoot, currentWorkingDir, currentConfig);

        assertThat(targets.contains(currentConfig.toAbsolutePath().normalize()), is(true));
        assertThat(targets.contains(currentWorkingDir.resolve("config").toAbsolutePath().normalize()), is(true));
        assertThat(targets.contains(installRoot.resolve("config").toAbsolutePath().normalize()), is(true));
    }

    @Test
    void installerHelperPreservesLegacyPreferenceFiles(@TempDir Path tempDir) {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");

        List<Path> prefsFiles = ApplicationUpdateInstallerHelper.legacyPrefsFiles(installRoot, currentWorkingDir);

        assertThat(prefsFiles.get(0), is(currentWorkingDir.resolve("client-prefs.json").toAbsolutePath().normalize()));
        assertThat(prefsFiles.get(1), is(installRoot.resolve("client-prefs.json").toAbsolutePath().normalize()));
    }
}
