package com.faforever.moderatorclient.api;


import com.faforever.commons.api.dto.ApiException;
import com.github.jasminb.jsonapi.exceptions.ResourceParseException;
import com.github.jasminb.jsonapi.models.errors.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Component
@Slf4j
public class JsonApiErrorHandler extends DefaultResponseErrorHandler {
    private static final int DEBUG_BODY_MAX_LENGTH = 512;
    private final JsonApiMessageConverter jsonApiMessageConverter;

    public JsonApiErrorHandler(JsonApiMessageConverter jsonApiMessageConverter) {
        this.jsonApiMessageConverter = jsonApiMessageConverter;
    }

    @Override
    protected void handleError(ClientHttpResponse response, HttpStatusCode statusCode, URI url, HttpMethod method) throws IOException {
        byte[] responseBody = response.getBody().readAllBytes();
        ClientHttpResponse bufferedResponse = new BufferedClientHttpResponse(response, responseBody);

        log.warn("Api call returned with error code '{}'", statusCode);
        log.debug("Api error response body: {}", redactAndTruncateBody(responseBody));

        if (statusCode == HttpStatus.UNPROCESSABLE_CONTENT) {
            try {
                jsonApiMessageConverter.readInternal(Errors.class, bufferedResponse);
            } catch (ResourceParseException e) {
                var errors = e.getErrors();
                throw new ApiException(errors != null ? errors.getErrors() : Collections.emptyList());
            }
        }
        super.handleError(bufferedResponse, statusCode, url, method);
    }

    private String redactAndTruncateBody(byte[] responseBody) {
        String body = new String(responseBody, StandardCharsets.UTF_8)
                .replaceAll("(?i)(\"(?:access_token|refresh_token|id_token|token|password|secret)\"\\s*:\\s*\")[^\"]*(\")", "$1[REDACTED]$2");

        if (body.length() <= DEBUG_BODY_MAX_LENGTH) {
            return body;
        }

        return body.substring(0, DEBUG_BODY_MAX_LENGTH) + "...[truncated]";
    }

    private record BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {
        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
