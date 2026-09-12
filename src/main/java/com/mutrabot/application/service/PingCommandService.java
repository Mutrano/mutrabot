package com.mutrabot.application.service;

import com.mutrabot.application.port.in.PingUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.domain.command.BotCommand;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class PingCommandService implements PingUseCase {

    private final LongSupplier gatewayPing;
    private final LongSupplier restPing;

    public PingCommandService(LongSupplier gatewayPing, LongSupplier restPing) {
        this.gatewayPing = Objects.requireNonNull(gatewayPing, "gatewayPing");
        this.restPing = Objects.requireNonNull(restPing, "restPing");
    }

    @Override
    public void ping(BotCommand.PingCmd command, InteractionResponderPort responder) {
        responder.reply(BotMessages.pong(gatewayPing.getAsLong(), restPing.getAsLong()));
    }
}
