package com.faforever.moderatorclient.ui.moderation_reports;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

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

    @Test
    void formatGameAgeUsesCompactRelativeUnits() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-07T12:00:00+02:00");

        assertEquals("just now", ModerationReportController.formatGameAge(now.minusSeconds(30), now));
        assertEquals("50m ago", ModerationReportController.formatGameAge(now.minusMinutes(50), now));
        assertEquals("2h ago", ModerationReportController.formatGameAge(now.minusHours(2), now));
        assertEquals("3d ago", ModerationReportController.formatGameAge(now.minusDays(3), now));
    }
}
