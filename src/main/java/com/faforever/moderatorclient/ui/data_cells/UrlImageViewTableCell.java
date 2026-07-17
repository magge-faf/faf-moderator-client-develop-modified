package com.faforever.moderatorclient.ui.data_cells;

import com.faforever.moderatorclient.ui.caches.AvatarCache;
import com.faforever.moderatorclient.ui.domain.AvatarAssignmentFX;
import com.faforever.moderatorclient.ui.domain.AvatarFX;
import javafx.application.Platform;
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
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class UrlImageViewTableCell<T> extends TableCell<T, String> {
    private final ImageView imageView = new ImageView();

    private static final int MAX_THREADS = 2;
    private static final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final AtomicInteger totalRequested = new AtomicInteger(0);
    private static final AtomicInteger totalCompleted = new AtomicInteger(0);

    private static final ExecutorService imageLoadExecutor =
            Executors.newFixedThreadPool(MAX_THREADS, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("AvatarImageLoader-" + threadCounter.incrementAndGet());
                return t;
            });

    private static final Set<String> loadingUrls = ConcurrentHashMap.newKeySet();
    private static final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public static void resetCounters() {
        totalRequested.set(0);
        totalCompleted.set(0);
    }

    public static boolean tryAddLoadingUrl(String cacheKey) {
        return loadingUrls.add(cacheKey);
    }

    public static void removeLoadingUrl(String cacheKey) {
        loadingUrls.remove(cacheKey);
    }

    public static Future<?> submitImageLoad(Runnable task) {
        return imageLoadExecutor.submit(task);
    }

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
            avatar = assignmentFX.getAvatar();
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
            totalRequested.incrementAndGet();
            updateProgressLabel();

            Future<?> previous = runningTasks.get(cacheKey);
            if (previous != null && !previous.isDone()) {
                previous.cancel(true);
            }

            final AvatarFX capturedAvatar = avatar;
            Future<?> future = imageLoadExecutor.submit(() -> {
                try {
                    Image img = new Image(item, true);

                    // Set up the listener on the FX thread to avoid a race where the image
                    // finishes loading before the listener is attached.
                    Platform.runLater(() -> {
                        img.progressProperty().addListener((obs, oldProg, newProg) -> {
                            if (newProg.doubleValue() >= 1.0 && !img.isError()) {
                                capturedAvatar.setImage(img);
                                totalCompleted.incrementAndGet();
                                updateProgressLabel();
                            }
                        });
                        // Handle the case where the image was already fully loaded by the time
                        // the listener was added.
                        if (img.getProgress() >= 1.0 && !img.isError()) {
                            capturedAvatar.setImage(img);
                            totalCompleted.incrementAndGet();
                            updateProgressLabel();
                        }
                        AvatarCache.getInstance().put(cacheKey, img);
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
