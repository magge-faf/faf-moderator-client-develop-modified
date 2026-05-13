package com.faforever.moderatorclient.api.elide;

import com.faforever.commons.api.dto.Map;
import com.faforever.commons.api.dto.MapVersion;
import com.faforever.commons.api.elide.ElideNavigator;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElideNavigatorTest {
    @Test
    public void testGetList() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .collection()
                .build(), is("/data/mapVersion"));
    }

    @Test
    public void testGetId() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .id("5")
                .build(), is("/data/mapVersion/5"));
    }

    @Test
    public void testGetListSingleInclude() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .collection()
                .addInclude("map")
                .build(), is("/data/mapVersion?include=map"));
    }

    @Test
    public void testGetListMultipleInclude() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .collection()
                .addInclude("map")
                .addInclude("map.author")
                .build(), is("/data/mapVersion?include=map,map.author"));
    }

    @Test
    public void testGetListFiltered() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .collection()
                .setFilter(
                        ElideNavigator.qBuilder()
                                .intNum("map.id").gt(10)
                                .or()
                                .string("hello").eq("nana")
                )
                .build(), is("/data/mapVersion?filter=map.id=gt=\"10\",hello==\"nana\""));
    }

    @Test
    public void testGetListCombinedFilter() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .collection()
                .addInclude("map")
                .addInclude("map.author")
                .pageSize(10)
                .pageNumber(3)
                .setFilter(
                        ElideNavigator.qBuilder()
                                .intNum("map.id").gt(10)
                                .or()
                                .string("hello").eq("nana")
                )
                .build(), is("/data/mapVersion?include=map,map.author&filter=map.id=gt=\"10\",hello==\"nana\"&page[size]=10&page[number]=3"));
    }

    @Test
    public void testGetIdMultipleInclude() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .id("5")
                .addInclude("map")
                .addInclude("map.author")
                .build(), is("/data/mapVersion/5?include=map,map.author"));
    }

    @Test
    public void testGetListSorted() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .collection()
                .addSortingRule("sortCritASC", true)
                .addSortingRule("sortCritDESC", false)
                .build(), is("/data/mapVersion?sort=sortCritASC,-sortCritDESC"));
    }

    @Test
    public void testNavigateFromIdToId() {
        assertThat(ElideNavigator.of(MapVersion.class)
                .id("5")
                .navigateRelationship(Map.class, "map")
                .id("1234")
                .build(), is("/data/mapVersion/5/map/1234"));
    }
}
