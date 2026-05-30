package com.faforever.moderatorclient.ui.moderation_reports;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModeratorStatisticsFormatter {

    private ModeratorStatisticsFormatter() {
    }

    static String formatStatistics(String period, List<ModeratorActivity> activities) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Moderator Statistics for " + period));
        sb.append("\n\n");

        long totalCompleted = activities.stream().mapToLong(ModeratorActivity::completedReports).sum();
        long totalDiscarded = activities.stream().mapToLong(ModeratorActivity::discardedReports).sum();
        long grandTotal = totalCompleted + totalDiscarded;

        activities.stream()
                .sorted((a1, a2) -> Long.compare(
                        a2.completedReports() + a2.discardedReports(),
                        a1.completedReports() + a1.discardedReports()))
                .forEach(activity -> {
                    long individualTotal = activity.completedReports() + activity.discardedReports();
                    sb.append(String.format("%-15s - Completed: %3d, Discarded: %3d, Total: %4d\n",
                            activity.moderator(),
                            activity.completedReports(),
                            activity.discardedReports(),
                            individualTotal));
                });

        sb.append("\n");
        sb.append(String.format("Overall - Completed: %3d, Discarded: %3d, Total: %4d\n",
                totalCompleted, totalDiscarded, grandTotal));

        return sb.toString();
    }

    static String formatQuotas(List<ModeratorActivity> lastWeekActivity, List<ModeratorActivity> thisWeekActivity, int quota) {
        StringBuilder sb = new StringBuilder();
        sb.append("Moderator Quotas\n");
        sb.append("------------------------------------\n\n");

        Map<String, ModeratorQuotaStatus> moderatorStatus = new HashMap<>();

        sb.append("Last Week Activity:\n\n");
        lastWeekActivity.stream()
                .sorted(Comparator.comparing(ModeratorActivity::moderator))
                .forEach(activity -> {
                    long totalReports = activity.completedReports() + activity.discardedReports();
                    boolean quotaReached = totalReports >= quota;
                    String statusSymbol = quotaReached ? "✅" : "❌";
                    sb.append(String.format("%-15s - Total Reports: %4d %s\n",
                            activity.moderator(),
                            totalReports,
                            statusSymbol));
                    moderatorStatus.put(activity.moderator(), new ModeratorQuotaStatus(totalReports, quotaReached));
                });
        sb.append("\n");

        sb.append("This Week Activity:\n\n");
        thisWeekActivity.stream()
                .sorted(Comparator.comparing(ModeratorActivity::moderator))
                .forEach(activity -> {
                    long totalReports = activity.completedReports() + activity.discardedReports();
                    boolean quotaReached = totalReports >= quota;
                    String statusSymbol = quotaReached ? "✅" : "❌";
                    String lastWeekStatus = moderatorStatus.containsKey(activity.moderator()) ?
                            (moderatorStatus.get(activity.moderator()).quotaReached() ? "✅" : "❌") : "N/A";
                    sb.append(String.format("%-15s - Total Reports: %4d %s (Last Week: %s)\n",
                            activity.moderator(),
                            totalReports,
                            statusSymbol,
                            lastWeekStatus));
                });

        sb.append("\n------------------------------------\n");
        sb.append(String.format("Required Quota: %d\n", quota));

        return sb.toString();
    }

    record ModeratorActivity(String moderator, long completedReports, long discardedReports) {
    }

    private record ModeratorQuotaStatus(long totalReports, boolean quotaReached) {
    }
}
