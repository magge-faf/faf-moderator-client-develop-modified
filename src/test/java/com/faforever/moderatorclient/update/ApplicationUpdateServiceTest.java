package com.faforever.moderatorclient.update;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationUpdateServiceTest {

    private final ApplicationUpdateService service = new ApplicationUpdateService(new ObjectMapper(), new LocalPreferences());

    @Test
    void newerReleaseOverridesFutureSnoozeForOlderRelease() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        reminder.setReminderVersionTag("v2026-07-01");
        reminder.setNextReminderEpoch(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(14));

        GithubRelease release = new GithubRelease("v9999-01-01", "v9999-01-01", "https://example.invalid", "", null, List.of());

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
    void nextStartReminderSuppressesOnlyCurrentSession() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        reminder.scheduleForNextStart("v9999-01-01", 3);
        GithubRelease release = new GithubRelease("v9999-01-01", "v9999-01-01", "https://example.invalid", "", null, List.of());

        assertThat(service.shouldShowUpdate(release, reminder), is(false));

        LocalPreferences.VersionReminder reloadedReminder = new LocalPreferences.VersionReminder();
        reloadedReminder.setReminderVersionTag(reminder.getReminderVersionTag());
        reloadedReminder.setReminderDelayDays(reminder.getReminderDelayDays());

        assertThat(service.shouldShowUpdate(release, reloadedReminder), is(true));
    }

    @Test
    void olderDateReleaseDoesNotShowUpdateForCurrentBuild() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        GithubRelease release = new GithubRelease("v2026-07-08", "v2026-07-08", "https://example.invalid", "", null, List.of());

        assertThat(service.shouldShowUpdate(release, reminder), is(false));
    }

    @Test
    void dateReleaseVersionsCompareChronologically() {
        assertThat(ApplicationUpdateService.compareVersions("v2026-07-08", "v2026-07-17") < 0, is(true));
        assertThat(ApplicationUpdateService.compareVersions("v2026-07-17", "v2026-07-08") > 0, is(true));
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
    void downloadProgressIncludesPercentSizeAndSpeedWhenContentLengthIsKnown() {
        String progress = ApplicationUpdateService.formatDownloadProgress(
                5L * 1024L * 1024L,
                10L * 1024L * 1024L,
                0L,
                2_000_000_000L
        );

        assertThat(progress, is("Downloading: 50% (5.0 MB / 10.0 MB, 2.5 MB/s)"));
    }

    @Test
    void downloadProgressOmitsPercentWhenContentLengthIsUnknown() {
        String progress = ApplicationUpdateService.formatDownloadProgress(
                512L * 1024L,
                -1L,
                0L,
                1_000_000_000L
        );

        assertThat(progress, is("Downloading: 512 KB (512 KB/s)"));
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
            Files.writeString(configDir.resolve("templatesAndReasons.json"), "second");

            Path overwrittenArchive = localService.createConfigurationBackupArchive();

            assertThat(overwrittenArchive, is(firstArchive));
            try (var backupFiles = Files.list(backupDir)) {
                assertThat(backupFiles.filter(Files::isRegularFile).count(), is(10L));
            }
            try (ZipFile zipFile = new ZipFile(firstArchive.toFile())) {
                ZipEntry entry = zipFile.getEntry("templatesAndReasons.json");
                assertThat(entry, is(notNullValue()));
                try (var inputStream = zipFile.getInputStream(entry)) {
                    String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(content, is("second"));
                }
            }
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
        assertThat(sources.contains(installRoot.resolve("bin").resolve("config").toAbsolutePath().normalize()), is(true));
        assertThat(sources.contains(currentWorkingDir.resolve("data").toAbsolutePath().normalize()), is(true));
        assertThat(sources.contains(installRoot.resolve("data").toAbsolutePath().normalize()), is(true));
        assertThat(sources.contains(installRoot.resolve("bin").resolve("data").toAbsolutePath().normalize()), is(true));
    }

    @Test
    void installerHelperRestoresCurrentAndInstallRootConfigTargets(@TempDir Path tempDir) {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");
        Path currentConfig = tempDir.resolve("current-config");

        List<Path> targets = ApplicationUpdateInstallerHelper.configTargetDirs(installRoot, currentWorkingDir, currentConfig);

        assertThat(targets.contains(currentConfig.toAbsolutePath().normalize()), is(true));
        assertThat(targets.contains(currentWorkingDir.resolve("config").toAbsolutePath().normalize()), is(false));
        assertThat(targets.contains(installRoot.resolve("config").toAbsolutePath().normalize()), is(true));
    }

    @Test
    void installerHelperRestoresSnapshotOnlyToConfigTargets(@TempDir Path tempDir) throws Exception {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");
        Path currentConfig = installRoot.resolve("config");
        Path snapshotDir = tempDir.resolve("snapshot");
        Path logPath = tempDir.resolve("apply-update.log");
        Files.createDirectories(snapshotDir);
        Files.writeString(snapshotDir.resolve("client-prefs.json"), "prefs");
        Files.writeString(snapshotDir.resolve("templatesAndReasons.json"), "templates");

        ReflectionTestUtils.invokeMethod(
                ApplicationUpdateInstallerHelper.class,
                "restoreExistingConfig",
                snapshotDir,
                ApplicationUpdateInstallerHelper.configTargetDirs(installRoot, currentWorkingDir, currentConfig),
                logPath
        );

        assertThat(Files.exists(installRoot.resolve("config").resolve("client-prefs.json")), is(true));
        assertThat(Files.exists(installRoot.resolve("client-prefs.json")), is(false));
        assertThat(Files.exists(installRoot.resolve("data")), is(false));
        assertThat(Files.exists(currentWorkingDir.resolve("config")), is(false));
    }

    @Test
    void installerHelperMergesConfigSourcesWithHigherPrioritySourcesWinning(@TempDir Path tempDir) throws Exception {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");
        Path currentConfig = installRoot.resolve("config");
        Path binConfig = currentWorkingDir.resolve("config");
        Path legacyData = currentWorkingDir.resolve("data");
        Path snapshotDir = tempDir.resolve("snapshot");
        Path logPath = tempDir.resolve("apply-update.log");

        Files.createDirectories(currentConfig);
        Files.createDirectories(binConfig);
        Files.createDirectories(legacyData);
        Files.writeString(currentConfig.resolve("client-prefs.json"), "top prefs");
        Files.writeString(binConfig.resolve("templatesAndReasons.json"), "bin templates");
        Files.writeString(binConfig.resolve("client-prefs.json"), "bin prefs");
        Files.writeString(legacyData.resolve("userNotes.txt"), "legacy notes");

        ReflectionTestUtils.invokeMethod(
                ApplicationUpdateInstallerHelper.class,
                "snapshotExistingConfig",
                snapshotDir,
                ApplicationUpdateInstallerHelper.configSourceDirs(installRoot, currentWorkingDir, currentConfig),
                List.of(),
                logPath
        );

        assertThat(Files.readString(snapshotDir.resolve("client-prefs.json")), is("top prefs"));
        assertThat(Files.readString(snapshotDir.resolve("templatesAndReasons.json")), is("bin templates"));
        assertThat(Files.readString(snapshotDir.resolve("userNotes.txt")), is("legacy notes"));
    }

    @Test
    void installerHelperMigratesBinDataWhenWorkingDirectoryIsInstallRoot(@TempDir Path tempDir) throws Exception {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot;
        Path currentConfig = installRoot.resolve("config");
        Path binData = installRoot.resolve("bin").resolve("data");
        Path snapshotDir = tempDir.resolve("snapshot");
        Path logPath = tempDir.resolve("apply-update.log");

        Files.createDirectories(binData);
        Files.writeString(binData.resolve("templatesAndReasons.json"), "legacy bin data templates");

        ReflectionTestUtils.invokeMethod(
                ApplicationUpdateInstallerHelper.class,
                "snapshotExistingConfig",
                snapshotDir,
                ApplicationUpdateInstallerHelper.configSourceDirs(installRoot, currentWorkingDir, currentConfig),
                List.of(),
                logPath
        );

        assertThat(Files.readString(snapshotDir.resolve("templatesAndReasons.json")), is("legacy bin data templates"));
    }

    @Test
    void installerHelperDoesNotLetLegacyPrefsOverwriteConfigPrefs(@TempDir Path tempDir) throws Exception {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot;
        Path currentConfig = installRoot.resolve("config");
        Path snapshotDir = tempDir.resolve("snapshot");
        Path logPath = tempDir.resolve("apply-update.log");

        Files.createDirectories(currentConfig);
        Files.writeString(currentConfig.resolve("client-prefs.json"), "config prefs");
        Files.writeString(installRoot.resolve("client-prefs.json"), "root legacy prefs");
        Files.createDirectories(installRoot.resolve("bin"));
        Files.writeString(installRoot.resolve("bin").resolve("client-prefs.json"), "bin legacy prefs");

        ReflectionTestUtils.invokeMethod(
                ApplicationUpdateInstallerHelper.class,
                "snapshotExistingConfig",
                snapshotDir,
                ApplicationUpdateInstallerHelper.configSourceDirs(installRoot, currentWorkingDir, currentConfig),
                ApplicationUpdateInstallerHelper.legacyPrefsFiles(installRoot, currentWorkingDir),
                logPath
        );

        assertThat(Files.readString(snapshotDir.resolve("client-prefs.json")), is("config prefs"));
    }

    @Test
    void installerHelperUsesNewestLegacyPrefsWhenConfigPrefsAreMissing(@TempDir Path tempDir) throws Exception {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot;
        Path currentConfig = installRoot.resolve("config");
        Path snapshotDir = tempDir.resolve("snapshot");
        Path logPath = tempDir.resolve("apply-update.log");
        Path rootPrefs = installRoot.resolve("client-prefs.json");
        Path binPrefs = installRoot.resolve("bin").resolve("client-prefs.json");

        Files.createDirectories(installRoot);
        Files.writeString(rootPrefs, "root legacy prefs");
        Files.createDirectories(binPrefs.getParent());
        Files.writeString(binPrefs, "bin legacy prefs");
        Instant baseTime = Instant.now();
        Files.setLastModifiedTime(rootPrefs, FileTime.from(baseTime));
        Files.setLastModifiedTime(binPrefs, FileTime.from(baseTime.plusSeconds(1)));

        ReflectionTestUtils.invokeMethod(
                ApplicationUpdateInstallerHelper.class,
                "snapshotExistingConfig",
                snapshotDir,
                ApplicationUpdateInstallerHelper.configSourceDirs(installRoot, currentWorkingDir, currentConfig),
                ApplicationUpdateInstallerHelper.legacyPrefsFiles(installRoot, currentWorkingDir),
                logPath
        );

        assertThat(Files.readString(snapshotDir.resolve("client-prefs.json")), is("bin legacy prefs"));
    }

    @Test
    void installerHelperReplacesBinAndLibContentsWithoutMovingDirectories(@TempDir Path tempDir) throws Exception {
        Path installRoot = tempDir.resolve("install");
        Path stageDir = tempDir.resolve("staged");
        Path previousInstall = tempDir.resolve("previous-install");
        Path logPath = tempDir.resolve("apply-update.log");

        Files.createDirectories(installRoot.resolve("bin"));
        Files.createDirectories(installRoot.resolve("lib"));
        Path originalBin = installRoot.resolve("bin").toRealPath();
        Files.writeString(installRoot.resolve("bin").resolve("faf-moderator-client.bat"), "old launcher");
        Files.writeString(installRoot.resolve("lib").resolve("old.jar"), "old jar");

        Files.createDirectories(stageDir.resolve("bin"));
        Files.createDirectories(stageDir.resolve("lib"));
        Files.writeString(stageDir.resolve("bin").resolve("faf-moderator-client.bat"), "new launcher");
        Files.writeString(stageDir.resolve("lib").resolve("new.jar"), "new jar");

        ReflectionTestUtils.invokeMethod(
                ApplicationUpdateInstallerHelper.class,
                "replaceInstallDirectories",
                installRoot,
                stageDir,
                previousInstall,
                logPath
        );

        assertThat(Files.readString(installRoot.resolve("bin").resolve("faf-moderator-client.bat")), is("new launcher"));
        assertThat(installRoot.resolve("bin").toRealPath(), is(originalBin));
        assertThat(Files.exists(installRoot.resolve("lib").resolve("new.jar")), is(true));
        assertThat(Files.exists(previousInstall.resolve("bin").resolve("faf-moderator-client.bat")), is(true));
        assertThat(Files.exists(previousInstall.resolve("lib").resolve("old.jar")), is(true));
    }

    @Test
    void installerHelperRollbackDeletesTargetsThatDidNotExistBeforeReplacement(@TempDir Path tempDir) throws Exception {
        String originalOsName = System.getProperty("os.name");
        Path installRoot = tempDir.resolve("install");
        Path stageDir = tempDir.resolve("staged");
        Path previousInstall = tempDir.resolve("previous-install");
        Path logPath = tempDir.resolve("apply-update.log");

        Files.createDirectories(installRoot);
        Files.createDirectories(stageDir.resolve("bin"));
        Files.createDirectories(stageDir.resolve("lib"));
        Files.writeString(stageDir.resolve("bin").resolve("launcher.sh"), "new launcher");
        Files.writeString(stageDir.resolve("lib").resolve("new.jar"), "new jar");

        try {
            System.setProperty("os.name", "Windows 11");

            UndeclaredThrowableException thrown = assertThrows(UndeclaredThrowableException.class, () -> ReflectionTestUtils.invokeMethod(
                    ApplicationUpdateInstallerHelper.class,
                    "replaceInstallDirectories",
                    installRoot,
                    stageDir,
                    previousInstall,
                    logPath
            ));

            assertThat(thrown.getUndeclaredThrowable() instanceof IOException, is(true));
            assertThat(Files.exists(installRoot.resolve("bin")), is(false));
            assertThat(Files.exists(installRoot.resolve("lib")), is(false));
        } finally {
            System.setProperty("os.name", originalOsName);
        }
    }

    @Test
    void installerHelperPreservesLegacyPreferenceFiles(@TempDir Path tempDir) {
        Path installRoot = tempDir.resolve("install");
        Path currentWorkingDir = installRoot.resolve("bin");

        List<Path> prefsFiles = ApplicationUpdateInstallerHelper.legacyPrefsFiles(installRoot, currentWorkingDir);

        assertThat(prefsFiles.get(0), is(currentWorkingDir.resolve("client-prefs.json").toAbsolutePath().normalize()));
        assertThat(prefsFiles.get(1), is(installRoot.resolve("client-prefs.json").toAbsolutePath().normalize()));
        assertThat(prefsFiles.contains(installRoot.resolve("bin").resolve("client-prefs.json").toAbsolutePath().normalize()), is(true));
    }
}
