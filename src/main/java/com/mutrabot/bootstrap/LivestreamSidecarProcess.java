package com.mutrabot.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class LivestreamSidecarProcess implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LivestreamSidecarProcess.class);
    private static final Path SCRIPT = Path.of("streamer", "index.mjs");
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(15);

    private final Process process;
    private final int port;

    private LivestreamSidecarProcess(Process process, int port) {
        this.process = process;
        this.port = port;
    }

    public static Optional<LivestreamSidecarProcess> start(int port, String secret, String token, String ffmpegPath) {
        if (token == null || token.isBlank() || secret == null || secret.isBlank()) {
            log.warn("Livestream desabilitada: configure LIVESTREAM_USER_TOKEN e LIVESTREAM_SIDECAR_SECRET.");
            return Optional.empty();
        }
        if (!Files.isRegularFile(SCRIPT)) {
            log.warn("Livestream desabilitada: {} não encontrado.", SCRIPT.toAbsolutePath());
            return Optional.empty();
        }
        ProcessBuilder builder = new ProcessBuilder(nodeCommand(), SCRIPT.toString());
        builder.directory(Path.of("").toAbsolutePath().toFile());
        Map<String, String> environment = builder.environment();
        environment.put("LIVESTREAM_SIDECAR_PORT", Integer.toString(port));
        environment.put("LIVESTREAM_SIDECAR_SECRET", secret);
        environment.put("LIVESTREAM_USER_TOKEN", token);
        if (ffmpegPath != null && !ffmpegPath.isBlank()) {
            environment.put("LIVESTREAM_FFMPEG_PATH", ffmpegPath);
        }
        builder.redirectErrorStream(true);
        builder.inheritIO();
        try {
            Process process = builder.start();
            LivestreamSidecarProcess sidecar = new LivestreamSidecarProcess(process, port);
            sidecar.awaitReady(secret);
            return Optional.of(sidecar);
        } catch (IOException e) {
            log.warn("Não consegui iniciar o sidecar de livestream: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void awaitReady(String secret) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(2))
                .header("Authorization", "Bearer " + secret)
                .GET()
                .build();
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                log.warn("Sidecar de livestream encerrou durante a inicialização.");
                return;
            }
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    log.info("Sidecar de livestream pronto na porta {}.", port);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException stillStarting) {
                // sidecar ainda subindo; tenta de novo
            }
            sleep();
        }
        log.warn("Sidecar de livestream não respondeu em {}s; os comandos responderão indisponível.",
                STARTUP_TIMEOUT.toSeconds());
    }

    @Override
    public void close() {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String nodeCommand() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "node.exe" : "node";
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
