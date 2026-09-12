package com.mutrabot.application;

import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.PingCommandService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.support.FakeResponder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingServiceTest {

    private final FakeResponder responder = new FakeResponder();

    @Test
    void repliesPongWithLatencies() {
        PingCommandService service = new PingCommandService(() -> 42L, () -> 17L);

        service.ping(new BotCommand.PingCmd(), responder);

        assertThat(responder.lastReply()).isEqualTo("🏓 pong (gateway: 42ms, rest: 17ms)");
    }

    @Test
    void repliesPongWithZeroLatencies() {
        PingCommandService service = new PingCommandService(() -> 0L, () -> 0L);

        service.ping(new BotCommand.PingCmd(), responder);

        assertThat(responder.lastReply()).isEqualTo("🏓 pong (gateway: 0ms, rest: 0ms)");
    }

    @Test
    void pingMessageFormatIsStable() {
        assertThat(BotMessages.pong(1, 2)).isEqualTo("🏓 pong (gateway: 1ms, rest: 2ms)");
    }
}
