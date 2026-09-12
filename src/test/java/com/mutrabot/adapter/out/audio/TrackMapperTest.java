package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.TestData;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackMapperTest {

    private final TrackAudioRegistry registry = new TrackAudioRegistry();

    private AudioTrack audioTrack(AudioTrackInfo info) {
        AudioTrack audioTrack = mock(AudioTrack.class);
        when(audioTrack.getInfo()).thenReturn(info);
        return audioTrack;
    }

    @Test
    void mapsMetadataAndDuration() {
        AudioTrack audioTrack = audioTrack(new AudioTrackInfo(
                "Título", "Artista", 125000, "vid1", false, "https://youtu.be/vid1", null, null));

        Track track = TrackMapper.toTrack(
                audioTrack, TestData.requester("Ana"), SourceKind.YOUTUBE, null, registry);

        assertThat(track.id()).isEqualTo(new com.mutrabot.domain.model.TrackId("vid1"));
        assertThat(track.title()).isEqualTo("Título");
        assertThat(track.author()).isEqualTo("Artista");
        assertThat(track.sourceUrl()).isEqualTo("https://youtu.be/vid1");
        assertThat(track.duration()).isEqualTo(Duration.ofSeconds(125));
        assertThat(track.source()).isEqualTo(SourceKind.YOUTUBE);
        assertThat(track.isLive()).isFalse();
        assertThat(registry.find(track.id())).contains(audioTrack);
    }

    @Test
    void streamTracksAreMarkedLive() {
        AudioTrack audioTrack = audioTrack(new AudioTrackInfo(
                "Live", "Artista", 0, "live1", true, "https://example.com/live", null, null));

        Track track = TrackMapper.toTrack(
                audioTrack, TestData.requester("Ana"), SourceKind.HTTP, null, registry);

        assertThat(track.duration()).isEqualTo(Duration.ZERO);
        assertThat(track.isLive()).isTrue();
    }

    @Test
    void nonPositiveLengthIsTreatedAsLive() {
        AudioTrack audioTrack = audioTrack(new AudioTrackInfo(
                "Sem duração", "Artista", 0, "x1", false, "https://example.com/x", null, null));

        Track track = TrackMapper.toTrack(
                audioTrack, TestData.requester("Ana"), SourceKind.HTTP, null, registry);

        assertThat(track.isLive()).isTrue();
    }

    @Test
    void missingIdentifierGeneratesOne() {
        AudioTrack audioTrack = audioTrack(new AudioTrackInfo(
                "Sem id", "Artista", 1000, "  ", false, "https://example.com/x", null, null));

        Track track = TrackMapper.toTrack(
                audioTrack, TestData.requester("Ana"), SourceKind.HTTP, null, registry);

        assertThat(track.id().value()).isNotBlank();
        assertThat(registry.find(track.id())).contains(audioTrack);
    }

    @Test
    void nullIdentifierGeneratesOne() {
        AudioTrack audioTrack = audioTrack(new AudioTrackInfo(
                "Sem id", "Artista", 1000, null, false, "https://example.com/x", null, null));

        Track track = TrackMapper.toTrack(
                audioTrack, TestData.requester("Ana"), SourceKind.HTTP, null, registry);

        assertThat(track.id().value()).isNotBlank();
    }

    @Test
    void sourceUrlOverrideWinsOverMetadataUri() {
        AudioTrack audioTrack = audioTrack(new AudioTrackInfo(
                "Título", "Artista", 1000, "y1", false, "https://youtu.be/y1", null, null));

        Track track = TrackMapper.toTrack(
                audioTrack,
                TestData.requester("Ana"),
                SourceKind.SPOTIFY,
                "https://open.spotify.com/track/abc",
                registry);

        assertThat(track.sourceUrl()).isEqualTo("https://open.spotify.com/track/abc");
        assertThat(track.source()).isEqualTo(SourceKind.SPOTIFY);
    }
}
