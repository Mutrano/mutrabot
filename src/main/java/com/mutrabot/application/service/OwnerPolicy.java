package com.mutrabot.application.service;

import com.mutrabot.domain.model.UserId;

import java.util.Objects;

public final class OwnerPolicy {

    private final UserId ownerId;

    private OwnerPolicy(UserId ownerId) {
        this.ownerId = ownerId;
    }

    public static OwnerPolicy from(String rawOwnerId) {
        if (rawOwnerId == null || rawOwnerId.isBlank()) {
            return new OwnerPolicy(null);
        }
        return new OwnerPolicy(new UserId(rawOwnerId.trim()));
    }

    public boolean isConfigured() {
        return ownerId != null;
    }

    public boolean isOwner(UserId user) {
        return ownerId != null && ownerId.equals(Objects.requireNonNull(user, "user"));
    }
}
