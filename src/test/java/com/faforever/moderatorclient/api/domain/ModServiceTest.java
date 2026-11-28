package com.faforever.moderatorclient.api.domain;

import com.faforever.commons.api.dto.ModVersionReview;
import com.faforever.commons.api.dto.ModVersion;
import com.faforever.commons.api.dto.ModVersionReviewsSummary;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.mapstruct.ModMapper;
import com.faforever.moderatorclient.mapstruct.ModVersionMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

class ModServiceTest {

    @Test
    void deletesTheSelectedModVersionReviewThroughTheApi() {
        FafApiCommunicationService communicationService = mock();
        ModService service = new ModService(communicationService, mock(ModMapper.class), mock(ModVersionMapper.class));

        service.deleteModVersionReview("42");

        verify(communicationService).delete(any(ModVersionReview.class));
    }

    @Test
    void loadsReviewsAndTheSummaryForTheSelectedModVersion() {
        FafApiCommunicationService communicationService = mock();
        ModService service = new ModService(communicationService, mock(ModMapper.class), mock(ModVersionMapper.class));
        ModVersionReview review = new ModVersionReview();
        ModVersionReviewsSummary summary = new ModVersionReviewsSummary();
        ModVersion version = new ModVersion().setModVersionReviewsSummary(summary);

        when(communicationService.getAll(any(), any())).thenReturn(List.of(review), List.of(version));

        ModService.ModVersionReviewData result = service.getModVersionReviewData("7");

        org.junit.jupiter.api.Assertions.assertEquals(List.of(review), result.reviews());
        org.junit.jupiter.api.Assertions.assertSame(summary, result.summary());
    }
}
