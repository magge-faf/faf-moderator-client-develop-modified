package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalPreferencesReaderWriter {

    private final Path prefsPath = Paths.get("client-prefs.json");
    private final ObjectMapper objectMapper;

    /**
     * Reads preferences from disk.
     * Missing fields are automatically populated with defaults from the class.
     */
    public LocalPreferences read() {
        if (Files.notExists(prefsPath)) {
            log.info("Preferences file does not exist. Using defaults.");
            return new LocalPreferences();
        }

        try {
            return objectMapper.readValue(Files.newBufferedReader(prefsPath), LocalPreferences.class);
        } catch (IOException e) {
            log.error("Failed to read preferences, using defaults", e);
            return new LocalPreferences();
        }
    }

    public void write(LocalPreferences localPreferences) {
        try {
            if (prefsPath.getParent() != null && Files.notExists(prefsPath.getParent())) {
                Files.createDirectories(prefsPath.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(Files.newBufferedWriter(prefsPath), localPreferences);
            log.info("Preferences saved to {}", prefsPath);
        } catch (IOException e) {
            log.error("Failed to write preferences", e);
        }
    }
}
