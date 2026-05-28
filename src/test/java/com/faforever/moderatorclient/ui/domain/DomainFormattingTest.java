package com.faforever.moderatorclient.ui.domain;

import com.faforever.commons.api.dto.BanLevel;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class DomainFormattingTest {

    @Test
    void gameReplayUrlUsesGameIdWhenPresent() {
        GameFX game = new GameFX();
        game.setId("12345");

        assertThat(game.getReplayUrl("https://replay.example.test/%s"), is("https://replay.example.test/12345"));
    }

    @Test
    void gameReplayUrlIsNullWithoutGameId() {
        assertThat(new GameFX().getReplayUrl("https://replay.example.test/%s"), is(nullValue()));
    }

    @Test
    void playerRepresentationTracksLoginAndId() {
        PlayerFX player = new PlayerFX();
        player.setLogin("Blackheart");
        player.setId("12");

        assertThat(player.getRepresentation(), is("Blackheart [id 12]"));

        player.setLogin("Calypso");

        assertThat(player.getRepresentation(), is("Calypso [id 12]"));
    }

    @Test
    void playerGlobalBanStatusReflectsActiveGlobalBansOnly() {
        PlayerFX player = new PlayerFX();
        BanInfoFX expiredGlobalBan = new BanInfoFX();
        expiredGlobalBan.setExpiresAt(OffsetDateTime.now().minusDays(1));
        expiredGlobalBan.setLevel(BanLevel.GLOBAL);
        player.getBans().add(expiredGlobalBan);

        assertThat(player.isBannedGlobally(), is(false));

        BanInfoFX activeGlobalBan = new BanInfoFX();
        activeGlobalBan.setLevel(BanLevel.GLOBAL);
        player.getBans().add(activeGlobalBan);

        assertThat(player.isBannedGlobally(), is(true));
    }

    @Test
    void votingEntitiesFormatWithDisplayTextAndId() {
        VotingSubjectFX subject = new VotingSubjectFX();
        subject.setSubject("Balance vote");
        subject.setId("1");
        VotingQuestionFX question = new VotingQuestionFX();
        question.setQuestion("Nerf it?");
        question.setId("2");
        VotingChoiceFX choice = new VotingChoiceFX();
        choice.setChoiceText("Yes");
        choice.setId("3");

        assertThat(subject.toString(), is("Balance vote (id=1)"));
        assertThat(question.toString(), is("Nerf it? (id=2)"));
        assertThat(choice.toString(), is("Yes (id=3)"));
    }
}
