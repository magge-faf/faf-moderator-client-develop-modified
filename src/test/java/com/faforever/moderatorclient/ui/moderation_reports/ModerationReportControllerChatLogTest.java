package com.faforever.moderatorclient.ui.moderation_reports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationReportControllerChatLogTest {

    @Test
    void extractNameHandlesRolePrefixedButtonText() {
        assertEquals("ReporterName", ModerationReportController.extractName("Reporter:\nReporterName [123]"));
        assertEquals("OffenderName", ModerationReportController.extractName("Offender:\nOffenderName [456]"));
    }

    @Test
    void extractNameHandlesUnprefixedButtonText() {
        assertEquals("PlayerName", ModerationReportController.extractName("PlayerName [789]"));
    }

    @Test
    void extractNameHandlesMissingButtonText() {
        assertEquals("", ModerationReportController.extractName(null));
    }
}
