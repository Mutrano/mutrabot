package com.mutrabot.application;

import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.HelpCommandService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.support.FakeResponder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelpCommandServiceTest {

    private final FakeResponder responder = new FakeResponder();
    private final HelpCommandService service = new HelpCommandService();

    @Test
    void repliesEphemeralHelpWithAllCommands() {
        service.help(new BotCommand.HelpCmd(), responder);

        String message = responder.lastEphemeralReply();
        assertThat(message).isEqualTo(BotMessages.help());
        assertThat(message)
                .contains("/play")
                .contains("/stop")
                .contains("/resume")
                .contains("/skip")
                .contains("/queue")
                .contains("/ping")
                .contains("/help");
    }

    @Test
    void doesNotReplyPublicly() {
        service.help(new BotCommand.HelpCmd(), responder);

        assertThat(responder.replies).isEmpty();
        assertThat(responder.followUps).isEmpty();
    }
}
