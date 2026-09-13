package com.mutrabot.application.service;

import com.mutrabot.application.port.in.LeaveUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.command.BotCommand;

import java.util.Objects;

public final class LeaveCommandService implements LeaveUseCase {

    private final MusicQueueRepository queues;
    private final AudioPlaybackPort playback;
    private final IdleDisconnectService idleDisconnect;

    public LeaveCommandService(
            MusicQueueRepository queues, AudioPlaybackPort playback, IdleDisconnectService idleDisconnect) {
        this.queues = Objects.requireNonNull(queues, "queues");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.idleDisconnect = Objects.requireNonNull(idleDisconnect, "idleDisconnect");
    }

    @Override
    public void leave(BotCommand.LeaveCmd command, InteractionResponderPort responder) {
        if (playback.voiceSession(command.guild()).isEmpty()) {
            responder.replyEphemeral(BotMessages.notInVoice());
            return;
        }
        idleDisconnect.cancel(command.guild());
        playback.disconnect(command.guild());
        queues.remove(command.guild());
        responder.reply(BotMessages.left());
    }
}
