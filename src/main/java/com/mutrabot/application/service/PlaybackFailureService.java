package com.mutrabot.application.service;

import com.mutrabot.application.port.in.TrackFailureUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.GuildAnnouncerPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackFailureKind;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PlaybackFailureService implements TrackFailureUseCase {

    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;
    private final GuildAnnouncerPort announcer;
    private final IdleDisconnectService idleDisconnect;

    public PlaybackFailureService(
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
    public void onTrackFailed(GuildId guild, TrackFailureKind kind) {
        FailedAdvance outcome = queues.withLock(guild, queue -> {
            Track failed = queue.current().orElse(null);
            GuildQueue.AdvanceResult advance = queue.skip(Instant.now());
            return new FailedAdvance(failed, advance);
        });
        Optional<String> failedTitle = Optional.ofNullable(outcome.failed()).map(Track::title);
        switch (outcome.advance()) {
            case GuildQueue.AdvanceResult.Advanced advanced -> {
                playback.play(guild, advanced.next());
                idleDisconnect.cancel(guild);
                announcer.announce(guild, BotMessages.trackFailed(
                        failedTitle.orElse(null), kind, Optional.of(advanced.next())));
            }
            case GuildQueue.AdvanceResult.QueueEnded ignored -> {
                idleDisconnect.scheduleDisconnect(guild);
                announcer.announce(guild, BotMessages.trackFailed(
                        failedTitle.orElse(null), kind, Optional.empty()));
            }
            case GuildQueue.AdvanceResult.NothingToAdvance ignored -> {
            }
        }
    }

    private record FailedAdvance(Track failed, GuildQueue.AdvanceResult advance) {
    }
}
