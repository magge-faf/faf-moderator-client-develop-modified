package com.faforever.moderatorclient.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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

    private int maxPageSize = 10_000;
    private int maxResultSize = 10_000_000;
    private int maxResultPageSizeAvatars = 100;
}
