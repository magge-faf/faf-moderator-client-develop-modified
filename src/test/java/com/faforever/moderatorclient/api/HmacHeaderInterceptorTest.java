package com.faforever.moderatorclient.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class HmacHeaderInterceptorTest {

    private final HmacHeaderInterceptor instance = new HmacHeaderInterceptor();

    @Test
    void addsHmacHeaderWhenConfigured() throws IOException {
        instance.setHmac("signed-value");
        MockClientHttpRequest request = new MockClientHttpRequest();

        instance.intercept(request, new byte[0], (executedRequest, body) -> {
            assertThat(executedRequest.getHeaders().get("X-HMAC"), contains("signed-value"));
            return new MockClientHttpResponse(new byte[0], 200);
        });
    }

    @Test
    void leavesRequestUntouchedWhenHmacIsNotConfigured() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest();

        instance.intercept(request, new byte[0], (executedRequest, body) -> {
            assertThat(executedRequest.getHeaders().getFirst("X-HMAC"), is(nullValue()));
            return new MockClientHttpResponse(new byte[0], 200);
        });
    }
}
