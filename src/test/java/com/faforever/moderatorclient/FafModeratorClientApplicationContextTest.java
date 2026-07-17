package com.faforever.moderatorclient;

import ch.qos.logback.classic.LoggerContext;
import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.dto.BanLevel;
import com.faforever.commons.api.dto.Player;
import com.faforever.moderatorclient.mapstruct.PlayerMapper;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class FafModeratorClientApplicationContextTest {

    @Test
    void springContextStartsWithoutMapperCycles() throws Exception {
        String originalAppHome = System.getProperty("faf.app.home");
        try {
            Path tempDir = Files.createTempDirectory("faf-context-test");
            System.setProperty("faf.app.home", tempDir.toString());
            SpringApplication application = new SpringApplication(FafModeratorClientApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);

            try (ConfigurableApplicationContext context = application.run()) {
                PlayerMapper playerMapper = context.getBean(PlayerMapper.class);
                Player player = new Player();
                player.setId("42");
                player.setLogin("BannedUser");
                BanInfo ban = new BanInfo();
                ban.setLevel(BanLevel.GLOBAL);
                ban.setPlayer(player);
                player.setBans(List.of(ban));

                PlayerFX mappedPlayer = playerMapper.map(player);

                assertThat(mappedPlayer.getBans(), hasSize(1));
                assertThat(mappedPlayer.isBannedGlobally(), is(true));
                assertThat(mappedPlayer.getBans().getFirst().getPlayer(), sameInstance(mappedPlayer));
            }
        } finally {
            if (originalAppHome == null) {
                System.clearProperty("faf.app.home");
            } else {
                System.setProperty("faf.app.home", originalAppHome);
            }
            if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
                loggerContext.stop();
            }
        }
    }
}
