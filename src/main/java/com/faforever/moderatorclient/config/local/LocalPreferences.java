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

    public LocalPreferences() {
        applyDefaultLayoutPreferences();
    }

    /**
     * Backfills default layout values (table column widths/order, divider positions) into the nested
     * preference sections. Safe to call again after Jackson deserialization has replaced those nested
     * objects: only keys/lists that are still missing are filled in, existing saved values are untouched.
     */
    void applyDefaultLayoutPreferences() {
        // tabUserManagement
        backfillMap(tabUserManagement.userSearchTableTableColumnWidthsTabUserManagement, Map.ofEntries(
                Map.entry("AccountLink", 174.58984375),
                Map.entry("Email", 175.541015625),
                Map.entry("RegistrationDate", 124.650390625),
                Map.entry("CPUName", 268.609375),
                Map.entry("UIDCreated", 109.111328125),
                Map.entry("ProcessorId", 118.29296875),
                Map.entry("DeviceID", 67.517578125),
                Map.entry("MemoryS/N", 91.7109375),
                Map.entry("Hash", 221.798828125),
                Map.entry("VolumeS/N", 200.0),
                Map.entry("Name", 96.951171875),
                Map.entry("UIDLastUsed", 109.111328125),
                Map.entry("S/N", 200.0),
                Map.entry("LastLogin", 124.650390625),
                Map.entry("UserAgent", 146.0),
                Map.entry("Manufacturer", 230.546875),
                Map.entry("ID", 54.0),
                Map.entry("UUID", 252.220703125),
                Map.entry("IPAddress", 94.9609375)
        ));
        backfillList(tabUserManagement.userSearchTableColumnOrderTabUserManagement, List.of("ID", "Name", "Email", "RegistrationDate", "LastLogin", "Hash", "UUID", "UIDCreated", "UIDLastUsed", "MemoryS/N", "IPAddress", "DeviceID", "AccountLink", "UserAgent", "Manufacturer", "CPUName", "ProcessorId", "S/N", "VolumeS/N"));
        backfillList(tabUserManagement.rootSplitPaneDividerPositionsTabUserManagement, List.of(0.39481268011527376));
        backfillMap(tabUserManagement.userBansTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("Level", 80.0),
                Map.entry("Status", 80.0),
                Map.entry("Duration", 195.794921875),
                Map.entry("Expiresat", 74.537109375),
                Map.entry("Reason", 923.244140625),
                Map.entry("Author", 153.0),
                Map.entry("RevocationReason", 250.0),
                Map.entry("RevocationAuthor", 150.0),
                Map.entry("Revocationat", 180.0),
                Map.entry("CreatedTime", 180.0),
                Map.entry("UpdateTime", 180.0)
        ));
        backfillList(tabUserManagement.userBansTableColumnOrder, List.of("ID", "Level", "Status", "Duration", "Expiresat", "Reason", "Author", "RevocationReason", "RevocationAuthor", "Revocationat", "CreatedTime", "UpdateTime"));
        backfillMap(tabUserManagement.userNoteTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("Watched", 80.0),
                Map.entry("Note", 600.0),
                Map.entry("Author", 150.0),
                Map.entry("Created", 160.0),
                Map.entry("Lastupdate", 160.0)
        ));
        backfillList(tabUserManagement.userNoteTableColumnOrder, List.of("ID", "Watched", "Note", "Author", "Created", "Lastupdate"));
        backfillMap(tabUserManagement.userNameHistoryTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("ChangeTime", 180.0),
                Map.entry("PreviousName", 200.0)
        ));
        backfillList(tabUserManagement.userNameHistoryTableColumnOrder, List.of("ID", "ChangeTime", "PreviousName"));
        backfillMap(tabUserManagement.userLastGamesTableColumnWidths, Map.ofEntries(
                Map.entry("GameID", 100.0),
                Map.entry("GameName", 845.7109375),
                Map.entry("GameValidity", 206.0),
                Map.entry("RatingBeforeGame", 150.0),
                Map.entry("RatingChange", 100.0),
                Map.entry("ScoreTime", 150.0),
                Map.entry("Replay", 150.0)
        ));
        backfillList(tabUserManagement.userLastGamesTableColumnOrder, List.of("GameID", "GameName", "GameValidity", "RatingBeforeGame", "RatingChange", "ScoreTime", "Replay"));
        backfillMap(tabUserManagement.userAvatarsTableColumnWidths, Map.ofEntries(
                Map.entry("AssignmentID", 140.0),
                Map.entry("AvatarID", 80.0),
                Map.entry("Preview", 80.0),
                Map.entry("Tooltip", 184.0),
                Map.entry("Selected", 80.0),
                Map.entry("ExpiresAt", 180.0)
        ));
        backfillList(tabUserManagement.userAvatarsTableColumnOrder, List.of("AssignmentID", "AvatarID", "Preview", "Tooltip", "Selected", "ExpiresAt"));
        backfillMap(tabUserManagement.userGroupsTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("GroupName", 150.0),
                Map.entry("Public", 80.0)
        ));
        backfillList(tabUserManagement.userGroupsTableColumnOrder, List.of("ID", "GroupName", "Public"));
        backfillMap(tabUserManagement.permissionsTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("PermissionName", 150.0)
        ));
        backfillList(tabUserManagement.permissionsTableColumnOrder, List.of("ID", "PermissionName"));
        // tabReports
        backfillMap(tabReports.reportTableColumnWidthsTabReports, Map.ofEntries(
                Map.entry("lastModeratorColumn", 108.982421875),
                Map.entry("reportDescriptionColumn", 946.251953125),
                Map.entry("statusColumn", 15.0),
                Map.entry("reportedUsersColumn", 116.998046875),
                Map.entry("privateNoteColumn", 149.0),
                Map.entry("incidentTimeCodeColumn", 225.6484375),
                Map.entry("moderatorPrivateNoticeColumn", 454.0),
                Map.entry("reporterColumn", 117.34375),
                Map.entry("gameColumn", 69.84375),
                Map.entry("createTimeColumn", 133.0),
                Map.entry("idColumn", 48.34375)
        ));
        backfillList(tabReports.reportTableColumnOrderTabReports, List.of("idColumn", "reporterColumn", "statusColumn", "reportedUsersColumn", "reportDescriptionColumn", "gameColumn", "incidentTimeCodeColumn", "privateNoteColumn", "moderatorPrivateNoticeColumn", "lastModeratorColumn", "createTimeColumn"));
        backfillList(tabReports.rootSplitPaneDividerPositionsTabReports, List.of(0.5295389048991355));
        // tabRecentNotes
        backfillMap(tabRecentNotes.columnWidthsTabRecentNotes, Map.ofEntries(
                Map.entry("playerIdColumn", 191.46484375),
                Map.entry("authorIdColumn", 92.9453125),
                Map.entry("noteColumn", 805.0),
                Map.entry("noteCreatedColumn", 124.650390625),
                Map.entry("noteUpdatedColumn", 124.650390625),
                Map.entry("noteIdColumn", 65.244140625)
        ));
        backfillList(tabRecentNotes.columnOrderTabRecentNotes, List.of("noteCreatedColumn", "noteUpdatedColumn", "noteIdColumn", "authorIdColumn", "playerIdColumn", "noteColumn"));
        // tabBans
        backfillMap(tabBans.columnWidthsTabBans, Map.ofEntries(
                Map.entry("Status", 17.0),
                Map.entry("RevocationReason", 250.0),
                Map.entry("CreatedTime", 180.0),
                Map.entry("RevocationAuthor", 150.0),
                Map.entry("Duration", 173.0),
                Map.entry("AffectedPlayer", 257.9453125),
                Map.entry("Reason", 871.0),
                Map.entry("Revocationat", 180.0),
                Map.entry("UpdateTime", 180.0),
                Map.entry("Author", 180.6953125),
                Map.entry("Level", 26.0),
                Map.entry("ID", 64.0),
                Map.entry("Expiresat", 134.494140625)
        ));
        backfillList(tabBans.columnOrderTabBans, List.of("Level", "ID", "Status", "Duration", "AffectedPlayer", "Expiresat", "Reason", "Author", "CreatedTime", "UpdateTime", "RevocationReason", "RevocationAuthor", "Revocationat"));
        // tabAvatars
        backfillMap(tabAvatars.avatarTableColumnWidths, Map.ofEntries(
                Map.entry("#", 35.40625),
                Map.entry("AvatarID", 72.83203125),
                Map.entry("Preview", 64.76953125),
                Map.entry("Tooltip", 281.248046875),
                Map.entry("Created", 180.0),
                Map.entry("URL", 534.537109375),
                Map.entry("Assignments", 90.0),
                Map.entry("Inuse", 80.0),
                Map.entry("Age", 90.0)
        ));
        backfillList(tabAvatars.avatarTableColumnOrder, List.of("#", "AvatarID", "Preview", "Tooltip", "Created", "URL", "Assignments", "Inuse", "Age"));
        backfillMap(tabAvatars.avatarAssignmentTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("UserID", 80.0),
                Map.entry("Username", 150.0),
                Map.entry("Selected", 80.0),
                Map.entry("Expiresat", 180.0),
                Map.entry("Assignedat", 180.0),
                Map.entry("Remove", 90.0)
        ));
        backfillList(tabAvatars.avatarAssignmentTableColumnOrder, List.of("ID", "UserID", "Username", "Selected", "Expiresat", "Assignedat", "Remove"));
        // tabMapVault
        backfillMap(tabMapVault.mapSearchTableColumnWidths, Map.ofEntries(
                Map.entry("MapID", 100.0),
                Map.entry("Name", 200.0),
                Map.entry("Author", 200.0),
                Map.entry("Recommended", 100.0),
                Map.entry("Firstupload", 160.0),
                Map.entry("Lastupdate", 160.0)
        ));
        backfillList(tabMapVault.mapSearchTableColumnOrder, List.of("MapID", "Name", "Author", "Recommended", "Firstupload", "Lastupdate"));
        backfillMap(tabMapVault.mapVersionTableColumnWidths, Map.ofEntries(
                Map.entry("VersionID", 100.0),
                Map.entry("VersionNo.", 100.0),
                Map.entry("Ranked", 80.0),
                Map.entry("Hidden", 80.0),
                Map.entry("MaxPlayers", 130.0),
                Map.entry("Width", 80.0),
                Map.entry("Height", 80.0),
                Map.entry("Description", 300.0),
                Map.entry("Uploaded", 160.0),
                Map.entry("Lastupdate", 160.0)
        ));
        backfillList(tabMapVault.mapVersionTableColumnOrder, List.of("VersionID", "VersionNo.", "Ranked", "Hidden", "MaxPlayers", "Width", "Height", "Description", "Uploaded", "Lastupdate"));
        // tabModVault
        backfillMap(tabModVault.modSearchTableColumnWidths, Map.ofEntries(
                Map.entry("ModID", 100.0),
                Map.entry("Name", 200.0),
                Map.entry("Uploader", 200.0),
                Map.entry("Author", 200.0),
                Map.entry("Recommended", 100.0),
                Map.entry("Firstupload", 160.0),
                Map.entry("Lastupdate", 160.0)
        ));
        backfillList(tabModVault.modSearchTableColumnOrder, List.of("ModID", "Name", "Uploader", "Author", "Recommended", "Firstupload", "Lastupdate"));
        backfillMap(tabModVault.modVersionTableColumnWidths, Map.ofEntries(
                Map.entry("VersionID", 100.0),
                Map.entry("VersionNo.", 100.0),
                Map.entry("UID", 280.0),
                Map.entry("Ranked", 80.0),
                Map.entry("Hidden", 80.0),
                Map.entry("Description", 300.0),
                Map.entry("Uploaded", 160.0),
                Map.entry("Lastupdate", 160.0)
        ));
        backfillList(tabModVault.modVersionTableColumnOrder, List.of("VersionID", "VersionNo.", "UID", "Ranked", "Hidden", "Description", "Uploaded", "Lastupdate"));
        // tabRecentActivity
        backfillMap(tabRecentActivity.userRegistrationFeedTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 48.8125),
                Map.entry("Name", 127.697265625),
                Map.entry("Email", 260.201171875),
                Map.entry("RegistrationDate", 128.494140625),
                Map.entry("LastLogin", 128.494140625),
                Map.entry("AccountLink", 168.58984375),
                Map.entry("IPAddress", 237.220703125),
                Map.entry("UserAgent", 117.53125),
                Map.entry("Hash", 80.0),
                Map.entry("UUID", 80.0),
                Map.entry("MemoryS/N", 80.0),
                Map.entry("DeviceID", 80.0),
                Map.entry("Manufacturer", 80.0),
                Map.entry("CPUName", 80.0),
                Map.entry("ProcessorId", 80.0),
                Map.entry("S/N", 200.0),
                Map.entry("VolumeS/N", 80.0),
                Map.entry("Ban", 80.0),
                Map.entry("UIDCreated", 80.0),
                Map.entry("UIDLastUsed", 80.0)
        ));
        backfillList(tabRecentActivity.userRegistrationFeedTableColumnOrder, List.of("ID", "Name", "Email", "RegistrationDate", "LastLogin", "AccountLink", "IPAddress", "UserAgent", "Hash", "UUID", "MemoryS/N", "DeviceID", "Manufacturer", "CPUName", "ProcessorId", "S/N", "VolumeS/N", "Ban", "UIDCreated", "UIDLastUsed"));
        backfillMap(tabRecentActivity.teamkillFeedTableColumnWidths, Map.ofEntries(
                Map.entry("ID", 80.0),
                Map.entry("Killer", 180.0),
                Map.entry("Victim", 180.0),
                Map.entry("GameID", 100.0),
                Map.entry("GameTime", 100.0),
                Map.entry("ReportedAt", 180.0)
        ));
        backfillList(tabRecentActivity.teamkillFeedTableColumnOrder, List.of("ID", "Killer", "Victim", "GameID", "GameTime", "ReportedAt"));
        backfillMap(tabRecentActivity.mapUploadFeedTableColumnWidths, Map.ofEntries(
                Map.entry("MapVersionID", 100.0),
                Map.entry("MapID", 80.0),
                Map.entry("MapName", 150.0),
                Map.entry("Uploader", 150.0),
                Map.entry("Version", 80.0),
                Map.entry("Ranked", 80.0),
                Map.entry("Hidden", 80.0),
                Map.entry("Width", 80.0),
                Map.entry("Height", 80.0),
                Map.entry("Maxplayers", 80.0),
                Map.entry("Versiondescription", 250.0),
                Map.entry("DownloadURL", 400.0),
                Map.entry("Action", 80.0)
        ));
        backfillList(tabRecentActivity.mapUploadFeedTableColumnOrder, List.of("MapVersionID", "MapID", "MapName", "Uploader", "Version", "Ranked", "Hidden", "Width", "Height", "Maxplayers", "Versiondescription", "DownloadURL", "Action"));
        // tabApiHistory
        backfillMap(tabApiHistory.historyTableColumnWidths, Map.ofEntries(
                Map.entry("timeColumn", 60.015625),
                Map.entry("methodColumn", 70.0),
                Map.entry("urlColumn", 1353.0),
                Map.entry("statusColumn", 70.0),
                Map.entry("durationColumn", 90.0)
        ));
        backfillList(tabApiHistory.historyTableColumnOrder, List.of("timeColumn", "methodColumn", "urlColumn", "statusColumn", "durationColumn"));
    }

    private static void backfillMap(Map<String, Double> target, Map<String, Double> defaults) {
        defaults.forEach(target::putIfAbsent);
    }

    private static <T> void backfillList(List<T> target, List<T> defaults) {
        if (target.isEmpty()) {
            target.addAll(defaults);
        }
    }

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
            String legacyPlaintextToken = CREDENTIAL_PREFERENCES.get(REFRESH_TOKEN_KEY, null);
            if (legacyPlaintextToken != null) {
                setRefreshToken(legacyPlaintextToken);
            }
            return legacyPlaintextToken;
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

            String legacyPlaintextToken = CREDENTIAL_PREFERENCES.get(LOBBY_REFRESH_TOKEN_KEY, null);
            if (legacyPlaintextToken != null) {
                setLobbyRefreshToken(legacyPlaintextToken);
            }
            return legacyPlaintextToken;
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
        private boolean skipAutomaticUpdateRestartConfirmation = false;

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
