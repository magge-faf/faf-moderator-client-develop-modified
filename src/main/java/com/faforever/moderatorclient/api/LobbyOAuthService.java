package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.login.OAuthValuesReceiver;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class LobbyOAuthService {
    private final LocalPreferences localPreferences;
    private final OAuthValuesReceiver oAuthValuesReceiver;

    private final Object refreshLock = new Object();
    private volatile CompletableFuture<Void> pendingAuthorization = null;

    private EnvironmentProperties environmentProperties;
    private RestTemplate oauthRestTemplate;
    private RestTemplate userRestTemplate;
    private volatile OAuth2AccessTokenResponse tokenCache;

    public void prepare(EnvironmentProperties environmentProperties) {
        this.environmentProperties = environmentProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        RestTemplate oauthTemplate = new RestTemplate(requestFactory);
        oauthTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(environmentProperties.getOauthBaseUrl()));
        this.oauthRestTemplate = oauthTemplate;

        RestTemplate userTemplate = new RestTemplate(requestFactory);
        userTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(environmentProperties.getUserBaseUrl()));
        this.userRestTemplate = userTemplate;
        this.tokenCache = null;
    }

    @SneakyThrows
    public String getRefreshedTokenValue() {
        ensurePrepared();

        OAuth2AccessTokenResponse currentToken = tokenCache;
        if (currentToken == null) {
            CompletableFuture<Void> authFuture;
            synchronized (refreshLock) {
                currentToken = tokenCache;
                if (currentToken == null) {
                    if (pendingAuthorization == null) {
                        pendingAuthorization = CompletableFuture.runAsync(() -> {
                            try {
                                authorizeForLobby();
                            } finally {
                                synchronized (refreshLock) {
                                    pendingAuthorization = null;
                                }
                            }
                        });
                    }
                    authFuture = pendingAuthorization;
                } else {
                    authFuture = null;
                }
            }
            if (authFuture != null) {
                authFuture.join();
                currentToken = tokenCache;
            }
        }

        Instant expiresAt = currentToken.getAccessToken().getExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            CompletableFuture<Void> authFuture = null;
            synchronized (refreshLock) {
                currentToken = tokenCache;
                expiresAt = currentToken.getAccessToken().getExpiresAt();
                if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
                    String refreshToken = currentToken.getRefreshToken() == null ? null : currentToken.getRefreshToken().getTokenValue();
                    if (!StringUtils.hasText(refreshToken)) {
                        if (pendingAuthorization == null) {
                            pendingAuthorization = CompletableFuture.runAsync(() -> {
                                try {
                                    authorizeForLobby();
                                } finally {
                                    synchronized (refreshLock) {
                                        pendingAuthorization = null;
                                    }
                                }
                            });
                        }
                        authFuture = pendingAuthorization;
                    } else {
                        try {
                            loginWithRefreshToken(refreshToken);
                        } catch (RuntimeException e) {
                            log.warn("Lobby token refresh failed, requesting fresh lobby authorization", e);
                            localPreferences.getAutoLogin().setLobbyRefreshToken(null);
                            if (pendingAuthorization == null) {
                                pendingAuthorization = CompletableFuture.runAsync(() -> {
                                    try {
                                        authorizeForLobby();
                                    } finally {
                                        synchronized (refreshLock) {
                                            pendingAuthorization = null;
                                        }
                                    }
                                });
                            }
                            authFuture = pendingAuthorization;
                        }
                    }
                }
            }
            if (authFuture != null) {
                authFuture.join();
            }
        }

        return tokenCache.getAccessToken().getTokenValue();
    }

    public String getLobbyAccessUrl() {
        ensurePrepared();
        return getLobbyAccessUrl(getRefreshedTokenValue());
    }

    public String getLobbyAccessUrl(String token) {
        ensurePrepared();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set(HttpHeaders.USER_AGENT, environmentProperties.getLobbyUserAgent());
        String hmac = getHmac(token);
        if (StringUtils.hasText(hmac)) {
            headers.add("X-HMAC", hmac);
        }

        ResponseEntity<LobbyAccessInfo> response = userRestTemplate.exchange(
                "/lobby/access",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                LobbyAccessInfo.class
        );

        LobbyAccessInfo accessInfo = response.getBody();
        if (accessInfo == null || accessInfo.accessUrl() == null || accessInfo.accessUrl().isBlank()) {
            throw new IllegalStateException("FAF lobby access response is empty");
        }
        return accessInfo.accessUrl();
    }

    private void authorizeForLobby() {
        String storedRefreshToken = localPreferences.getAutoLogin().getLobbyRefreshToken();
        if (StringUtils.hasText(storedRefreshToken)) {
            try {
                loginWithRefreshToken(storedRefreshToken);
                return;
            } catch (RuntimeException e) {
                log.warn("Stored lobby refresh token is invalid, falling back to interactive lobby authorization", e);
                localPreferences.getAutoLogin().setLobbyRefreshToken(null);
            }
        }

        try {
            OAuthValuesReceiver.Values values = oAuthValuesReceiver.receiveValues(buildLobbyEnvironment()).join();
            loginWithAuthorizationCode(values);
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Lobby authorization failed", cause);
        }
    }

    private EnvironmentProperties buildLobbyEnvironment() {
        EnvironmentProperties lobbyEnvironment = new EnvironmentProperties();
        lobbyEnvironment.setBaseUrl(environmentProperties.getBaseUrl());
        lobbyEnvironment.setClientId(environmentProperties.getLobbyClientId());
        lobbyEnvironment.setReplayDownloadUrlFormat(environmentProperties.getReplayDownloadUrlFormat());
        lobbyEnvironment.setOauthBaseUrl(environmentProperties.getOauthBaseUrl());
        lobbyEnvironment.setOauthRedirectUrl(environmentProperties.getOauthRedirectUrl());
        lobbyEnvironment.setOauthScopes(environmentProperties.getLobbyOauthScopes());
        lobbyEnvironment.setUserBaseUrl(environmentProperties.getUserBaseUrl());
        lobbyEnvironment.setLobbyClientId(environmentProperties.getLobbyClientId());
        lobbyEnvironment.setLobbyOauthScopes(environmentProperties.getLobbyOauthScopes());
        lobbyEnvironment.setLobbyUserAgent(environmentProperties.getLobbyUserAgent());
        lobbyEnvironment.setLobbyClientVersion(environmentProperties.getLobbyClientVersion());
        return lobbyEnvironment;
    }

    private void loginWithAuthorizationCode(OAuthValuesReceiver.Values values) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", values.code());
        map.add("client_id", values.clientId());
        map.add("redirect_uri", values.redirectUri().toASCIIString());
        map.add("grant_type", "authorization_code");
        map.add("code_verifier", values.codeVerifier());

        parseResponse(requestToken(headers, map), null);
    }

    private void loginWithRefreshToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("refresh_token", refreshToken);
        map.add("client_id", environmentProperties.getLobbyClientId());
        map.add("grant_type", "refresh_token");

        parseResponse(requestToken(headers, map), refreshToken);
    }

    private void parseResponse(Map<String, Object> responseBody, String fallbackRefreshToken) {
        Object accessTokenObj = responseBody.get("access_token");
        Object expiresInObj = responseBody.get("expires_in");
        if (accessTokenObj == null || expiresInObj == null) {
            throw new IllegalStateException("OAuth token response is missing required fields (access_token or expires_in)");
        }
        String accessToken = (String) accessTokenObj;
        String returnedRefreshToken = (String) responseBody.get("refresh_token");
        String refreshToken = StringUtils.hasText(returnedRefreshToken) ? returnedRefreshToken : fallbackRefreshToken;
        long expiresIn = Long.parseLong(expiresInObj.toString());

        tokenCache = OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .build();

        if (localPreferences.getAutoLogin().isEnabled() && StringUtils.hasText(returnedRefreshToken)) {
            localPreferences.getAutoLogin().setLobbyRefreshToken(returnedRefreshToken);
        }
    }

    private Map<String, Object> requestToken(HttpHeaders headers, MultiValueMap<String, String> map) {
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<Map<String, Object>> responseEntity = oauthRestTemplate.exchange(
                "/oauth2/token",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );

        Map<String, Object> body = responseEntity.getBody();
        if (body == null) {
            throw new IllegalStateException("OAuth token endpoint returned an empty response body");
        }
        return body;
    }

    private String getHmac(String tokenValue) {
        if (tokenValue == null) {
            return null;
        }
        var claims = extractCustomClaims(tokenValue);
        @SuppressWarnings("unchecked")
        var extensions = (Map<String, Object>) claims.get("ext");
        if (extensions == null) {
            return null;
        }
        return (String) extensions.get("hmac");
    }

    private Map<String, Object> extractCustomClaims(String tokenValue) {
        try {
            JWT jwt = JWTParser.parse(tokenValue);
            return jwt.getJWTClaimsSet().getClaims();
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT", e);
        }
    }

    private void ensurePrepared() {
        if (environmentProperties == null || oauthRestTemplate == null || userRestTemplate == null) {
            throw new IllegalStateException("Lobby OAuth service has not been prepared");
        }
    }

    public EnvironmentProperties getEnvironmentProperties() {
        ensurePrepared();
        return environmentProperties;
    }
}
