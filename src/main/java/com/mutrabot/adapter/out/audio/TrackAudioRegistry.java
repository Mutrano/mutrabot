package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.TrackId;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackAudioRegistry {

    private final ConcurrentHashMap<String, AudioTrack> audioTracks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Path> audioFiles = new ConcurrentHashMap<>();

    public void register(TrackId id, AudioTrack audioTrack) {
        register(id, audioTrack, null);
    }

    public void register(TrackId id, AudioTrack audioTrack, Path file) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(audioTrack, "audioTrack");
        audioTracks.put(id.value(), audioTrack);
        if (file != null) {
            audioFiles.put(id.value(), file);
        }
    }

    public Optional<AudioTrack> find(TrackId id) {
        return Optional.ofNullable(audioTracks.get(id.value()));
    }

    public void remove(TrackId id) {
        audioTracks.remove(id.value());
        deleteQuietly(audioFiles.remove(id.value()));
    }

    static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
