package com.faforever.moderatorclient.ui.data_cells;

import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.ui.caches.AvatarCache;
import com.faforever.moderatorclient.ui.domain.AvatarAssignmentFX;
import com.faforever.moderatorclient.ui.domain.AvatarFX;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class UrlImageViewTableCell<T> extends TableCell<T, String> {
    private final ImageView imageView = new ImageView();

    private static final int MAX_THREADS = 2;
    private static int threadCounter = 0;
    public static final ExecutorService imageLoadExecutor =
            Executors.newFixedThreadPool(MAX_THREADS, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("AvatarImageLoader-" + (++threadCounter));
                return t;
            });

    public static final Set<String> loadingUrls = ConcurrentHashMap.newKeySet();
    public static final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public static Label loadProgressLabel;
    public static int totalRequested = 0;
    public static int totalCompleted = 0;

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || getTableRow().getItem() == null) {
            setGraphic(null);
            return;
        }

        Object rowItem = getTableRow().getItem();
        AvatarFX avatar;

        if (rowItem instanceof AvatarAssignmentFX assignmentFX) {
            avatar = assignmentFX.getAvatar(); // <-- method to get AvatarFX from AvatarAssignmentFX
        } else if (rowItem instanceof AvatarFX avatarFX) {
            avatar = avatarFX;
        } else {
            avatar = null;
        }

        if (avatar == null) {
            setGraphic(null);
            return;
        }

        imageView.imageProperty().unbind();
        imageView.imageProperty().bind(avatar.imageProperty());
        setGraphic(imageView);

        if (avatar.getImage() != null || item == null || item.isEmpty()) {
            return;
        }

        String cacheKey = cacheKeyFrom(item, rowItem);

        if (loadingUrls.add(cacheKey)) {
            totalRequested++;
            updateProgressLabel();

            Future<?> previous = runningTasks.get(cacheKey);
            if (previous != null && !previous.isDone()) {
                previous.cancel(true);
            }

            Future<?> future = imageLoadExecutor.submit(() -> {
                try {
                    FafApiCommunicationService.checkRateLimit();
                    Image img = new Image(item, true);
                    AvatarCache.getInstance().put(cacheKey, img);

                    img.progressProperty().addListener((obs, oldProg, newProg) -> {
                        if (newProg.doubleValue() >= 1.0) {
                            Platform.runLater(() -> {
                                avatar.setImage(img);
                                totalCompleted++;
                                updateProgressLabel();
                            });
                        }
                    });

                } catch (Exception e) {
                    log.warn("[Executor] Failed loading image: {}", item, e);
                } finally {
                    loadingUrls.remove(cacheKey);
                    runningTasks.remove(cacheKey);
                }
            });

            runningTasks.put(cacheKey, future);
        }
    }

    public static void updateProgressLabel() {
        if (loadProgressLabel != null) {
            Platform.runLater(() ->
                    loadProgressLabel.setText("Previews requested: " + totalRequested + ", loaded: " + totalCompleted));
        }
    }

    private String cacheKeyFrom(String item, Object rawData) {
        OffsetDateTime updateTime = null;
        if (rawData instanceof AvatarFX avatarFX) {
            updateTime = avatarFX.getUpdateTime();
        } else if (rawData instanceof AvatarAssignmentFX assignmentFX) {
            updateTime = assignmentFX.getUpdateTime();
        }
        return updateTime != null ? item + updateTime : item;
    }
}
