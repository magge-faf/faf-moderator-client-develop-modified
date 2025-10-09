package com.faforever.moderatorclient;

import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.config.ApplicationVersion;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.ui.MainController;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.PlatformServiceImpl;
import com.faforever.moderatorclient.ui.StageHolder;
import com.faforever.moderatorclient.ui.UiService;
import com.faforever.moderatorclient.ui.main_window.UserManagementController;
import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@ComponentScan
@Slf4j
public class FafModeratorClientApplication extends Application {

    @Bean
    public PlatformService platformService() {
        return new PlatformServiceImpl(getHostServices());
    }

    private ConfigurableApplicationContext applicationContext;
    @Autowired
    private UserManagementController userManagementController;
    @Autowired
    private ModerationReportController moderationReportController;

    private Timeline timeline;
    private Stage primaryStage;
    private boolean isFetching = false;
    private long fetchingDurationMillis = 0;
    private boolean hasFetchedReports = false;
    private long lastRefreshedTime = -1;

    public static void applicationMain(String[] args) {
        Application.launch(FafModeratorClientApplication.class, args);
    }

    @Override
    public void init() {
        SpringApplication app = new SpringApplication(FafModeratorClientApplication.class);
        applicationContext = app.run();
        applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
    }

    @Override
    public void start(Stage primaryStage) {
        Font.loadFont(Objects.requireNonNull(getClass().getResource("/style/NotoEmoji-Regular.ttf")).toExternalForm(), 12);
        StageHolder.setStage(primaryStage);
        primaryStage.setTitle("MM");
        UiService uiService = applicationContext.getBean(UiService.class);
        MainController mainController = uiService.loadFxml("ui/mainWindow.fxml");
        mainController.display();
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("/media/favicon.png"))));
        Scene scene = new Scene(mainController.getRoot());

        String stylesheet = "/style/main-light.css";
        var localPreferences = applicationContext.getBean(LocalPreferences.class);
        if (localPreferences.getTabSettings().isDarkModeCheckBox()) {
            stylesheet = "/style/main-dark.css";
        }

        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(stylesheet)).toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            log.info("Close request received, exiting");
            Platform.runLater(() -> {
                try {
                    userManagementController.saveOnExitContent();
                } finally {
                    Platform.exit();
                }
            });
        });

        startTimerThread(primaryStage);
    }

    private void startTimerThread(Stage primaryStage) {
        long startTime = System.currentTimeMillis();
        Timer timer = new Timer(true);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                int requestsInLastMinute = FafApiCommunicationService.getRequestsInLastMinute();
                double requestsPerSecond = FafApiCommunicationService.getRequestsPerSecondRolling(3);
                long cooldownRemaining = FafApiCommunicationService.getCooldownRemainingMillis();

                Platform.runLater(() ->
                        updateWindowTitle(primaryStage, startTime, requestsInLastMinute, requestsPerSecond, cooldownRemaining));
            }
        };
        timer.scheduleAtFixedRate(task, 0, 1000);
    }

    public void updateWindowTitle(Stage primaryStage, long startTime, int requestsInLastMinute,
                                  double requestsPerSecond, long cooldownRemaining) {
        this.primaryStage = primaryStage;

        AtomicInteger activeRequests = moderationReportController.getActiveApiRequests();
        boolean hasActiveRequests = activeRequests.get() > 0;

        long now = System.currentTimeMillis();
        long elapsedTimeMillis = now - startTime;
        long elapsedSeconds = elapsedTimeMillis / 1000;
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        String elapsedTimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        String fetchingDurationStr = "";
        if (fetchingDurationMillis > 0) {
            long fetchingSeconds = fetchingDurationMillis / 1000;
            long minFetched = fetchingSeconds / 60;
            long secFetched = fetchingSeconds % 60;
            fetchingDurationStr = String.format(" (Took %02d:%02d)", minFetched, secFetched);
        }

        String lastUpdateStr = getLastUpdateTime();

        String cooldownText = cooldownRemaining > 0
                ? String.format(" | Cooldown: %ds", (int) (cooldownRemaining / 1000))
                : "";

        String title = ApplicationVersion.CURRENT_VERSION + " | MM Session: " + elapsedTimeStr +
                " | Reports Loaded: " + moderationReportController.getTotalReportsLoaded() +
                " | Last Refresh: " + lastUpdateStr +
                fetchingDurationStr +
                " | Requests in Last 60s: " + requestsInLastMinute +
                cooldownText;

        if (hasActiveRequests) {
            if (!isFetching) startFetchingPattern();
            title += " | Fetching reports...";
        } else {
            stopFetchingPattern();
        }

        primaryStage.setTitle(title);
    }

    private void startFetchingPattern() {
        if (timeline != null && timeline.getStatus() == Timeline.Status.RUNNING) {
            return;
        }

        final long[] counterSeconds = {0};
        lastRefreshedTime = System.currentTimeMillis();
        hasFetchedReports = true;

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            counterSeconds[0]++;
            updateWindowTitle(primaryStage,
                    System.currentTimeMillis() - counterSeconds[0] * 1000,
                    FafApiCommunicationService.getRequestsInLastMinute(),
                    FafApiCommunicationService.getRequestsPerSecondRolling(3),
                    FafApiCommunicationService.getCooldownRemainingMillis());
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void stopFetchingPattern() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        if (hasFetchedReports) {
            fetchingDurationMillis = System.currentTimeMillis() - lastRefreshedTime;
            hasFetchedReports = false;
        }
    }

    private String getLastUpdateTime() {
        if (lastRefreshedTime < 0) {
            return "Never";
        }
        long elapsedSeconds = (System.currentTimeMillis() - lastRefreshedTime) / 1000;
        if (elapsedSeconds < 60) return "a few seconds ago";
        if (elapsedSeconds < 3600)
            return (elapsedSeconds / 60) + (elapsedSeconds / 60 == 1 ? " minute ago" : " minutes ago");
        return (elapsedSeconds / 3600) + (elapsedSeconds / 3600 == 1 ? " hour ago" : " hours ago");
    }
}
