package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class TokenServiceTest {

    @Test
    void parseResponsePreservesRefreshTokenWhenRefreshResponseOmitsReplacement() {
        LocalPreferences localPreferences = new LocalPreferences();
        localPreferences.getAutoLogin().setEnabled(true);
        localPreferences.getAutoLogin().setRefreshToken("stored-refresh");
        TokenService tokenService = new TokenService(localPreferences, mock(), new HmacHeaderInterceptor());

        ReflectionTestUtils.invokeMethod(
                tokenService,
                "parseResponse",
                Map.of(
                        "access_token", jwtWithHmac("hmac"),
                        "expires_in", 3600
                ),
                "existing-refresh");

        OAuth2AccessTokenResponse tokenCache = (OAuth2AccessTokenResponse) ReflectionTestUtils.getField(tokenService, "tokenCache");

        assertThat(tokenCache.getRefreshToken().getTokenValue(), is("existing-refresh"));
        assertThat(localPreferences.getAutoLogin().getRefreshToken(), is("stored-refresh"));
    }

    @Test
    void parseResponsePersistsReturnedRefreshToken() {
        LocalPreferences localPreferences = new LocalPreferences();
        localPreferences.getAutoLogin().setEnabled(true);
        TokenService tokenService = new TokenService(localPreferences, mock(), new HmacHeaderInterceptor());

        ReflectionTestUtils.invokeMethod(
                tokenService,
                "parseResponse",
                Map.of(
                        "access_token", jwtWithHmac("hmac"),
                        "refresh_token", "new-refresh",
                        "expires_in", 3600
                ),
                "existing-refresh");

        OAuth2AccessTokenResponse tokenCache = (OAuth2AccessTokenResponse) ReflectionTestUtils.getField(tokenService, "tokenCache");

        assertThat(tokenCache.getRefreshToken().getTokenValue(), is("new-refresh"));
        assertThat(localPreferences.getAutoLogin().getRefreshToken(), is("new-refresh"));
    }

    private static String jwtWithHmac(String hmac) {
        return encode("{\"alg\":\"none\"}") + "." + encode("{\"ext\":{\"hmac\":\"" + hmac + "\"}}") + ".";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
