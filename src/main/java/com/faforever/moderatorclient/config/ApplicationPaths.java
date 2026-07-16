package com.faforever.moderatorclient.config;

import java.nio.file.Path;
public final class ApplicationPaths {
    public static final String CONFIGURATION_DIRECTORY_NAME = "config";
    public static final String LEGACY_CONFIGURATION_DIRECTORY_NAME = "data";
    public static final String PREFERENCES_FILE_NAME = "client-prefs.json";
    public static final String REPLAY_DIRECTORY_NAME = "replay";

    private ApplicationPaths() {
    }

    public static Path resolveWorkingDirectory() {
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    public static Path resolveConfigurationDirectory() {
        return resolveWorkingDirectory().resolve(CONFIGURATION_DIRECTORY_NAME);
    }

    public static Path resolveLegacyConfigurationDirectory() {
        return resolveWorkingDirectory().resolve(LEGACY_CONFIGURATION_DIRECTORY_NAME);
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
