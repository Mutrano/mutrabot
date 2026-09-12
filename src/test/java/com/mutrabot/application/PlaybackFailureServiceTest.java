package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.PlaybackFailureService;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackFailureKind;
import com.mutrabot.support.FakePlaybackAdapter;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackFailureServiceTest {

    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private final List<String> announcements = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    private IdleDisconnectService idleDisconnect;
    private PlaybackFailureService service;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        service = new PlaybackFailureService(
                queues, playback, (guild, message) -> announcements.add(message), idleDisconnect);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private void seed(Track... tracks) {
        queues.withLock(TestData.GUILD, queue -> {
            for (Track track : tracks) {
                queue.enqueue(track);
            }
            return null;
        });
    }

    @Test
    void failureWithNextTrackSkipsAndPlaysIt() {
        Track failed = TestData.track("t1", "Restrita");
        Track next = TestData.track("t2", "Próxima");
        seed(failed, next);

        service.onTrackFailed(TestData.GUILD, TrackFailureKind.RESTRICTED);

        assertThat(playback.lastPlayed()).isEqualTo(next);
        assertThat(announcements).containsExactly(
                BotMessages.trackFailed("Restrita", TrackFailureKind.RESTRICTED, Optional.of(next)));
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
        assertThat(queues.find(TestData.GUILD).orElseThrow().current()).contains(next);
    }

    @Test
    void failureWithNextTrackCancelsPendingIdleDisconnect() {
        seed(TestData.track("t1", "Restrita"), TestData.track("t2", "Próxima"));
        idleDisconnect.scheduleDisconnect(TestData.GUILD);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();

        service.onTrackFailed(TestData.GUILD, TrackFailureKind.RESTRICTED);

        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void failureOnLastTrackEndsQueueAndSchedulesIdleDisconnect() {
        Track failed = TestData.track("t1", "Restrita");
        seed(failed);

        service.onTrackFailed(TestData.GUILD, TrackFailureKind.RESTRICTED);

        assertThat(playback.played).isEmpty();
        assertThat(announcements).containsExactly(
                BotMessages.trackFailed("Restrita", TrackFailureKind.RESTRICTED, Optional.empty()));
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();
        assertThat(queues.find(TestData.GUILD).orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void unavailableFailureAnnouncesSpecificReason() {
        seed(TestData.track("t1", "Sumida"));

        service.onTrackFailed(TestData.GUILD, TrackFailureKind.UNAVAILABLE);

        assertThat(announcements.get(0))
                .startsWith("🚫 Não consegui tocar **Sumida**: a faixa está indisponível, privada ou removida.");
    }

    @Test
    void stuckFailureAnnouncesStuckMessage() {
        Track stuck = TestData.track("t1", "Travada");
        seed(stuck, TestData.track("t2", "Próxima"));

        service.onTrackFailed(TestData.GUILD, TrackFailureKind.STUCK);

        assertThat(announcements.get(0)).startsWith("⚠️ **Travada** travou e foi pulada.");
    }

    @Test
    void failureWhileIdleIsIgnored() {
        service.onTrackFailed(TestData.GUILD, TrackFailureKind.UNKNOWN);

        assertThat(announcements).isEmpty();
        assertThat(playback.played).isEmpty();
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }
}
