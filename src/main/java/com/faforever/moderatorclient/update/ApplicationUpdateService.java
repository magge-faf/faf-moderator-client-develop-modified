package com.faforever.moderatorclient.update;

import com.faforever.moderatorclient.Launcher;
import com.faforever.moderatorclient.config.ApplicationPaths;
import com.faforever.moderatorclient.config.ApplicationVersion;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationUpdateService {
    private static final URI LATEST_RELEASE_URI =
            URI.create("https://api.github.com/repos/magge-faf/faf-moderator-client-develop-modified/releases/latest");
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectMapper objectMapper;
    private final LocalPreferences localPreferences;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public Optional<GithubRelease> fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_URI)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "faf-moderator-client")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub release lookup failed with HTTP " + response.statusCode());
        }

        return Optional.ofNullable(objectMapper.readValue(response.body(), GithubRelease.class));
    }

    public boolean isNewerVersion(String latestVersion) {
        return compareVersions(latestVersion, ApplicationVersion.CURRENT_VERSION) > 0;
    }

    public boolean shouldShowUpdate(GithubRelease release, LocalPreferences.VersionReminder reminder) {
        if (release == null || !isNewerVersion(release.tagName())) {
            return false;
        }

        String reminderVersion = blankToNull(reminder.getReminderVersionTag());
        if (reminderVersion != null && compareVersions(release.tagName(), reminderVersion) > 0) {
            return true;
        }

        long nextReminderEpoch = reminder.getEffectiveNextReminderEpoch();
        return nextReminderEpoch == 0 || System.currentTimeMillis() >= nextReminderEpoch;
    }

    public Optional<GithubReleaseAsset> findMatchingAsset(GithubRelease release) {
        List<GithubReleaseAsset> zipAssets = release.assets().stream()
                .filter(GithubReleaseAsset::isZipAsset)
                .toList();

        if (zipAssets.isEmpty()) {
            return Optional.empty();
        }

        for (String candidateSuffix : platformAssetSuffixes()) {
            Optional<GithubReleaseAsset> match = zipAssets.stream()
                    .filter(asset -> asset.name() != null && asset.name().toLowerCase(Locale.ROOT).endsWith(candidateSuffix))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }

        return zipAssets.size() == 1 ? Optional.of(zipAssets.getFirst()) : Optional.empty();
    }

    public boolean canSelfUpdate(GithubRelease release) {
        return findMatchingAsset(release).isPresent() && resolveInstallLocation().isPresent();
    }

    public Path resolveDefaultBackupDirectory() {
        return resolveInstallLocation()
                .map(installLocation -> installLocation.installRoot().resolve("logs"))
                .orElseGet(() -> Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve("logs"));
    }

    public Path resolveConfiguredBackupDirectory() {
        String configuredDirectory = blankToNull(localPreferences.getTabSettings().getUpdateBackupFolder());
        if (configuredDirectory == null) {
            return resolveDefaultBackupDirectory();
        }
        return Path.of(configuredDirectory).toAbsolutePath().normalize();
    }

    public BackupFolderStats describeBackupFolder() throws IOException {
        Path backupDir = resolveConfiguredBackupDirectory();
        if (!Files.isDirectory(backupDir)) {
            return new BackupFolderStats(backupDir, 0L, 0L);
        }

        try (var paths = Files.walk(backupDir)) {
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
            return new BackupFolderStats(backupDir, totals[0], totals[1]);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    public BackupPurgeResult purgeBackupFilesOlderThan(int days) throws IOException {
        Path backupDir = resolveConfiguredBackupDirectory();
        if (days <= 0 || !Files.isDirectory(backupDir)) {
            return new BackupPurgeResult(backupDir, days, 0L, 0L);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        List<Path> pathsToDelete;
        try (var paths = Files.walk(backupDir)) {
            pathsToDelete = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isOlderThan(path, cutoff))
                    .toList();
        }

        long deletedFiles = 0L;
        long deletedBytes = 0L;
        for (Path path : pathsToDelete) {
            long size = Files.size(path);
            Files.deleteIfExists(path);
            deletedFiles++;
            deletedBytes += size;
        }

        return new BackupPurgeResult(backupDir, days, deletedFiles, deletedBytes);
    }

    public Path createConfigurationBackupArchive() throws IOException {
        Path configurationDirectory = ApplicationPaths.resolveConfigurationDirectory();
        Files.createDirectories(configurationDirectory);

        Path backupDir = resolveConfiguredBackupDirectory();
        Files.createDirectories(backupDir);
        purgeConfiguredBackupFilesIfEnabled();

        Path backupArchive = backupDir.resolve("config-backup-" + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + ".zip");
        zipDirectory(configurationDirectory, backupArchive);
        return backupArchive;
    }

    public void prepareUpdateAndLaunchInstaller(GithubRelease release, Consumer<String> statusCallback)
            throws IOException, InterruptedException {
        InstallLocation installLocation = resolveInstallLocation()
                .orElseThrow(() -> new IllegalStateException("Automatic update is only available from an unpacked release folder."));
        GithubReleaseAsset asset = findMatchingAsset(release)
                .orElseThrow(() -> new IllegalStateException("No compatible release asset was found for this platform."));

        Path sessionDir = createUpdaterSessionDirectory(release.tagName());
        Path downloadedZip = sessionDir.resolve(asset.name());
        Path stagedInstall = sessionDir.resolve("staged");

        reportStatus(statusCallback, "Downloading " + asset.name() + "...");
        downloadAsset(asset.browserDownloadUrl(), downloadedZip);

        reportStatus(statusCallback, "Extracting release files...");
        extractZip(downloadedZip, stagedInstall);
        validateStagedInstall(stagedInstall);

        reportStatus(statusCallback, "Creating backup zip of the current installation...");
        Path backupArchive = createBackupArchivePath(installLocation.installRoot());
        zipDirectory(installLocation.installRoot(), backupArchive);

        reportStatus(statusCallback, "Scheduling restart...");
        launchInstallerHelper(installLocation, stagedInstall, sessionDir);
        log.info("Prepared update {}. Backup saved to {}", release.tagName(), backupArchive);
    }

    static int compareVersions(String left, String right) {
        return new ComparableVersion(normalizeVersion(left)).compareTo(new ComparableVersion(normalizeVersion(right)));
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "0";
        }

        String normalized = version.trim().replaceFirst("^[^0-9]+", "");
        return normalized.isBlank() ? "0" : normalized;
    }

    private void downloadAsset(String downloadUrl, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("User-Agent", "faf-moderator-client")
                .GET()
                .build();

        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Release asset download failed with HTTP " + response.statusCode());
        }
    }

    private void extractZip(Path zipFile, Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);

        String sharedPrefix = resolveSharedTopLevelDirectory(zipFile);
        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = trimLeadingDirectory(entry.getName(), sharedPrefix);
                if (entryName.isBlank()) {
                    continue;
                }

                Path target = targetDirectory.resolve(entryName).normalize();
                if (!target.startsWith(targetDirectory)) {
                    throw new IOException("Refusing to extract zip entry outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (var inputStream = archive.getInputStream(entry)) {
                    Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String resolveSharedTopLevelDirectory(Path zipFile) throws IOException {
        String sharedPrefix = null;
        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }

                String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                if (normalized.isBlank() || !normalized.contains("/")) {
                    return null;
                }

                String prefix = normalized.substring(0, normalized.indexOf('/') + 1);
                if (sharedPrefix == null) {
                    sharedPrefix = prefix;
                } else if (!sharedPrefix.equals(prefix)) {
                    return null;
                }
            }
        }

        return sharedPrefix;
    }

    private String trimLeadingDirectory(String entryName, String sharedPrefix) {
        if (sharedPrefix == null || sharedPrefix.isBlank()) {
            return entryName;
        }
        return entryName.startsWith(sharedPrefix) ? entryName.substring(sharedPrefix.length()) : entryName;
    }

    private void validateStagedInstall(Path stagedInstall) {
        if (!Files.isDirectory(stagedInstall.resolve("bin")) || !Files.isDirectory(stagedInstall.resolve("lib"))) {
            throw new IllegalStateException("Downloaded release archive does not contain the expected bin/lib layout.");
        }
    }

    private Path createUpdaterSessionDirectory(String versionTag) throws IOException {
        Path baseDir = resolveUpdaterBaseDirectory();
        Files.createDirectories(baseDir);

        String sanitizedVersion = (versionTag == null || versionTag.isBlank()) ? "unknown" : versionTag.replaceAll("[^A-Za-z0-9._-]", "_");
        Path sessionDir = baseDir.resolve("update-" + sanitizedVersion + "-" + UUID.randomUUID());
        Files.createDirectories(sessionDir);
        return sessionDir;
    }

    private Path resolveUpdaterBaseDirectory() {
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "faf-moderator-client", "updater");
            }
        }

        return Path.of(System.getProperty("user.home"), ".faf-moderator-client", "updater");
    }

    private Path createBackupArchivePath(Path installRoot) throws IOException {
        Path backupDir = resolveConfiguredBackupDirectory();
        Files.createDirectories(backupDir);
        purgeConfiguredBackupFilesIfEnabled();

        String installName = installRoot.getFileName() == null ? "faf-moderator-client" : installRoot.getFileName().toString();
        String backupName = installName + "-" + normalizeVersion(ApplicationVersion.CURRENT_VERSION) + "-"
                + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + ".zip";
        return backupDir.resolve(backupName);
    }

    private void zipDirectory(Path sourceDirectory, Path zipPath) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(sourceDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toAbsolutePath().normalize().equals(zipPath.toAbsolutePath().normalize()))
                    .forEach(path -> {
                        Path relativePath = sourceDirectory.relativize(path);
                        ZipEntry entry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                        try {
                            zipOutputStream.putNextEntry(entry);
                            Files.copy(path, zipOutputStream);
                            zipOutputStream.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to write " + path + " into backup archive", e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private void launchInstallerHelper(InstallLocation installLocation, Path stagedInstall, Path sessionDirectory) throws IOException {
        Path scriptPath = sessionDirectory.resolve(isWindows() ? "apply-update.ps1" : "apply-update.sh");
        Files.writeString(scriptPath, buildInstallerScript(installLocation, stagedInstall), StandardCharsets.UTF_8);
        if (!isWindows()) {
            scriptPath.toFile().setExecutable(true, false);
        }

        ProcessBuilder processBuilder;
        if (isWindows()) {
            processBuilder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-WindowStyle",
                    "Hidden",
                    "-File",
                    scriptPath.toString()
            );
        } else {
            processBuilder = new ProcessBuilder("sh", scriptPath.toString());
        }

        processBuilder.start();
    }

    private String buildInstallerScript(InstallLocation installLocation, Path stagedInstall) {
        if (isWindows()) {
            return """
                    $ErrorActionPreference = 'Stop'
                    $ProgressPreference = 'SilentlyContinue'
                    $pidToWait = %d
                    $installDir = '%s'
                    $stageDir = '%s'
                    $launcherPath = '%s'
                    $workingDir = '%s'

                    while (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue) {
                        Start-Sleep -Milliseconds 500
                    }

                    $binDir = Join-Path $installDir 'bin'
                    $libDir = Join-Path $installDir 'lib'
                    if (Test-Path -LiteralPath $binDir) { Remove-Item -LiteralPath $binDir -Recurse -Force }
                    if (Test-Path -LiteralPath $libDir) { Remove-Item -LiteralPath $libDir -Recurse -Force }

                    Get-ChildItem -LiteralPath $stageDir -Force | ForEach-Object {
                        Copy-Item -LiteralPath $_.FullName -Destination $installDir -Recurse -Force
                    }

                    Start-Process -FilePath $launcherPath -WorkingDirectory $workingDir
                    """.formatted(
                    ProcessHandle.current().pid(),
                    escapeForPowerShell(installLocation.installRoot()),
                    escapeForPowerShell(stagedInstall),
                    escapeForPowerShell(installLocation.launcherScript()),
                    escapeForPowerShell(installLocation.installRoot())
            );
        }

        return """
                #!/usr/bin/env sh
                set -eu
                PID=%d
                INSTALL_DIR='%s'
                STAGE_DIR='%s'
                LAUNCHER_PATH='%s'

                while kill -0 "$PID" 2>/dev/null; do
                  sleep 1
                done

                rm -rf "$INSTALL_DIR/bin" "$INSTALL_DIR/lib"
                cp -R "$STAGE_DIR/." "$INSTALL_DIR/"
                chmod +x "$LAUNCHER_PATH" || true
                nohup "$LAUNCHER_PATH" >/dev/null 2>&1 &
                """.formatted(
                ProcessHandle.current().pid(),
                escapeForShell(installLocation.installRoot()),
                escapeForShell(stagedInstall),
                escapeForShell(installLocation.launcherScript())
        );
    }

    private Optional<InstallLocation> resolveInstallLocation() {
        try {
            Path codeSource = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();

            List<Path> candidates = new ArrayList<>();
            candidates.add(Files.isDirectory(codeSource) ? codeSource : codeSource.getParent());

            Path current = codeSource;
            for (int i = 0; i < 6 && current != null; i++) {
                current = current.getParent();
                if (current != null) {
                    candidates.add(current);
                }
            }

            for (Path candidate : candidates) {
                if (candidate == null) {
                    continue;
                }

                Path root = "lib".equalsIgnoreCase(candidate.getFileName() == null ? "" : candidate.getFileName().toString())
                        ? candidate.getParent()
                        : candidate;
                if (root == null) {
                    continue;
                }

                if (Files.isDirectory(root.resolve("bin")) && Files.isDirectory(root.resolve("lib"))) {
                    Path launcherScript = resolveLauncherScript(root);
                    if (launcherScript != null) {
                        return Optional.of(new InstallLocation(root, launcherScript));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve packaged install location", e);
        }

        return Optional.empty();
    }

    private Path resolveLauncherScript(Path installRoot) throws IOException {
        Path binDirectory = installRoot.resolve("bin");
        String extension = isWindows() ? ".bat" : ".sh";

        try (var files = Files.list(binDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                    .sorted((left, right) -> {
                        boolean leftPreferred = left.getFileName().toString().startsWith("faf-moderator-client");
                        boolean rightPreferred = right.getFileName().toString().startsWith("faf-moderator-client");
                        return Boolean.compare(rightPreferred, leftPreferred);
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    private List<String> platformAssetSuffixes() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        if (osName.contains("win")) {
            return List.of("-win.zip");
        }
        if (osName.contains("mac")) {
            if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                return List.of("-mac-aarch64.zip", "-mac.zip");
            }
            return List.of("-mac.zip");
        }
        return List.of("-linux.zip");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void purgeConfiguredBackupFilesIfEnabled() {
        int retentionDays = localPreferences.getTabSettings().getUpdateBackupAutoPurgeDays();
        if (retentionDays <= 0) {
            return;
        }
        try {
            BackupPurgeResult result = purgeBackupFilesOlderThan(retentionDays);
            if (result.deletedFileCount() > 0) {
                log.info("Auto-purged {} update archive files ({} bytes) older than {} days from {}",
                        result.deletedFileCount(),
                        result.deletedBytes(),
                        result.ageDays(),
                        result.directory());
            }
        } catch (IOException e) {
            log.warn("Failed to auto-purge update archives from {}", resolveConfiguredBackupDirectory(), e);
        }
    }

    private boolean isOlderThan(Path path, Instant cutoff) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            return lastModifiedTime.toInstant().isBefore(cutoff);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void reportStatus(Consumer<String> statusCallback, String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }

    private String escapeForPowerShell(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    private String escapeForShell(Path path) {
        return path.toAbsolutePath().toString().replace("'", "'\"'\"'");
    }

    private record InstallLocation(Path installRoot, Path launcherScript) {
    }

    public record BackupFolderStats(Path directory, long fileCount, long totalBytes) {
    }

    public record BackupPurgeResult(Path directory, int ageDays, long deletedFileCount, long deletedBytes) {
    }
}
