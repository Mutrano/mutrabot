package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.JoinCommandService;
import com.mutrabot.application.service.LeaveCommandService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.support.FakePlaybackAdapter;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class VoicePresenceServiceTest {

    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final FakeResponder responder = new FakeResponder();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private ScheduledExecutorService scheduler;
    private IdleDisconnectService idleDisconnect;
    private JoinCommandService joinService;
    private LeaveCommandService leaveService;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        joinService = new JoinCommandService(playback, idleDisconnect);
        leaveService = new LeaveCommandService(queues, playback, idleDisconnect);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private BotCommand.JoinCmd joinCommand(VoiceChannelId channel) {
        return new BotCommand.JoinCmd(TestData.GUILD, TestData.USER, channel);
    }

    private BotCommand.LeaveCmd leaveCommand() {
        return new BotCommand.LeaveCmd(TestData.GUILD, TestData.USER);
    }

    @Test
    void joinWithoutVoiceChannelWarns() {
        joinService.join(joinCommand(null), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo(BotMessages.voiceRequired());
        assertThat(playback.connectedTo).isEmpty();
    }

    @Test
    void joinConnectsAndCancelsIdleTimer() {
        idleDisconnect.scheduleDisconnect(TestData.GUILD);
        VoiceChannelId channel = new VoiceChannelId("777");

        joinService.join(joinCommand(channel), responder);

        assertThat(playback.connectedTo).containsExactly(channel);
        assertThat(playback.currentChannel).isEqualTo(channel);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
        assertThat(responder.lastReply()).isEqualTo(BotMessages.joined());
    }

    @Test
    void joinWhenAlreadyInSameChannelWarns() {
        VoiceChannelId channel = new VoiceChannelId("777");
        playback.currentChannel = channel;

        joinService.join(joinCommand(channel), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo(BotMessages.alreadyInVoice());
        assertThat(playback.connectedTo).isEmpty();
    }

    @Test
    void joinFromAnotherChannelReconnects() {
        playback.currentChannel = new VoiceChannelId("888");
        VoiceChannelId channel = new VoiceChannelId("777");

        joinService.join(joinCommand(channel), responder);

        assertThat(playback.connectedTo).containsExactly(channel);
        assertThat(responder.lastReply()).isEqualTo(BotMessages.joined());
    }

    @Test
    void leaveWithoutSessionWarns() {
        leaveService.leave(leaveCommand(), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo(BotMessages.notInVoice());
        assertThat(playback.disconnected).isEmpty();
    }

    @Test
    void leaveDisconnectsClearsQueueAndCancelsIdleTimer() {
        playback.currentChannel = new VoiceChannelId("777");
        queues.withLock(TestData.GUILD, queue -> queue.enqueue(TestData.track("t1", "Música")));
        idleDisconnect.scheduleDisconnect(TestData.GUILD);

        leaveService.leave(leaveCommand(), responder);

        assertThat(playback.disconnected).containsExactly(TestData.GUILD);
        assertThat(playback.currentChannel).isNull();
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
        assertThat(queues.find(TestData.GUILD)).isEmpty();
        assertThat(responder.lastReply()).isEqualTo(BotMessages.left());
    }
}
