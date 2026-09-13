package com.mutrabot.adapter.out.streaming;

import com.mutrabot.application.port.out.LivestreamControlPort;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.VoiceChannelId;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class HttpLivestreamSidecarAdapter implements LivestreamControlPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient http;
    private final URI baseUri;
    private final String secret;

    public HttpLivestreamSidecarAdapter(String baseUrl, String secret) {
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.baseUri = URI.create(trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl")));
        this.secret = Objects.requireNonNull(secret, "secret");
    }

    @Override
    public Result start(GuildId guild, VoiceChannelId channel, String window) {
        String body = "{\"guildId\":" + quote(guild.value())
                + ",\"channelId\":" + quote(channel.value())
                + ",\"window\":" + quote(window) + "}";
        return post("/start", body);
    }

    @Override
    public Result stop(GuildId guild) {
        return post("/stop", "{\"guildId\":" + quote(guild.value()) + "}");
    }

    private Result post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + secret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return interpret(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result.Unavailable("requisição interrompida");
        } catch (IOException e) {
            return new Result.Unavailable(e.getMessage() == null ? "sidecar inacessível" : e.getMessage());
        }
    }

    private static Result interpret(int status, String body) {
        if (status >= 200 && status < 300) {
            return new Result.Ok(booleanField(body, "wasActive"));
        }
        if (status == 400 || status == 409) {
            String reason = stringField(body, "reason");
            return new Result.Failed(kindOf(stringField(body, "code")),
                    reason == null ? "erro " + status : reason);
        }
        if (status == 401) {
            return new Result.Failed(FailureKind.UNKNOWN, "não autorizado pelo sidecar");
        }
        return new Result.Unavailable("status " + status);
    }

    private static FailureKind kindOf(String code) {
        if (code == null) {
            return FailureKind.UNKNOWN;
        }
        return switch (code) {
            case "WINDOW_NOT_FOUND" -> FailureKind.WINDOW_NOT_FOUND;
            case "TRANSMITTER_UNAVAILABLE" -> FailureKind.TRANSMITTER_UNAVAILABLE;
            case "CAPTURE_FAILED" -> FailureKind.CAPTURE_FAILED;
            default -> FailureKind.UNKNOWN;
        };
    }

    static String stringField(String json, String name) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + name + "\"";
        int key = json.indexOf(needle);
        if (key < 0) {
            return null;
        }
        int colon = json.indexOf(':', key + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char current = json.charAt(i);
            if (current == '\\' && i + 1 < json.length()) {
                char escaped = json.charAt(i + 1);
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> escaped;
                });
                i++;
            } else if (current == '"') {
                return value.toString();
            } else {
                value.append(current);
            }
        }
        return null;
    }

    static boolean booleanField(String json, String name) {
        if (json == null) {
            return false;
        }
        String needle = "\"" + name + "\"";
        int key = json.indexOf(needle);
        if (key < 0) {
            return false;
        }
        int colon = json.indexOf(':', key + needle.length());
        if (colon < 0) {
            return false;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        return json.startsWith("true", start);
    }

    static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(current);
            }
        }
        return escaped.append('"').toString();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
