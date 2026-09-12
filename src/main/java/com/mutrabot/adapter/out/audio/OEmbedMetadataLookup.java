package com.mutrabot.adapter.out.audio;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OEmbedMetadataLookup implements MetadataLookup {

    private static final Pattern JSON_TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern OG_TITLE = Pattern.compile(
            "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TITLE = Pattern.compile(
            "<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);

    private final HttpClient http;
    private final Duration timeout;

    public OEmbedMetadataLookup() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Duration.ofSeconds(10));
    }

    OEmbedMetadataLookup(HttpClient http, Duration timeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public Optional<String> lookupTitle(String sourceUrl) {
        String lower = sourceUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("spotify.com")) {
            String endpoint = "https://open.spotify.com/oembed?url="
                    + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8);
            return get(endpoint).flatMap(body -> extractJsonString(body, "title"));
        }
        if (lower.contains("tidal.com")) {
            return get(sourceUrl).flatMap(OEmbedMetadataLookup::extractHtmlTitle);
        }
        return Optional.empty();
    }

    static Optional<String> extractJsonString(String json, String field) {
        Pattern pattern = switch (field) {
            case "title" -> JSON_TITLE;
            default -> Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        };
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescapeJson(matcher.group(1)));
    }

    static Optional<String> extractHtmlTitle(String html) {
        Matcher openGraph = OG_TITLE.matcher(html);
        if (openGraph.find()) {
            return Optional.of(openGraph.group(1).trim());
        }
        Matcher plain = HTML_TITLE.matcher(html);
        if (plain.find()) {
            return Optional.of(plain.group(1).trim());
        }
        return Optional.empty();
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private Optional<String> get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", "Mozilla/5.0 (compatible; mutrabot/0.1)")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
