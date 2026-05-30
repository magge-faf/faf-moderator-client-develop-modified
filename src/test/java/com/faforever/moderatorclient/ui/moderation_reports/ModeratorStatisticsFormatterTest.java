package com.faforever.moderatorclient.ui.moderation_reports;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

class ModeratorStatisticsFormatterTest {

    @Test
    void formatStatisticsOrdersModeratorsByTotalActivity() {
        String result = ModeratorStatisticsFormatter.formatStatistics(
                "This Week",
                List.of(
                        new ModeratorStatisticsFormatter.ModeratorActivity("Mod B", 1, 1),
                        new ModeratorStatisticsFormatter.ModeratorActivity("Mod A", 5, 0)));

        assertThat(result, containsString("Moderator Statistics for This Week"));
        assertThat(result, containsString("Mod A           - Completed:   5, Discarded:   0, Total:    5\n"));
        assertThat(result, containsString("Mod B           - Completed:   1, Discarded:   1, Total:    2\n"));
        assertThat(result, containsString("Overall - Completed:   6, Discarded:   1, Total:    7\n"));
    }

    @Test
    void formatQuotasIncludesThisWeekAndLastWeekStatus() {
        String result = ModeratorStatisticsFormatter.formatQuotas(
                List.of(new ModeratorStatisticsFormatter.ModeratorActivity("Mod A", 2, 0)),
                List.of(new ModeratorStatisticsFormatter.ModeratorActivity("Mod A", 1, 0)),
                2);

        assertThat(result, containsString("Moderator Quotas"));
        assertThat(result, containsString("Mod A           - Total Reports:    2 ✅\n"));
        assertThat(result, containsString("Mod A           - Total Reports:    1 ❌ (Last Week: ✅)\n"));
        assertThat(result, containsString("Required Quota: 2\n"));
    }
}
