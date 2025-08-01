package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.api.event.HydraAuthorizedEvent;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.login.OAuthValuesReceiver;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TokenService {
    private final LocalPreferences localPreferences;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final HmacHeaderInterceptor hmacHeaderInterceptor;
    private RestTemplate restTemplate;
    private EnvironmentProperties environmentProperties;
    private OAuth2AccessTokenResponse tokenCache;

    public void prepare(EnvironmentProperties environmentProperties) {
        this.environmentProperties = environmentProperties;
        this.restTemplate = new RestTemplateBuilder()
                .requestFactory(JdkClientHttpRequestFactory.class)
                .additionalInterceptors(hmacHeaderInterceptor)
                .rootUri(environmentProperties.getOauthBaseUrl())
                .build();
    }

    @SneakyThrows
    public String getRefreshedTokenValue() {
        if (tokenCache.getAccessToken().getExpiresAt().isBefore(Instant.now())) {
            log.info("Token expired, requesting new with refresh token");
            loginWithRefreshToken(tokenCache.getRefreshToken().getTokenValue(), false);
        } else {
            log.debug("Token still valid for {} seconds", Duration.between(Instant.now(), tokenCache.getAccessToken().getExpiresAt()).getSeconds());
        }

        return tokenCache.getAccessToken().getTokenValue();
    }

    public String getHmac() {
        var claims = extractCustomClaims(tokenCache.getAccessToken().getTokenValue());
        var extensions = (Map<String, Object>) claims.get("ext");
        return (String) extensions.get("hmac");
    }

    public Map<String, Object> extractCustomClaims(String tokenValue) {
        try {
            JWT jwt = JWTParser.parse(tokenValue);
            return jwt.getJWTClaimsSet().getClaims();
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT", e);
        }
    }

    public void loginWithAuthorizationCode(OAuthValuesReceiver.Values values) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", values.code());
        map.add("client_id", values.clientId());
        map.add("redirect_uri", values.redirectUri().toASCIIString());
        map.add("grant_type", "authorization_code");
        map.add("code_verifier", values.codeVerifier());

        Map<String, Object>  responseBody = requestToken(headers, map);
        if (responseBody != null) {
            parseResponse(responseBody);

            applicationEventPublisher.publishEvent(new HydraAuthorizedEvent());
        }

    }

    private void parseResponse(Map<String, Object> responseBody) {
        String accessToken = (String) responseBody.get("access_token");
        String refreshToken = (String) responseBody.get("refresh_token");
        long expiresIn = Long.parseLong(responseBody.get("expires_in").toString());

        tokenCache = OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .build();

        if (localPreferences.getAutoLogin().isEnabled()) {
            log.info("Auto login enabled, persisting refresh token");
            localPreferences.getAutoLogin().setRefreshToken(refreshToken);
        }
        hmacHeaderInterceptor.setHmac(getHmac());
    }

    public void loginWithRefreshToken(String refreshToken, boolean fireEvent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("refresh_token", refreshToken);
        map.add("client_id", environmentProperties.getClientId());
        map.add("grant_type", "refresh_token");

        Map<String, Object> responseBody = requestToken(headers, map);

        if (responseBody != null) {
            parseResponse(responseBody);

            if (fireEvent) {
                applicationEventPublisher.publishEvent(new HydraAuthorizedEvent());
            }
        }
    }

    private Map<String, Object> requestToken(HttpHeaders headers, MultiValueMap<String, String> map) {
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                "/oauth2/token",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
        );

        return responseEntity.getBody();
    }
}
