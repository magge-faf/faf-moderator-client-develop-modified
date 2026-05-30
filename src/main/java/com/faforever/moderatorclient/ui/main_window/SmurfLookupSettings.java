package com.faforever.moderatorclient.ui.main_window;

record SmurfLookupSettings(
        boolean includeUUID,
        boolean includeHash,
        boolean includeIP,
        boolean includeMemorySerial,
        boolean includeVolumeSerial,
        boolean includeSerial,
        boolean includeProcessorId,
        boolean includeCpuName,
        boolean includeManufacturer,
        int threshold,
        boolean promptOnThreshold,
        boolean onlyShowActive,
        boolean suppressCleanOutput) {

    private static final int DEFAULT_THRESHOLD = 10;

    static SmurfLookupSettings defaults() {
        return new SmurfLookupSettings(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                DEFAULT_THRESHOLD,
                false,
                false,
                false);
    }

    static int parseThreshold(String thresholdText) {
        try {
            return Integer.parseInt(thresholdText.trim());
        } catch (RuntimeException e) {
            return DEFAULT_THRESHOLD;
        }
    }

    SmurfLookupSettings withSuppressCleanOutput(boolean suppressCleanOutput) {
        return new SmurfLookupSettings(
                includeUUID,
                includeHash,
                includeIP,
                includeMemorySerial,
                includeVolumeSerial,
                includeSerial,
                includeProcessorId,
                includeCpuName,
                includeManufacturer,
                threshold,
                promptOnThreshold,
                onlyShowActive,
                suppressCleanOutput);
    }
}
