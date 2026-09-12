package com.mutrabot.adapter.out.persistence;

import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.GuildQueue;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public final class InMemoryQueueAdapter implements MusicQueueRepository {

    private final ConcurrentHashMap<GuildId, GuildQueue> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GuildId, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public GuildQueue getOrCreate(GuildId guild) {
        return queues.computeIfAbsent(guild, GuildQueue::new);
    }

    @Override
    public Optional<GuildQueue> find(GuildId guild) {
        return Optional.ofNullable(queues.get(guild));
    }

    @Override
    public void remove(GuildId guild) {
        queues.remove(guild);
        locks.remove(guild);
    }

    @Override
    public <T> T withLock(GuildId guild, Function<GuildQueue, T> action) {
        GuildQueue queue = getOrCreate(guild);
        ReentrantLock lock = locks.computeIfAbsent(guild, key -> new ReentrantLock());
        lock.lock();
        try {
            return action.apply(queue);
        } finally {
            lock.unlock();
        }
    }
}
