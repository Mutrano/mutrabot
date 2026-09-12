package com.mutrabot.adapter.out.audio;

import com.mutrabot.application.port.out.TrackResolverPort;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.result.ResolverResult;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.time.Duration;
import java.util.List;
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

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager, TrackAudioRegistry registry, MetadataLookup metadata) {
        this(manager, registry, metadata, DEFAULT_TIMEOUT);
    }

    public LavaplayerResolverAdapter(
            AudioPlayerManager manager, TrackAudioRegistry registry, MetadataLookup metadata, Duration timeout) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
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
            return load(SearchQuery.withSearchPrefix(title.get()), trimmed, nonStreamable.get(), true, requester, trimmed);
        }
        if (SearchQuery.isUrl(trimmed)) {
            return load(trimmed, null, SourceKindResolver.detect(trimmed), false, requester, trimmed);
        }
        return load(SearchQuery.withSearchPrefix(trimmed), null, SourceKind.SEARCH_RESULT, true, requester, trimmed);
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
