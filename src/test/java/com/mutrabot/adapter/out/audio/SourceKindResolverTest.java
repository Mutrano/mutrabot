package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.SourceKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceKindResolverTest {

    @Test
    void detectsYoutube() {
        assertThat(SourceKindResolver.detect("https://www.youtube.com/watch?v=abc")).isEqualTo(SourceKind.YOUTUBE);
        assertThat(SourceKindResolver.detect("https://youtu.be/abc")).isEqualTo(SourceKind.YOUTUBE);
        assertThat(SourceKindResolver.detect("https://music.youtube.com/watch?v=abc")).isEqualTo(SourceKind.YOUTUBE);
    }

    @Test
    void detectsSoundCloud() {
        assertThat(SourceKindResolver.detect("https://soundcloud.com/artist/track")).isEqualTo(SourceKind.SOUNDCLOUD);
    }

    @Test
    void detectsSpotifyAndTidal() {
        assertThat(SourceKindResolver.detect("https://open.spotify.com/track/x")).isEqualTo(SourceKind.SPOTIFY);
        assertThat(SourceKindResolver.detect("https://tidal.com/browse/track/1")).isEqualTo(SourceKind.TIDAL);
    }

    @Test
    void fallsBackToHttp() {
        assertThat(SourceKindResolver.detect("https://example.com/stream.mp3")).isEqualTo(SourceKind.HTTP);
        assertThat(SourceKindResolver.detect("")).isEqualTo(SourceKind.HTTP);
        assertThat(SourceKindResolver.detect("   ")).isEqualTo(SourceKind.HTTP);
        assertThat(SourceKindResolver.detect(null)).isEqualTo(SourceKind.HTTP);
    }
}
