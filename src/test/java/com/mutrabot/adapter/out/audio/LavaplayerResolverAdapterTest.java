package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.result.ResolverResult;
import com.mutrabot.support.TestData;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LavaplayerResolverAdapterTest {

    private final AudioPlayerManager manager = mock(AudioPlayerManager.class);
    private final TrackAudioRegistry registry = new TrackAudioRegistry();
    private final Requester requester = TestData.requester("Ana");

    private MetadataLookup metadata;
    private LavaplayerResolverAdapter adapter;

    @BeforeEach
    void setUp() {
        metadata = sourceUrl -> Optional.empty();
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata);
    }

    private static AudioTrack audioTrack(String identifier, String title) {
        AudioTrack audioTrack = mock(AudioTrack.class);
        when(audioTrack.getInfo()).thenReturn(new AudioTrackInfo(
                title, "Artista", 60000, identifier, false, "https://youtu.be/" + identifier, null, null));
        return audioTrack;
    }

    private void stubLoad(Consumer<AudioLoadResultHandler> completion) {
        when(manager.loadItemOrdered(any(), anyString(), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    completion.accept(invocation.getArgument(2));
                    return null;
                });
    }

    @Test
    void textQueryBecomesYoutubeSearch() {
        AudioTrack audioTrack = audioTrack("v1", "Melhor resultado");
        stubLoad(handler -> handler.trackLoaded(audioTrack));

        ResolverResult result = adapter.resolve("artista musica", requester);

        verify(manager).loadItemOrdered(any(), eq("ytsearch:artista musica"), any());
        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        Track track = ((ResolverResult.ResolvedTrack) result).track();
        assertThat(track.source()).isEqualTo(SourceKind.SEARCH_RESULT);
        assertThat(track.title()).isEqualTo("Melhor resultado");
        assertThat(track.requestedBy()).isEqualTo(requester);
        assertThat(registry.find(track.id())).contains(audioTrack);
    }

    @Test
    void searchPlaylistPicksSelectedTrackAsSingleResult() {
        AudioTrack best = audioTrack("best", "Escolhida");
        AudioTrack other = audioTrack("other", "Outra");
        BasicAudioPlaylist playlist = new BasicAudioPlaylist(
                "resultados", List.of(other, best), best, true);
        stubLoad(handler -> handler.playlistLoaded(playlist));

        ResolverResult result = adapter.resolve("busca", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        assertThat(((ResolverResult.ResolvedTrack) result).track().title()).isEqualTo("Escolhida");
    }

    @Test
    void searchPlaylistWithoutSelectedTrackPicksFirst() {
        AudioTrack first = audioTrack("first", "Primeira");
        BasicAudioPlaylist playlist = new BasicAudioPlaylist(
                "resultados", List.of(first), null, true);
        stubLoad(handler -> handler.playlistLoaded(playlist));

        ResolverResult result = adapter.resolve("busca", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        assertThat(((ResolverResult.ResolvedTrack) result).track().title()).isEqualTo("Primeira");
    }

    @Test
    void emptySearchResultsAreNotFound() {
        BasicAudioPlaylist playlist = new BasicAudioPlaylist("resultados", List.of(), null, true);
        stubLoad(handler -> handler.playlistLoaded(playlist));

        ResolverResult result = adapter.resolve("busca", requester);

        assertThat(result).isEqualTo(new ResolverResult.NotFound("busca"));
    }

    @Test
    void directYoutubeUrlKeepsYoutubeSource() {
        AudioTrack audioTrack = audioTrack("vid", "Vídeo");
        stubLoad(handler -> handler.trackLoaded(audioTrack));

        ResolverResult result = adapter.resolve("https://youtu.be/vid", requester);

        verify(manager).loadItemOrdered(any(), eq("https://youtu.be/vid"), any());
        Track track = ((ResolverResult.ResolvedTrack) result).track();
        assertThat(track.source()).isEqualTo(SourceKind.YOUTUBE);
        assertThat(track.sourceUrl()).isEqualTo("https://youtu.be/vid");
    }

    @Test
    void playlistUrlReturnsOrderedPlaylist() {
        AudioTrack one = audioTrack("p1", "Faixa 1");
        AudioTrack two = audioTrack("p2", "Faixa 2");
        AudioTrack three = audioTrack("p3", "Faixa 3");
        BasicAudioPlaylist playlist = new BasicAudioPlaylist(
                "minha playlist", List.of(one, two, three), null, false);
        stubLoad(handler -> handler.playlistLoaded(playlist));

        ResolverResult result = adapter.resolve("https://youtube.com/playlist?list=x", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedPlaylist.class);
        ResolverResult.ResolvedPlaylist resolved = (ResolverResult.ResolvedPlaylist) result;
        assertThat(resolved.tracks()).extracting(Track::title)
                .containsExactly("Faixa 1", "Faixa 2", "Faixa 3");
        assertThat(resolved.totalReported()).isEqualTo(3);
        assertThat(resolved.ignored()).isZero();
        assertThat(resolved.tracks()).allMatch(track -> track.requestedBy().equals(requester));
    }

    @Test
    void emptyPlaylistIsNotFound() {
        BasicAudioPlaylist playlist = new BasicAudioPlaylist("vazia", List.of(), null, false);
        stubLoad(handler -> handler.playlistLoaded(playlist));

        ResolverResult result = adapter.resolve("https://youtube.com/playlist?list=x", requester);

        assertThat(result).isEqualTo(new ResolverResult.NotFound("https://youtube.com/playlist?list=x"));
    }

    @Test
    void noMatchesReturnsNotFound() {
        stubLoad(AudioLoadResultHandler::noMatches);

        ResolverResult result = adapter.resolve("inexistente", requester);

        assertThat(result).isEqualTo(new ResolverResult.NotFound("inexistente"));
    }

    @Test
    void commonFailureIsNotRetryable() {
        stubLoad(handler -> handler.loadFailed(
                new FriendlyException("bad", FriendlyException.Severity.COMMON, null)));

        ResolverResult.LoadFailed failed = (ResolverResult.LoadFailed) adapter.resolve("entrada", requester);

        assertThat(failed.reason()).isEqualTo("a fonte não reconheceu ou não aceitou essa entrada");
        assertThat(failed.retryable()).isFalse();
    }

    @Test
    void suspiciousFailureIsNotRetryable() {
        stubLoad(handler -> handler.loadFailed(
                new FriendlyException("forbidden", FriendlyException.Severity.SUSPICIOUS, null)));

        ResolverResult.LoadFailed failed = (ResolverResult.LoadFailed) adapter.resolve("entrada", requester);

        assertThat(failed.reason()).isEqualTo("a fonte bloqueou ou restringiu o acesso");
        assertThat(failed.retryable()).isFalse();
    }

    @Test
    void faultFailureIsRetryable() {
        stubLoad(handler -> handler.loadFailed(
                new FriendlyException("down", FriendlyException.Severity.FAULT, null)));

        ResolverResult.LoadFailed failed = (ResolverResult.LoadFailed) adapter.resolve("entrada", requester);

        assertThat(failed.reason()).isEqualTo("falha temporária na fonte");
        assertThat(failed.retryable()).isTrue();
    }

    @Test
    void unexpectedFailureIsGenericAndRetryable() {
        AudioTrack broken = mock(AudioTrack.class);
        when(broken.getInfo()).thenThrow(new IllegalStateException("metadados inválidos"));
        stubLoad(handler -> handler.trackLoaded(broken));

        ResolverResult.LoadFailed failed = (ResolverResult.LoadFailed) adapter.resolve("entrada", requester);

        assertThat(failed.reason()).isEqualTo("falha inesperada ao carregar a entrada");
        assertThat(failed.retryable()).isTrue();
    }

    @Test
    void timeoutReturnsRetryableFailure() {
        when(manager.loadItemOrdered(any(), anyString(), any(AudioLoadResultHandler.class))).thenReturn(null);
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, Duration.ofMillis(30));

        ResolverResult.LoadFailed failed = (ResolverResult.LoadFailed) adapter.resolve("lentidão", requester);

        assertThat(failed.reason()).isEqualTo("tempo esgotado ao consultar a fonte");
        assertThat(failed.retryable()).isTrue();
    }

    @Test
    void spotifyUrlUsesMetadataLookupAndKeepsOriginalSource() {
        metadata = sourceUrl -> Optional.of("Artista - Música");
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata);
        AudioTrack audioTrack = audioTrack("yt1", "Artista - Música");
        stubLoad(handler -> handler.trackLoaded(audioTrack));

        ResolverResult result = adapter.resolve("https://open.spotify.com/track/abc", requester);

        verify(manager).loadItemOrdered(any(), eq("ytsearch:Artista - Música"), any());
        Track track = ((ResolverResult.ResolvedTrack) result).track();
        assertThat(track.source()).isEqualTo(SourceKind.SPOTIFY);
        assertThat(track.sourceUrl()).isEqualTo("https://open.spotify.com/track/abc");
    }

    @Test
    void tidalUrlUsesMetadataLookup() {
        metadata = sourceUrl -> Optional.of("Artista - Faixa Tidal");
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata);
        AudioTrack audioTrack = audioTrack("yt2", "Artista - Faixa Tidal");
        stubLoad(handler -> handler.trackLoaded(audioTrack));

        ResolverResult result = adapter.resolve("https://tidal.com/browse/track/1", requester);

        verify(manager).loadItemOrdered(any(), eq("ytsearch:Artista - Faixa Tidal"), any());
        Track track = ((ResolverResult.ResolvedTrack) result).track();
        assertThat(track.source()).isEqualTo(SourceKind.TIDAL);
        assertThat(track.sourceUrl()).isEqualTo("https://tidal.com/browse/track/1");
    }

    @Test
    void spotifyWithoutMetadataFails() {
        ResolverResult result = adapter.resolve("https://open.spotify.com/track/abc", requester);

        assertThat(result).isInstanceOf(ResolverResult.LoadFailed.class);
        ResolverResult.LoadFailed failed = (ResolverResult.LoadFailed) result;
        assertThat(failed.reason()).isEqualTo("não consegui identificar a faixa nessa fonte");
        assertThat(failed.retryable()).isTrue();
    }

    @Test
    void blankMetadataTitleFails() {
        metadata = sourceUrl -> Optional.of("   ");
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata);

        ResolverResult result = adapter.resolve("https://tidal.com/browse/track/1", requester);

        assertThat(result).isInstanceOf(ResolverResult.LoadFailed.class);
    }

    @Test
    void blankQueryIsNotFound() {
        assertThat(adapter.resolve("   ", requester)).isEqualTo(new ResolverResult.NotFound(""));
        assertThat(adapter.resolve(null, requester)).isEqualTo(new ResolverResult.NotFound(""));
    }

    @Test
    void queryIsTrimmedBeforeLoading() {
        AudioTrack audioTrack = audioTrack("v1", "Resultado");
        stubLoad(handler -> handler.trackLoaded(audioTrack));

        adapter.resolve("  artista  ", requester);

        verify(manager).loadItemOrdered(any(), eq("ytsearch:artista"), any());
    }

    private final List<List<String>> ytDlpCommands = new ArrayList<>();

    private YtDlpResolver ytDlp(String stdout) {
        return new YtDlpResolver(command -> {
            ytDlpCommands.add(List.copyOf(command));
            return new YtDlpResolver.CommandRunner.Result(0, stdout, "");
        }, List.of("yt-dlp"), List.of());
    }

    private static final String YTDLP_JSON = """
            {"id":"ytdlp1","title":"Título via yt-dlp","uploader":"Canal","duration":200,\
            "webpage_url":"https://www.youtube.com/watch?v=ytdlp1",\
            "url":"https://stream.example/audio","is_live":false}""";

    @Test
    void ytDlpStreamUrlIsPlayedDirectlyForYoutubeUrl() {
        AudioTrack streamTrack = mock(AudioTrack.class);
        when(manager.loadItemOrdered(any(), eq("https://stream.example/audio"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).trackLoaded(streamTrack);
                    return null;
                });
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=Yjfo2vzYVno", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        Track track = ((ResolverResult.ResolvedTrack) result).track();
        assertThat(track.title()).isEqualTo("Título via yt-dlp");
        assertThat(track.author()).isEqualTo("Canal");
        assertThat(track.source()).isEqualTo(SourceKind.YOUTUBE);
        assertThat(track.sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=Yjfo2vzYVno");
        assertThat(track.duration()).isEqualTo(Duration.ofSeconds(200));
        assertThat(registry.find(track.id())).contains(streamTrack);
        assertThat(ytDlpCommands).hasSize(1);
    }

    @Test
    void ytDlpReceivesYoutubeSearchForTextQuery() {
        AudioTrack streamTrack = mock(AudioTrack.class);
        when(manager.loadItemOrdered(any(), eq("https://stream.example/audio"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).trackLoaded(streamTrack);
                    return null;
                });
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(YTDLP_JSON));

        adapter.resolve("artista musica", requester);

        assertThat(ytDlpCommands.get(0)).contains("ytsearch1:artista musica");
    }

    @Test
    void ytDlpFallsBackToYoutubeSourceWhenItFails() {
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp("{\"title\":\"sem url\"}"));
        AudioTrack audioTrack = audioTrack("v1", "Fallback youtube");
        stubLoad(handler -> handler.trackLoaded(audioTrack));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=abc", requester);

        assertThat(((ResolverResult.ResolvedTrack) result).track().title()).isEqualTo("Fallback youtube");
        verify(manager).loadItemOrdered(any(), eq("https://www.youtube.com/watch?v=abc"), any());
    }

    @Test
    void ytDlpFallsBackWhenStreamUrlCannotBeLoaded() {
        stubLoad(handler -> handler.trackLoaded(audioTrack("v2", "Youtube normal")));
        when(manager.loadItemOrdered(any(), eq("https://stream.example/audio"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).noMatches();
                    return null;
                });
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=abc", requester);

        assertThat(((ResolverResult.ResolvedTrack) result).track().title()).isEqualTo("Youtube normal");
    }

    @Test
    void ytDlpIsSkippedForPlaylistUrls() {
        AudioTrack one = audioTrack("p1", "Faixa 1");
        BasicAudioPlaylist playlist = new BasicAudioPlaylist("playlist", List.of(one), null, false);
        stubLoad(handler -> handler.playlistLoaded(playlist));
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve(
                "https://www.youtube.com/playlist?list=PL123", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedPlaylist.class);
        assertThat(ytDlpCommands).isEmpty();
    }

    @Test
    void ytDlpIsSkippedForSoundcloudUrls() {
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(YTDLP_JSON));
        stubLoad(handler -> handler.trackLoaded(audioTrack("sc1", "SoundCloud track")));

        ResolverResult result = adapter.resolve("https://soundcloud.com/artist/track", requester);

        assertThat(((ResolverResult.ResolvedTrack) result).track().source()).isEqualTo(SourceKind.SOUNDCLOUD);
        assertThat(ytDlpCommands).isEmpty();
    }

    @Test
    void playlistQueryDetection() {
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/playlist?list=PL1")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/watch?v=x&list=RDx")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://soundcloud.com/a/sets/my-set")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://open.spotify.com/album/abc")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://youtu.be/abc")).isFalse();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("artista musica")).isFalse();
    }
}
