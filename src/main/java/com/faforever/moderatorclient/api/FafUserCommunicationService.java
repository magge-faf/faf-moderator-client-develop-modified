package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.api.event.FafUserFailModifyEvent;
import com.faforever.moderatorclient.api.event.HydraAuthorizedEvent;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Service
@Slf4j
public class FafUserCommunicationService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final OAuthTokenInterceptor oAuthTokenInterceptor;
    private final HmacHeaderInterceptor hmacHeaderInterceptor;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CountDownLatch authorizedLatch;
    private RestTemplate restTemplate;
    private EnvironmentProperties environmentProperties;

    public FafUserCommunicationService(OAuthTokenInterceptor oAuthTokenInterceptor,
                                       HmacHeaderInterceptor hmacHeaderInterceptor,
                                       ApplicationEventPublisher applicationEventPublisher) {
        this.oAuthTokenInterceptor = oAuthTokenInterceptor;
        this.hmacHeaderInterceptor = hmacHeaderInterceptor;
        this.applicationEventPublisher = applicationEventPublisher;
        authorizedLatch = new CountDownLatch(1);
    }

    public RestOperations getRestTemplate() {
        return restTemplate;
    }

    public void initialize(EnvironmentProperties environmentProperties) {
        this.environmentProperties = environmentProperties;
    }

    @SneakyThrows
    @EventListener
    public void authorize(HydraAuthorizedEvent event) {
        // Ban saves call this client on the JavaFX thread, so a stalled request must not hang the UI.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        restTemplate = new RestTemplate(requestFactory);
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(environmentProperties.getUserBaseUrl()));
        restTemplate.setInterceptors(List.of(oAuthTokenInterceptor, hmacHeaderInterceptor));
        authorizedLatch.countDown();
    }

    @SneakyThrows
    public void post(String url, Object object) {
        try {
            authorizedLatch.await();
            restTemplate.postForObject(url, object, String.class);
        } catch (Throwable t) {
            applicationEventPublisher.publishEvent(new FafUserFailModifyEvent(t, object.getClass(), url));
            throw t;
        }
    }

    /**
     * Best-effort POST that never publishes a failure event or throws.
     * Returns null on success, otherwise why it failed.
     */
    public UserServiceFailure tryPost(String url, Object object) {
        try {
            authorizedLatch.await();
            restTemplate.postForObject(url, object, String.class);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new UserServiceFailure("interrupted", false);
        } catch (Exception e) {
            return describeFailure(e);
        }
    }

    /**
     * The HMAC in the access token's ext claim is minted once at browser login and copied unchanged on every refresh,
     * so long-lived auto-login sessions carry an HMAC that Cloudflare's timed check eventually rejects with a challenge.
     * Fetches a freshly minted HMAC and makes it the X-HMAC header for subsequent requests.
     * Returns null on success, otherwise why it failed.
     */
    public UserServiceFailure tryRefreshHmac() {
        try {
            authorizedLatch.await();
            HmacChallengeToken challengeToken = restTemplate.getForObject("/challenge/token", HmacChallengeToken.class);
            if (challengeToken == null || challengeToken.token() == null || challengeToken.token().isBlank()) {
                return new UserServiceFailure("empty /challenge/token response", false);
            }
            hmacHeaderInterceptor.setHmac(challengeToken.token());
            log.info("Refreshed X-HMAC from /challenge/token (issued at {})", hmacIssuedAt(challengeToken.token()));
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new UserServiceFailure("interrupted", false);
        } catch (Exception e) {
            return describeFailure(e);
        }
    }

    /** The HMAC format is {epochSeconds}-{signature}; only the public timestamp part is logged. */
    private static String hmacIssuedAt(String hmac) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(hmac.substring(0, hmac.indexOf('-')))).toString();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static UserServiceFailure describeFailure(Exception e) {
        if (e instanceof RestClientResponseException responseException) {
            String cfMitigated = responseException.getResponseHeaders() == null
                    ? null : responseException.getResponseHeaders().getFirst("cf-mitigated");
            return cfMitigated != null
                    ? new UserServiceFailure("HTTP " + responseException.getStatusCode().value()
                    + ", blocked by Cloudflare (cf-mitigated: " + cfMitigated + ")", true)
                    : new UserServiceFailure("HTTP " + responseException.getStatusCode().value()
                    + " " + responseException.getStatusText(), false);
        }
        return new UserServiceFailure(e.getClass().getSimpleName() + ": " + e.getMessage(), false);
    }

    @SneakyThrows
    public String getIrcChatToken() {
        authorizedLatch.await();
        IrcChatToken token = restTemplate.getForObject("/irc/ergochat/token", IrcChatToken.class);
        if (token == null || token.value() == null || token.value().isBlank()) {
            throw new IllegalStateException("FAF IRC token response is empty");
        }
        return token.value();
    }

    @SneakyThrows
    public String getLobbyAccessUrl() {
        authorizedLatch.await();
        LobbyAccessInfo accessInfo = restTemplate.getForObject("/lobby/access", LobbyAccessInfo.class);
        if (accessInfo == null || accessInfo.accessUrl() == null || accessInfo.accessUrl().isBlank()) {
            throw new IllegalStateException("FAF lobby access response is empty");
        }
        return accessInfo.accessUrl();
    }
}
