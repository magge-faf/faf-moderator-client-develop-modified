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
            deleteIfExists(installDir.resolve("bin"), logPath);
            deleteIfExists(installDir.resolve("lib"), logPath);
            copyDirectoryContents(stageDir, installDir, logPath);

            if (!Files.isDirectory(installDir.resolve("bin")) || !Files.isDirectory(installDir.resolve("lib"))) {
                throw new IOException("Updated install is missing bin or lib after copy.");
            }

            restoreExistingConfig(snapshotDir, configTargetDirs(installDir, workingDir, currentConfigDir), legacyPrefsFiles(installDir, workingDir), logPath);
            startUpdatedClient(launcherPath, workingDir, logPath);
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
                workingDir.resolve(ApplicationPaths.LEGACY_CONFIGURATION_DIRECTORY_NAME),
                installDir.resolve(ApplicationPaths.LEGACY_CONFIGURATION_DIRECTORY_NAME)
        ));
    }

    static List<Path> configTargetDirs(Path installDir, Path workingDir, Path currentConfigDir) {
        return List.copyOf(uniquePaths(
                currentConfigDir,
                workingDir.resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME),
                installDir.resolve(ApplicationPaths.CONFIGURATION_DIRECTORY_NAME)
        ));
    }

    static List<Path> legacyPrefsFiles(Path installDir, Path workingDir) {
        return List.copyOf(uniquePaths(
                workingDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME),
                installDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME)
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
        for (Path sourceDir : sourceDirs) {
            if (Files.isDirectory(sourceDir)) {
                log(logPath, "Snapshotting existing config from '" + sourceDir + "' to '" + snapshotDir + "'.");
                deleteIfExists(snapshotDir, logPath);
                Files.createDirectories(snapshotDir);
                copyDirectoryContents(sourceDir, snapshotDir, logPath);
                break;
            }
        }

        for (Path prefsFile : legacyPrefsFiles) {
            if (Files.isRegularFile(prefsFile)) {
                log(logPath, "Snapshotting legacy preferences file '" + prefsFile + "'.");
                Files.createDirectories(snapshotDir);
                Files.copy(prefsFile, snapshotDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
                break;
            }
        }

        if (Files.notExists(snapshotDir)) {
            log(logPath, "No existing config folder or legacy preferences file found to restore.");
        }
    }

    private static void restoreExistingConfig(Path snapshotDir, List<Path> targetDirs, List<Path> legacyPrefsFiles, Path logPath) throws IOException {
        if (Files.notExists(snapshotDir)) {
            return;
        }

        for (Path targetDir : targetDirs) {
            log(logPath, "Restoring existing config to '" + targetDir + "'.");
            Files.createDirectories(targetDir);
            copyDirectoryContents(snapshotDir, targetDir, logPath);
        }

        Path snapshotPrefsFile = snapshotDir.resolve(ApplicationPaths.PREFERENCES_FILE_NAME);
        if (Files.isRegularFile(snapshotPrefsFile)) {
            for (Path prefsTarget : legacyPrefsFiles) {
                log(logPath, "Restoring legacy preferences file to '" + prefsTarget + "'.");
                Files.copy(snapshotPrefsFile, prefsTarget, StandardCopyOption.REPLACE_EXISTING);
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

    private static void log(Path logPath, String message) throws IOException {
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, "[" + LocalDateTime.now().format(LOG_TIMESTAMP) + "] " + message + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
