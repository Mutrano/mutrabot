package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.SourceKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryTest {

    @Test
    void detectsHttpUrls() {
        assertThat(SearchQuery.isUrl("http://example.com/a")).isTrue();
        assertThat(SearchQuery.isUrl("https://youtu.be/abc")).isTrue();
        assertThat(SearchQuery.isUrl("HTTPS://YOUTU.BE/ABC")).isTrue();
        assertThat(SearchQuery.isUrl("  https://example.com  ")).isTrue();
    }

    @Test
    void rejectsTextAndNull() {
        assertThat(SearchQuery.isUrl("nome da musica")).isFalse();
        assertThat(SearchQuery.isUrl("www.example.com")).isFalse();
        assertThat(SearchQuery.isUrl(null)).isFalse();
        assertThat(SearchQuery.isUrl("")).isFalse();
    }

    @Test
    void flagsSpotifyAndTidalLinksOnly() {
        assertThat(SearchQuery.nonStreamableSource("https://open.spotify.com/track/abc"))
                .contains(SourceKind.SPOTIFY);
        assertThat(SearchQuery.nonStreamableSource("https://tidal.com/browse/track/123"))
                .contains(SourceKind.TIDAL);
        assertThat(SearchQuery.nonStreamableSource("https://youtube.com/watch?v=x")).isEmpty();
        assertThat(SearchQuery.nonStreamableSource("spotify.com sem esquema")).isEmpty();
    }

    @Test
    void buildsYoutubeSearchPrefix() {
        assertThat(SearchQuery.withSearchPrefix("artista musica")).isEqualTo("ytsearch:artista musica");
        assertThat(SearchQuery.SEARCH_PREFIX).isEqualTo("ytsearch:");
    }
}
