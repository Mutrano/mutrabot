package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.SourceKind;

import java.util.Locale;
import java.util.Optional;

public final class SearchQuery {

    public static final String SEARCH_PREFIX = "ytsearch:";

    private SearchQuery() {
    }

    public static boolean isUrl(String query) {
        if (query == null) {
            return false;
        }
        String lower = query.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public static Optional<SourceKind> nonStreamableSource(String query) {
        if (!isUrl(query)) {
            return Optional.empty();
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("spotify.com")) {
            return Optional.of(SourceKind.SPOTIFY);
        }
        if (lower.contains("tidal.com")) {
            return Optional.of(SourceKind.TIDAL);
        }
        return Optional.empty();
    }

    public static String withSearchPrefix(String query) {
        return SEARCH_PREFIX + query;
    }
}
