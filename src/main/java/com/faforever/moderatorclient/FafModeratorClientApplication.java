package com.faforever.moderatorclient;

import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.ui.MainController;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.PlatformServiceImpl;
import com.faforever.moderatorclient.ui.StageHolder;
import com.faforever.moderatorclient.ui.UiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@ComponentScan
@Slf4j

public class FafModeratorClientApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

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
        primaryStage.show();
        startTimerThread(primaryStage);
    }

    private void waitSecond() {
        try {
            // Sleep cycle has a slight delay by ~5ms for executing code. Not important to have it that accurate
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startTimerThread(Stage primaryStage) {
        long startTime = System.currentTimeMillis();
        Thread timerThread = new Thread(() -> {
            while (true) {
                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                String elapsedTimeStr = String.format("%02d:%02d:%02d", TimeUnit.SECONDS.toHours(elapsedTime),
                        TimeUnit.SECONDS.toMinutes(elapsedTime) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.SECONDS.toSeconds(elapsedTime) % TimeUnit.MINUTES.toSeconds(1));
                if (elapsedTime >= 414 && elapsedTime <=420) {
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " (o︵o )"));
                    waitSecond();
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " (o︵o)"));
                    waitSecond();
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " ( o︵o)"));
                    waitSecond();
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " ( o︵o)/"));
                    waitSecond();
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " ( o︵o)y─"));
                    waitSecond();
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " ( o︵o)y─🔥"));
                    waitSecond();
                    Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr + " ( *‿*)y─┛~"));
                    waitSecond();
                    }
                Platform.runLater(() -> primaryStage.setTitle("magge's modified Mordor - Running Time: " + elapsedTimeStr));
                waitSecond();
            }
        });
        timerThread.start();
    }

    @Bean
    public PlatformService platformService() {
        return new PlatformServiceImpl(getHostServices());
    }
}
