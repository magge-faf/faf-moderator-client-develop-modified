package com.faforever.moderatorclient.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LogMaintenanceServiceTest {

    @Test
    void archiveLegacyClientLogsMovesOnlyLegacyClientFiles(@TempDir Path tempDir) throws Exception {
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        Files.writeString(logsDir.resolve("client.log"), "old client log");
        Files.writeString(logsDir.resolve("client.log.2026-07-12.0.gz"), "rolled client log");
        Files.writeString(logsDir.resolve("application.log"), "current log");

        LogMaintenanceService service = new LogMaintenanceService(logsDir);

        service.archiveLegacyClientLogs();

        assertThat(Files.exists(logsDir.resolve("client.log")), is(false));
        assertThat(Files.exists(logsDir.resolve("client.log.2026-07-12.0.gz")), is(false));
        assertThat(Files.exists(logsDir.resolve("application.log")), is(true));
        assertThat(Files.exists(logsDir.resolve("legacy").resolve("client.log")), is(true));
        assertThat(Files.exists(logsDir.resolve("legacy").resolve("client.log.2026-07-12.0.gz")), is(true));
    }
}
