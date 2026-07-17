package com.faforever.moderatorclient.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class PlatformServiceImpl implements PlatformService {

    @Override
    public void showDocument(String url) {
        try {
            String osName = System.getProperty("os.name");
            if (osName.startsWith("Mac OS")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else if (osName.startsWith("Windows")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException e) {
            log.warn("Failed to open document: {}", url, e);
            throw new RuntimeException(e);
        }
    }
}
