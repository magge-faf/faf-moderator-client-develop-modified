package com.faforever.moderatorclient;

import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.ui.MainController;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.PlatformServiceImpl;
import com.faforever.moderatorclient.ui.StageHolder;
import com.faforever.moderatorclient.ui.UiService;
import com.faforever.moderatorclient.ui.main_window.SettingsController;
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
import java.io.IOException;
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
        log.debug("Initializing CreateTableColumnsWidthSettingsJSON");
        SettingsController.CreateTableColumnsWidthSettingsJSON();

        Font.loadFont(getClass().getResource("/style/NotoEmoji-Regular.ttf").toExternalForm(), 12);
        StageHolder.setStage(primaryStage);
        primaryStage.setTitle("magge's modified Mordor");
        UiService uiService = applicationContext.getBean(UiService.class);
        MainController mainController = uiService.loadFxml("ui/mainWindow.fxml");
        mainController.display();
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("/media/favicon.png"))));
        Scene scene = new Scene(mainController.getRoot());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/main.css")).toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            log.info("Close request received, exiting");
            log.info("Saving Configuration.");
            try {
                userManagementController.savePropertiesAmountToCheckRecentAccounts();
                userManagementController.saveContent();
            } catch (IOException ex) {
                log.error("Error saving:", ex);
            }
            Platform.exit();
            System.exit(0);
        });
        startTimerThread(primaryStage);
    }

    private void startTimerThread(Stage primaryStage) {
        long startTime = System.currentTimeMillis();
        Timer timer = new Timer(true);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> updateWindowTitle(primaryStage, startTime));
            }
        };
        timer.scheduleAtFixedRate(task, 0, 1000);
    }

    private Timeline timeline;
    private Stage primaryStage;
    private long startTime;

    public void updateWindowTitle(Stage primaryStage, long startTime) {
        this.primaryStage = primaryStage;
        this.startTime = startTime;

        AtomicInteger activeRequests = moderationReportController.getActiveApiRequests();
        boolean hasActiveRequests = activeRequests.get() > 0;

        if (hasActiveRequests) {
            startFetchingPattern();
        } else {
            stopFetchingPattern();
            updateTitle();
        }
    }

    private void startFetchingPattern() {
        if (timeline != null && timeline.getStatus() == Timeline.Status.RUNNING) {
            return;
        }

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            long elapsedTimeMillis = System.currentTimeMillis() - startTime;
            long elapsedTimeSeconds = elapsedTimeMillis / 1000;
            long minutes = elapsedTimeSeconds / 60;
            long seconds = elapsedTimeSeconds % 60;
            String elapsedTimeStr = String.format("%02d:%02d", minutes, seconds);

            primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " - Fetching Reports (" + moderationReportController.getTotalReportsLoaded() + ")");
        }));

        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void stopFetchingPattern() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
            moderationReportController.setTotalReportsLoaded(new AtomicInteger(0));
        }
    }

    private void updateTitle() {
        long elapsedTimeMillis = System.currentTimeMillis() - startTime;
        long elapsedTimeSeconds = elapsedTimeMillis / 1000;
        long minutes = elapsedTimeSeconds / 60;
        long seconds = elapsedTimeSeconds % 60;
        String elapsedTimeStr = String.format("%02d:%02d", minutes, seconds);

        primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr);
    }
}
