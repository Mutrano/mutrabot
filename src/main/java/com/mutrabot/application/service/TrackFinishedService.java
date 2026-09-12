package com.mutrabot.application.service;

import com.mutrabot.application.port.in.TrackFinishedUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.GuildAnnouncerPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.GuildQueue;

import java.time.Instant;
import java.util.Objects;

public final class TrackFinishedService implements TrackFinishedUseCase {

    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;
    private final GuildAnnouncerPort announcer;
    private final IdleDisconnectService idleDisconnect;

    public TrackFinishedService(
            MusicQueueRepository queues,
            AudioPlaybackPort playback,
            GuildAnnouncerPort announcer,
            IdleDisconnectService idleDisconnect) {
        this.queues = Objects.requireNonNull(queues, "queues");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
        this.idleDisconnect = Objects.requireNonNull(idleDisconnect, "idleDisconnect");
    }

    @Override
    public void onTrackFinished(GuildId guild) {
        GuildQueue.AdvanceResult result = queues.withLock(guild, queue -> queue.onTrackFinished(Instant.now()));
        switch (result) {
            case GuildQueue.AdvanceResult.Advanced advanced -> {
                playback.play(guild, advanced.next());
                idleDisconnect.cancel(guild);
                announcer.announce(guild, BotMessages.nowPlaying(advanced.next()));
            }
            case GuildQueue.AdvanceResult.QueueEnded ignored -> {
                idleDisconnect.scheduleDisconnect(guild);
                announcer.announce(guild, BotMessages.queueEndedAnnouncement());
            }
            case GuildQueue.AdvanceResult.NothingToAdvance ignored -> {
            }
        }
    }
}
