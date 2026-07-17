package com.faforever.moderatorclient.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRelease(
        @JsonProperty("tag_name") String tagName,
        @JsonProperty("name") String name,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("body") String body,
        @JsonProperty("published_at") OffsetDateTime publishedAt,
        @JsonProperty("assets") List<GithubReleaseAsset> assets
) {
    public GithubRelease {
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : tagName;
    }

    public String changelogText() {
        return body == null ? "" : body.trim();
    }
}
