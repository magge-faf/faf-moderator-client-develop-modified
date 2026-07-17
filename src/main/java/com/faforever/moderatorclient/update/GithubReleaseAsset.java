package com.faforever.moderatorclient.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubReleaseAsset(
        @JsonProperty("name") String name,
        @JsonProperty("browser_download_url") String browserDownloadUrl,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("size") long size
) {
    public boolean isZipAsset() {
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalizedName.endsWith(".zip") || normalizedType.contains("zip");
    }
}
