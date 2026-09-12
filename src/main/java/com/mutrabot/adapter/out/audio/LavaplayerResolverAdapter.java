package com.mutrabot.adapter.out.audio;

import com.mutrabot.application.port.out.TrackResolverPort;
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

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class LavaplayerResolverAdapter implements TrackResolverPort {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final AudioPlayerManager manager;
    private final TrackAudioRegistry registry;
    private final MetadataLookup metadata;
    private final Duration timeout;
    private final YtDlpResolver ytDlp;

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager, TrackAudioRegistry registry, MetadataLookup metadata) {
        this(manager, registry, metadata, DEFAULT_TIMEOUT, YtDlpResolver.disabled());
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager, TrackAudioRegistry registry, MetadataLookup metadata, Duration timeout) {
        this(manager, registry, metadata, timeout, YtDlpResolver.disabled());
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager,
            TrackAudioRegistry registry,
            MetadataLookup metadata,
            YtDlpResolver ytDlp) {
        this(manager, registry, metadata, DEFAULT_TIMEOUT, ytDlp);
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager,
            TrackAudioRegistry registry,
            MetadataLookup metadata,
            Duration timeout,
            YtDlpResolver ytDlp) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.ytDlp = Objects.requireNonNull(ytDlp, "ytDlp");
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
        if (!ytDlp.available() || isPlaylistQuery(effectiveQuery)) {
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
        return loadStream(media.get(), requester, kind, sourceUrlOverride);
    }

    static boolean isPlaylistQuery(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        return query.contains("list=") && lower.contains("youtube.com")
                || lower.contains("/playlist")
                || lower.contains("/sets/")
                || lower.contains("/album/");
    }

    private ResolverResult loadStream(
            YtDlpMedia media, Requester requester, SourceKind kind, String sourceUrlOverride) {
        CompletableFuture<AudioTrack> future = new CompletableFuture<>();
        manager.loadItemOrdered(this, media.streamUrl(), new AudioLoadResultHandler() {
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
        AudioTrack audioTrack;
        try {
            audioTrack = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
        if (audioTrack == null) {
            return null;
        }
        TrackId trackId = new TrackId(media.id());
        Track track = new Track(
                trackId,
                media.title(),
                media.uploader(),
                sourceUrlOverride != null ? sourceUrlOverride : media.webpageUrl(),
                media.duration(),
                kind,
                requester);
        registry.register(trackId, audioTrack);
        return new ResolverResult.ResolvedTrack(track);
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
