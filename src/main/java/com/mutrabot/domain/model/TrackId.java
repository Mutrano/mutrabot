package com.mutrabot.domain.model;

import java.util.Objects;

public record TrackId(String value) {
    public TrackId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TrackId não pode ser vazio");
        }
    }
}
