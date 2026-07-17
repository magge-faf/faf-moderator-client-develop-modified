package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;
import java.util.concurrent.TimeUnit;

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
    private TabIrcChat tabIrcChat = new TabIrcChat();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class AutoLogin {
        private static final Preferences CREDENTIAL_PREFERENCES = Preferences.userNodeForPackage(AutoLogin.class);
        private static final String REFRESH_TOKEN_KEY = "refreshToken";
        private static final String ENCRYPTED_TOKEN_KEY = "encryptedRefreshToken";
        private static final String LOBBY_REFRESH_TOKEN_KEY = "lobbyRefreshToken";
        private static final String ENCRYPTED_LOBBY_TOKEN_KEY = "encryptedLobbyRefreshToken";

        boolean enabled = true;
        String environment;
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String refreshToken;
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String lobbyRefreshToken;

        public String getRefreshToken() {
            // First check if there's a legacy plaintext token in the JSON field
            if (refreshToken != null) {
                return refreshToken;
            }

            // Try to get from encrypted Preferences store
            String encryptedToken = CREDENTIAL_PREFERENCES.get(ENCRYPTED_TOKEN_KEY, null);
            if (encryptedToken != null) {
                String decryptedToken = decryptToken(encryptedToken);
                if (decryptedToken != null) {
                    return decryptedToken;
                }

                String legacyDecryptedToken = decryptLegacyToken(encryptedToken);
                if (legacyDecryptedToken != null) {
                    // Migrate legacy XOR-encrypted tokens to the current AES format after a successful read.
                    setRefreshToken(legacyDecryptedToken);
                    return legacyDecryptedToken;
                }
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
                flushPreferences();
                this.refreshToken = null;
            } else {
                // Store encrypted in Preferences
                String encrypted = encryptToken(refreshToken);
                CREDENTIAL_PREFERENCES.put(ENCRYPTED_TOKEN_KEY, encrypted);
                // Remove legacy plaintext entry
                CREDENTIAL_PREFERENCES.remove(REFRESH_TOKEN_KEY);
                flushPreferences();
                // Don't store in JSON field
                this.refreshToken = null;
            }
        }

        public String getLobbyRefreshToken() {
            if (lobbyRefreshToken != null) {
                return lobbyRefreshToken;
            }

            String encryptedToken = CREDENTIAL_PREFERENCES.get(ENCRYPTED_LOBBY_TOKEN_KEY, null);
            if (encryptedToken != null) {
                String decryptedToken = decryptToken(encryptedToken);
                if (decryptedToken != null) {
                    return decryptedToken;
                }

                String legacyDecryptedToken = decryptLegacyToken(encryptedToken);
                if (legacyDecryptedToken != null) {
                    setLobbyRefreshToken(legacyDecryptedToken);
                    return legacyDecryptedToken;
                }
            }

            return CREDENTIAL_PREFERENCES.get(LOBBY_REFRESH_TOKEN_KEY, null);
        }

        public void setLobbyRefreshToken(String refreshToken) {
            if (this.lobbyRefreshToken != null && !this.lobbyRefreshToken.isBlank()) {
                this.lobbyRefreshToken = null;
            }

            if (refreshToken == null || refreshToken.isBlank()) {
                CREDENTIAL_PREFERENCES.remove(ENCRYPTED_LOBBY_TOKEN_KEY);
                CREDENTIAL_PREFERENCES.remove(LOBBY_REFRESH_TOKEN_KEY);
                flushPreferences();
                this.lobbyRefreshToken = null;
            } else {
                String encrypted = encryptToken(refreshToken);
                CREDENTIAL_PREFERENCES.put(ENCRYPTED_LOBBY_TOKEN_KEY, encrypted);
                CREDENTIAL_PREFERENCES.remove(LOBBY_REFRESH_TOKEN_KEY);
                flushPreferences();
                this.lobbyRefreshToken = null;
            }
        }

        private String encryptToken(String token) {
            try {
                javax.crypto.SecretKey secretKey = getOrGenerateSecretKey();
                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                byte[] iv = new byte[12];
                java.security.SecureRandom random = new java.security.SecureRandom();
                random.nextBytes(iv);
                javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
                byte[] encrypted = cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                // Prepend IV to encrypted data
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

                return java.util.Base64.getEncoder().encodeToString(combined);
            } catch (Exception e) {
                throw new RuntimeException("Failed to encrypt token", e);
            }
        }

        private String decryptToken(String encryptedToken) {
            try {
                javax.crypto.SecretKey secretKey = getOrGenerateSecretKey();
                byte[] combined = Base64.getDecoder().decode(encryptedToken);

                if (combined.length <= 12) {
                    return null;
                }

                // Extract IV and encrypted data
                byte[] iv = new byte[12];
                byte[] encrypted = new byte[combined.length - 12];
                System.arraycopy(combined, 0, iv, 0, 12);
                System.arraycopy(combined, 12, encrypted, 0, encrypted.length);

                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, gcmSpec);
                byte[] decrypted = cipher.doFinal(encrypted);

                return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Return null if decryption fails (might be old XOR-encrypted data)
                return null;
            }
        }

        private String decryptLegacyToken(String encryptedToken) {
            try {
                String key = getLegacySystemKey();
                byte[] encrypted = Base64.getDecoder().decode(encryptedToken);
                byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] decrypted = new byte[encrypted.length];

                for (int i = 0; i < encrypted.length; i++) {
                    decrypted[i] = (byte) (encrypted[i] ^ keyBytes[i % keyBytes.length]);
                }

                String decryptedToken = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
                return decryptedToken.isBlank() ? null : decryptedToken;
            } catch (Exception e) {
                return null;
            }
        }

        private javax.crypto.SecretKey getOrGenerateSecretKey() {
            try {
                String encodedKey = CREDENTIAL_PREFERENCES.get("aesKey", null);
                if (encodedKey != null) {
                    byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
                    return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
                }

                // Generate new key
                javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
                keyGen.init(256, new java.security.SecureRandom());
                javax.crypto.SecretKey secretKey = keyGen.generateKey();

                // Store it
                encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                CREDENTIAL_PREFERENCES.put("aesKey", encodedKey);
                flushPreferences();

                return secretKey;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize encryption key", e);
            }
        }

        private String getLegacySystemKey() {
            String osName = System.getProperty("os.name", "");
            String osVersion = System.getProperty("os.version", "");
            String userName = System.getProperty("user.name", "");
            String userHome = System.getProperty("user.home", "");

            return osName + osVersion + userName + userHome + "faf-moderator-salt";
        }

        private void flushPreferences() {
            try {
                CREDENTIAL_PREFERENCES.flush();
            } catch (BackingStoreException e) {
                throw new RuntimeException("Failed to persist credential preferences", e);
            }
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
        String updateBackupFolder = "";

        // TextArea

        // CheckBoxes
        boolean syncPermanentBansAtStartupCheckbox = false;
        boolean syncTemporaryBansAtStartupCheckbox = false;
        boolean rememberLoginCheckBox = true;
        boolean darkModeCheckBox = true;
        boolean fetchBansOnStartupCheckBox = false;
        boolean autoBackupConfigurationFolderOnSaveCheckBox = false;
        boolean automaticConfigurationBackupsOnExitCheckBox = true;
        boolean autoPurgeTempReplaysOlderThanOneDayCheckBox = false;

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
        boolean enableManualReplayLookupCheckBox = false;
        boolean showReportPlayerRoleLabelsCheckBox = true;

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
        private long lastReminderEpoch = 0; // legacy timestamp in milliseconds
        private long nextReminderEpoch = 0;
        private int reminderDelayDays = 3;
        private String reminderVersionTag = "";

        public long getEffectiveNextReminderEpoch() {
            if (nextReminderEpoch > 0) {
                return nextReminderEpoch;
            }
            if (lastReminderEpoch > 0) {
                return lastReminderEpoch + TimeUnit.DAYS.toMillis(Math.max(1, reminderDelayDays));
            }
            return 0;
        }

        public void scheduleAfterDays(String versionTag, int days) {
            reminderDelayDays = Math.max(1, days);
            reminderVersionTag = versionTag == null ? "" : versionTag;
            nextReminderEpoch = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(reminderDelayDays);
            lastReminderEpoch = 0;
        }

        public void scheduleForNextStart(String versionTag, int preferredDelayDays) {
            reminderDelayDays = Math.max(1, preferredDelayDays);
            reminderVersionTag = versionTag == null ? "" : versionTag;
            nextReminderEpoch = 0;
            lastReminderEpoch = 0;
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TabIrcChat {
        String nickname = "";
        boolean autoConnectOnStartup = false;
        boolean debugTraffic = false;
        boolean suppressJoinLeaveNoise = true;
        boolean autoLoadLastDayHistory = true;
        boolean showIrcLocalLog = true;
        boolean mentionSoundEnabled = true;
        boolean mentionToastEnabled = true;
        String selectedChannel = "#aeolus";
        List<String> autoJoinChannels = new ArrayList<>(List.of("#aeolus", "#moderators"));
    }
}
