package com.faforever.moderatorclient.irc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserManagerTest {
    @Test
    void renamesAndRemovesUsersAcrossChannels() {
        UserManager userManager = new UserManager();
        userManager.addUser("#Aeolus", "Bravo");
        userManager.addUser("#Aeolus", "alpha");
        userManager.addUser("#mods", "Bravo");

        userManager.renameUser("Bravo", "Charlie");

        assertEquals(List.of("alpha", "Charlie"), userManager.getUsers("#Aeolus"));
        assertEquals(List.of("Charlie"), userManager.getUsers("#mods"));

        userManager.removeUserEverywhere("Charlie");

        assertEquals(List.of("alpha"), userManager.getUsers("#Aeolus"));
        assertEquals(List.of(), userManager.getUsers("#mods"));
    }

    @Test
    void replacesUsersFromWhoQueryAndTracksMembership() {
        UserManager userManager = new UserManager();
        userManager.addUser("#aeolus", "existing");

        userManager.startWhoQuery("#aeolus");
        userManager.addWhoUser("#aeolus", "alpha");
        userManager.addWhoUser("#aeolus", "bravo");
        userManager.completeWhoQuery("#aeolus");

        assertEquals(List.of("alpha", "bravo"), userManager.getUsers("#aeolus"));
        assertEquals(List.of("#aeolus"), userManager.channelsContaining("alpha"));
    }
}
