package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Component
@Slf4j
@RequiredArgsConstructor
public class ReportStatisticsController implements Controller<Region> {
    @FXML
    public VBox root;
    @FXML
    public TableView<ModerationReportController.ModeratorStatistics> moderatorStatisticsTableView;
    @FXML
    private final ModerationReportController moderationReportController;
    @FXML
    public TextArea reportStatisticsTextArea;

    @Override
    public VBox getRoot() {
        reportStatisticsTextArea.setText("If all reports from Reports tab are available, then they will be analyzed.");
        return root;
    }

    public void processStatistics(List<ModerationReportFX> reports, boolean showAllTime) {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth currentMonth = YearMonth.now();  // Current month to exclude

        Map<String, Long> reportsPerDay = reports.stream()
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate().toString(), Collectors.counting()));

        Map<String, Long> reportsPerWeek = reports.stream()
                .collect(Collectors.groupingBy(r -> r.getCreateTime().getYear() + "-W" + r.getCreateTime().get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()), Collectors.counting()));

        Map<String, Long> reportsPerMonth = reports.stream()
                .filter(r -> !YearMonth.from(r.getCreateTime()).equals(currentMonth))  // Exclude the current month
                .collect(Collectors.groupingBy(r -> r.getCreateTime().getYear() + "-" + String.format("%02d", r.getCreateTime().getMonthValue()), Collectors.counting()));

        Map<String, Long> reportsLastMonth = reports.stream()
                .filter(r -> YearMonth.from(r.getCreateTime()).equals(lastMonth))
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate().toString(), Collectors.counting()));

        List<Map.Entry<String, Long>> sortedReportsPerDay;
        List<Map.Entry<String, Long>> sortedReportsPerWeek;
        List<Map.Entry<String, Long>> sortedReportsPerMonth;
        List<Map.Entry<String, Long>> sortedReportsLastMonth;
        LocalDate today = LocalDate.now();

        if (showAllTime) {
            sortedReportsPerDay = reportsPerDay.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .toList();

            sortedReportsPerWeek = reportsPerWeek.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .toList();

            sortedReportsPerMonth = reportsPerMonth.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .toList();

            sortedReportsLastMonth = reportsLastMonth.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .toList();

        } else {
            sortedReportsPerDay = reportsPerDay.entrySet().stream()
                    .filter(entry -> LocalDate.parse(entry.getKey()).isBefore(today))
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .limit(7)
                    .toList();

            sortedReportsPerWeek = reportsPerWeek.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .limit(7)
                    .toList();

            sortedReportsPerMonth = reportsPerMonth.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .limit(12)  // Only take the last 12 months, excluding the current month
                    .toList();

            sortedReportsLastMonth = reportsLastMonth.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .limit(31)
                    .toList();
        }

        int avgReportsPerDay = (int) sortedReportsPerDay.stream().mapToLong(Map.Entry::getValue).average().orElse(0);
        int avgReportsPerWeek = (int) sortedReportsPerWeek.stream().mapToLong(Map.Entry::getValue).average().orElse(0);
        int avgReportsPerMonth = (int) sortedReportsPerMonth.stream().mapToLong(Map.Entry::getValue).average().orElse(0);
        int avgReportsLastMonth = (int) sortedReportsLastMonth.stream().mapToLong(Map.Entry::getValue).average().orElse(0);

        String formattedDailyReports = sortedReportsPerDay.stream()
                .map(entry -> String.format("📅 %s → %d reports%s", entry.getKey(), entry.getValue(), isMonday(entry.getKey()) ? " (Monday)" : ""))
                .collect(Collectors.joining(System.lineSeparator()));

        String formattedWeeklyReports = sortedReportsPerWeek.stream()
                .map(entry -> String.format("📆 %s → %d reports", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));

        String formattedMonthlyReports = sortedReportsPerMonth.stream()
                .map(entry -> String.format("📊 %s → %d reports", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));

        String formattedLastMonthReports = sortedReportsLastMonth.stream()
                .map(entry -> String.format("📅 %s → %d reports%s", entry.getKey(), entry.getValue(), isMonday(entry.getKey()) ? " (Monday)" : ""))
                .collect(Collectors.joining(System.lineSeparator()));

        reportStatisticsTextArea.setText("");
        StringBuilder reportLog = new StringBuilder();

        reportLog.append(String.format("📊 Average Reports Per Day: ~%d%n", avgReportsPerDay));

        int numDays = sortedReportsPerDay.size();
        reportLog.append(String.format("📅 Reports Per Day (Last %d Days):%n%s%n%n", numDays, formattedDailyReports));

        reportLog.append(String.format("📊 Average Reports Per Week: ~%d%n", avgReportsPerWeek));

        int numWeeks = sortedReportsPerWeek.size();
        reportLog.append(String.format("📆 Reports Per Week (Last %d Weeks):%n%s%n%n", numWeeks, formattedWeeklyReports));

        reportLog.append(String.format("📊 Average Reports Per Month: ~%d%n", avgReportsPerMonth));

        int numMonths = sortedReportsPerMonth.size();
        reportLog.append(String.format("📅 Reports Per Month (Last %d Months):%n%s%n%n", numMonths, formattedMonthlyReports));

        reportLog.append(String.format("📊 Average Reports Per Day Last Month: ~%d%n", avgReportsLastMonth));

        reportLog.append(String.format("📅 Reports Per Day (Last Month - %s):%n%s%n", lastMonth, formattedLastMonthReports));

        reportStatisticsTextArea.setText(reportLog.toString());
    }

    private boolean isMonday(String date) {
        LocalDate localDate = LocalDate.parse(date);
        return localDate.getDayOfWeek() == DayOfWeek.MONDAY;
    }

    public void onUpdateStatisticsAllReports() {
        List<ModerationReportFX> allReports = moderationReportController.getAllCachedReports();
        processStatistics(allReports, true);
    }

    public void onUpdateStatisticsButtonLastYear() {
        List<ModerationReportFX> allReports = moderationReportController.getAllCachedReports();

        YearMonth cutoff = YearMonth.now().minusMonths(24);
        List<ModerationReportFX> lastTwoYearsReports = allReports.stream()
                .filter(r -> YearMonth.from(r.getCreateTime()).isAfter(cutoff) || YearMonth.from(r.getCreateTime()).equals(cutoff))
                .toList();

        processStatistics(lastTwoYearsReports, false);
    }
}
