package com.faforever.moderatorclient.update;

import com.faforever.moderatorclient.config.ApplicationPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ApplicationUpdateInstallerHelper {
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ApplicationUpdateInstallerHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("Expected pid, installDir, stageDir, launcherPath, workingDir, currentConfigDir");
        }

        long pidToWait = Long.parseLong(args[0]);
        Path installDir = Path.of(args[1]).toAbsolutePath().normalize();
        Path stageDir = Path.of(args[2]).toAbsolutePath().normalize();
        Path launcherPath = Path.of(args[3]).toAbsolutePath().normalize();
        Path workingDir = Path.of(args[4]).toAbsolutePath().normalize();
        Path currentConfigDir = Path.of(args[5]).toAbsolutePath().normalize();
        Path sessionDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path logPath = sessionDir.resolve("apply-update.log");

        try {
            log(logPath, "Waiting for process " + pidToWait + " to exit.");
            ProcessHandle.of(pidToWait).ifPresent(handle -> handle.onExit().join());

            Path snapshotDir = sessionDir.resolve("config-snapshot");
            snapshotExistingConfig(snapshotDir, configSourceDirs(installDir, workingDir, currentConfigDir), legacyPrefsFiles(installDir, workingDir), logPath);

            log(logPath, "Applying update from '" + stageDir + "' to '" + installDir + "'.");
            replaceInstallDirectories(installDir, stageDir, sessionDir.resolve("previous-install"), logPath);

            restoreExistingConfig(snapshotDir, configTargetDirs(installDir, workingDir, currentConfigDir), logPath);
            startUpdatedClient(launcherPath, installDir, logPath);
            log(logPath, "Update finished.");
        } catch (Exception e) {
            log(logPath, "Update failed: " + e.getMessage());
            throw e;
        }
    }

    static List<Path> configSourceDirs(Path installDir, Path workingDir, Path currentConfigDir) {
        return List.copyOf(uniquePaths(
                currentConfigDir,
                workingDir.resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME),
                installDir.resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME),
                workingDir.resolve("bin").resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME),
                installDir.resolve("bin").resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME),
                workingDir.resolve(ApplicationPaths.LEGACY_CONFIGURATION_DIRECTORY_NAME),
                installDir.resolve(ApplicationPaths.LEGACY_CONFIGURATION_DIRECTORY_NAME),
                workingDir.resolve("bin").resolve(ApplicationPaths.LEGACY_CONFIGURATION_DIRECTORY_NAME),
                installDir.resolve("bin").resolve(ApplicationPaths.LEGACY_CONFIGURATION_DIRECTORY_NAME)
        ));
    }

    static List<Path> configTargetDirs(Path installDir, Path workingDir, Path currentConfigDir) {
        return List.copyOf(uniquePaths(
                currentConfigDir,
                installDir.resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME)
        ));
    }

    static List<Path> legacyPrefsFiles(Path installDir, Path workingDir) {
        return List.copyOf(uniquePaths(
                workingDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME),
                installDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME),
                workingDir.resolve("bin").resolve(ApplicationPaths.PREFERENCES_FILE_NAME),
                installDir.resolve("bin").resolve(ApplicationPaths.PREFERENCES_FILE_NAME)
        ));
    }

    private static Set<Path> uniquePaths(Path... paths) {
        Set<Path> unique = new LinkedHashSet<>();
        for (Path path : paths) {
            if (path != null) {
                unique.add(path.toAbsolutePath().normalize());
            }
        }
        return unique;
    }

    private static void snapshotExistingConfig(Path snapshotDir, List<Path> sourceDirs, List<Path> legacyPrefsFiles, Path logPath) throws IOException {
        boolean foundConfig = false;
        for (int index = sourceDirs.size() - 1; index >= 0; index--) {
            Path sourceDir = sourceDirs.get(index);
            if (Files.isDirectory(sourceDir)) {
                log(logPath, "Merging existing config from '" + sourceDir + "' into '" + snapshotDir + "'.");
                Files.createDirectories(snapshotDir);
                copyDirectoryContents(sourceDir, snapshotDir, logPath);
                foundConfig = true;
            }
        }

        Path snapshotPreferences = snapshotDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME);
        if (Files.notExists(snapshotPreferences)) {
            Path prefsFile = newestRegularFile(legacyPrefsFiles);
            if (prefsFile != null) {
                log(logPath, "Snapshotting legacy preferences file '" + prefsFile + "'.");
                Files.createDirectories(snapshotDir);
                Files.copy(prefsFile, snapshotPreferences, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (!foundConfig && Files.notExists(snapshotDir)) {
            log(logPath, "No existing config folder or legacy preferences file found to restore.");
        }
    }

    private static Path newestRegularFile(List<Path> files) throws IOException {
        Path newest = null;
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            if (newest == null || Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(newest)) > 0) {
                newest = file;
            }
        }
        return newest;
    }

    private static void restoreExistingConfig(Path snapshotDir, List<Path> targetDirs, Path logPath) throws IOException {
        if (Files.notExists(snapshotDir)) {
            return;
        }

        for (Path targetDir : targetDirs) {
            log(logPath, "Restoring existing config to '" + targetDir + "'.");
            Files.createDirectories(targetDir);
            copyDirectoryContents(snapshotDir, targetDir, logPath);
        }
    }

    private static void replaceInstallDirectories(Path installDir, Path stageDir, Path previousInstallDir, Path logPath) throws IOException {
        Path installBin = installDir.resolve("bin");
        Path installLib = installDir.resolve("lib");
        Path stagedBin = stageDir.resolve("bin");
        Path stagedLib = stageDir.resolve("lib");
        if (!Files.isDirectory(stagedBin) || !Files.isDirectory(stagedLib)) {
            throw new IOException("Staged update is missing bin or lib.");
        }

        deleteIfExists(previousInstallDir, logPath);
        Files.createDirectories(previousInstallDir);

        try {
            replaceDirectoryContents(installBin, stagedBin, previousInstallDir.resolve("bin"), logPath);
            replaceDirectoryContents(installLib, stagedLib, previousInstallDir.resolve("lib"), logPath);

            if (!Files.isRegularFile(installBin.resolve("faf-moderator-client.bat")) && isWindows()) {
                throw new IOException("Updated install is missing the Windows launcher after copy.");
            }
            if (!Files.isDirectory(installBin) || !Files.isDirectory(installLib)) {
                throw new IOException("Updated install is missing bin or lib after copy.");
            }
        } catch (IOException | RuntimeException e) {
            log(logPath, "Restoring previous install directory contents after update failure.");
            restoreDirectoryContents(previousInstallDir.resolve("bin"), installBin, logPath);
            restoreDirectoryContents(previousInstallDir.resolve("lib"), installLib, logPath);
            throw e;
        }
    }

    private static void replaceDirectoryContents(Path targetDir, Path stagedDir, Path previousDir, Path logPath) throws IOException {
        if (Files.isDirectory(targetDir)) {
            log(logPath, "Snapshotting current contents of '" + targetDir + "' to '" + previousDir + "'.");
            copyDirectory(targetDir, previousDir, logPath);
        }

        Files.createDirectories(targetDir);
        deleteDirectoryContents(targetDir, logPath);
        copyDirectory(stagedDir, targetDir, logPath);
    }

    private static void restoreDirectoryContents(Path previousDir, Path targetDir, Path logPath) throws IOException {
        if (Files.notExists(previousDir)) {
            return;
        }

        Files.createDirectories(targetDir);
        deleteDirectoryContents(targetDir, logPath);
        log(logPath, "Restoring previous contents of '" + targetDir + "' from '" + previousDir + "'.");
        copyDirectory(previousDir, targetDir, logPath);
    }

    private static void deleteDirectoryContents(Path directory, Path logPath) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }

        log(logPath, "Removing contents of '" + directory + "'.");
        try (var paths = Files.walk(directory)) {
            for (Path current : paths
                    .filter(path -> !path.equals(directory))
                    .sorted((left, right) -> right.compareTo(left))
                    .toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static void deleteIfExists(Path path, Path logPath) throws IOException {
        if (Files.notExists(path)) {
            return;
        }

        log(logPath, "Removing '" + path + "'.");
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static void copyDirectoryContents(Path sourceDir, Path targetDir, Path logPath) throws IOException {
        Files.createDirectories(targetDir);
        try (var paths = Files.list(sourceDir)) {
            for (Path source : paths.toList()) {
                Path target = targetDir.resolve(source.getFileName());
                log(logPath, "Copying '" + source + "' to '" + targetDir + "'.");
                if (Files.isDirectory(source)) {
                    copyDirectory(source, target, logPath);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyDirectory(Path sourceDir, Path targetDir, Path logPath) throws IOException {
        try (var paths = Files.walk(sourceDir)) {
            for (Path source : paths.toList()) {
                Path target = targetDir.resolve(sourceDir.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    log(logPath, "Copying '" + source + "' to '" + target + "'.");
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void startUpdatedClient(Path launcherPath, Path workingDir, Path logPath) throws IOException {
        log(logPath, "Starting '" + launcherPath + "'.");
        ProcessBuilder processBuilder;
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            processBuilder = new ProcessBuilder("cmd", "/c", "start", "", launcherPath.toString());
        } else {
            processBuilder = new ProcessBuilder(launcherPath.toString());
        }
        processBuilder.directory(workingDir.toFile());
        processBuilder.start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void log(Path logPath, String message) throws IOException {
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, "[" + LocalDateTime.now().format(LOG_TIMESTAMP) + "] " + message + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
