package com.mutrabot.application.port.in;

import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.domain.command.BotCommand;

public interface StopUseCase {
    void stop(BotCommand.StopCmd command, InteractionResponderPort responder);
}
