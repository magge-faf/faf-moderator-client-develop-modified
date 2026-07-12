package com.faforever.moderatorclient.update;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ApplicationUpdateServiceTest {

    private final ApplicationUpdateService service = new ApplicationUpdateService(new ObjectMapper(), new LocalPreferences());

    @Test
    void newerReleaseOverridesFutureSnoozeForOlderRelease() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        reminder.setReminderVersionTag("v2026-07-01");
        reminder.setNextReminderEpoch(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(14));

        GithubRelease release = new GithubRelease("v2026-07-08", "v2026-07-08", "https://example.invalid", "", null, List.of());

        assertThat(service.shouldShowUpdate(release, reminder), is(true));
    }

    @Test
    void legacyReminderTimestampStillSuppressesPopupForThreeDays() {
        LocalPreferences.VersionReminder reminder = new LocalPreferences.VersionReminder();
        reminder.setLastReminderEpoch(System.currentTimeMillis());

        GithubRelease release = new GithubRelease("v9999-01-01", "v9999-01-01", "https://example.invalid", "", null, List.of());

        assertThat(service.shouldShowUpdate(release, reminder), is(false));
    }

    @Test
    void singleZipAssetIsUsedAsFallback() {
        GithubReleaseAsset asset = new GithubReleaseAsset(
                "faf-moderator-client-v2026-07-08-custom.zip",
                "https://example.invalid/download.zip",
                "application/zip",
                123L
        );
        GithubRelease release = new GithubRelease("v2026-07-08", "v2026-07-08", "https://example.invalid", "", null, List.of(asset));

        Optional<GithubReleaseAsset> selected = service.findMatchingAsset(release);

        assertThat(selected.isPresent(), is(true));
        assertThat(selected.get(), is(asset));
    }
}
