package com.mutrabot.application.port.in;

import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.domain.command.BotCommand;

public interface PingUseCase {
    void ping(BotCommand.PingCmd command, InteractionResponderPort responder);
}
