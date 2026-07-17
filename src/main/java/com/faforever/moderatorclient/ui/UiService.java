package com.faforever.moderatorclient.ui;

import javafx.fxml.FXMLLoader;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.net.URL;

@Service
@Slf4j
@RequiredArgsConstructor
public class UiService {
    private final ApplicationContext applicationContext;

    @SneakyThrows
    public <T extends Controller<?>> T loadFxml(String relativePath) {
        log.trace("Loading fxml from relative path: {}", relativePath);
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(applicationContext::getBean);
        URL location = getClass().getResource("/" + relativePath);
        if (location == null) {
            throw new IllegalStateException("FXML resource not found on classpath: /" + relativePath);
        }
        loader.setLocation(location);
        loader.load();
        return loader.getController();
    }
}
