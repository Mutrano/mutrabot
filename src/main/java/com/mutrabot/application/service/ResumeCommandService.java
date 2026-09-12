package com.mutrabot.application.service;

import com.mutrabot.application.port.in.ResumeUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.Track;

import java.util.Objects;
import java.util.Optional;

public final class ResumeCommandService implements ResumeUseCase {

    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;

    public ResumeCommandService(MusicQueueRepository queues, AudioPlaybackPort playback) {
        this.queues = Objects.requireNonNull(queues, "queues");
        this.playback = Objects.requireNonNull(playback, "playback");
    }

    @Override
    public void resume(BotCommand.ResumeCmd command, InteractionResponderPort responder) {
        Optional<Track> resumed = queues.withLock(command.guild(), GuildQueue::resume);
        if (resumed.isPresent()) {
            playback.resume(command.guild());
            responder.reply(BotMessages.resumed(resumed.get()));
        } else {
            responder.replyEphemeral(BotMessages.nothingPaused());
        }
    }
}
