package com.mutrabot.domain;

import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.PlaybackState;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GuildQueuePlayTest {

    private final GuildQueue queue = new GuildQueue(TestData.GUILD);

    @Test
    void startsEmptyAndIdle() {
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.state()).isEqualTo(PlaybackState.IDLE);
        assertThat(queue.current()).isEmpty();
        assertThat(queue.upcoming()).isEmpty();
        assertThat(queue.becameIdleAt()).isEmpty();
    }

    @Test
    void firstTrackStartsImmediately() {
        Track track = TestData.track("t1", "Primeira");

        GuildQueue.EnqueueResult result = queue.enqueue(track);

        assertThat(result).isEqualTo(new GuildQueue.EnqueueResult.Started(track));
        assertThat(queue.current()).contains(track);
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
        assertThat(queue.upcoming()).isEmpty();
        assertThat(queue.becameIdleAt()).isEmpty();
    }

    @Test
    void secondTrackIsQueuedAtPositionOne() {
        Track first = TestData.track("t1", "Primeira");
        Track second = TestData.track("t2", "Segunda");
        queue.enqueue(first);

        GuildQueue.EnqueueResult result = queue.enqueue(second);

        assertThat(result).isEqualTo(new GuildQueue.EnqueueResult.Queued(second, 1));
        assertThat(queue.current()).contains(first);
        assertThat(queue.upcoming()).containsExactly(second);
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void preservesFifoOrderForManyTracks() {
        queue.enqueue(TestData.track("t1", "Primeira"));
        queue.enqueue(TestData.track("t2", "Segunda"));
        queue.enqueue(TestData.track("t3", "Terceira"));
        queue.enqueue(TestData.track("t4", "Quarta"));

        assertThat(queue.upcoming())
                .extracting(Track::title)
                .containsExactly("Segunda", "Terceira", "Quarta");
        assertThat(queue.upcoming().get(0).requestedBy().displayName()).isEqualTo("Ana");
    }

    @Test
    void keepsRequesterAttributionPerTrack() {
        Track mine = TestData.track("t1", "Minha", TestData.requester("Ana"));
        Track other = TestData.track("t2", "Outra", TestData.otherRequester("Bia"));
        queue.enqueue(mine);
        queue.enqueue(other);

        assertThat(queue.current().orElseThrow().requestedBy().displayName()).isEqualTo("Ana");
        assertThat(queue.upcoming().get(0).requestedBy().displayName()).isEqualTo("Bia");
    }

    @Test
    void playlistStartsFirstTrackAndPreservesOrder() {
        Track first = TestData.track("p1", "Faixa 1");
        Track second = TestData.track("p2", "Faixa 2");
        Track third = TestData.track("p3", "Faixa 3");

        GuildQueue.PlaylistResult result = queue.enqueuePlaylist(List.of(first, second, third));

        assertThat(result.added()).isEqualTo(3);
        assertThat(result.ignored()).isZero();
        assertThat(result.startedNow()).isEqualTo(first);
        assertThat(queue.current()).contains(first);
        assertThat(queue.upcoming()).containsExactly(second, third);
    }

    @Test
    void playlistQueuesBehindCurrentTrack() {
        Track current = TestData.track("t1", "Atual");
        queue.enqueue(current);

        GuildQueue.PlaylistResult result = queue.enqueuePlaylist(
                List.of(TestData.track("p1", "Faixa 1"), TestData.track("p2", "Faixa 2")));

        assertThat(result.added()).isEqualTo(2);
        assertThat(result.ignored()).isZero();
        assertThat(result.startedNow()).isNull();
        assertThat(result.startedNowOptional()).isEmpty();
        assertThat(queue.current()).contains(current);
        assertThat(queue.upcoming()).hasSize(2);
    }

    @Test
    void playlistTruncatesAtOneHundredTracks() {
        List<Track> tracks = java.util.stream.IntStream.range(0, 150)
                .mapToObj(i -> TestData.track("p" + i, "Faixa " + i))
                .toList();

        GuildQueue.PlaylistResult result = queue.enqueuePlaylist(tracks);

        assertThat(result.added()).isEqualTo(100);
        assertThat(result.ignored()).isEqualTo(50);
        assertThat(queue.current()).contains(tracks.get(0));
        assertThat(queue.upcoming()).hasSize(99);
        assertThat(queue.upcoming().get(98).title()).isEqualTo("Faixa 99");
    }

    @Test
    void playlistOfExactlyOneHundredIsNotTruncated() {
        List<Track> tracks = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> TestData.track("p" + i, "Faixa " + i))
                .toList();

        GuildQueue.PlaylistResult result = queue.enqueuePlaylist(tracks);

        assertThat(result.added()).isEqualTo(100);
        assertThat(result.ignored()).isZero();
        assertThat(queue.upcoming()).hasSize(99);
    }

    @Test
    void emptyPlaylistEnqueuesNothing() {
        GuildQueue.PlaylistResult result = queue.enqueuePlaylist(List.of());

        assertThat(result.added()).isZero();
        assertThat(result.ignored()).isZero();
        assertThat(result.startedNowOptional()).isEmpty();
        assertThat(queue.state()).isEqualTo(PlaybackState.IDLE);
    }

    @Test
    void enqueueAfterQueueEndedClearsIdleTimestamp() {
        queue.enqueue(TestData.track("t1", "Primeira"));
        queue.onTrackFinished(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(queue.becameIdleAt()).isPresent();

        queue.enqueue(TestData.track("t2", "Segunda"));

        assertThat(queue.becameIdleAt()).isEmpty();
        assertThat(queue.current()).isPresent();
        assertThat(queue.state()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    void playlistReceivesRequesterPerTrack() {
        Track first = TestData.track("p1", "Faixa 1", TestData.requester("Ana"));
        Track second = TestData.track("p2", "Faixa 2", TestData.requester("Ana"));

        queue.enqueuePlaylist(List.of(first, second));

        assertThat(queue.upcoming()).allMatch(t -> t.requestedBy().displayName().equals("Ana"));
    }

    @Test
    void enqueueRejectsNull() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> queue.enqueue(null))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> queue.enqueuePlaylist(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposeGuildIdAndOptionalCurrent() {
        assertThat(queue.guildId()).isEqualTo(TestData.GUILD);
        assertThat(queue.current()).isEqualTo(Optional.empty());
    }
}
