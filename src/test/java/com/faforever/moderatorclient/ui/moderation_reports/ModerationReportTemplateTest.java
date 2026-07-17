package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.moderatorclient.config.TemplateAndReasonConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ModerationReportTemplateTest {

    @Test
    public void findWarningTemplateReturnsConfiguredTemplate() {
        TemplateAndReasonConfig configuredTemplate = new TemplateAndReasonConfig();
        configuredTemplate.setName("Warning");
        configuredTemplate.setFormat("configured");

        TemplateAndReasonConfig result = ModerationReportController.findWarningTemplate(List.of(configuredTemplate));

        assertThat(result, is(configuredTemplate));
    }

    @Test
    public void findWarningTemplateFallsBackToDefaultWarningTemplate() {
        TemplateAndReasonConfig banTemplate = new TemplateAndReasonConfig();
        banTemplate.setName("Standard Ban");
        banTemplate.setFormat("ban");

        TemplateAndReasonConfig result = ModerationReportController.findWarningTemplate(List.of(banTemplate));

        assertThat(result.getName(), is(ModerationReportController.WARNING_TEMPLATE_NAME));
        assertThat(result.getFormat(), is(ModerationReportController.WARNING_TEMPLATE_FORMAT));
    }
}
