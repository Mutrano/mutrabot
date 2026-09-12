package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.SourceKind;

import java.util.Locale;

public final class SourceKindResolver {

    private SourceKindResolver() {
    }

    public static SourceKind detect(String uri) {
        if (uri == null || uri.isBlank()) {
            return SourceKind.HTTP;
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            return SourceKind.YOUTUBE;
        }
        if (lower.contains("soundcloud.com")) {
            return SourceKind.SOUNDCLOUD;
        }
        if (lower.contains("spotify.com")) {
            return SourceKind.SPOTIFY;
        }
        if (lower.contains("tidal.com")) {
            return SourceKind.TIDAL;
        }
        return SourceKind.HTTP;
    }
}
