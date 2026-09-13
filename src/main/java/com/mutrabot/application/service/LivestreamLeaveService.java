package com.mutrabot.application.service;

import com.mutrabot.application.port.in.LivestreamLeaveUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.LivestreamControlPort;
import com.mutrabot.domain.command.BotCommand;

import java.util.Objects;

public final class LivestreamLeaveService implements LivestreamLeaveUseCase {

    private final OwnerPolicy ownerPolicy;
    private final LivestreamControlPort control;

    public LivestreamLeaveService(OwnerPolicy ownerPolicy, LivestreamControlPort control) {
        this.ownerPolicy = Objects.requireNonNull(ownerPolicy, "ownerPolicy");
        this.control = Objects.requireNonNull(control, "control");
    }

    @Override
    public void leave(BotCommand.LivestreamLeaveCmd command, InteractionResponderPort responder) {
        if (!ownerPolicy.isConfigured()) {
            responder.followUpEphemeral(BotMessages.livestreamDisabled());
            return;
        }
        if (!ownerPolicy.isOwner(command.user())) {
            responder.followUpEphemeral(BotMessages.ownerOnly());
            return;
        }
        switch (control.stop(command.guild())) {
            case LivestreamControlPort.Result.Ok ok -> responder.followUpEphemeral(
                    ok.changed() ? BotMessages.livestreamStopped() : BotMessages.livestreamNoneActive());
            case LivestreamControlPort.Result.Unavailable unavailable ->
                    responder.followUpEphemeral(BotMessages.livestreamServiceDown());
            case LivestreamControlPort.Result.Failed failed ->
                    responder.followUpEphemeral(BotMessages.livestreamFailed(failed.detail()));
        }
    }
}
