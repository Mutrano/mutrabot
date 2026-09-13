package com.mutrabot.contract;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
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

class VoicePresenceContractTest {

    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final FakeResponder responder = new FakeResponder();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private ScheduledExecutorService scheduler;
    private JoinCommandService joinService;
    private LeaveCommandService leaveService;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        IdleDisconnectService idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        joinService = new JoinCommandService(playback, idleDisconnect);
        leaveService = new LeaveCommandService(queues, playback, idleDisconnect);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void joinMatchesContract() {
        joinService.join(
                new BotCommand.JoinCmd(TestData.GUILD, TestData.USER, new VoiceChannelId("777")), responder);

        assertThat(responder.lastReply()).isEqualTo("🔊 Entrei no canal de voz.");
    }

    @Test
    void joinWithoutVoiceChannelMatchesContract() {
        joinService.join(new BotCommand.JoinCmd(TestData.GUILD, TestData.USER, null), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo("🔇 Entre em um canal de voz primeiro e tente de novo.");
    }

    @Test
    void joinAlreadyConnectedMatchesContract() {
        VoiceChannelId channel = new VoiceChannelId("777");
        playback.currentChannel = channel;

        joinService.join(new BotCommand.JoinCmd(TestData.GUILD, TestData.USER, channel), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo("ℹ️ Já estou no seu canal de voz.");
    }

    @Test
    void leaveMatchesContract() {
        playback.currentChannel = new VoiceChannelId("777");
        queues.withLock(TestData.GUILD, queue -> queue.enqueue(TestData.track("t1", "Música")));

        leaveService.leave(new BotCommand.LeaveCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo("👋 Saí do canal de voz e limpei a fila.");
    }

    @Test
    void leaveWithoutSessionMatchesContract() {
        leaveService.leave(new BotCommand.LeaveCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo("ℹ️ Não estou em um canal de voz.");
    }
}
