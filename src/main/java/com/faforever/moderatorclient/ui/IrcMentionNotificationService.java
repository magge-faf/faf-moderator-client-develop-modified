package com.faforever.moderatorclient.ui;

import jakarta.annotation.PreDestroy;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

@Service
@Slf4j
public class IrcMentionNotificationService {
    private static final String MENTION_SOUND = "/media/userMentionSound.mp3";
    private static final String TRAY_ICON = "/media/appicon.png";
    private static final double TOAST_WIDTH = 320;
    private static final double TOAST_MARGIN = 18;

    private AudioClip mentionSound;
    private Popup activePopup;
    private SequentialTransition activeToastAnimation;
    private TrayIcon trayIcon;
    private boolean trayUnsupported;

    public void playMentionSound() {
        playBundledMentionSound();
    }

    public void showMentionToast(String sender, String channel, String message) {
        Platform.runLater(() -> {
            boolean desktopNotificationPreferred = shouldPreferDesktopNotification();
            if (desktopNotificationPreferred && showSystemNotification(sender, channel, message)) {
                return;
            }

            if (canShowInAppToast()) {
                showToast(sender, channel, message);
                return;
            }

            if (!showSystemNotification(sender, channel, message)) {
                log.debug("Unable to show a mention notification: no system tray available and no visible window.");
            }
        });
    }

    private AudioClip getMentionSound() {
        if (mentionSound != null) {
            return mentionSound;
        }

        URL resource = getClass().getResource(MENTION_SOUND);
        if (resource == null) {
            log.warn("IRC mention sound not found at {}", MENTION_SOUND);
            return null;
        }

        mentionSound = new AudioClip(resource.toExternalForm());
        return mentionSound;
    }

    private boolean showSystemNotification(String sender, String channel, String message) {
        TrayIcon systemTrayIcon = getOrCreateTrayIcon();
        if (systemTrayIcon == null) {
            return false;
        }

        String title = "IRC mention";
        String body = abbreviate(sender + " in " + channel + System.lineSeparator() + message, 220);
        systemTrayIcon.displayMessage(title, body, MessageType.INFO);
        return true;
    }

    private synchronized TrayIcon getOrCreateTrayIcon() {
        if (trayUnsupported) {
            return null;
        }
        if (trayIcon != null) {
            return trayIcon;
        }
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            trayUnsupported = true;
            return null;
        }

        Image image = loadTrayImage();
        if (image == null) {
            trayUnsupported = true;
            return null;
        }

        try {
            TrayIcon createdTrayIcon = new TrayIcon(image, "FAF Moderator Client");
            createdTrayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(createdTrayIcon);
            trayIcon = createdTrayIcon;
            return trayIcon;
        } catch (AWTException ex) {
            log.warn("Failed to initialize system tray notifications", ex);
            trayUnsupported = true;
            return null;
        }
    }

    private Image loadTrayImage() {
        URL resource = getClass().getResource(TRAY_ICON);
        if (resource == null) {
            log.warn("IRC tray icon not found at {}", TRAY_ICON);
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(resource);
            if (image == null) {
                log.warn("IRC tray icon could not be decoded from {}", TRAY_ICON);
            }
            return image;
        } catch (IOException ex) {
            log.warn("Failed to read IRC tray icon from {}", TRAY_ICON, ex);
            return null;
        }
    }

    private void showToast(String sender, String channel, String message) {
        Stage owner = getPrimaryStage();
        if (owner == null) {
            return;
        }

        dismissActiveToast();

        Label titleLabel = new Label("IRC mention");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label metaLabel = new Label(sender + " in " + channel);
        metaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #c7d2fe;");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e5e7eb;");

        VBox content = new VBox(6, titleLabel, metaLabel, messageLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(12));
        content.setPrefWidth(TOAST_WIDTH);
        content.setMaxWidth(TOAST_WIDTH);
        content.setStyle("-fx-background-color: rgba(17,24,39,0.96);"
                + "-fx-background-radius: 10;"
                + "-fx-border-color: rgba(96,165,250,0.7);"
                + "-fx-border-radius: 10;");
        content.setOpacity(0);

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.getContent().add(content);
        popup.show(owner, owner.getX(), owner.getY());

        content.applyCss();
        content.layout();

        double width = content.prefWidth(-1);
        double height = content.prefHeight(width);
        popup.setX(owner.getX() + owner.getWidth() - width - TOAST_MARGIN);
        popup.setY(owner.getY() + owner.getHeight() - height - TOAST_MARGIN);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(140), content);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition hold = new PauseTransition(Duration.seconds(4.5));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), content);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            popup.hide();
            if (activePopup == popup) {
                activePopup = null;
                activeToastAnimation = null;
            }
        });

        activePopup = popup;
        activeToastAnimation = new SequentialTransition(fadeIn, hold, fadeOut);
        activeToastAnimation.play();
    }

    private boolean shouldPreferDesktopNotification() {
        Stage owner = getPrimaryStage();
        if (owner == null) {
            return true;
        }
        return owner.isIconified() || !owner.isFocused() || !owner.isShowing();
    }

    private boolean canShowInAppToast() {
        Stage owner = getPrimaryStage();
        return owner != null && owner.isShowing() && !owner.isIconified();
    }

    private Stage getPrimaryStage() {
        try {
            return StageHolder.getStage();
        } catch (IllegalStateException ex) {
            log.debug("Primary stage not ready for IRC notifications", ex);
            return null;
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void playBundledMentionSound() {
        Platform.runLater(() -> {
            AudioClip clip = getMentionSound();
            if (clip != null) {
                clip.play();
            }
        });
    }

    private void dismissActiveToast() {
        if (activeToastAnimation != null) {
            activeToastAnimation.stop();
            activeToastAnimation = null;
        }
        if (activePopup != null) {
            activePopup.hide();
            activePopup = null;
        }
    }

    @PreDestroy
    public synchronized void destroy() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }
}
