package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.TrackId;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TrackAudioRegistryTest {

    private final TrackAudioRegistry registry = new TrackAudioRegistry();

    @TempDir
    Path tempDir;

    @Test
    void registersAndFindsAudioTracks() {
        AudioTrack audioTrack = mock(AudioTrack.class);
        TrackId id = new TrackId("abc");

        registry.register(id, audioTrack);

        assertThat(registry.find(id)).contains(audioTrack);
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertThat(registry.find(new TrackId("missing"))).isEqualTo(Optional.empty());
    }

    @Test
    void removesEntries() {
        AudioTrack audioTrack = mock(AudioTrack.class);
        TrackId id = new TrackId("abc");
        registry.register(id, audioTrack);

        registry.remove(id);

        assertThat(registry.find(id)).isEmpty();
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> registry.register(null, mock(AudioTrack.class)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.register(new TrackId("a"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void removesDownloadedFileWithEntry() throws IOException {
        Path file = Files.writeString(tempDir.resolve("track.webm"), "audio");
        TrackId id = new TrackId("abc");
        registry.register(id, mock(AudioTrack.class), file);

        registry.remove(id);

        assertThat(registry.find(id)).isEmpty();
        assertThat(file).doesNotExist();
    }

    @Test
    void deleteQuietlyIgnoresNullAndUndeletablePath() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("dir"));
        Files.writeString(directory.resolve("child"), "x");

        assertThatCode(() -> TrackAudioRegistry.deleteQuietly(null)).doesNotThrowAnyException();
        assertThatCode(() -> TrackAudioRegistry.deleteQuietly(directory)).doesNotThrowAnyException();

        assertThat(directory).exists();
    }
}
