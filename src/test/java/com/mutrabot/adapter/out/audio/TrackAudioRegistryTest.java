package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.TrackId;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TrackAudioRegistryTest {

    private final TrackAudioRegistry registry = new TrackAudioRegistry();

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
}
