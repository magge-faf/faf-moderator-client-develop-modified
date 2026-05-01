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
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.List;

import java.util.concurrent.CountDownLatch;

@Service
@Slf4j
public class FafUserCommunicationService {
    private final OAuthTokenInterceptor oAuthTokenInterceptor;
    private final HmacHeaderInterceptor hmacHeaderInterceptor;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CountDownLatch authorizedLatch;
    private RestTemplate restTemplate;
    private EnvironmentProperties environmentProperties;


    public FafUserCommunicationService(OAuthTokenInterceptor oAuthTokenInterceptor, HmacHeaderInterceptor hmacHeaderInterceptor, ApplicationEventPublisher applicationEventPublisher) {
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
        restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
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
}
