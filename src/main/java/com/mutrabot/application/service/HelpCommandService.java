package com.mutrabot.application.service;

import com.mutrabot.application.port.in.HelpUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.domain.command.BotCommand;

public final class HelpCommandService implements HelpUseCase {

    @Override
    public void help(BotCommand.HelpCmd command, InteractionResponderPort responder) {
        responder.replyEphemeral(BotMessages.help());
    }
}
