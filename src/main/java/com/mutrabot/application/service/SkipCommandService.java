package com.mutrabot.application.service;

import com.mutrabot.application.port.in.SkipUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildQueue;

import java.time.Instant;
import java.util.Objects;

public final class SkipCommandService implements SkipUseCase {

    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;
    private final IdleDisconnectService idleDisconnect;

    public SkipCommandService(
            MusicQueueRepository queues, AudioPlaybackPort playback, IdleDisconnectService idleDisconnect) {
        this.queues = Objects.requireNonNull(queues, "queues");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.idleDisconnect = Objects.requireNonNull(idleDisconnect, "idleDisconnect");
    }

    @Override
    public void skip(BotCommand.SkipCmd command, InteractionResponderPort responder) {
        GuildQueue.AdvanceResult result = queues.withLock(command.guild(), queue -> queue.skip(Instant.now()));
        switch (result) {
            case GuildQueue.AdvanceResult.Advanced advanced -> {
                playback.play(command.guild(), advanced.next());
                idleDisconnect.cancel(command.guild());
                responder.reply(BotMessages.skippedTo(advanced.next()));
            }
            case GuildQueue.AdvanceResult.QueueEnded ignored -> {
                playback.stop(command.guild());
                idleDisconnect.scheduleDisconnect(command.guild());
                responder.reply(BotMessages.queueEndedAfterSkip());
            }
            case GuildQueue.AdvanceResult.NothingToAdvance ignored -> responder.replyEphemeral(
                    BotMessages.nothingToSkip());
        }
    }
}
