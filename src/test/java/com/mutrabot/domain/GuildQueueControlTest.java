package com.mutrabot.domain;

import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.PlaybackState;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GuildQueueControlTest {

    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    private final GuildQueue queue = new GuildQueue(TestData.GUILD);

    @Test
    void pauseTransitionsFromPlayingToPaused() {
        Track track = TestData.track("t1", "Primeira");
        queue.enqueue(track);

        Optional<Track> paused = queue.pause();

        assertThat(paused).contains(track);
        assertThat(queue.state()).isEqualTo(PlaybackState.PAUSED);
    }

    @Test
    void pauseDoesNothingWhenIdle() {
        assertThat(queue.pause()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.IDLE);
    }

    @Test
    void pauseDoesNothingWhenAlreadyPaused() {
        queue.enqueue(TestData.track("t1", "Primeira"));
        queue.pause();

        assertThat(queue.pause()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.PAUSED);
    }

    @Test
    void resumeTransitionsFromPausedToPlaying() {
        Track track = TestData.track("t1", "Primeira");
        queue.enqueue(track);
        queue.pause();

        Optional<Track> resumed = queue.resume();

        assertThat(resumed).contains(track);
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void resumeDoesNothingWhenPlaying() {
        queue.enqueue(TestData.track("t1", "Primeira"));

        assertThat(queue.resume()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void resumeDoesNothingWhenIdle() {
        assertThat(queue.resume()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.IDLE);
    }

    @Test
    void skipAdvancesToNextTrack() {
        Track first = TestData.track("t1", "Primeira");
        Track second = TestData.track("t2", "Segunda");
        queue.enqueue(first);
        queue.enqueue(second);

        GuildQueue.AdvanceResult result = queue.skip(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.Advanced(second));
        assertThat(queue.current()).contains(second);
        assertThat(queue.upcoming()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
        assertThat(queue.becameIdleAt()).isEmpty();
    }

    @Test
    void skipFromPausedStartsPlayingNext() {
        queue.enqueue(TestData.track("t1", "Primeira"));
        Track second = TestData.track("t2", "Segunda");
        queue.enqueue(second);
        queue.pause();

        GuildQueue.AdvanceResult result = queue.skip(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.Advanced(second));
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void skipLastTrackEndsQueueAndStartsIdleTimer() {
        queue.enqueue(TestData.track("t1", "Primeira"));

        GuildQueue.AdvanceResult result = queue.skip(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.QueueEnded());
        assertThat(queue.current()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.IDLE);
        assertThat(queue.becameIdleAt()).contains(NOW);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void skipWhenIdleIsNoOp() {
        GuildQueue.AdvanceResult result = queue.skip(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.NothingToAdvance());
        assertThat(queue.becameIdleAt()).isEmpty();
    }

    @Test
    void naturalEndAdvancesToNextTrack() {
        Track first = TestData.track("t1", "Primeira");
        Track second = TestData.track("t2", "Segunda");
        queue.enqueue(first);
        queue.enqueue(second);

        GuildQueue.AdvanceResult result = queue.onTrackFinished(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.Advanced(second));
        assertThat(queue.current()).contains(second);
    }

    @Test
    void naturalEndOfLastTrackEndsQueue() {
        queue.enqueue(TestData.track("t1", "Primeira"));

        GuildQueue.AdvanceResult result = queue.onTrackFinished(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.QueueEnded());
        assertThat(queue.state()).isEqualTo(PlaybackState.IDLE);
        assertThat(queue.becameIdleAt()).contains(NOW);
    }

    @Test
    void naturalEndWhenNothingPlayingIsNoOp() {
        GuildQueue.AdvanceResult result = queue.onTrackFinished(NOW);

        assertThat(result).isEqualTo(new GuildQueue.AdvanceResult.NothingToAdvance());
        assertThat(queue.becameIdleAt()).isEmpty();
    }

    @Test
    void fullSequencePreservesOrderAcrossSkips() {
        Track t1 = TestData.track("t1", "Primeira");
        Track t2 = TestData.track("t2", "Segunda");
        Track t3 = TestData.track("t3", "Terceira");
        queue.enqueue(t1);
        queue.enqueue(t2);
        queue.enqueue(t3);

        assertThat(queue.skip(NOW)).isEqualTo(new GuildQueue.AdvanceResult.Advanced(t2));
        assertThat(queue.skip(NOW)).isEqualTo(new GuildQueue.AdvanceResult.Advanced(t3));
        assertThat(queue.skip(NOW)).isEqualTo(new GuildQueue.AdvanceResult.QueueEnded());
        assertThat(queue.skip(NOW)).isEqualTo(new GuildQueue.AdvanceResult.NothingToAdvance());
    }

    @Test
    void pauseResumeSkipInterleaved() {
        queue.enqueue(TestData.track("t1", "Primeira"));
        Track second = TestData.track("t2", "Segunda");
        queue.enqueue(second);

        assertThat(queue.pause()).isPresent();
        assertThat(queue.resume()).isPresent();
        assertThat(queue.skip(NOW)).isEqualTo(new GuildQueue.AdvanceResult.Advanced(second));
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
    }
}
