package com.mutrabot.adapter.out.audio;

import com.mutrabot.application.port.out.TrackResolverPort;
import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackId;
import com.mutrabot.domain.result.ResolverResult;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class LavaplayerResolverAdapter implements TrackResolverPort {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String YTDLP_REQUIRED = "o resolvedor de YouTube (yt-dlp) não está disponível";

    private final AudioPlayerManager manager;
    private final TrackAudioRegistry registry;
    private final MetadataLookup metadata;
    private final Duration timeout;
    private final YtDlpResolver ytDlp;
    private final Path downloadDirectory;

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager, TrackAudioRegistry registry, MetadataLookup metadata) {
        this(manager, registry, metadata, DEFAULT_TIMEOUT, YtDlpResolver.disabled(), defaultDownloadDirectory());
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager, TrackAudioRegistry registry, MetadataLookup metadata, Duration timeout) {
        this(manager, registry, metadata, timeout, YtDlpResolver.disabled(), defaultDownloadDirectory());
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager,
            TrackAudioRegistry registry,
            MetadataLookup metadata,
            YtDlpResolver ytDlp) {
        this(manager, registry, metadata, DEFAULT_TIMEOUT, ytDlp, defaultDownloadDirectory());
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager,
            TrackAudioRegistry registry,
            MetadataLookup metadata,
            Duration timeout,
            YtDlpResolver ytDlp) {
        this(manager, registry, metadata, timeout, ytDlp, defaultDownloadDirectory());
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager,
            TrackAudioRegistry registry,
            MetadataLookup metadata,
            Duration timeout,
            YtDlpResolver ytDlp,
            Path downloadDirectory) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.ytDlp = Objects.requireNonNull(ytDlp, "ytDlp");
        this.downloadDirectory = Objects.requireNonNull(downloadDirectory, "downloadDirectory");
    }

    static Path defaultDownloadDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir"), "mutrabot-audio");
    }

    public void clearCache() {
        File[] files = downloadDirectory.toFile().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                TrackAudioRegistry.deleteQuietly(file.toPath());
            }
        }
    }

    @Override
    public ResolverResult resolve(String query, Requester requester) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new ResolverResult.NotFound(trimmed);
        }
        Optional<SourceKind> nonStreamable = SearchQuery.nonStreamableSource(trimmed);
        if (nonStreamable.isPresent()) {
            Optional<String> title = metadata.lookupTitle(trimmed);
            if (title.isEmpty() || title.get().isBlank()) {
                return new ResolverResult.LoadFailed(
                        trimmed, "não consegui identificar a faixa nessa fonte", true);
            }
            ResolverResult viaYtDlp = resolveWithYtDlp(
                    SearchQuery.withSearchPrefix(title.get()), requester, nonStreamable.get(), trimmed);
            if (viaYtDlp != null) {
                return viaYtDlp;
            }
            return load(SearchQuery.withSearchPrefix(title.get()), trimmed, nonStreamable.get(), true, requester, trimmed);
        }
        if (SearchQuery.isUrl(trimmed)) {
            SourceKind kind = SourceKindResolver.detect(trimmed);
            if (kind == SourceKind.YOUTUBE) {
                return resolveYoutube(trimmed, requester);
            }
            ResolverResult viaYtDlp = resolveWithYtDlp(trimmed, requester, kind, trimmed);
            if (viaYtDlp != null) {
                return viaYtDlp;
            }
            return load(trimmed, null, kind, false, requester, trimmed);
        }
        ResolverResult viaYtDlp = resolveWithYtDlp(
                SearchQuery.withSearchPrefix(trimmed), requester, SourceKind.SEARCH_RESULT, null);
        if (viaYtDlp != null) {
            return viaYtDlp;
        }
        return load(SearchQuery.withSearchPrefix(trimmed), null, SourceKind.SEARCH_RESULT, true, requester, trimmed);
    }

    private ResolverResult resolveWithYtDlp(
            String effectiveQuery, Requester requester, SourceKind kind, String sourceUrlOverride) {
        if (!ytDlp.available()) {
            return null;
        }
        if (kind == SourceKind.SOUNDCLOUD || kind == SourceKind.HTTP) {
            return null;
        }
        String ytDlpQuery = effectiveQuery.startsWith(SearchQuery.SEARCH_PREFIX)
                ? "ytsearch1:" + effectiveQuery.substring(SearchQuery.SEARCH_PREFIX.length())
                : effectiveQuery;
        Optional<YtDlpMedia> media = ytDlp.resolve(ytDlpQuery);
        if (media.isEmpty()) {
            return null;
        }
        return loadBest(media.get(), requester, kind, sourceUrlOverride);
    }

    private ResolverResult resolveYoutube(String url, Requester requester) {
        if (isPlaylistQuery(url)) {
            return resolveYoutubePlaylist(url, requester);
        }
        if (!ytDlp.available()) {
            return new ResolverResult.LoadFailed(url, YTDLP_REQUIRED, true);
        }
        Optional<YtDlpMedia> media = ytDlp.resolve(url);
        if (media.isEmpty()) {
            return new ResolverResult.LoadFailed(url, "não consegui resolver esse vídeo pelo yt-dlp", true);
        }
        ResolverResult loaded = loadBest(media.get(), requester, SourceKind.YOUTUBE, url);
        if (loaded == null) {
            return new ResolverResult.LoadFailed(url, "não consegui carregar o áudio resolvido pelo yt-dlp", true);
        }
        return loaded;
    }

    private ResolverResult resolveYoutubePlaylist(String url, Requester requester) {
        if (!ytDlp.available()) {
            return new ResolverResult.LoadFailed(url, YTDLP_REQUIRED, true);
        }
        List<YtDlpMedia> entries = ytDlp.resolvePlaylist(
                canonicalPlaylistUrl(url), GuildQueue.MAX_PLAYLIST_TRACKS);
        List<Track> tracks = new ArrayList<>();
        for (YtDlpMedia entry : entries) {
            ResolverResult loaded = loadStream(entry, requester, SourceKind.YOUTUBE, entry.webpageUrl());
            if (loaded instanceof ResolverResult.ResolvedTrack resolved) {
                tracks.add(resolved.track());
            }
        }
        if (tracks.isEmpty()) {
            return new ResolverResult.NotFound(url);
        }
        return new ResolverResult.ResolvedPlaylist(tracks, tracks.size(), 0);
    }

    static boolean isPlaylistQuery(String query) {
        if (query == null) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("/playlist") || lower.contains("/sets/") || lower.contains("/album/")) {
            return true;
        }
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) {
            return false;
        }
        String list = queryParam(query, "list");
        return list != null && !list.toLowerCase(Locale.ROOT).startsWith("rd");
    }

    static String canonicalPlaylistUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) {
            return url;
        }
        String list = queryParam(url, "list");
        if (list == null || list.isBlank()) {
            return url;
        }
        return "https://www.youtube.com/playlist?list=" + list;
    }

    private static String queryParam(String url, String name) {
        String token = name + "=";
        int index = 0;
        while ((index = url.indexOf(token, index)) >= 0) {
            char before = index == 0 ? '?' : url.charAt(index - 1);
            if (before == '?' || before == '&') {
                int start = index + token.length();
                int end = url.indexOf('&', start);
                return end < 0 ? url.substring(start) : url.substring(start, end);
            }
            index += token.length();
        }
        return null;
    }

    private ResolverResult loadBest(
            YtDlpMedia media, Requester requester, SourceKind kind, String sourceUrlOverride) {
        if (!media.live()) {
            String fileBaseName = YtDlpResolver.safeFileBaseName(media.id())
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            Optional<Path> downloaded = ytDlp.download(media.streamUrl(), downloadDirectory, fileBaseName);
            if (downloaded.isPresent()) {
                ResolverResult local = loadLocalFile(media, downloaded.get(), requester, kind, sourceUrlOverride);
                if (local != null) {
                    return local;
                }
            }
        }
        return loadStream(media, requester, kind, sourceUrlOverride);
    }

    private ResolverResult loadStream(
            YtDlpMedia media, Requester requester, SourceKind kind, String sourceUrlOverride) {
        AudioTrack audioTrack = awaitTrack(media.streamUrl());
        if (audioTrack == null) {
            return null;
        }
        TrackId trackId = new TrackId(media.id());
        registry.register(trackId, audioTrack);
        return new ResolverResult.ResolvedTrack(trackOf(media, requester, kind, sourceUrlOverride));
    }

    private ResolverResult loadLocalFile(
            YtDlpMedia media, Path file, Requester requester, SourceKind kind, String sourceUrlOverride) {
        AudioTrack audioTrack = awaitTrack(file.toString());
        if (audioTrack == null) {
            TrackAudioRegistry.deleteQuietly(file);
            return null;
        }
        TrackId trackId = new TrackId(media.id());
        registry.register(trackId, audioTrack, file);
        return new ResolverResult.ResolvedTrack(trackOf(media, requester, kind, sourceUrlOverride));
    }

    private AudioTrack awaitTrack(String identifier) {
        CompletableFuture<AudioTrack> future = new CompletableFuture<>();
        manager.loadItemOrdered(this, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0));
            }

            @Override
            public void noMatches() {
                future.complete(null);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.complete(null);
            }
        });
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Track trackOf(
            YtDlpMedia media, Requester requester, SourceKind kind, String sourceUrlOverride) {
        return new Track(
                new TrackId(media.id()),
                media.title(),
                media.uploader(),
                sourceUrlOverride != null ? sourceUrlOverride : media.webpageUrl(),
                media.duration(),
                kind,
                requester);
    }

    private ResolverResult load(
            String effectiveQuery,
            String sourceUrlOverride,
            SourceKind sourceKind,
            boolean search,
            Requester requester,
            String originalQuery) {
        CompletableFuture<ResolverResult> future = new CompletableFuture<>();
        manager.loadItemOrdered(this, effectiveQuery, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                try {
                    future.complete(trackResult(track, requester, sourceKind, sourceUrlOverride));
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                try {
                    future.complete(playlistResult(
                            playlist, requester, sourceKind, sourceUrlOverride, search, originalQuery));
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void noMatches() {
                future.completeExceptionally(new NoMatchesException());
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return new ResolverResult.LoadFailed(originalQuery, "tempo esgotado ao consultar a fonte", true);
        } catch (ExecutionException e) {
            return toFailure(e.getCause(), originalQuery);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResolverResult.LoadFailed(originalQuery, "consulta interrompida", true);
        }
    }

    private ResolverResult trackResult(
            AudioTrack track, Requester requester, SourceKind sourceKind, String sourceUrlOverride) {
        return new ResolverResult.ResolvedTrack(
                TrackMapper.toTrack(track, requester, sourceKind, sourceUrlOverride, registry));
    }

    private ResolverResult playlistResult(
            AudioPlaylist playlist,
            Requester requester,
            SourceKind sourceKind,
            String sourceUrlOverride,
            boolean search,
            String originalQuery) {
        if (search || playlist.isSearchResult()) {
            AudioTrack selected = playlist.getSelectedTrack();
            AudioTrack picked = selected != null ? selected : playlist.getTracks().stream().findFirst().orElse(null);
            if (picked == null) {
                return new ResolverResult.NotFound(originalQuery);
            }
            return trackResult(picked, requester, sourceKind, sourceUrlOverride);
        }
        List<Track> tracks = playlist.getTracks().stream()
                .map(track -> TrackMapper.toTrack(track, requester, sourceKind, null, registry))
                .toList();
        if (tracks.isEmpty()) {
            return new ResolverResult.NotFound(originalQuery);
        }
        return new ResolverResult.ResolvedPlaylist(tracks, tracks.size(), 0);
    }

    private ResolverResult toFailure(Throwable cause, String originalQuery) {
        if (cause instanceof NoMatchesException) {
            return new ResolverResult.NotFound(originalQuery);
        }
        if (cause instanceof FriendlyException friendly) {
            return new ResolverResult.LoadFailed(
                    originalQuery,
                    friendlyReason(friendly.severity),
                    friendly.severity == FriendlyException.Severity.FAULT);
        }
        return new ResolverResult.LoadFailed(originalQuery, "falha inesperada ao carregar a entrada", true);
    }

    static String friendlyReason(FriendlyException.Severity severity) {
        return switch (severity) {
            case COMMON -> "a fonte não reconheceu ou não aceitou essa entrada";
            case SUSPICIOUS -> "a fonte bloqueou ou restringiu o acesso";
            case FAULT -> "falha temporária na fonte";
        };
    }

    private static final class NoMatchesException extends RuntimeException {
    }
}
