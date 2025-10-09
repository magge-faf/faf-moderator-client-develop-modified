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
    // Moderators = page 10_000
    // Regular users = page 100
    // Server Requests Per Minute 100

    private int defaultPageSize = 10_000;
    private int defaultResultSize = 10_000;

    private int maxPageSizeBans = 10_000;
    private int maxPageSizeReports = 10_000;
    private int maxPageSizeLatestRegistrations = 10_000;
    private int maxPageSizeSmurfVillageLookup = 10_000;

    private int maxResultPageSizeAvatars = 100;
    private int maxRequestsToServerPerMinute = 90;

    // Old Values For Legacy Compatibility
    private int maxPageSize = 10_000;
    private int maxResultSize = 10_000_000;
}
