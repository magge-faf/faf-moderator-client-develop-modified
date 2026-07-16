package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LocalPreferencesReaderWriterTest {

    @Test
    void preferencesAreReadFromConfigFolder(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            Path configDir = tempDir.resolve("config");
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("client-prefs.json"), """
                    {
                      "autoLogin": {
                        "enabled": false,
                        "environment": "faforever.com"
                      }
                    }
                    """);

            LocalPreferences preferences = new LocalPreferencesReaderWriter(new ObjectMapper()).read();

            assertThat(preferences.getAutoLogin().isEnabled(), is(false));
            assertThat(preferences.getAutoLogin().getEnvironment(), is("faforever.com"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void legacyRootPreferencesFileIsMovedToConfigFolder(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            Path legacyPrefs = tempDir.resolve("client-prefs.json");
            Path configPrefs = tempDir.resolve("config").resolve("client-prefs.json");
            Files.writeString(legacyPrefs, """
                    {
                      "autoLogin": {
                        "enabled": false,
                        "environment": "faforever.com"
                      }
                    }
                    """);

            LocalPreferences preferences = new LocalPreferencesReaderWriter(new ObjectMapper()).read();

            assertThat(preferences.getAutoLogin().isEnabled(), is(false));
            assertThat(preferences.getAutoLogin().getEnvironment(), is("faforever.com"));
            assertThat(Files.exists(configPrefs), is(true));
            assertThat(Files.exists(legacyPrefs), is(false));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
