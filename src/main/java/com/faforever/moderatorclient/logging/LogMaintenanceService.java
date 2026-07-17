package com.faforever.moderatorclient.logging;

import com.faforever.moderatorclient.config.ApplicationPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Service
@Slf4j
public class LogMaintenanceService {
    private final Path logsDirectory;

    public LogMaintenanceService() {
        this(ApplicationPaths.resolveLogDirectory());
    }

    LogMaintenanceService(Path logsDirectory) {
        this.logsDirectory = logsDirectory;
    }

    public void archiveLegacyClientLogs() {
        if (!Files.isDirectory(logsDirectory)) {
            return;
        }

        Path legacyDirectory = logsDirectory.resolve("legacy");
        try (Stream<Path> files = Files.list(logsDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("client.log"))
                    .forEach(path -> moveToLegacy(path, legacyDirectory));
        } catch (IOException e) {
            log.warn("Failed to inspect legacy log files in {}", logsDirectory.toAbsolutePath(), e);
        }
    }

    private void moveToLegacy(Path source, Path legacyDirectory) {
        try {
            Files.createDirectories(legacyDirectory);

            Path destination = legacyDirectory.resolve(source.getFileName().toString());
            if (Files.exists(destination)) {
                destination = legacyDirectory.resolve(System.currentTimeMillis() + "-" + source.getFileName());
            }

            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved legacy log {} to {}", source.getFileName(), destination.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to move legacy log {}", source.toAbsolutePath(), e);
        }
    }
}
