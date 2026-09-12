package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.TrackFinishedService;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.PlaybackState;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.FakePlaybackAdapter;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class TrackFinishedServiceTest {

    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private final List<String> announcements = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    private IdleDisconnectService idleDisconnect;
    private TrackFinishedService service;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        service = new TrackFinishedService(
                queues, playback, (guild, message) -> announcements.add(guild + ":" + message), idleDisconnect);
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
    void finishingTrackStartsNextAndAnnounces() {
        Track first = TestData.track("t1", "Primeira");
        Track second = TestData.track("t2", "Segunda");
        seedPlayingQueue(first, second);

        service.onTrackFinished(TestData.GUILD);

        assertThat(playback.lastPlayed()).isEqualTo(second);
        assertThat(announcements).containsExactly(TestData.GUILD + ":" + BotMessages.nowPlaying(second));
        assertThat(queues.find(TestData.GUILD).orElseThrow().state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void finishingLastTrackAnnouncesEndAndSchedulesIdleDisconnect() {
        seedPlayingQueue(TestData.track("t1", "Primeira"));

        service.onTrackFinished(TestData.GUILD);

        assertThat(announcements).containsExactly(TestData.GUILD + ":" + BotMessages.queueEndedAnnouncement());
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();
        assertThat(playback.played).isEmpty();
    }

    @Test
    void finishingTrackCancelsPreviousIdleTimer() {
        seedPlayingQueue(TestData.track("t1", "Primeira"));
        service.onTrackFinished(TestData.GUILD);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();

        seedPlayingQueue(TestData.track("t2", "Segunda"), TestData.track("t3", "Terceira"));
        service.onTrackFinished(TestData.GUILD);

        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void strayFinishEventForIdleGuildIsIgnored() {
        service.onTrackFinished(new GuildId("999"));

        assertThat(announcements).isEmpty();
        assertThat(playback.played).isEmpty();
    }
}
