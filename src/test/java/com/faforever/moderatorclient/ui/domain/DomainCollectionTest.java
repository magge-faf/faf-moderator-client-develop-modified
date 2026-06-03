package com.faforever.moderatorclient.ui.domain;

import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class DomainCollectionTest {

    @Test
    void gamePlayerStatsSetterReplacesExistingValuesAndAcceptsNull() {
        GamePlayerStatsFX first = new GamePlayerStatsFX();
        GamePlayerStatsFX second = new GamePlayerStatsFX();
        GameFX game = new GameFX();
        game.getPlayerStats().add(first);

        game.setPlayerStats(List.of(second));

        assertThat(game.getPlayerStats(), contains(second));

        game.setPlayerStats(null);

        assertThat(game.getPlayerStats(), is(empty()));
    }

    @Test
    void mapVersionsSetterReplacesExistingValuesAndAcceptsNull() {
        MapVersionFX first = new MapVersionFX();
        MapVersionFX second = new MapVersionFX();
        MapFX map = new MapFX();
        map.getVersions().add(first);

        map.setVersions(List.of(second));

        assertThat(map.getVersions(), contains(second));

        map.setVersions(null);

        assertThat(map.getVersions(), is(empty()));
    }

    @Test
    void playerCollectionSettersReplaceExistingValuesAndAcceptNull() {
        PlayerFX player = new PlayerFX();
        NameRecordFX name = new NameRecordFX();
        BanInfoFX ban = new BanInfoFX();
        AvatarAssignmentFX avatarAssignment = new AvatarAssignmentFX();
        AccountLinkFx accountLink = new AccountLinkFx();
        UniqueIdAssignmentFx uniqueIdAssignment = new UniqueIdAssignmentFx();

        NameRecordFX oldName = new NameRecordFX();
        BanInfoFX oldBan = new BanInfoFX();
        AvatarAssignmentFX oldAvatarAssignment = new AvatarAssignmentFX();
        AccountLinkFx oldAccountLink = new AccountLinkFx();
        UniqueIdAssignmentFx oldUniqueIdAssignment = new UniqueIdAssignmentFx();

        uniqueIdAssignment.setId("new-unique-id");
        oldUniqueIdAssignment.setId("old-unique-id");

        player.getNames().add(oldName);
        player.getBans().add(oldBan);
        player.getAvatarAssignments().add(oldAvatarAssignment);
        player.getAccountLinks().add(oldAccountLink);
        player.getUniqueIdAssignments().add(oldUniqueIdAssignment);

        player.setNames(FXCollections.observableArrayList(name));
        player.setBans(FXCollections.observableArrayList(ban));
        player.setAvatarAssignments(FXCollections.observableArrayList(avatarAssignment));
        player.setAccountLinks(FXCollections.observableSet(accountLink));
        player.setUniqueIdAssignments(FXCollections.observableSet(uniqueIdAssignment));

        assertThat(player.getNames(), contains(name));
        assertThat(player.getBans(), contains(ban));
        assertThat(player.getAvatarAssignments(), contains(avatarAssignment));
        assertThat(player.getAccountLinks().size(), is(1));
        assertThat(player.getAccountLinks().contains(accountLink), is(true));
        assertThat(player.getAccountLinks().contains(oldAccountLink), is(false));
        assertThat(player.getUniqueIdAssignments().size(), is(1));
        assertThat(player.getUniqueIdAssignments().contains(uniqueIdAssignment), is(true));
        assertThat(player.getUniqueIdAssignments().contains(oldUniqueIdAssignment), is(false));

        player.setNames(null);
        player.setBans(null);
        player.setAvatarAssignments(null);
        player.setAccountLinks(null);
        player.setUniqueIdAssignments(null);

        assertThat(player.getNames(), is(empty()));
        assertThat(player.getBans(), is(empty()));
        assertThat(player.getAvatarAssignments(), is(empty()));
        assertThat(player.getAccountLinks(), is(empty()));
        assertThat(player.getUniqueIdAssignments(), is(empty()));
    }

    @Test
    void mapPoolAssignmentsSetterKeepsExistingValuesWhenInputIsNull() {
        MapPoolAssignmentFX first = new MapPoolAssignmentFX();
        MapPoolAssignmentFX second = new MapPoolAssignmentFX();
        MapPoolFX mapPool = new MapPoolFX();
        mapPool.getMapPoolAssignments().add(first);

        MapPoolFX result = mapPool.setMapPoolAssignments(List.of(second));

        assertThat(result, is(mapPool));
        assertThat(mapPool.getMapPoolAssignments(), contains(second));

        mapPool.setMapPoolAssignments(null);

        assertThat(mapPool.getMapPoolAssignments(), contains(second));
    }
}
