package com.mutrabot.domain.model;

import java.util.Objects;

public record VoiceChannelId(String value) {
    public VoiceChannelId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("VoiceChannelId não pode ser vazio");
        }
    }
}
