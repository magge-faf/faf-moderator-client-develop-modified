package com.faforever.moderatorclient.ui.main_window;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SmurfLookupSettingsTest {

    @Test
    void parseThresholdFallsBackToDefaultForInvalidInput() {
        assertThat(SmurfLookupSettings.parseThreshold("not-a-number"), is(10));
    }

    @Test
    void withSuppressCleanOutputPreservesOtherSnapshotValues() {
        SmurfLookupSettings settings = new SmurfLookupSettings(
                true, true, true, true, true, true, true, true, true, 25, true, true, false);

        SmurfLookupSettings updated = settings.withSuppressCleanOutput(true);

        assertThat(updated.includeUUID(), is(true));
        assertThat(updated.threshold(), is(25));
        assertThat(updated.promptOnThreshold(), is(true));
        assertThat(updated.onlyShowActive(), is(true));
        assertThat(updated.suppressCleanOutput(), is(true));
    }
}
