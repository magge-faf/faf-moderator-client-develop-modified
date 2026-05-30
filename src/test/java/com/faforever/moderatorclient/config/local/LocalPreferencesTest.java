package com.faforever.moderatorclient.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class LocalPreferencesTest {

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
}
