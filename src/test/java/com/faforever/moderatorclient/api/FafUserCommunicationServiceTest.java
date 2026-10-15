package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.api.event.HydraAuthorizedEvent;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FafUserCommunicationServiceTest {

    private static final String BASE_URL = "https://user.example.com";

    private final HmacHeaderInterceptor hmacHeaderInterceptor = new HmacHeaderInterceptor();
    private FafUserCommunicationService instance;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TokenService tokenService = mock();
        when(tokenService.getRefreshedTokenValue()).thenReturn("access-token");
        EnvironmentProperties environmentProperties = new EnvironmentProperties();
        environmentProperties.setUserBaseUrl(BASE_URL);

        instance = new FafUserCommunicationService(new OAuthTokenInterceptor(tokenService), hmacHeaderInterceptor,
                mock(ApplicationEventPublisher.class));
        instance.initialize(environmentProperties);
        instance.authorize(new HydraAuthorizedEvent());
        server = MockRestServiceServer.bindTo((RestTemplate) instance.getRestTemplate()).build();
    }

    @Test
    void refreshedHmacIsSentOnFollowingRequests() {
        hmacHeaderInterceptor.setHmac("1000-stale");
        server.expect(requestTo(BASE_URL + "/challenge/token"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"token\":\"2000-fresh\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/oauth2/revokeTokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-HMAC", "2000-fresh"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(instance.tryRefreshHmac(), is(nullValue()));
        assertThat(instance.tryPost("/oauth2/revokeTokens", "{}"), is(nullValue()));
        server.verify();
    }

    @Test
    void keepsLoginHmacAndReportsCloudflareChallenge() {
        hmacHeaderInterceptor.setHmac("1000-stale");
        HttpHeaders challengeHeaders = new HttpHeaders();
        challengeHeaders.add("cf-mitigated", "challenge");
        server.expect(requestTo(BASE_URL + "/challenge/token"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(challengeHeaders));
        server.expect(requestTo(BASE_URL + "/oauth2/revokeTokens"))
                .andExpect(header("X-HMAC", "1000-stale"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(challengeHeaders));

        assertThat(instance.tryRefreshHmac(), is(new UserServiceFailure("HTTP 403, blocked by Cloudflare (cf-mitigated: challenge)", true)));
        assertThat(instance.tryPost("/oauth2/revokeTokens", "{}"), is(new UserServiceFailure("HTTP 403, blocked by Cloudflare (cf-mitigated: challenge)", true)));
        server.verify();
    }
}
