package com.faforever.moderatorclient.ui;

import com.faforever.moderatorclient.config.ApplicationPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MainControllerPathTest {

    @Test
    void configurationFolderUsesAppHomeInsteadOfBinWorkingDirectory(@TempDir Path tempDir) {
        String originalUserDir = System.getProperty("user.dir");
        String originalAppHome = System.getProperty("faf.app.home");
        try {
            Path appHome = tempDir.resolve("install");
            System.setProperty("user.dir", appHome.resolve("bin").toString());
            System.setProperty("faf.app.home", appHome.toString());

            assertThat(ApplicationPaths.resolveConfigurationDirectory(), is(appHome.resolve("config")));
        } finally {
            System.setProperty("user.dir", originalUserDir);
            if (originalAppHome == null) {
                System.clearProperty("faf.app.home");
            } else {
                System.setProperty("faf.app.home", originalAppHome);
            }
        }
    }
}
