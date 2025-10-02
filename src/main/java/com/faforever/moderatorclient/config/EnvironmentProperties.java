package com.faforever.moderatorclient.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Data
public class EnvironmentProperties {

    @NotBlank
    private String baseUrl;
    @NotBlank
    private String clientId;
    @NotBlank
    private String replayDownloadUrlFormat;
    @NotBlank
    private String oauthBaseUrl;
    @NotBlank
    private String oauthRedirectUrl;
    @NotBlank
    private String oauthScopes;
    @NotBlank
    private String userBaseUrl;

    // Defines default pagination and result limits based on user roles:
    // defaultResultSize was 10_000_000
    // Moderators = page 10_000
    // Regular users = page 100
    private int defaultPageSize = 1_000;
    private int defaultResultSize = 1_000;

    private int maxPageSizeBans = 1_000;
    private int maxPageSizeReports = 1_000;

    private int maxResultPageSizeAvatars = 100;
    private int maxRequestsToServerPerMinute = 100;

    // Old Values
    private int maxPageSize = 10_000;
    private int maxResultSize = 10_000_000;
}
