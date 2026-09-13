package com.mutrabot.application.service;

import com.mutrabot.application.port.in.LivestreamJoinUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.LivestreamControlPort;
import com.mutrabot.domain.command.BotCommand;

import java.util.Objects;

public final class LivestreamJoinService implements LivestreamJoinUseCase {

    private final OwnerPolicy ownerPolicy;
    private final LivestreamControlPort control;

    public LivestreamJoinService(OwnerPolicy ownerPolicy, LivestreamControlPort control) {
        this.ownerPolicy = Objects.requireNonNull(ownerPolicy, "ownerPolicy");
        this.control = Objects.requireNonNull(control, "control");
    }

    @Override
    public void join(BotCommand.LivestreamJoinCmd command, InteractionResponderPort responder) {
        if (!ownerPolicy.isConfigured()) {
            responder.followUpEphemeral(BotMessages.livestreamDisabled());
            return;
        }
        if (!ownerPolicy.isOwner(command.user())) {
            responder.followUpEphemeral(BotMessages.ownerOnly());
            return;
        }
        if (command.voiceChannel() == null) {
            responder.followUpEphemeral(BotMessages.voiceRequired());
            return;
        }
        String window = command.window().trim();
        if (window.isEmpty()) {
            responder.followUpEphemeral(BotMessages.livestreamWindowRequired());
            return;
        }
        switch (control.start(command.guild(), command.voiceChannel(), window)) {
            case LivestreamControlPort.Result.Ok ignored ->
                    responder.followUpEphemeral(BotMessages.livestreamStarted(window));
            case LivestreamControlPort.Result.Unavailable unavailable ->
                    responder.followUpEphemeral(BotMessages.livestreamServiceDown());
            case LivestreamControlPort.Result.Failed failed -> responder.followUpEphemeral(
                    messageFor(failed, window));
        }
    }

    private String messageFor(LivestreamControlPort.Result.Failed failed, String window) {
        return switch (failed.kind()) {
            case WINDOW_NOT_FOUND -> BotMessages.livestreamWindowNotFound(window);
            case TRANSMITTER_UNAVAILABLE -> BotMessages.livestreamTransmitterUnavailable();
            case CAPTURE_FAILED -> BotMessages.livestreamCaptureFailed(failed.detail());
            case UNKNOWN -> BotMessages.livestreamFailed(failed.detail());
        };
    }
}
