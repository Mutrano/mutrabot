package com.mutrabot.domain.model;

import java.time.Instant;
import java.util.Objects;

public record VoiceSession(GuildId guild, VoiceChannelId channel, Instant connectedAt, Instant lastActivityAt) {
    public VoiceSession {
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(connectedAt, "connectedAt");
        lastActivityAt = lastActivityAt == null ? connectedAt : lastActivityAt;
    }
}
