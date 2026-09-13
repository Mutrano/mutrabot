package com.mutrabot.application;

import com.mutrabot.application.port.out.LivestreamControlPort;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.LivestreamJoinService;
import com.mutrabot.application.service.OwnerPolicy;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.UserId;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.support.FakeLivestreamControl;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LivestreamJoinServiceTest {

    private final FakeLivestreamControl control = new FakeLivestreamControl();
    private final FakeResponder responder = new FakeResponder();
    private final LivestreamJoinService service =
            new LivestreamJoinService(OwnerPolicy.from("42"), control);

    private BotCommand.LivestreamJoinCmd command(UserId user, VoiceChannelId channel, String window) {
        return new BotCommand.LivestreamJoinCmd(TestData.GUILD, user, channel, window);
    }

    @Test
    void ownerStartsStreamOnTheirVoiceChannel() {
        service.join(command(TestData.USER, new VoiceChannelId("777"), "Notepad"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamStarted("Notepad"));
        assertThat(control.startedGuilds).containsExactly(TestData.GUILD);
        assertThat(control.startedChannels).containsExactly(new VoiceChannelId("777"));
        assertThat(control.startedWindows).containsExactly("Notepad");
    }

    @Test
    void desktopTargetIsReportedAsFullScreen() {
        service.join(command(TestData.USER, new VoiceChannelId("777"), "desktop"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamStarted("desktop"));
    }

    @Test
    void trimsWindowBeforeStarting() {
        service.join(command(TestData.USER, new VoiceChannelId("777"), "  Notepad  "), responder);

        assertThat(control.startedWindows).containsExactly("Notepad");
    }

    @Test
    void nonOwnerIsRejectedWithoutTouchingSidecar() {
        service.join(command(TestData.OTHER_USER, new VoiceChannelId("777"), "Notepad"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.ownerOnly());
        assertThat(control.startedWindows).isEmpty();
    }

    @Test
    void disabledPolicyRejects() {
        LivestreamJoinService disabled = new LivestreamJoinService(OwnerPolicy.from(null), control);

        disabled.join(command(TestData.USER, new VoiceChannelId("777"), "Notepad"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamDisabled());
        assertThat(control.startedWindows).isEmpty();
    }

    @Test
    void rejectsWithoutVoiceChannel() {
        service.join(command(TestData.USER, null, "Notepad"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.voiceRequired());
        assertThat(control.startedWindows).isEmpty();
    }

    @Test
    void rejectsBlankWindow() {
        service.join(command(TestData.USER, new VoiceChannelId("777"), "   "), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamWindowRequired());
        assertThat(control.startedWindows).isEmpty();
    }

    @Test
    void mapsWindowNotFoundFailure() {
        control.startResult = new LivestreamControlPort.Result.Failed(
                LivestreamControlPort.FailureKind.WINDOW_NOT_FOUND, "não achei");

        service.join(command(TestData.USER, new VoiceChannelId("777"), "X"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamWindowNotFound("X"));
    }

    @Test
    void mapsTransmitterUnavailableFailure() {
        control.startResult = new LivestreamControlPort.Result.Failed(
                LivestreamControlPort.FailureKind.TRANSMITTER_UNAVAILABLE, "token inválido");

        service.join(command(TestData.USER, new VoiceChannelId("777"), "X"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamTransmitterUnavailable());
    }

    @Test
    void mapsCaptureFailedFailure() {
        control.startResult = new LivestreamControlPort.Result.Failed(
                LivestreamControlPort.FailureKind.CAPTURE_FAILED, "ffmpeg off");

        service.join(command(TestData.USER, new VoiceChannelId("777"), "X"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamCaptureFailed("ffmpeg off"));
    }

    @Test
    void mapsUnknownFailure() {
        control.startResult = new LivestreamControlPort.Result.Failed(
                LivestreamControlPort.FailureKind.UNKNOWN, "sei lá");

        service.join(command(TestData.USER, new VoiceChannelId("777"), "X"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamFailed("sei lá"));
    }

    @Test
    void mapsUnavailableTransport() {
        control.startResult = new LivestreamControlPort.Result.Unavailable("connect refused");

        service.join(command(TestData.USER, new VoiceChannelId("777"), "X"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.livestreamServiceDown());
    }
}
