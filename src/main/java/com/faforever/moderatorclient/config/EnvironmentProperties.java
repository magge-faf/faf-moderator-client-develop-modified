package com.faforever.moderatorclient.config;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

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

    private int maxPageSize = 10_000; // Server allows 10k max
    private int maxResultSize = 10_000_000;
}
