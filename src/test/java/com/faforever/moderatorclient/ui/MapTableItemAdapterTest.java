package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.Map;
import com.faforever.commons.api.dto.MapVersion;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class MapTableItemAdapterTest {

    @Test
    void adaptsMapFields() {
        Map map = new Map();
        map.setId("42");
        map.setDisplayName("Seton's Clutch");

        MapTableItemAdapter adapter = new MapTableItemAdapter(map);

        assertThat(adapter.isMap(), is(true));
        assertThat(adapter.isMapVersion(), is(false));
        assertThat(adapter.getId(), is("42"));
        assertThat(adapter.getNameOrDescription(), is("Seton's Clutch"));
        assertThat(adapter.getVersion(), is(nullValue()));
        assertThat(adapter.getSize(), is(nullValue()));
        assertThat(adapter.getFilename(), is(nullValue()));
        assertThat(adapter.isRanked(), is(nullValue()));
        assertThat(adapter.isHidden(), is(nullValue()));
        assertThat(adapter.getThumbnailUrlLarge(), is(nullValue()));
    }

    @Test
    void adaptsMapVersionFields() throws Exception {
        URL thumbnailUrl = new URL("https://content.example.test/maps/preview.png");
        MapVersion mapVersion = new MapVersion();
        mapVersion.setId("99");
        mapVersion.setDescription("A map version");
        mapVersion.setVersion(new ComparableVersion("2"));
        mapVersion.setWidth(512);
        mapVersion.setHeight(256);
        mapVersion.setMaxPlayers(6);
        mapVersion.setFilename("map.v0002.zip");
        mapVersion.setRanked(true);
        mapVersion.setHidden(false);
        mapVersion.setThumbnailUrlLarge(thumbnailUrl);

        MapTableItemAdapter adapter = new MapTableItemAdapter(mapVersion);

        assertThat(adapter.isMap(), is(false));
        assertThat(adapter.isMapVersion(), is(true));
        assertThat(adapter.getId(), is("99"));
        assertThat(adapter.getNameOrDescription(), is("A map version"));
        assertThat(adapter.getVersion(), is(new ComparableVersion("2")));
        assertThat(adapter.getSize(), is("512x256 (6 slots)"));
        assertThat(adapter.getFilename(), is("map.v0002.zip"));
        assertThat(adapter.isRanked(), is("yes"));
        assertThat(adapter.isHidden(), is("no"));
        assertThat(adapter.getThumbnailUrlLarge(), is(thumbnailUrl));
    }
}
