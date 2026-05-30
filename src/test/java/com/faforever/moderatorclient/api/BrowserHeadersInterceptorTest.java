package com.faforever.moderatorclient.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class BrowserHeadersInterceptorTest {

    private final BrowserHeadersInterceptor instance = new BrowserHeadersInterceptor();

    @Test
    void addsBrowserLikeHeadersBeforeExecutingRequest() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest();
        byte[] body = "payload".getBytes();

        instance.intercept(request, body, (executedRequest, executedBody) -> {
            HttpHeaders headers = executedRequest.getHeaders();
            assertThat(headers.getFirst(HttpHeaders.USER_AGENT), containsString("Chrome/124.0.0.0"));
            assertThat(headers.getFirst(HttpHeaders.ACCEPT), is("application/json, text/plain, */*"));
            assertThat(headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE), is("en-US,en;q=0.9"));
            assertThat(executedBody, is(body));
            return new MockClientHttpResponse(new byte[0], 200);
        });
    }
}
