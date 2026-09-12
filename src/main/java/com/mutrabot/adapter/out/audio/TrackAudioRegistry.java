package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.TrackId;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackAudioRegistry {

    private final ConcurrentHashMap<String, AudioTrack> audioTracks = new ConcurrentHashMap<>();

    public void register(TrackId id, AudioTrack audioTrack) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(audioTrack, "audioTrack");
        audioTracks.put(id.value(), audioTrack);
    }

    public Optional<AudioTrack> find(TrackId id) {
        return Optional.ofNullable(audioTracks.get(id.value()));
    }

    public void remove(TrackId id) {
        audioTracks.remove(id.value());
    }
}
