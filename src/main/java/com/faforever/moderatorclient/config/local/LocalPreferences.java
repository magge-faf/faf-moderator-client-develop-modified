package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class LocalPreferences {
    private int version;
    private AutoLogin autoLogin = new AutoLogin();
    private UI ui = new UI();
    private TabUserManagement tabUserManagement = new TabUserManagement();
    private TabReports tabReports = new TabReports();
    private TabSettings tabSettings = new TabSettings();
    private VersionReminder versionReminder = new VersionReminder();
    private TabEditModerationReport tabEditModerationReport = new TabEditModerationReport();
    private TabRecentNotes tabRecentNotes = new TabRecentNotes();
    private TabBans tabBans = new TabBans();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class AutoLogin {
        boolean enabled;
        String environment;
        String refreshToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class UI {
        // ComboBox
        String browserComboBox = "SelectBrowser";
        boolean darkMode = true;
        String startUpTab = "userManagementTab";
        boolean suppressRateLimitWarning = false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabUserManagement {
        // TextField
        String smurfVillageLookupTextField = "";
        String smurfOutputTextArea = "";
        String daysToCheckRecentAccountsTextField = "1";

        // CheckBox
        boolean promptUserOnThresholdExceededSmurfVillageLookupCheckBox = true;
        boolean catchFirstLayerSmurfsOnlyCheckBox = true;

        boolean includeUUIDCheckBox = false;
        boolean includeUIDHashCheckBox = true;
        boolean includeIPCheckBox = false;
        boolean includeMemorySerialNumberCheckBox = false;
        boolean includeSerialNumberCheckBox = false;
        boolean includeVolumeSerialNumberCheckBox = false;
        boolean includeProcessorIdCheckBox = false;
        boolean includeProcessorNameCheckBox = false;
        boolean includeManufacturerCheckBox = false;

        boolean searchHistoryTexAreaVisibilityState = false;
        boolean userNotesTextAreaVisibilityState = false;

        // TitledPane
        boolean searchHistoryTitledPane = false;
        boolean smurfVillageLookupTitledPane = false;
        boolean smurfVillageLookupSettingsTitledPane = false;
        boolean banCheckerSmurfManagementTitledPane = false;
        boolean latestRegistrationsTitledPane = false;

        // Table column persistence
        Map<String, Double> userSearchTableTableColumnWidthsTabUserManagement = new HashMap<>();
        List<String> userSearchTableColumnOrderTabUserManagement = new ArrayList<>();
        List<Double> rootSplitPaneDividerPositionsTabUserManagement = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabSettings {
        // TextFields

        // TextArea

        // CheckBoxes
        boolean syncPermanentBansAtStartupCheckbox = false;
        boolean syncPermanentBansBeforeSearchCheckbox = false;
        boolean syncTemporaryBansAtStartupCheckbox = false;
        boolean syncTemporaryBansBeforeSearchCheckbox = false;
        boolean rememberLoginCheckBox = true;
        boolean darkModeCheckBox = true;
        boolean fetchBansOnStartupCheckBox = false;

        // TitledPanes

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabReports {
        // CheckBoxes
        boolean autoLoadChatLogCheckBox = true;
        boolean showEnforceRatingCheckBox = true;
        boolean showGameResultCheckBox = false;
        boolean showJsonStatsCheckBox = false;
        boolean showGameEndedCheckBox = true;
        boolean showFocusArmyFromCheckBox = true;

        boolean pingOfTypeMoveFilterCheckBox = true;
        boolean pingOfTypeAttackFilterCheckBox = true;
        boolean pingOfTypeAlertFilterCheckBox = true;
        boolean textMarkerTypeFilterCheckBox = true;

        boolean showSelfDestructionUnitsCheckBox = true;
        boolean showNotifyChatMessages = true;

        boolean fetchReportsOnStartupCheckBox = true;

        // TextFields
        String thresholdToShowSelfDestructionUnitsEventTextField = "0";
        String initialReportsLoadingTextField = "100";

        // Table column persistence
        Map<String, Double> reportTableColumnWidthsTabReports = new HashMap<>();
        List<String> reportTableColumnOrderTabReports = new ArrayList<>();
        List<Double> rootSplitPaneDividerPositionsTabReports = new ArrayList<>();

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabEditModerationReport {
        boolean applyTemplateAndAutoSaveCheckBox = false;

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class VersionReminder {
        private long lastReminderEpoch = 0; // timestamp in milliseconds
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabRecentNotes {
        Map<String, Double> columnWidthsTabRecentNotes = new HashMap<>();
        List<String> columnOrderTabRecentNotes = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabBans {
        Map<String, Double> columnWidthsTabBans = new HashMap<>();
        List<String> columnOrderTabBans = new ArrayList<>();
    }
}
