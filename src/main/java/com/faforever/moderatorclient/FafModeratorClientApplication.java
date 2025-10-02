package com.faforever.moderatorclient;

import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.config.ApplicationProperties;
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
import com.faforever.moderatorclient.config.PreferencesConfig;

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
    @Autowired
    public PreferencesConfig preferencesConfig;

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
        if (localPreferences.getUi().isDarkMode()) {
            stylesheet = "/style/main-dark.css";
        }

        scene.getStylesheets().add(getClass().getResource(stylesheet).toExternalForm());
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

                Platform.runLater(() -> updateWindowTitle(primaryStage, startTime, requestsInLastMinute, requestsPerSecond));
            }
        };
        timer.scheduleAtFixedRate(task, 0, 1000);
    }

    private Timeline timeline;
    private Stage primaryStage;
    private long startTime;
    private boolean isFetching = false;

    public void updateWindowTitle(Stage primaryStage, long startTime, int requestsInLastMinute, double requestsPerSecond) {
    this.primaryStage = primaryStage;
    this.startTime = startTime;

    AtomicInteger activeRequests = moderationReportController.getActiveApiRequests();
    boolean hasActiveRequests = activeRequests.get() > 0;

    if (hasActiveRequests) {
        if (!isFetching) {
            startFetchingPattern();
            isFetching = true;
        }
    } else {
        stopFetchingPattern();
        isFetching = false;

        if (hasFetchedReports) {
            lastRefreshedTime = System.currentTimeMillis();
        }

        long elapsedTimeMillis = System.currentTimeMillis() - startTime;
        long elapsedTimeSeconds = elapsedTimeMillis / 1000;
        long hours = elapsedTimeSeconds / 3600;
        long minutes = (elapsedTimeSeconds % 3600) / 60;
        long seconds = elapsedTimeSeconds % 60;
        String elapsedTimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds); // HH:mm:ss

        String fetchingDurationStr = "";
        if (fetchingDurationMillis > 0) {
            long fetchingDurationSeconds = fetchingDurationMillis / 1000;
            long minutesFetched = fetchingDurationSeconds / 60;
            long secondsFetched = fetchingDurationSeconds % 60;
            fetchingDurationStr = String.format(" (Took %02d:%02d)", minutesFetched, secondsFetched);
        }

        String lastUpdateStr = getLastUpdateTime();

        primaryStage.setTitle("MM - Session: " + elapsedTimeStr +
                " | Reports Loaded: " + moderationReportController.getTotalReportsLoaded() +
                " | Last Refresh was " + lastUpdateStr +
                fetchingDurationStr +
                " | Requests in Last 60s: " + requestsInLastMinute +
                String.format(" | RPS (3s): %.2f", requestsPerSecond));
    }
}

    private long fetchingDurationMillis = 0;
    private boolean hasFetchedReports = false;

    private void startFetchingPattern() {
        if (timeline != null && timeline.getStatus() == Timeline.Status.RUNNING) {
            return;
        }

        final long[] counterSecondsRequestReportsFromServer = {0};
        lastRefreshedTime = System.currentTimeMillis();
        hasFetchedReports = true;

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            long elapsedTimeMillis = System.currentTimeMillis() - startTime;
            long elapsedTimeSeconds = elapsedTimeMillis / 1000;
            long hours = elapsedTimeSeconds / 3600;
            long minutes = (elapsedTimeSeconds % 3600) / 60;
            long seconds = elapsedTimeSeconds % 60;
            String elapsedTimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds); // HH:mm:ss

            counterSecondsRequestReportsFromServer[0]++;

            int requestsInLastMinute = FafApiCommunicationService.getRequestsInLastMinute();
            double requestsPerSecond = FafApiCommunicationService.getRequestsPerSecondRolling(3);

            primaryStage.setTitle("MM: Session: " + elapsedTimeStr +
                    " | Latest " + preferencesConfig.getInitialPageSize() +
                    " Reports Fetched | Requesting now all reports from the server... (" +
                    counterSecondsRequestReportsFromServer[0] + " seconds ago)" +
                    " | Requests in Last 60s: " + requestsInLastMinute +
                    String.format(" | RPS (3s): %.2f", requestsPerSecond));
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
            long fetchingEndTime = System.currentTimeMillis();
            fetchingDurationMillis = fetchingEndTime - lastRefreshedTime;
            hasFetchedReports = false;
        }
    }

    private long lastRefreshedTime = -1;

    private String getLastUpdateTime() {
        if (lastRefreshedTime < 0) {
            return "Never";
        }
        long now = System.currentTimeMillis();
        long elapsedMillis = now - lastRefreshedTime;
        long elapsedSeconds = elapsedMillis / 1000;

        if (elapsedSeconds < 60) {
            return "a few seconds ago";
        } else if (elapsedSeconds < 3600) {
            long minutes = elapsedSeconds / 60;
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else {
            long hours = elapsedSeconds / 3600;
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
    }

}
