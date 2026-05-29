package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void getRefreshedTokenValueSerializesConcurrentRefreshRequests() throws Exception {
        RefreshCountingTokenService tokenService = new RefreshCountingTokenService();
        ReflectionTestUtils.setField(tokenService, "tokenCache", expiredTokenResponse("expired-token", "refresh-token"));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<String> first = executorService.submit(() -> {
            barrier.await();
            return tokenService.getRefreshedTokenValue();
        });
        Future<String> second = executorService.submit(() -> {
            barrier.await();
            return tokenService.getRefreshedTokenValue();
        });

        assertThat(first.get(), is("refreshed-token"));
        assertThat(second.get(), is("refreshed-token"));
        assertThat(tokenService.refreshCount.get(), is(1));
        executorService.shutdownNow();
    }

    private static String jwtWithHmac(String hmac) {
        return encode("{\"alg\":\"none\"}") + "." + encode("{\"ext\":{\"hmac\":\"" + hmac + "\"}}") + ".";
    }

    private static OAuth2AccessTokenResponse tokenResponse(String accessToken, String refreshToken, long expiresIn) {
        return OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .build();
    }

    private static OAuth2AccessTokenResponse expiredTokenResponse(String accessToken, String refreshToken) {
        OAuth2AccessTokenResponse response = tokenResponse(accessToken, refreshToken, 3600);
        ReflectionTestUtils.setField(
                response,
                "accessToken",
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        accessToken,
                        Instant.now().minus(Duration.ofHours(2)),
                        Instant.now().minus(Duration.ofHours(1))));
        return response;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static class RefreshCountingTokenService extends TokenService {
        private final AtomicInteger refreshCount = new AtomicInteger();

        private RefreshCountingTokenService() {
            super(new LocalPreferences(), mock(), new HmacHeaderInterceptor());
        }

        @Override
        public void loginWithRefreshToken(String refreshToken, boolean fireEvent) {
            refreshCount.incrementAndGet();
            try {
                Thread.sleep(Duration.ofMillis(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ReflectionTestUtils.setField(this, "tokenCache", tokenResponse("refreshed-token", refreshToken, 3600));
        }
    }
}
