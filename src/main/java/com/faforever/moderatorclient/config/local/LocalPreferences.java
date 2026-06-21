package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

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
    private TabAvatars tabAvatars = new TabAvatars();
    private TabMapVault tabMapVault = new TabMapVault();
    private TabModVault tabModVault = new TabModVault();
    private TabVoting tabVoting = new TabVoting();
    private TabTutorial tabTutorial = new TabTutorial();
    private TabMessages tabMessages = new TabMessages();
    private TabUserGroups tabUserGroups = new TabUserGroups();
    private TabRecentActivity tabRecentActivity = new TabRecentActivity();
    private TabApiHistory tabApiHistory = new TabApiHistory();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class AutoLogin {
        private static final Preferences CREDENTIAL_PREFERENCES = Preferences.userNodeForPackage(AutoLogin.class);
        private static final String REFRESH_TOKEN_KEY = "refreshToken";
        private static final String ENCRYPTED_TOKEN_KEY = "encryptedRefreshToken";

        boolean enabled;
        String environment;
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String refreshToken;

        public String getRefreshToken() {
            // First check if there's a legacy plaintext token in the JSON field
            if (refreshToken != null) {
                return refreshToken;
            }

            // Try to get from encrypted Preferences store
            String encryptedToken = CREDENTIAL_PREFERENCES.get(ENCRYPTED_TOKEN_KEY, null);
            if (encryptedToken != null) {
                return decryptToken(encryptedToken);
            }

            // Fallback to legacy plaintext Preferences (for migration)
            return CREDENTIAL_PREFERENCES.get(REFRESH_TOKEN_KEY, null);
        }

        public void setRefreshToken(String refreshToken) {
            // Migrate legacy plaintext token from JSON field if present
            if (this.refreshToken != null && !this.refreshToken.isBlank()) {
                // Clear the JSON field by setting it to null
                this.refreshToken = null;
            }

            if (refreshToken == null || refreshToken.isBlank()) {
                CREDENTIAL_PREFERENCES.remove(ENCRYPTED_TOKEN_KEY);
                CREDENTIAL_PREFERENCES.remove(REFRESH_TOKEN_KEY);
                this.refreshToken = null;
            } else {
                // Store encrypted in Preferences
                String encrypted = encryptToken(refreshToken);
                CREDENTIAL_PREFERENCES.put(ENCRYPTED_TOKEN_KEY, encrypted);
                // Remove legacy plaintext entry
                CREDENTIAL_PREFERENCES.remove(REFRESH_TOKEN_KEY);
                // Don't store in JSON field
                this.refreshToken = null;
            }
        }

        private String encryptToken(String token) {
            try {
                // Use a simple XOR cipher with a system-derived key
                // This is not cryptographically strong but better than plaintext
                String key = getSystemKey();
                byte[] tokenBytes = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] encrypted = new byte[tokenBytes.length];

                for (int i = 0; i < tokenBytes.length; i++) {
                    encrypted[i] = (byte) (tokenBytes[i] ^ keyBytes[i % keyBytes.length]);
                }

                return java.util.Base64.getEncoder().encodeToString(encrypted);
            } catch (Exception e) {
                // Fallback to plaintext if encryption fails
                return token;
            }
        }

        private String decryptToken(String encryptedToken) {
            try {
                String key = getSystemKey();
                byte[] encrypted = java.util.Base64.getDecoder().decode(encryptedToken);
                byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] decrypted = new byte[encrypted.length];

                for (int i = 0; i < encrypted.length; i++) {
                    decrypted[i] = (byte) (encrypted[i] ^ keyBytes[i % keyBytes.length]);
                }

                return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Return null if decryption fails
                return null;
            }
        }

        private String getSystemKey() {
            // Generate a key from system properties to make it hardware-specific
            String osName = System.getProperty("os.name", "");
            String osVersion = System.getProperty("os.version", "");
            String userName = System.getProperty("user.name", "");
            String userHome = System.getProperty("user.home", "");

            return osName + osVersion + userName + userHome + "faf-moderator-salt";
        }
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
        boolean onlyShowActiveAccountsCheckBox = false;
        boolean suppressNoRelatedAccountsCheckBox = false;
        boolean suppressExcludedItemsCheckBox = false;

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
        Map<String, Double> userBansTableColumnWidths = new HashMap<>();
        List<String> userBansTableColumnOrder = new ArrayList<>();
        Map<String, Double> userNoteTableColumnWidths = new HashMap<>();
        List<String> userNoteTableColumnOrder = new ArrayList<>();
        Map<String, Double> userNameHistoryTableColumnWidths = new HashMap<>();
        List<String> userNameHistoryTableColumnOrder = new ArrayList<>();
        Map<String, Double> userLastGamesTableColumnWidths = new HashMap<>();
        List<String> userLastGamesTableColumnOrder = new ArrayList<>();
        Map<String, Double> userAvatarsTableColumnWidths = new HashMap<>();
        List<String> userAvatarsTableColumnOrder = new ArrayList<>();
        Map<String, Double> userGroupsTableColumnWidths = new HashMap<>();
        List<String> userGroupsTableColumnOrder = new ArrayList<>();
        Map<String, Double> permissionsTableColumnWidths = new HashMap<>();
        List<String> permissionsTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabSettings {
        // TextFields

        // TextArea

        // CheckBoxes
        boolean syncPermanentBansAtStartupCheckbox = false;
        boolean syncTemporaryBansAtStartupCheckbox = false;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabAvatars {
        Map<String, Double> avatarTableColumnWidths = new HashMap<>();
        List<String> avatarTableColumnOrder = new ArrayList<>();
        Map<String, Double> avatarAssignmentTableColumnWidths = new HashMap<>();
        List<String> avatarAssignmentTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabMapVault {
        Map<String, Double> mapSearchTableColumnWidths = new HashMap<>();
        List<String> mapSearchTableColumnOrder = new ArrayList<>();
        Map<String, Double> mapVersionTableColumnWidths = new HashMap<>();
        List<String> mapVersionTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabModVault {
        Map<String, Double> modSearchTableColumnWidths = new HashMap<>();
        List<String> modSearchTableColumnOrder = new ArrayList<>();
        Map<String, Double> modVersionTableColumnWidths = new HashMap<>();
        List<String> modVersionTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabVoting {
        Map<String, Double> subjectTableColumnWidths = new HashMap<>();
        List<String> subjectTableColumnOrder = new ArrayList<>();
        Map<String, Double> questionTableColumnWidths = new HashMap<>();
        List<String> questionTableColumnOrder = new ArrayList<>();
        Map<String, Double> choiceTableColumnWidths = new HashMap<>();
        List<String> choiceTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabTutorial {
        Map<String, Double> tutorialTableColumnWidths = new HashMap<>();
        List<String> tutorialTableColumnOrder = new ArrayList<>();
        Map<String, Double> categoryTableColumnWidths = new HashMap<>();
        List<String> categoryTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabMessages {
        Map<String, Double> messageTableColumnWidths = new HashMap<>();
        List<String> messageTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabUserGroups {
        Map<String, Double> groupsTableColumnWidths = new HashMap<>();
        List<String> groupsTableColumnOrder = new ArrayList<>();
        Map<String, Double> groupPermissionsTableColumnWidths = new HashMap<>();
        List<String> groupPermissionsTableColumnOrder = new ArrayList<>();
        Map<String, Double> groupChildrenTableColumnWidths = new HashMap<>();
        List<String> groupChildrenTableColumnOrder = new ArrayList<>();
        Map<String, Double> membersTableColumnWidths = new HashMap<>();
        List<String> membersTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabRecentActivity {
        Map<String, Double> userRegistrationFeedTableColumnWidths = new HashMap<>();
        List<String> userRegistrationFeedTableColumnOrder = new ArrayList<>();
        Map<String, Double> teamkillFeedTableColumnWidths = new HashMap<>();
        List<String> teamkillFeedTableColumnOrder = new ArrayList<>();
        Map<String, Double> mapUploadFeedTableColumnWidths = new HashMap<>();
        List<String> mapUploadFeedTableColumnOrder = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabApiHistory {
        Map<String, Double> historyTableColumnWidths = new HashMap<>();
        List<String> historyTableColumnOrder = new ArrayList<>();
    }
}
