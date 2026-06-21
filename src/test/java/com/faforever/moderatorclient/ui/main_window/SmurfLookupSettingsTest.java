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
                true, true, true, true, true, true, true, true, true, 25, true, true, false, false, true);

        SmurfLookupSettings updated = settings.withSuppressCleanOutput(true);

        assertThat(updated.includeUUID(), is(true));
        assertThat(updated.threshold(), is(25));
        assertThat(updated.promptOnThreshold(), is(true));
        assertThat(updated.onlyShowActive(), is(true));
        assertThat(updated.suppressCleanOutput(), is(true));
        assertThat(updated.suppressExcludedItems(), is(false));
        assertThat(updated.catchFirstLayerOnly(), is(true));
    }

    @Test
    void withAllEnabledTurnsOnEveryBooleanFlagWithoutChangingThreshold() {
        SmurfLookupSettings settings = new SmurfLookupSettings(
                false, false, false, false, false, false, false, false, false, 25, false, false, false, false, false);

        SmurfLookupSettings updated = settings.withAllEnabled();

        assertThat(updated.includeUUID(), is(true));
        assertThat(updated.includeHash(), is(true));
        assertThat(updated.includeIP(), is(true));
        assertThat(updated.includeMemorySerial(), is(true));
        assertThat(updated.includeVolumeSerial(), is(true));
        assertThat(updated.includeSerial(), is(true));
        assertThat(updated.includeProcessorId(), is(true));
        assertThat(updated.includeCpuName(), is(true));
        assertThat(updated.includeManufacturer(), is(true));
        assertThat(updated.threshold(), is(25));
        assertThat(updated.promptOnThreshold(), is(true));
        assertThat(updated.onlyShowActive(), is(true));
        assertThat(updated.suppressCleanOutput(), is(true));
        assertThat(updated.suppressExcludedItems(), is(false));
        assertThat(updated.catchFirstLayerOnly(), is(true));
    }
}
