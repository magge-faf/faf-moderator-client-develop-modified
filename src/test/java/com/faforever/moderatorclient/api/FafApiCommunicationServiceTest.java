package com.faforever.moderatorclient.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Deque;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

class FafApiCommunicationServiceTest {

    @BeforeEach
    void clearRequestTimestamps() throws Exception {
        requestTimestamps().clear();
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

    @SuppressWarnings("unchecked")
    private Deque<Long> requestTimestamps() throws Exception {
        Field field = FafApiCommunicationService.class.getDeclaredField("requestTimestamps");
        field.setAccessible(true);
        return (Deque<Long>) field.get(null);
    }
}
