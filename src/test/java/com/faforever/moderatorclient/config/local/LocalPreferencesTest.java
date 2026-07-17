package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class LocalPreferencesTest {
    private static final Preferences CREDENTIAL_PREFERENCES =
            Preferences.userNodeForPackage(LocalPreferences.AutoLogin.class);
    private static final String ENCRYPTED_TOKEN_KEY = "encryptedRefreshToken";
    private static final String REFRESH_TOKEN_KEY = "refreshToken";
    private static final String AES_KEY = "aesKey";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void autoLoginRefreshTokenIsNotSerializedToPreferencesJson() throws Exception {
        LocalPreferences localPreferences = new LocalPreferences();
        try {
            localPreferences.getAutoLogin().setRefreshToken("secret-refresh-token");

            String json = objectMapper.writeValueAsString(localPreferences);

            assertThat(json, not(containsString("secret-refresh-token")));
            assertThat(json, not(containsString("refreshToken")));
        } finally {
            localPreferences.getAutoLogin().setRefreshToken(null);
        }
    }

    @Test
    void autoLoginRefreshTokenCanBeMigratedFromLegacyPreferencesJson() throws Exception {
        LocalPreferences localPreferences = null;
        try {
            localPreferences = objectMapper.readValue(
                    "{\"autoLogin\":{\"refreshToken\":\"legacy-refresh-token\"}}",
                    LocalPreferences.class);

            assertThat(localPreferences.getAutoLogin().getRefreshToken(), is("legacy-refresh-token"));
        } finally {
            if (localPreferences != null) {
                localPreferences.getAutoLogin().setRefreshToken(null);
            }
        }
    }

    @Test
    void autoLoginRefreshTokenCanBeMigratedFromLegacyEncryptedPreferencesStore() {
        clearCredentialPreferences();

        String legacyRefreshToken = "legacy-encrypted-refresh-token";
        String legacyEncryptedToken = legacyEncryptToken(legacyRefreshToken);
        CREDENTIAL_PREFERENCES.put(ENCRYPTED_TOKEN_KEY, legacyEncryptedToken);

        try {
            LocalPreferences localPreferences = new LocalPreferences();

            assertThat(localPreferences.getAutoLogin().getRefreshToken(), is(legacyRefreshToken));
            assertThat(CREDENTIAL_PREFERENCES.get(ENCRYPTED_TOKEN_KEY, null), not(is(legacyEncryptedToken)));
            assertThat(CREDENTIAL_PREFERENCES.get(AES_KEY, null) != null, is(true));
        } finally {
            clearCredentialPreferences();
        }
    }

    @Test
    void corruptEncryptedRefreshTokenIsNotMigratedAsLegacyToken() {
        clearCredentialPreferences();

        String corruptEncryptedToken = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5, 6});
        CREDENTIAL_PREFERENCES.put(ENCRYPTED_TOKEN_KEY, corruptEncryptedToken);

        try {
            LocalPreferences localPreferences = new LocalPreferences();

            assertThat(localPreferences.getAutoLogin().getRefreshToken(), is(nullValue()));
            assertThat(CREDENTIAL_PREFERENCES.get(ENCRYPTED_TOKEN_KEY, null), is(corruptEncryptedToken));
        } finally {
            clearCredentialPreferences();
        }
    }

    @Test
    void legacyVersionReminderTimestampStillMapsToDeferredReminderWindow() throws Exception {
        LocalPreferences localPreferences = objectMapper.readValue(
                "{\"versionReminder\":{\"lastReminderEpoch\":1000}}",
                LocalPreferences.class);

        assertThat(
                localPreferences.getVersionReminder().getEffectiveNextReminderEpoch(),
                is(1000L + TimeUnit.DAYS.toMillis(3))
        );
    }

    private static String legacyEncryptToken(String token) {
        String key = System.getProperty("os.name", "")
                + System.getProperty("os.version", "")
                + System.getProperty("user.name", "")
                + System.getProperty("user.home", "")
                + "faf-moderator-salt";
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = new byte[tokenBytes.length];

        for (int i = 0; i < tokenBytes.length; i++) {
            encrypted[i] = (byte) (tokenBytes[i] ^ keyBytes[i % keyBytes.length]);
        }

        return Base64.getEncoder().encodeToString(encrypted);
    }

    private static void clearCredentialPreferences() {
        CREDENTIAL_PREFERENCES.remove(ENCRYPTED_TOKEN_KEY);
        CREDENTIAL_PREFERENCES.remove(REFRESH_TOKEN_KEY);
        CREDENTIAL_PREFERENCES.remove(AES_KEY);
    }
}
