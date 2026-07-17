package com.faforever.moderatorclient.ui;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class ViewHelperTest {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void formatBanExpiresAtShowsPermanentForNullExpiry() {
        assertThat(ViewHelper.formatBanExpiresAt(null), is("Permanent"));
    }

    @Test
    void formatBanExpiresAtShowsReadableFutureExpiry() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(2).plusHours(3);

        String result = ViewHelper.formatBanExpiresAt(expiresAt);

        assertThat(result, containsString(DATE_TIME_FORMATTER.format(expiresAt.atZoneSameInstant(ZoneId.systemDefault()))));
        assertThat(result, containsString("(in "));
    }

    @Test
    void formatBanExpiresAtShowsReadableExpiredExpiry() {
        OffsetDateTime expiresAt = OffsetDateTime.now().minusDays(2).minusHours(3);

        String result = ViewHelper.formatBanExpiresAt(expiresAt);

        assertThat(result, containsString(DATE_TIME_FORMATTER.format(expiresAt.atZoneSameInstant(ZoneId.systemDefault()))));
        assertThat(result, containsString("(expired "));
        assertThat(result, containsString(" ago)"));
    }
}
