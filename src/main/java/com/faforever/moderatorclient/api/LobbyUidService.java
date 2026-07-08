package com.faforever.moderatorclient.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class LobbyUidService {
    private static final String UID_VERSION = "v4.0.7";
    private static final String WINDOWS_DOWNLOAD_URL = "https://github.com/FAForever/uid/releases/download/" + UID_VERSION + "/faf-uid.exe";
    private static final String LINUX_DOWNLOAD_URL = "https://github.com/FAForever/uid/releases/download/" + UID_VERSION + "/faf-uid";
    private static final String MAC_DOWNLOAD_URL = "https://github.com/FAForever/uid/releases/download/" + UID_VERSION + "/faf-uid-macos";

    public String generateUid(long sessionId) {
        Path executable = ensureExecutablePresent();

        Process process = startProcess(executable, String.valueOf(sessionId));
        String stdout;
        String stderr;
        try {
            stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read faf-uid output", e);
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("faf-uid exited with code " + exitCode + (stderr.isBlank() ? "" : ": " + stderr));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for faf-uid", e);
        }

        if (stdout.isBlank()) {
            throw new IllegalStateException("faf-uid returned an empty UID");
        }
        return stdout;
    }

    private Process startProcess(Path executable, String sessionId) {
        try {
            return new ProcessBuilder(executable.toAbsolutePath().toString(), sessionId)
                    .redirectErrorStream(false)
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start faf-uid at " + executable, e);
        }
    }

    private Path ensureExecutablePresent() {
        Path executable = resolveExecutablePath();
        if (Files.exists(executable)) {
            return executable;
        }

        try {
            Files.createDirectories(executable.getParent());
            downloadExecutable(executable);
            executable.toFile().setExecutable(true, false);
            return executable;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to provision faf-uid executable", e);
        }
    }

    private void downloadExecutable(Path target) throws IOException {
        String url = switch (osFamily()) {
            case WINDOWS -> WINDOWS_DOWNLOAD_URL;
            case MAC -> MAC_DOWNLOAD_URL;
            case LINUX -> LINUX_DOWNLOAD_URL;
        };

        log.info("Downloading faf-uid from {}", url);
        try (InputStream inputStream = java.net.URI.create(url).toURL().openStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveExecutablePath() {
        String fileName = switch (osFamily()) {
            case WINDOWS -> "faf-uid.exe";
            case MAC -> "faf-uid-macos";
            case LINUX -> "faf-uid";
        };

        Path baseDir;
        if (osFamily() == OsFamily.WINDOWS) {
            String localAppData = System.getenv("LOCALAPPDATA");
            baseDir = Path.of(localAppData == null || localAppData.isBlank() ? System.getProperty("java.io.tmpdir") : localAppData)
                    .resolve("faf-moderator-client")
                    .resolve("native");
        } else {
            baseDir = Path.of(System.getProperty("user.home"), ".faf-moderator-client", "native");
        }

        return baseDir.resolve(fileName);
    }

    private OsFamily osFamily() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return OsFamily.WINDOWS;
        }
        if (osName.contains("mac")) {
            return OsFamily.MAC;
        }
        return OsFamily.LINUX;
    }

    private enum OsFamily {
        WINDOWS,
        MAC,
        LINUX
    }
}
