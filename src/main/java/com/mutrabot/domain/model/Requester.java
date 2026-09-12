package com.mutrabot.domain.model;

import java.util.Objects;

public record Requester(UserId userId, String displayName) {
    public static final String UNKNOWN_DISPLAY_NAME = "Membro";

    public Requester {
        Objects.requireNonNull(userId, "userId");
        displayName = (displayName == null || displayName.isBlank()) ? UNKNOWN_DISPLAY_NAME : displayName;
    }
}
