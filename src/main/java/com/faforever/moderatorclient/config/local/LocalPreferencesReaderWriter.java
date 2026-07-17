package com.faforever.moderatorclient.config.local;

import com.faforever.moderatorclient.config.ApplicationPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalPreferencesReaderWriter {

    private final Path prefsPath = ApplicationPaths.resolvePreferencesFile();
    private final Path legacyPrefsPath = ApplicationPaths.resolveLegacyPreferencesFile();
    private final ObjectMapper objectMapper;

    /**
     * Reads preferences from disk.
     * Missing fields are automatically populated with defaults from the class.
     */
    public LocalPreferences read() {
        Path readPath = migrateLegacyPreferencesFile();
        if (Files.notExists(readPath)) {
            log.info("Preferences file does not exist. Using defaults.");
            return new LocalPreferences();
        }

        try (BufferedReader reader = Files.newBufferedReader(readPath)) {
            LocalPreferences localPreferences = objectMapper.readValue(reader, LocalPreferences.class);
            localPreferences.applyDefaultLayoutPreferences();
            return localPreferences;
        } catch (IOException e) {
            log.error("Failed to read preferences, using defaults", e);
            return new LocalPreferences();
        }
    }

    private Path migrateLegacyPreferencesFile() {
        if (Files.exists(prefsPath) || Files.notExists(legacyPrefsPath) || prefsPath.equals(legacyPrefsPath)) {
            return prefsPath;
        }

        try {
            if (prefsPath.getParent() != null && Files.notExists(prefsPath.getParent())) {
                Files.createDirectories(prefsPath.getParent());
            }
            Files.move(legacyPrefsPath, prefsPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved preferences file from {} to {}", legacyPrefsPath, prefsPath);
            return prefsPath;
        } catch (IOException e) {
            log.warn("Failed to move legacy preferences file from {} to {}. Reading legacy file for this run.",
                    legacyPrefsPath, prefsPath, e);
            return legacyPrefsPath;
        }
    }

    public boolean write(LocalPreferences localPreferences) {
        try {
            if (prefsPath.getParent() != null && Files.notExists(prefsPath.getParent())) {
                Files.createDirectories(prefsPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(prefsPath)) {
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(writer, localPreferences);
            }
            log.info("Preferences saved to {}", prefsPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to write preferences", e);
            return false;
        }
    }
}
