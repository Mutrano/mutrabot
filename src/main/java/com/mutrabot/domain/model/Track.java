package com.mutrabot.domain.model;

import java.time.Duration;
import java.util.Objects;

public record Track(
        TrackId id,
        String title,
        String author,
        String sourceUrl,
        Duration duration,
        SourceKind source,
        Requester requestedBy) {

    public static final String UNKNOWN_TITLE = "Faixa desconhecida";

    public Track {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestedBy, "requestedBy");
        title = (title == null || title.isBlank()) ? UNKNOWN_TITLE : title;
        author = author == null ? "" : author;
        sourceUrl = sourceUrl == null ? "" : sourceUrl;
        source = source == null ? SourceKind.SEARCH_RESULT : source;
        duration = (duration == null || duration.isNegative()) ? Duration.ZERO : duration;
    }

    public Track requestedBy(Requester requester) {
        return new Track(id, title, author, sourceUrl, duration, source, requester);
    }

    public boolean isLive() {
        return duration.isZero();
    }
}
