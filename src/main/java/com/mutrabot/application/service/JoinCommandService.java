package com.mutrabot.application.service;

import com.mutrabot.application.port.in.JoinUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.VoiceSession;

import java.util.Objects;
import java.util.Optional;

public final class JoinCommandService implements JoinUseCase {

    private final AudioPlaybackPort playback;
    private final IdleDisconnectService idleDisconnect;

    public JoinCommandService(AudioPlaybackPort playback, IdleDisconnectService idleDisconnect) {
        this.playback = Objects.requireNonNull(playback, "playback");
        this.idleDisconnect = Objects.requireNonNull(idleDisconnect, "idleDisconnect");
    }

    @Override
    public void join(BotCommand.JoinCmd command, InteractionResponderPort responder) {
        if (command.voiceChannel() == null) {
            responder.replyEphemeral(BotMessages.voiceRequired());
            return;
        }
        Optional<VoiceSession> session = playback.voiceSession(command.guild());
        if (session.isPresent() && session.get().channel().equals(command.voiceChannel())) {
            responder.replyEphemeral(BotMessages.alreadyInVoice());
            return;
        }
        playback.ensureConnected(command.guild(), command.voiceChannel());
        idleDisconnect.cancel(command.guild());
        responder.reply(BotMessages.joined());
    }
}
