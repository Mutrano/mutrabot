package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.ResumeCommandService;
import com.mutrabot.application.service.SkipCommandService;
import com.mutrabot.application.service.StopCommandService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.PlaybackState;
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

class PlaybackControlServiceTest {

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

    private BotCommand.StopCmd stopCommand() {
        return new BotCommand.StopCmd(TestData.GUILD, TestData.USER);
    }

    private BotCommand.ResumeCmd resumeCommand() {
        return new BotCommand.ResumeCmd(TestData.GUILD, TestData.USER);
    }

    private BotCommand.SkipCmd skipCommand() {
        return new BotCommand.SkipCmd(TestData.GUILD, TestData.USER);
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
    void stopPausesCurrentTrack() {
        Track track = TestData.track("t1", "Música");
        seedPlayingQueue(track);

        stopService.stop(stopCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo(BotMessages.paused(track));
        assertThat(playback.paused).containsExactly(TestData.GUILD);
        assertThat(queues.find(TestData.GUILD).orElseThrow().state()).isEqualTo(PlaybackState.PAUSED);
    }

    @Test
    void stopWhenIdleWarnsWithoutTouchingQueue() {
        stopService.stop(stopCommand(), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo(BotMessages.nothingPlaying());
        assertThat(playback.paused).isEmpty();
    }

    @Test
    void stopTwiceWarnsSecondTime() {
        seedPlayingQueue(TestData.track("t1", "Música"));
        stopService.stop(stopCommand(), responder);

        stopService.stop(stopCommand(), responder);

        assertThat(responder.ephemeralReplies).last().isEqualTo(BotMessages.nothingPlaying());
        assertThat(playback.paused).hasSize(1);
    }

    @Test
    void resumeContinuesPausedTrack() {
        Track track = TestData.track("t1", "Música");
        seedPlayingQueue(track);
        stopService.stop(stopCommand(), responder);

        resumeService.resume(resumeCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo(BotMessages.resumed(track));
        assertThat(playback.resumed).containsExactly(TestData.GUILD);
        assertThat(queues.find(TestData.GUILD).orElseThrow().state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void resumeWhenPlayingWarns() {
        seedPlayingQueue(TestData.track("t1", "Música"));

        resumeService.resume(resumeCommand(), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo(BotMessages.nothingPaused());
        assertThat(playback.resumed).isEmpty();
    }

    @Test
    void skipAdvancesAnnouncesAndKeepsPlaying() {
        Track first = TestData.track("t1", "Primeira");
        Track second = TestData.track("t2", "Segunda");
        seedPlayingQueue(first, second);

        skipService.skip(skipCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo(BotMessages.skippedTo(second));
        assertThat(playback.lastPlayed()).isEqualTo(second);
        assertThat(playback.stopped).isEmpty();
        assertThat(queues.find(TestData.GUILD).orElseThrow().current()).contains(second);
    }

    @Test
    void skipLastTrackEndsQueueStopsPlaybackAndSchedulesIdle() {
        seedPlayingQueue(TestData.track("t1", "Primeira"));

        skipService.skip(skipCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo(BotMessages.queueEndedAfterSkip());
        assertThat(playback.stopped).containsExactly(TestData.GUILD);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();
        assertThat(queues.find(TestData.GUILD).orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void skipWhenIdleWarns() {
        skipService.skip(skipCommand(), responder);

        assertThat(responder.lastEphemeralReply()).isEqualTo(BotMessages.nothingToSkip());
        assertThat(playback.stopped).isEmpty();
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void skipCancelsPreviouslyScheduledIdleTimer() {
        seedPlayingQueue(TestData.track("t1", "Primeira"));
        skipService.skip(skipCommand(), responder);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();

        seedPlayingQueue(TestData.track("t2", "Segunda"), TestData.track("t3", "Terceira"));
        skipService.skip(skipCommand(), responder);

        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void stopDoesNotClearQueueNorDisconnect() {
        seedPlayingQueue(TestData.track("t1", "Primeira"), TestData.track("t2", "Segunda"));

        stopService.stop(stopCommand(), responder);

        var queue = queues.find(TestData.GUILD).orElseThrow();
        assertThat(queue.upcoming()).hasSize(1);
        assertThat(queue.current()).isPresent();
        assertThat(playback.disconnected).isEmpty();
    }
}
