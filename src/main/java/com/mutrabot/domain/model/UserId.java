package com.mutrabot.domain.model;

import java.util.Objects;

public record UserId(String value) {
    public UserId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserId não pode ser vazio");
        }
    }
}
