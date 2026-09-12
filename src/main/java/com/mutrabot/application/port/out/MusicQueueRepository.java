package com.mutrabot.application.port.out;

import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.GuildQueue;

import java.util.Optional;
import java.util.function.Function;

public interface MusicQueueRepository {
    GuildQueue getOrCreate(GuildId guild);

    Optional<GuildQueue> find(GuildId guild);

    void remove(GuildId guild);

    <T> T withLock(GuildId guild, Function<GuildQueue, T> action);
}
