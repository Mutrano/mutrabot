package com.mutrabot.application.port.in;

import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.domain.command.BotCommand;

public interface ResumeUseCase {
    void resume(BotCommand.ResumeCmd command, InteractionResponderPort responder);
}
