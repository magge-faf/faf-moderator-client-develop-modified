package com.faforever.moderatorclient.config;

import java.nio.file.Path;
public final class ApplicationPaths {
    public static final String CONFIGURATION_DIRECTORY_NAME = "config";
    public static final String LEGACY_CONFIGURATION_DIRECTORY_NAME = "data";
    public static final String LOG_DIRECTORY_NAME = "logs";
    public static final String PREFERENCES_FILE_NAME = "client-prefs.json";
    public static final String REPLAY_DIRECTORY_NAME = "replay";
    public static final String APP_HOME_PROPERTY = "faf.app.home";

    private ApplicationPaths() {
    }

    public static Path resolveWorkingDirectory() {
        String appHome = System.getProperty(APP_HOME_PROPERTY);
        if (appHome != null && !appHome.isBlank()) {
            return Path.of(appHome).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    public static Path resolveConfigurationDirectory() {
        return resolveWorkingDirectory().resolve(CONFIGURATION_DIRECTORY_NAME);
    }

    public static Path resolveConfigurationFile(String fileName) {
        return resolveConfigurationDirectory().resolve(fileName);
    }

    public static Path resolveLegacyConfigurationDirectory() {
        return resolveWorkingDirectory().resolve(LEGACY_CONFIGURATION_DIRECTORY_NAME);
    }

    public static Path resolveLogDirectory() {
        return resolveWorkingDirectory().resolve(LOG_DIRECTORY_NAME);
    }

    public static Path resolvePreferencesFile() {
        return resolveConfigurationDirectory().resolve(PREFERENCES_FILE_NAME);
    }

    public static Path resolveLegacyPreferencesFile() {
        return resolveWorkingDirectory().resolve(PREFERENCES_FILE_NAME);
    }

    public static Path resolveReplayDirectory() {
        return resolveWorkingDirectory().resolve(REPLAY_DIRECTORY_NAME);
    }
}
