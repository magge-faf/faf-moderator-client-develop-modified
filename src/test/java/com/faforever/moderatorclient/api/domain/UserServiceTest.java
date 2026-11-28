package com.faforever.moderatorclient.api.domain;

import com.faforever.commons.api.dto.GamePlayerStats;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class UserServiceTest {
    private final FafApiCommunicationService api = mock(FafApiCommunicationService.class);
    private final UserService service = new UserService(api, null, null, null, null, null);

    @Test
    void paginatesWithFixedPageSizeWhenOnlyPartOfTheLastPageIsNeeded() {
        GamePlayerStats firstPageGame = new GamePlayerStats();
        GamePlayerStats secondPageGame = new GamePlayerStats();
        stubPage(1, Collections.nCopies(1000, firstPageGame));
        stubPage(2, Collections.nCopies(1000, secondPageGame));

        List<Integer> loadedCounts = new ArrayList<>();
        List<GamePlayerStats> games = service.getRecentPlayedGames("42", null, 1500, loadedCounts::add);

        assertEquals(1500, games.size());
        assertEquals(List.of(1000, 1500), loadedCounts);
        assertSame(firstPageGame, games.get(999));
        assertSame(secondPageGame, games.get(1000));
        verifyPages(2);
    }

    @Test
    void stopsAtTheEndOfTheAvailableHistory() {
        GamePlayerStats game = new GamePlayerStats();
        stubPage(1, Collections.nCopies(1000, game));
        stubPage(2, List.of(game));

        List<Integer> loadedCounts = new ArrayList<>();
        assertEquals(1001, service.getRecentPlayedGames("42", null, 10_000, loadedCounts::add).size());
        assertEquals(List.of(1000, 1001), loadedCounts);
        verifyPages(2);
    }

    private void stubPage(int page, List<GamePlayerStats> games) {
        when(api.getPage(eq(GamePlayerStats.class), any(), eq(1000), eq(page), eq(Collections.emptyMap())))
                .thenReturn(games);
    }

    private void verifyPages(int pageCount) {
        for (int page = 1; page <= pageCount; page++) {
            verify(api).getPage(eq(GamePlayerStats.class), any(), eq(1000), eq(page), eq(Collections.emptyMap()));
        }
        verifyNoMoreInteractions(api);
    }
}
