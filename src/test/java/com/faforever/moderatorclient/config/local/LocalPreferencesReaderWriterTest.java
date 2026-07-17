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
        String originalAppHome = System.getProperty("faf.app.home");
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
            restoreAppHome(originalAppHome);
        }
    }

    @Test
    void legacyRootPreferencesFileIsMovedToConfigFolder(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        String originalAppHome = System.getProperty("faf.app.home");
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
            restoreAppHome(originalAppHome);
        }
    }

    @Test
    void appHomePropertyOverridesBinWorkingDirectory(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        String originalAppHome = System.getProperty("faf.app.home");
        try {
            Path appHome = tempDir.resolve("install");
            Path binDir = appHome.resolve("bin");
            System.setProperty("user.dir", binDir.toString());
            System.setProperty("faf.app.home", appHome.toString());
            Path configPrefs = appHome.resolve("config").resolve("client-prefs.json");
            Files.createDirectories(configPrefs.getParent());
            Files.writeString(configPrefs, """
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
            assertThat(Files.exists(binDir.resolve("config").resolve("client-prefs.json")), is(false));
        } finally {
            System.setProperty("user.dir", originalUserDir);
            restoreAppHome(originalAppHome);
        }
    }

    private static void restoreAppHome(String originalAppHome) {
        if (originalAppHome == null) {
            System.clearProperty("faf.app.home");
        } else {
            System.setProperty("faf.app.home", originalAppHome);
        }
    }
}
