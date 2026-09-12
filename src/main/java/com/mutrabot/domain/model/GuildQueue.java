package com.mutrabot.domain.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GuildQueue {

    public static final int MAX_PLAYLIST_TRACKS = 100;

    private final GuildId guildId;
    private final Deque<Track> upcoming = new ArrayDeque<>();

    private Track current;
    private PlaybackState state = PlaybackState.IDLE;
    private Instant becameIdleAt;

    public GuildQueue(GuildId guildId) {
        this.guildId = Objects.requireNonNull(guildId, "guildId");
    }

    public GuildId guildId() {
        return guildId;
    }

    public PlaybackState state() {
        return state;
    }

    public Optional<Track> current() {
        return Optional.ofNullable(current);
    }

    public List<Track> upcoming() {
        return List.copyOf(upcoming);
    }

    public Optional<Instant> becameIdleAt() {
        return Optional.ofNullable(becameIdleAt);
    }

    public boolean isEmpty() {
        return current == null && upcoming.isEmpty();
    }

    public EnqueueResult enqueue(Track track) {
        Objects.requireNonNull(track, "track");
        if (current == null) {
            current = track;
            state = PlaybackState.PLAYING;
            becameIdleAt = null;
            return new EnqueueResult.Started(track);
        }
        upcoming.addLast(track);
        return new EnqueueResult.Queued(track, upcoming.size());
    }

    public PlaylistResult enqueuePlaylist(List<Track> tracks) {
        Objects.requireNonNull(tracks, "tracks");
        int accepted = Math.min(tracks.size(), MAX_PLAYLIST_TRACKS);
        Track started = null;
        for (int i = 0; i < accepted; i++) {
            if (enqueue(tracks.get(i)) instanceof EnqueueResult.Started s) {
                started = s.track();
            }
        }
        return new PlaylistResult(accepted, tracks.size() - accepted, started);
    }

    public Optional<Track> pause() {
        if (state != PlaybackState.PLAYING) {
            return Optional.empty();
        }
        state = PlaybackState.PAUSED;
        return Optional.of(current);
    }

    public Optional<Track> resume() {
        if (state != PlaybackState.PAUSED) {
            return Optional.empty();
        }
        state = PlaybackState.PLAYING;
        return Optional.of(current);
    }

    public AdvanceResult skip(Instant now) {
        return advance(now);
    }

    public AdvanceResult onTrackFinished(Instant now) {
        return advance(now);
    }

    private AdvanceResult advance(Instant now) {
        Objects.requireNonNull(now, "now");
        if (current == null) {
            return new AdvanceResult.NothingToAdvance();
        }
        current = upcoming.pollFirst();
        if (current == null) {
            state = PlaybackState.IDLE;
            becameIdleAt = now;
            return new AdvanceResult.QueueEnded();
        }
        state = PlaybackState.PLAYING;
        becameIdleAt = null;
        return new AdvanceResult.Advanced(current);
    }

    public sealed interface EnqueueResult {
        record Started(Track track) implements EnqueueResult {
        }

        record Queued(Track track, int position) implements EnqueueResult {
        }
    }

    public record PlaylistResult(int added, int ignored, Track startedNow) {
        public Optional<Track> startedNowOptional() {
            return Optional.ofNullable(startedNow);
        }
    }

    public sealed interface AdvanceResult {
        record Advanced(Track next) implements AdvanceResult {
        }

        record QueueEnded() implements AdvanceResult {
        }

        record NothingToAdvance() implements AdvanceResult {
        }
    }
}
