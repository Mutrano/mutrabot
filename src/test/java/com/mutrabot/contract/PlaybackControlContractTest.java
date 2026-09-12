package com.mutrabot.contract;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.ResumeCommandService;
import com.mutrabot.application.service.SkipCommandService;
import com.mutrabot.application.service.StopCommandService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.Track;
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

class PlaybackControlContractTest {

    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final FakeResponder responder = new FakeResponder();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private ScheduledExecutorService scheduler;
    private IdleDisconnectService idleDisconnect;
    private StopCommandService stopService;
    private ResumeCommandService resumeService;
    private SkipCommandService skipService;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        stopService = new StopCommandService(queues, playback);
        resumeService = new ResumeCommandService(queues, playback);
        skipService = new SkipCommandService(queues, playback, idleDisconnect);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private void seedPlayingQueue(Track... tracks) {
        queues.withLock(TestData.GUILD, queue -> {
            for (Track track : tracks) {
                queue.enqueue(track);
            }
            return null;
        });
    }

    @Test
    void stopPausesWithContractMessage() {
        seedPlayingQueue(TestData.track("t1", "Música"));

        stopService.stop(new BotCommand.StopCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo("⏸ Pausado: **Música**. Use /resume para continuar.");
    }

    @Test
    void stopWithNothingPlayingMatchesContract() {
        stopService.stop(new BotCommand.StopCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo("ℹ️ Nada tocando no momento.");
    }

    @Test
    void resumeContinuesWithContractMessage() {
        seedPlayingQueue(TestData.track("t1", "Música"));
        stopService.stop(new BotCommand.StopCmd(TestData.GUILD, TestData.USER), responder);

        resumeService.resume(new BotCommand.ResumeCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo("▶ Continuando: **Música**.");
    }

    @Test
    void resumeWithNothingPausedMatchesContract() {
        resumeService.resume(new BotCommand.ResumeCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo("ℹ️ Nada pausado no momento.");
    }

    @Test
    void skipWithNextTrackMatchesContract() {
        seedPlayingQueue(TestData.track("t1", "Primeira"), TestData.track("t2", "Segunda"));

        skipService.skip(new BotCommand.SkipCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo(
                "⏭ Pulado. ▶ Tocando agora: **Segunda** — pedido por Ana");
    }

    @Test
    void skipLastTrackMatchesContract() {
        seedPlayingQueue(TestData.track("t1", "Primeira"));

        skipService.skip(new BotCommand.SkipCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo("⏭ Pulado. 📭 A fila acabou.");
    }

    @Test
    void skipWithNothingPlayingMatchesContract() {
        skipService.skip(new BotCommand.SkipCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo("ℹ️ Nada para pular.");
    }
}
