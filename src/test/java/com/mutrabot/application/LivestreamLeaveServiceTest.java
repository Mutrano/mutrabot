package com.mutrabot.application;

import com.mutrabot.application.port.out.LivestreamControlPort;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.LivestreamLeaveService;
import com.mutrabot.application.service.OwnerPolicy;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.UserId;
import com.mutrabot.support.FakeLivestreamControl;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LivestreamLeaveServiceTest {

    private final FakeLivestreamControl control = new FakeLivestreamControl();
    private final FakeResponder responder = new FakeResponder();
    private final LivestreamLeaveService service =
            new LivestreamLeaveService(OwnerPolicy.from("42"), control);

    private BotCommand.LivestreamLeaveCmd command(UserId user) {
        return new BotCommand.LivestreamLeaveCmd(TestData.GUILD, user);
    }

    @Test
    void ownerStopsActiveStream() {
        control.stopResult = new LivestreamControlPort.Result.Ok(true);

        service.leave(command(TestData.USER), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamStopped());
        assertThat(control.stoppedGuilds).containsExactly(TestData.GUILD);
    }

    @Test
    void ownerWithNothingActiveIsInformed() {
        control.stopResult = new LivestreamControlPort.Result.Ok(false);

        service.leave(command(TestData.USER), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamNoneActive());
    }

    @Test
    void nonOwnerIsRejectedWithoutTouchingSidecar() {
        service.leave(command(TestData.OTHER_USER), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.ownerOnly());
        assertThat(control.stoppedGuilds).isEmpty();
    }

    @Test
    void disabledPolicyRejects() {
        LivestreamLeaveService disabled = new LivestreamLeaveService(OwnerPolicy.from(" "), control);

        disabled.leave(command(TestData.USER), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamDisabled());
        assertThat(control.stoppedGuilds).isEmpty();
    }

    @Test
    void mapsUnavailableTransport() {
        control.stopResult = new LivestreamControlPort.Result.Unavailable("timeout");

        service.leave(command(TestData.USER), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamServiceDown());
    }

    @Test
    void mapsFailure() {
        control.stopResult = new LivestreamControlPort.Result.Failed(
                LivestreamControlPort.FailureKind.UNKNOWN, "erro");

        service.leave(command(TestData.USER), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamFailed("erro"));
    }
}
