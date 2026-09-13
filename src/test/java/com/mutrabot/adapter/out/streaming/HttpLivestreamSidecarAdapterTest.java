package com.mutrabot.adapter.out.streaming;

import com.mutrabot.application.port.out.LivestreamControlPort.FailureKind;
import com.mutrabot.application.port.out.LivestreamControlPort.Result;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.VoiceChannelId;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLivestreamSidecarAdapterTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastAuth;
    private AtomicReference<String> lastBody;
    private volatile int status = 200;
    private volatile String response = "{}";

    @BeforeEach
    void setUp() throws IOException {
        lastAuth = new AtomicReference<>();
        lastBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, status, response);
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void startSendsAuthAndEscapedBodyAndReturnsOk() {
        response = "{\"status\":\"streaming\"}";

        Result result = adapter().start(new GuildId("100"), new VoiceChannelId("777"), "Notepad \"x\"");

        assertThat(result).isInstanceOf(Result.Ok.class);
        assertThat(lastAuth.get()).isEqualTo("Bearer segredo");
        assertThat(lastBody.get())
                .contains("\"guildId\":\"100\"")
                .contains("\"channelId\":\"777\"")
                .contains("\"window\":\"Notepad \\\"x\\\"\"");
    }

    @Test
    void stopParsesWasActiveTrue() {
        response = "{\"status\":\"idle\",\"wasActive\":true}";

        assertThat(adapter().stop(new GuildId("100"))).isEqualTo(new Result.Ok(true));
    }

    @Test
    void stopParsesWasActiveFalse() {
        response = "{\"status\":\"idle\",\"wasActive\":false}";

        assertThat(adapter().stop(new GuildId("100"))).isEqualTo(new Result.Ok(false));
    }

    @Test
    void mapsWindowNotFound() {
        status = 400;
        response = "{\"code\":\"WINDOW_NOT_FOUND\",\"reason\":\"não achei\"}";

        Result result = adapter().start(new GuildId("100"), new VoiceChannelId("777"), "X");

        assertThat(result).isEqualTo(new Result.Failed(FailureKind.WINDOW_NOT_FOUND, "não achei"));
    }

    @Test
    void mapsTransmitterUnavailable() {
        status = 400;
        response = "{\"code\":\"TRANSMITTER_UNAVAILABLE\",\"reason\":\"login\"}";

        assertThat(adapter().stop(new GuildId("100")))
                .isEqualTo(new Result.Failed(FailureKind.TRANSMITTER_UNAVAILABLE, "login"));
    }

    @Test
    void mapsCaptureFailed() {
        status = 409;
        response = "{\"code\":\"CAPTURE_FAILED\",\"reason\":\"ffmpeg\"}";

        assertThat(adapter().stop(new GuildId("100")))
                .isEqualTo(new Result.Failed(FailureKind.CAPTURE_FAILED, "ffmpeg"));
    }

    @Test
    void unknownCodeFallsBackToUnknownKind() {
        status = 400;
        response = "{\"code\":\"ALGO\",\"reason\":\"conflito\"}";

        assertThat(adapter().stop(new GuildId("100")))
                .isEqualTo(new Result.Failed(FailureKind.UNKNOWN, "conflito"));
    }

    @Test
    void missingReasonUsesStatusText() {
        status = 400;
        response = "{}";

        assertThat(adapter().stop(new GuildId("100")))
                .isEqualTo(new Result.Failed(FailureKind.UNKNOWN, "erro 400"));
    }

    @Test
    void unauthorizedIsFailed() {
        status = 401;
        response = "{}";

        assertThat(adapter().stop(new GuildId("100")))
                .isEqualTo(new Result.Failed(FailureKind.UNKNOWN, "não autorizado pelo sidecar"));
    }

    @Test
    void serverErrorIsUnavailable() {
        status = 503;
        response = "{}";

        assertThat(adapter().stop(new GuildId("100"))).isInstanceOf(Result.Unavailable.class);
    }

    @Test
    void connectionFailureIsUnavailable() {
        HttpLivestreamSidecarAdapter dead =
                new HttpLivestreamSidecarAdapter("http://127.0.0.1:1", "s");

        assertThat(dead.start(new GuildId("100"), new VoiceChannelId("777"), "X"))
                .isInstanceOf(Result.Unavailable.class);
    }

    private HttpLivestreamSidecarAdapter adapter() {
        return new HttpLivestreamSidecarAdapter("http://127.0.0.1:" + port, "segredo");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
