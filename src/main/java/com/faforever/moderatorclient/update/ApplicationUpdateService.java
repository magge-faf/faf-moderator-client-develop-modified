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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.BooleanSupplier;
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
    private static final int CONFIGURATION_BACKUP_SLOT_COUNT = 10;
    private static final int DOWNLOAD_BUFFER_SIZE = 64 * 1024;
    private static final long DOWNLOAD_PROGRESS_INTERVAL_NANOS = Duration.ofMillis(750).toNanos();

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

    public List<GithubRelease> fetchRecentReleases() throws IOException, InterruptedException {
        URI releasesUri = URI.create("https://api.github.com/repos/magge-faf/faf-moderator-client-develop-modified/releases?per_page=30");
        HttpRequest request = HttpRequest.newBuilder(releasesUri)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "faf-moderator-client")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub release lookup failed with HTTP " + response.statusCode());
        }

        return List.of(objectMapper.readValue(response.body(), GithubRelease[].class));
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
        return describeAutomaticUpdateUnavailableReason(release).isEmpty();
    }

    public boolean isPackagedReleaseInstall() {
        return resolveInstallLocation().isPresent();
    }

    public Optional<String> describeAutomaticUpdateUnavailableReason(GithubRelease release) {
        if (findMatchingAsset(release).isEmpty()) {
            return Optional.of("No compatible downloadable release zip was found for this platform in "
                    + release.displayName() + ".");
        }
        if (resolveInstallLocation().isEmpty()) {
            return Optional.of("This run does not look like a packaged release install.\n\n"
                    + "Self-update currently only works when the client was started from an unpacked release folder "
                    + "that contains the normal bin/ and lib/ directories.\n\n"
                    + "If you started the app from source, IntelliJ, Gradle, or some custom layout, install the release package manually.");
        }
        return describeInstallerRuntimeUnavailableReason();
    }

    public Path resolveDefaultBackupDirectory() {
        return resolveInstallLocation()
                .map(installLocation -> installLocation.installRoot().resolve("backup"))
                .orElseGet(() -> Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve("backup"));
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
        FolderStats stats = describeFolder(backupDir);
        return new BackupFolderStats(stats.directory(), stats.fileCount(), stats.totalBytes());
    }

    public UpdaterFolderStats describeUpdaterFolder() throws IOException {
        FolderStats stats = describeFolder(resolveUpdaterBaseDirectory());
        return new UpdaterFolderStats(stats.directory(), stats.fileCount(), stats.totalBytes());
    }

    public UpdaterFolderCleanupResult purgeUpdaterFolder() throws IOException {
        Path updaterDir = resolveUpdaterBaseDirectory();
        if (!Files.isDirectory(updaterDir)) {
            Files.createDirectories(updaterDir);
            return new UpdaterFolderCleanupResult(updaterDir, 0L, 0L);
        }

        long[] totals = new long[]{0L, 0L};
        try (var paths = Files.walk(updaterDir)) {
            paths
                    .filter(path -> !path.equals(updaterDir))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            if (Files.isRegularFile(path)) {
                                totals[0]++;
                                totals[1] += Files.size(path);
                            }
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }

        Files.createDirectories(updaterDir);
        return new UpdaterFolderCleanupResult(updaterDir, totals[0], totals[1]);
    }

    private FolderStats describeFolder(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return new FolderStats(directory, 0L, 0L);
        }

        try (var paths = Files.walk(directory)) {
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
            return new FolderStats(directory, totals[0], totals[1]);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    public Path createConfigurationBackupArchive() throws IOException {
        return createConfigurationBackupArchive(true);
    }

    public Path createManualConfigurationBackupArchive() throws IOException {
        return createConfigurationBackupArchive(false);
    }

    private Path createConfigurationBackupArchive(boolean useRotatingSlot) throws IOException {
        Path configurationDirectory = ApplicationPaths.resolveConfigurationDirectory();
        Files.createDirectories(configurationDirectory);

        Path backupDir = resolveConfiguredBackupDirectory();
        Files.createDirectories(backupDir);

        Path backupArchive = useRotatingSlot
                ? createConfigurationBackupArchivePath(backupDir)
                : createManualConfigurationBackupArchivePath(backupDir);
        Path stagingArchive = backupDir.resolve(backupArchive.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            zipDirectory(configurationDirectory, stagingArchive);
            moveArchiveAtomically(stagingArchive, backupArchive);
        } catch (IOException e) {
            Files.deleteIfExists(stagingArchive);
            throw e;
        }
        return backupArchive;
    }

    private void moveArchiveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean prepareUpdateAndLaunchInstaller(GithubRelease release, Consumer<String> statusCallback, BooleanSupplier restartConfirmation)
            throws IOException, InterruptedException {
        InstallLocation installLocation = resolveInstallLocation()
                .orElseThrow(() -> new IllegalStateException("Automatic update is only available from an unpacked release folder."));
        GithubReleaseAsset asset = findMatchingAsset(release)
                .orElseThrow(() -> new IllegalStateException("No compatible release asset was found for this platform."));

        Path sessionDir = createUpdaterSessionDirectory(release.tagName());
        Path downloadedZip = sessionDir.resolve(asset.name());
        Path stagedInstall = sessionDir.resolve("staged");

        reportStatus(statusCallback, "Downloading " + asset.name() + "...");
        downloadAsset(asset.browserDownloadUrl(), downloadedZip, statusCallback);

        reportStatus(statusCallback, "Extracting release files...");
        extractZip(downloadedZip, stagedInstall);
        reportStatus(statusCallback, "Validating extracted release layout...");
        validateStagedInstall(stagedInstall);

        reportStatus(statusCallback, "Creating backup zip of the current installation...");
        Path backupArchive = createBackupArchivePath(installLocation.installRoot());
        zipDirectory(installLocation.installRoot(), backupArchive, Set.of(resolveDefaultBackupDirectory()));
        reportStatus(statusCallback, "Backup saved to " + backupArchive);

        reportStatus(statusCallback, "Ready to apply the update to the FAF Moderator Client. Your PC will not restart.");
        if (restartConfirmation != null && !restartConfirmation.getAsBoolean()) {
            reportStatus(statusCallback, "Update prepared. Client restart canceled by user.");
            return false;
        }

        reportStatus(statusCallback, "Writing installer helper in " + sessionDir);
        launchInstallerHelper(
                installLocation,
                stagedInstall,
                sessionDir,
                ApplicationPaths.resolveWorkingDirectory(),
                ApplicationPaths.resolveConfigurationDirectory()
        );
        reportStatus(statusCallback, "Installer helper launched. The FAF Moderator Client will close and open again after files are replaced. Your PC will not restart.");
        log.info("Prepared update {}. Backup saved to {}", release.tagName(), backupArchive);
        return true;
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

    private void downloadAsset(String downloadUrl, Path target, Consumer<String> statusCallback)
            throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("User-Agent", "faf-moderator-client")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Release asset download failed with HTTP " + response.statusCode());
        }

        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long downloadedBytes = 0L;
        long startedAt = System.nanoTime();
        long lastReportedAt = startedAt;
        byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];

        try (InputStream inputStream = response.body();
             var outputStream = Files.newOutputStream(target)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                downloadedBytes += read;

                long now = System.nanoTime();
                if (now - lastReportedAt >= DOWNLOAD_PROGRESS_INTERVAL_NANOS) {
                    reportStatus(statusCallback, formatDownloadProgress(downloadedBytes, totalBytes, startedAt, now));
                    lastReportedAt = now;
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }

        long finishedAt = System.nanoTime();
        reportStatus(statusCallback, "Download complete: " + formatBytes(downloadedBytes)
                + " at " + formatBytesPerSecond(downloadedBytes, startedAt, finishedAt));
    }

    static String formatDownloadProgress(long downloadedBytes, long totalBytes, long startedAtNanos, long nowNanos) {
        String speed = formatBytesPerSecond(downloadedBytes, startedAtNanos, nowNanos);
        if (totalBytes > 0) {
            double percent = downloadedBytes * 100d / totalBytes;
            return String.format(
                    Locale.ROOT,
                    "Downloading: %.0f%% (%s / %s, %s)",
                    Math.min(100d, percent),
                    formatBytes(downloadedBytes),
                    formatBytes(totalBytes),
                    speed
            );
        }

        return "Downloading: " + formatBytes(downloadedBytes) + " (" + speed + ")";
    }

    private static String formatBytesPerSecond(long downloadedBytes, long startedAtNanos, long nowNanos) {
        long elapsedNanos = Math.max(1L, nowNanos - startedAtNanos);
        double bytesPerSecond = downloadedBytes / (elapsedNanos / 1_000_000_000d);
        return formatBytes(bytesPerSecond) + "/s";
    }

    private static String formatBytes(double bytes) {
        double megabytes = bytes / (1024d * 1024d);
        if (megabytes >= 1d) {
            return String.format(Locale.ROOT, "%.1f MB", megabytes);
        }
        return String.format(Locale.ROOT, "%.0f KB", bytes / 1024d);
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
                if (normalized.isBlank()) {
                    continue;
                }
                if (!normalized.contains("/")) {
                    if (entry.isDirectory()) {
                        continue;
                    }
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

    public Path resolveUpdaterBaseDirectory() {
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

        String installName = installRoot.getFileName() == null ? "faf-moderator-client" : installRoot.getFileName().toString();
        String backupName = installName + "-" + normalizeVersion(ApplicationVersion.CURRENT_VERSION) + "-"
                + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + ".zip";
        return backupDir.resolve(backupName);
    }

    private Path createConfigurationBackupArchivePath(Path backupDir) throws IOException {
        for (int slot = 1; slot <= CONFIGURATION_BACKUP_SLOT_COUNT; slot++) {
            Path candidate = backupDir.resolve(configurationBackupSlotName(slot));
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }

        Path oldestBackup = backupDir.resolve(configurationBackupSlotName(1));
        for (int slot = 2; slot <= CONFIGURATION_BACKUP_SLOT_COUNT; slot++) {
            Path candidate = backupDir.resolve(configurationBackupSlotName(slot));
            if (Files.getLastModifiedTime(candidate).compareTo(Files.getLastModifiedTime(oldestBackup)) < 0) {
                oldestBackup = candidate;
            }
        }
        return oldestBackup;
    }

    private Path createManualConfigurationBackupArchivePath(Path backupDir) {
        String backupName = "config-manual-backup-" + BACKUP_TIMESTAMP.format(LocalDateTime.now());
        Path candidate = backupDir.resolve(backupName + ".zip");
        for (int index = 2; Files.exists(candidate); index++) {
            candidate = backupDir.resolve(backupName + "-" + index + ".zip");
        }
        return candidate;
    }

    private String configurationBackupSlotName(int slot) {
        return "config-backup-%02d.zip".formatted(slot);
    }

    private void zipDirectory(Path sourceDirectory, Path zipPath) throws IOException {
        zipDirectory(sourceDirectory, zipPath, Set.of());
    }

    private void zipDirectory(Path sourceDirectory, Path zipPath, Set<Path> excludedDirectories) throws IOException {
        Set<Path> normalizedExclusions = excludedDirectories.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(Collectors.toSet());
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(sourceDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toAbsolutePath().normalize().equals(zipPath.toAbsolutePath().normalize()))
                    .filter(path -> normalizedExclusions.stream().noneMatch(excluded -> path.toAbsolutePath().normalize().startsWith(excluded)))
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

    private void launchInstallerHelper(
            InstallLocation installLocation,
            Path stagedInstall,
            Path sessionDirectory,
            Path currentWorkingDirectory,
            Path currentConfigurationDirectory
    ) throws IOException {
        Path helperClasspath = copyInstallerHelperClasspath(sessionDirectory);
        List<String> command = new ArrayList<>();
        command.add(resolveJavaExecutable().toString());
        command.add("-cp");
        command.add(helperClasspath.toString());
        command.add(ApplicationUpdateInstallerHelper.class.getName());
        command.add(String.valueOf(ProcessHandle.current().pid()));
        command.add(installLocation.installRoot().toString());
        command.add(stagedInstall.toString());
        command.add(installLocation.launcherScript().toString());
        command.add(currentWorkingDirectory.toString());
        command.add(currentConfigurationDirectory.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(sessionDirectory.toFile());
        processBuilder.start();
    }

    private Path copyInstallerHelperClasspath(Path sessionDirectory) throws IOException {
        Path codeSource;
        try {
            codeSource = Path.of(ApplicationUpdateInstallerHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
        } catch (Exception e) {
            throw new IOException("Failed to locate update helper classpath", e);
        }
        if (Files.isRegularFile(codeSource)) {
            Path helperJar = sessionDirectory.resolve("update-helper.jar");
            Files.copy(codeSource, helperJar, StandardCopyOption.REPLACE_EXISTING);
            return helperJar;
        }
        return codeSource;
    }

    private Path resolveJavaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
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

    private Optional<String> describeInstallerRuntimeUnavailableReason() {
        Path javaExecutable = resolveJavaExecutable();
        if (Files.isRegularFile(javaExecutable)) {
            return Optional.empty();
        }

        return Optional.of("The Java runtime used to start this client could not be found at "
                + javaExecutable + ".\n\n"
                + "Start the FAF Moderator Client from a valid Java runtime and try the automatic update again. "
                + "You can still use Manual Download.");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void reportStatus(Consumer<String> statusCallback, String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }

    private record InstallLocation(Path installRoot, Path launcherScript) {
    }

    public record BackupFolderStats(Path directory, long fileCount, long totalBytes) {
    }

    public record UpdaterFolderStats(Path directory, long fileCount, long totalBytes) {
    }

    public record UpdaterFolderCleanupResult(Path directory, long deletedFileCount, long deletedBytes) {
    }

    private record FolderStats(Path directory, long fileCount, long totalBytes) {
    }

}
