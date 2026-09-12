package com.mutrabot.application.service;

import com.mutrabot.application.port.in.StopUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.Track;

import java.util.Objects;
import java.util.Optional;

public final class StopCommandService implements StopUseCase {

    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;

    public StopCommandService(MusicQueueRepository queues, AudioPlaybackPort playback) {
        this.queues = Objects.requireNonNull(queues, "queues");
        this.playback = Objects.requireNonNull(playback, "playback");
    }

    @Override
    public void stop(BotCommand.StopCmd command, InteractionResponderPort responder) {
        Optional<Track> paused = queues.withLock(command.guild(), GuildQueue::pause);
        if (paused.isPresent()) {
            playback.pause(command.guild());
            responder.reply(BotMessages.paused(paused.get()));
        } else {
            responder.replyEphemeral(BotMessages.nothingPlaying());
        }
    }
}
