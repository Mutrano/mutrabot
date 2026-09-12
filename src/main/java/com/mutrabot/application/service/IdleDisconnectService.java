package com.mutrabot.application.service;

import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.GuildAnnouncerPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.GuildQueue;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class IdleDisconnectService {

    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final ScheduledExecutorService scheduler;
    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;
    private final GuildAnnouncerPort announcer;
    private final Duration idleTimeout;
    private final ConcurrentHashMap<GuildId, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public IdleDisconnectService(
            ScheduledExecutorService scheduler,
            MusicQueueRepository queues,
            AudioPlaybackPort playback,
            GuildAnnouncerPort announcer) {
        this(scheduler, queues, playback, announcer, DEFAULT_IDLE_TIMEOUT);
    }

    public IdleDisconnectService(
            ScheduledExecutorService scheduler,
            MusicQueueRepository queues,
            AudioPlaybackPort playback,
            GuildAnnouncerPort announcer,
            Duration idleTimeout) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.queues = Objects.requireNonNull(queues, "queues");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
    }

    public void scheduleDisconnect(GuildId guild) {
        cancel(guild);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> disconnectIfIdle(guild), idleTimeout.toMillis(), TimeUnit.MILLISECONDS);
        timers.put(guild, future);
    }

    public void cancel(GuildId guild) {
        ScheduledFuture<?> future = timers.remove(guild);
        if (future != null) {
            future.cancel(false);
        }
    }

    public boolean isScheduled(GuildId guild) {
        return timers.containsKey(guild);
    }

    void disconnectIfIdle(GuildId guild) {
        timers.remove(guild);
        Optional<GuildQueue> found = queues.find(guild);
        if (found.isEmpty()) {
            return;
        }
        if (!queues.withLock(guild, GuildQueue::isEmpty)) {
            return;
        }
        playback.disconnect(guild);
        queues.remove(guild);
        announcer.announce(guild, BotMessages.idleDisconnected());
    }
}
