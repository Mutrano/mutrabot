package com.mutrabot.domain.model;

import java.util.Objects;

public record GuildId(String value) {
    public GuildId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GuildId não pode ser vazio");
        }
    }
}
