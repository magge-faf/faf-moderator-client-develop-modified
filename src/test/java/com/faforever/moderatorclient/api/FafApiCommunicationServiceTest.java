package com.faforever.moderatorclient.api;

import com.faforever.commons.api.dto.MeResult;
import com.faforever.commons.api.dto.MapVersion;
import com.faforever.commons.api.elide.ElideNavigator;
import com.faforever.commons.api.elide.ElideNavigatorOnCollection;
import com.faforever.moderatorclient.api.event.ApiAuthorizedEvent;
import com.faforever.moderatorclient.api.event.HydraAuthorizedEvent;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Field;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FafApiCommunicationServiceTest {

    @BeforeEach
    void clearRequestTimestamps() throws Exception {
        requestTimestamps().clear();
        effectiveMaxRequestsPerMinute().setInt(null, 90);
    }

    @Test
    void checkRateLimitRecordsRequestTimestamp() {
        FafApiCommunicationService.checkRateLimit();

        assertThat(FafApiCommunicationService.getRequestsInLastMinute(), is(1));
        assertThat(FafApiCommunicationService.getRequestsPerSecondRolling(10), is(0.1));
    }

    @Test
    void getCooldownRemainingMillisIsZeroBelowLimit() {
        FafApiCommunicationService.checkRateLimit();

        assertThat(FafApiCommunicationService.getCooldownRemainingMillis(), is(0L));
    }

    @Test
    void getRequestsInLastMinuteDropsExpiredTimestamps() throws Exception {
        requestTimestamps().add(System.currentTimeMillis() - 61_000);
        requestTimestamps().add(System.currentTimeMillis());

        assertThat(FafApiCommunicationService.getRequestsInLastMinute(), is(1));
    }

    @Test
    void getCooldownRemainingMillisReportsWaitTimeAtLimit() throws Exception {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestTimestamps();
        for (int i = 0; i < 90; i++) {
            timestamps.add(now);
        }

        assertThat(FafApiCommunicationService.getCooldownRemainingMillis(), greaterThanOrEqualTo(59_000L));
    }

    @Test
    void getCooldownRemainingMillisUsesEffectiveLimit() throws Exception {
        effectiveMaxRequestsPerMinute().setInt(null, 2);
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestTimestamps();
        timestamps.add(now);
        timestamps.add(now);

        assertThat(FafApiCommunicationService.getCooldownRemainingMillis(), greaterThanOrEqualTo(59_000L));
    }

    @Test
    void getManyDoesNotRequestOrReturnMoreThanRequestedCount() {
        EnvironmentProperties environmentProperties = new EnvironmentProperties();
        environmentProperties.setMaxPageSize(10);
        RecordingFafApiCommunicationService service = new RecordingFafApiCommunicationService(environmentProperties);

        List<MapVersion> results = service.getMany(
                MapVersion.class,
                ElideNavigator.of(MapVersion.class).collection(),
                7,
                Collections.emptyMap());

        assertThat(results.size(), is(7));
        assertThat(service.requestedPageSizes, contains(7));
    }

    @Test
    void hasPermissionReturnsFalseWhenMeResultIsMissing() {
        FafApiCommunicationService service = new FafApiCommunicationService(null, null, null, null, null, null, null, null, new EnvironmentProperties(), null);

        assertFalse(service.hasPermission("ROLE_ADMIN"));
    }

    @Test
    void hasPermissionReturnsFalseWhenPermissionsAreMissing() throws Exception {
        FafApiCommunicationService service = new FafApiCommunicationService(null, null, null, null, null, null, null, null, new EnvironmentProperties(), null);
        MeResult meResult = mock();
        when(meResult.getPermissions()).thenReturn(null);
        meResult().set(service, meResult);

        assertFalse(service.hasPermission("ROLE_ADMIN"));
    }

    @Test
    void authorizeDoesNotPublishAuthorizedEventOrOpenLatchWhenMeFails() throws Exception {
        EnvironmentProperties environmentProperties = new EnvironmentProperties();
        environmentProperties.setBaseUrl("https://example.test");
        ApplicationEventPublisher eventPublisher = mock();
        FafApiCommunicationService service = new FailingMeFafApiCommunicationService(environmentProperties, eventPublisher);

        service.authorize(new HydraAuthorizedEvent());

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(ApiAuthorizedEvent.class));
        assertThat(authorizedLatch(service).getCount(), is(1L));
    }

    @SuppressWarnings("unchecked")
    private Deque<Long> requestTimestamps() throws Exception {
        Field field = FafApiCommunicationService.class.getDeclaredField("requestTimestamps");
        field.setAccessible(true);
        return (Deque<Long>) field.get(null);
    }

    private Field effectiveMaxRequestsPerMinute() throws Exception {
        Field field = FafApiCommunicationService.class.getDeclaredField("effectiveMaxRequestsPerMinute");
        field.setAccessible(true);
        return field;
    }

    private Field meResult() throws Exception {
        Field field = FafApiCommunicationService.class.getDeclaredField("meResult");
        field.setAccessible(true);
        return field;
    }

    private CountDownLatch authorizedLatch(FafApiCommunicationService service) throws Exception {
        Field field = FafApiCommunicationService.class.getDeclaredField("authorizedLatch");
        field.setAccessible(true);
        return (CountDownLatch) field.get(service);
    }

    private static class RecordingFafApiCommunicationService extends FafApiCommunicationService {
        private final List<Integer> requestedPageSizes = new ArrayList<>();

        private RecordingFafApiCommunicationService(EnvironmentProperties environmentProperties) {
            super(null, null, null, null, null, null, null, null, environmentProperties, null);
        }

        @Override
        public <T extends com.faforever.commons.api.elide.ElideEntity> List<T> getPage(
                Class<T> clazz,
                ElideNavigatorOnCollection<T> routeBuilder,
                int pageSize,
                int page,
                Map<String, Serializable> params) {
            requestedPageSizes.add(pageSize);
            List<T> pageResults = new ArrayList<>();
            for (int i = 0; i < pageSize + 1; i++) {
                pageResults.add(clazz.cast(mock(clazz)));
            }
            return pageResults;
        }

        @Override
        public <T extends com.faforever.commons.api.elide.ElideEntity> List<T> getPage(
                Class<T> clazz,
                ElideNavigatorOnCollection<T> routeBuilder,
                int pageSize,
                int page,
                MultiValueMap<String, String> params) {
            throw new UnsupportedOperationException("This overload is not used by getMany(Map params)");
        }
    }

    private static class FailingMeFafApiCommunicationService extends FafApiCommunicationService {
        private FailingMeFafApiCommunicationService(EnvironmentProperties environmentProperties, ApplicationEventPublisher eventPublisher) {
            super(null, null, mock(OAuthTokenInterceptor.class), mock(HmacHeaderInterceptor.class), eventPublisher, mock(), mock(JsonApiMessageConverter.class), mock(JsonApiErrorHandler.class), environmentProperties, mock());
        }

        @Override
        public <T extends com.faforever.commons.api.elide.ElideEntity> T getOne(String endpointPath, Class<T> type) {
            throw new RuntimeException("me failed");
        }
    }
}
