package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackId;
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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    void youtubeUrlWithoutYtDlpFails() {
        ResolverResult result = adapter.resolve("https://youtu.be/vid", requester);

        assertThat(result).isInstanceOf(ResolverResult.LoadFailed.class);
        assertThat(((ResolverResult.LoadFailed) result).retryable()).isTrue();
    }

    @Test
    void youtubePlaylistResolvesEntriesInOrderThroughYtDlp() {
        String playlistJson = "{\"id\":\"a1\",\"title\":\"Faixa 1\",\"url\":\"https://stream.example/1\","
                + "\"webpage_url\":\"https://youtu.be/a1\"}\n"
                + "{\"id\":\"a2\",\"title\":\"Faixa 2\",\"url\":\"https://stream.example/2\","
                + "\"webpage_url\":\"https://youtu.be/a2\"}";
        when(manager.loadItemOrdered(any(), anyString(), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    String target = invocation.getArgument(1);
                    AudioLoadResultHandler handler = invocation.getArgument(2);
                    if (target.endsWith("/1")) {
                        handler.trackLoaded(audioTrack("a1", "Faixa 1"));
                    } else if (target.endsWith("/2")) {
                        handler.trackLoaded(audioTrack("a2", "Faixa 2"));
                    } else {
                        handler.noMatches();
                    }
                    return null;
                });
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(playlistJson));

        ResolverResult result = adapter.resolve("https://youtube.com/playlist?list=PL1", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedPlaylist.class);
        ResolverResult.ResolvedPlaylist resolved = (ResolverResult.ResolvedPlaylist) result;
        assertThat(resolved.tracks()).extracting(Track::title).containsExactly("Faixa 1", "Faixa 2");
        assertThat(resolved.tracks()).allMatch(track -> track.source() == SourceKind.YOUTUBE);
        assertThat(resolved.tracks()).allMatch(track -> track.requestedBy().equals(requester));
        assertThat(ytDlpCommands).hasSize(1);
        assertThat(ytDlpCommands.get(0)).contains("--yes-playlist");
    }

    @Test
    void emptyYoutubePlaylistIsNotFound() {
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(""));

        ResolverResult result = adapter.resolve("https://youtube.com/playlist?list=PL1", requester);

        assertThat(result).isEqualTo(new ResolverResult.NotFound("https://youtube.com/playlist?list=PL1"));
    }

    @Test
    void youtubePlaylistWithoutYtDlpFails() {
        ResolverResult result = adapter.resolve("https://youtube.com/playlist?list=PL1", requester);

        assertThat(result).isInstanceOf(ResolverResult.LoadFailed.class);
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

    @TempDir
    Path tempDir;

    private YtDlpResolver ytDlp(String stdout) {
        return ytDlp(stdout, true);
    }

    private YtDlpResolver ytDlp(String stdout, boolean downloadSucceeds) {
        return new YtDlpResolver(command -> {
            ytDlpCommands.add(List.copyOf(command));
            if (command.contains("-j")) {
                return new YtDlpResolver.CommandRunner.Result(0, stdout, "");
            }
            int outputIndex = command.indexOf("-o");
            if (outputIndex < 0 || !downloadSucceeds) {
                return new YtDlpResolver.CommandRunner.Result(1, "", "download falhou");
            }
            Path template = Path.of(command.get(outputIndex + 1));
            Path file = template.getParent().resolve(
                    template.getFileName().toString().replace("%(ext)s", "webm"));
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "audio");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new YtDlpResolver.CommandRunner.Result(0, "", "");
        }, List.of("yt-dlp"), List.of());
    }

    private LavaplayerResolverAdapter ytDlpAdapter(YtDlpResolver resolver) {
        return new LavaplayerResolverAdapter(
                manager, registry, metadata, LavaplayerResolverAdapter.DEFAULT_TIMEOUT, resolver, tempDir);
    }

    private void stubLocalTrack(AudioTrack audioTrack) {
        when(manager.loadItemOrdered(any(), argThat((String id) -> id != null && id.endsWith(".webm")), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).trackLoaded(audioTrack);
                    return null;
                });
    }

    private static final String YTDLP_JSON = """
            {"id":"ytdlp1","title":"Título via yt-dlp","uploader":"Canal","duration":200,\
            "webpage_url":"https://www.youtube.com/watch?v=ytdlp1",\
            "url":"https://stream.example/audio","is_live":false}""";

    private static final String YTDLP_LIVE_JSON = """
            {"id":"live1","title":"Ao vivo","uploader":"Canal","duration":0,\
            "webpage_url":"https://www.youtube.com/watch?v=live1",\
            "url":"https://stream.example/live.m3u8","is_live":true}""";

    @Test
    void ytDlpDownloadsFileBeforePlayingForYoutubeUrl() {
        AudioTrack localTrack = mock(AudioTrack.class);
        stubLocalTrack(localTrack);
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=Yjfo2vzYVno", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        Track track = ((ResolverResult.ResolvedTrack) result).track();
        assertThat(track.title()).isEqualTo("Título via yt-dlp");
        assertThat(track.author()).isEqualTo("Canal");
        assertThat(track.source()).isEqualTo(SourceKind.YOUTUBE);
        assertThat(track.sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=Yjfo2vzYVno");
        assertThat(track.duration()).isEqualTo(Duration.ofSeconds(200));
        assertThat(registry.find(track.id())).contains(localTrack);
        assertThat(ytDlpCommands).hasSize(2);
        assertThat(ytDlpCommands.get(1)).contains("--http-chunk-size", "10M");
        assertThat(ytDlpCommands.get(1).get(ytDlpCommands.get(1).size() - 1))
                .isEqualTo("https://stream.example/audio");
    }

    @Test
    void ytDlpPlaysSingleVideoForYoutubeWatchUrlWithRadioPlaylist() {
        AudioTrack localTrack = mock(AudioTrack.class);
        stubLocalTrack(localTrack);
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve(
                "https://www.youtube.com/watch?v=Yjfo2vzYVno&list=RDYjfo2vzYVno&start_radio=1", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        assertThat(ytDlpCommands).hasSize(2);
        assertThat(ytDlpCommands.get(0)).contains("--no-playlist");
    }

    @Test
    void ytDlpReceivesYoutubeSearchForTextQuery() {
        stubLocalTrack(mock(AudioTrack.class));
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));

        adapter.resolve("artista musica", requester);

        assertThat(ytDlpCommands.get(0)).contains("ytsearch1:artista musica");
    }

    @Test
    void ytDlpFailureForYoutubeIsLoadFailed() {
        adapter = new LavaplayerResolverAdapter(
                manager, registry, metadata, ytDlp("{\"title\":\"sem url\"}"));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=abc", requester);

        assertThat(result).isInstanceOf(ResolverResult.LoadFailed.class);
        assertThat(((ResolverResult.LoadFailed) result).retryable()).isTrue();
    }

    @Test
    void ytDlpStreamsWhenDownloadFails() {
        AudioTrack streamTrack = mock(AudioTrack.class);
        when(manager.loadItemOrdered(any(), eq("https://stream.example/audio"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).trackLoaded(streamTrack);
                    return null;
                });
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON, false));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=abc", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        assertThat(registry.find(new TrackId("ytdlp1"))).contains(streamTrack);
    }

    @Test
    void ytDlpStreamsLiveWithoutDownloading() {
        AudioTrack streamTrack = mock(AudioTrack.class);
        when(manager.loadItemOrdered(any(), eq("https://stream.example/live.m3u8"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).trackLoaded(streamTrack);
                    return null;
                });
        adapter = ytDlpAdapter(ytDlp(YTDLP_LIVE_JSON));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=live1", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        assertThat(ytDlpCommands).hasSize(1);
        assertThat(registry.find(new TrackId("live1"))).contains(streamTrack);
    }

    @Test
    void ytDlpFailureWhenDownloadedFileAndStreamCannotBeLoaded() {
        when(manager.loadItemOrdered(any(), argThat((String id) -> id != null && id.endsWith(".webm")), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).noMatches();
                    return null;
                });
        when(manager.loadItemOrdered(any(), eq("https://stream.example/audio"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).noMatches();
                    return null;
                });
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=abc", requester);

        assertThat(result).isInstanceOf(ResolverResult.LoadFailed.class);
    }

    @Test
    void ytDlpStreamsWhenDownloadedFileCannotBeLoaded() {
        AudioTrack streamTrack = mock(AudioTrack.class);
        when(manager.loadItemOrdered(any(), argThat((String id) -> id != null && id.endsWith(".webm")), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).noMatches();
                    return null;
                });
        when(manager.loadItemOrdered(any(), eq("https://stream.example/audio"), any(AudioLoadResultHandler.class)))
                .thenAnswer(invocation -> {
                    invocation.<AudioLoadResultHandler>getArgument(2).trackLoaded(streamTrack);
                    return null;
                });
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));

        ResolverResult result = adapter.resolve("https://www.youtube.com/watch?v=abc", requester);

        assertThat(result).isInstanceOf(ResolverResult.ResolvedTrack.class);
        assertThat(registry.find(new TrackId("ytdlp1"))).contains(streamTrack);
    }

    @Test
    void youtubePlaylistUrlUsesYtDlp() {
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(""));

        adapter.resolve("https://www.youtube.com/playlist?list=PL123", requester);

        assertThat(ytDlpCommands).hasSize(1);
        assertThat(ytDlpCommands.get(0)).contains("--yes-playlist");
    }

    @Test
    void youtubeWatchUrlWithRealPlaylistUsesCanonicalPlaylistUrl() {
        adapter = new LavaplayerResolverAdapter(manager, registry, metadata, ytDlp(""));

        adapter.resolve(
                "https://www.youtube.com/watch?v=fFFzj46lx7s&list=PLUZmdQJkcDxgt1weN69Kaa-1efoWBd3Ld", requester);

        assertThat(ytDlpCommands).hasSize(1);
        assertThat(ytDlpCommands.get(0))
                .contains("https://www.youtube.com/playlist?list=PLUZmdQJkcDxgt1weN69Kaa-1efoWBd3Ld");
    }

    @Test
    void ytDlpIsSkippedForSoundcloudUrls() {
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));
        stubLoad(handler -> handler.trackLoaded(audioTrack("sc1", "SoundCloud track")));

        ResolverResult result = adapter.resolve("https://soundcloud.com/artist/track", requester);

        assertThat(((ResolverResult.ResolvedTrack) result).track().source()).isEqualTo(SourceKind.SOUNDCLOUD);
        assertThat(ytDlpCommands).isEmpty();
    }

    @Test
    void clearCacheRemovesDownloadedFilesButKeepsDirectories() throws IOException {
        Path file = Files.writeString(tempDir.resolve("track.webm"), "audio");
        Path directory = Files.createDirectory(tempDir.resolve("sub"));
        adapter = ytDlpAdapter(ytDlp(YTDLP_JSON));

        adapter.clearCache();

        assertThat(file).doesNotExist();
        assertThat(directory).exists();
    }

    @Test
    void clearCacheIgnoresMissingDirectory() {
        adapter = new LavaplayerResolverAdapter(
                manager, registry, metadata, LavaplayerResolverAdapter.DEFAULT_TIMEOUT,
                YtDlpResolver.disabled(), tempDir.resolve("nao-existe"));

        adapter.clearCache();

        assertThat(tempDir.resolve("nao-existe")).doesNotExist();
    }

    @Test
    void playlistQueryDetection() {
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/playlist?list=PL1")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/watch?v=x&list=PL1")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/watch?v=x&list=RDx")).isFalse();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/watch?list=RDx")).isFalse();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/watch?v=Yjfo2vzYVno&list=RDYjfo2vzYVno&start_radio=1")).isFalse();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://www.youtube.com/watch?v=fFFzj46lx7s&list=PLUZmdQJkcDxgt1weN69Kaa-1efoWBd3Ld")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://soundcloud.com/a/sets/my-set")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://open.spotify.com/album/abc")).isTrue();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("https://youtu.be/abc")).isFalse();
        assertThat(LavaplayerResolverAdapter.isPlaylistQuery("artista musica")).isFalse();
    }
}
