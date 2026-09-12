package com.mutrabot.application.service;

import com.mutrabot.application.port.in.PlayUseCase;
import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.application.port.out.TrackResolverPort;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.result.ResolverResult;

import java.util.Objects;

public final class PlayCommandService implements PlayUseCase {

    private final TrackResolverPort resolver;
    private final AudioPlaybackPort playback;
    private final MusicQueueRepository queues;
    private final IdleDisconnectService idleDisconnect;

    public PlayCommandService(
            TrackResolverPort resolver,
            AudioPlaybackPort playback,
            MusicQueueRepository queues,
            IdleDisconnectService idleDisconnect) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.queues = Objects.requireNonNull(queues, "queues");
        this.idleDisconnect = Objects.requireNonNull(idleDisconnect, "idleDisconnect");
    }

    @Override
    public void play(BotCommand.PlayCmd command, InteractionResponderPort responder) {
        String query = command.query().trim();
        if (query.isEmpty()) {
            responder.followUpEphemeral(BotMessages.emptyQuery());
            return;
        }
        if (command.voiceChannel() == null && playback.voiceSession(command.guild()).isEmpty()) {
            responder.followUpEphemeral(BotMessages.voiceRequired());
            return;
        }
        ResolverResult result = resolver.resolve(query, command.requester());
        switch (result) {
            case ResolverResult.ResolvedTrack resolved -> enqueueTrack(command, responder, resolved.track());
            case ResolverResult.ResolvedPlaylist playlist -> enqueuePlaylist(command, responder, playlist);
            case ResolverResult.NotFound notFound -> responder.followUpEphemeral(BotMessages.notFound(notFound.query()));
            case ResolverResult.LoadFailed failed -> responder.followUpEphemeral(
                    BotMessages.loadFailed(failed.query(), failed.reason()));
        }
    }

    private void enqueueTrack(BotCommand.PlayCmd command, InteractionResponderPort responder, Track track) {
        connectIfNeeded(command);
        GuildQueue.EnqueueResult result = queues.withLock(command.guild(), queue -> queue.enqueue(track));
        idleDisconnect.cancel(command.guild());
        switch (result) {
            case GuildQueue.EnqueueResult.Started started -> {
                playback.play(command.guild(), started.track());
                responder.followUp(BotMessages.nowPlaying(started.track()));
            }
            case GuildQueue.EnqueueResult.Queued queued -> responder.followUp(
                    BotMessages.addedToQueue(queued.track(), queued.position()));
        }
    }

    private void enqueuePlaylist(
            BotCommand.PlayCmd command, InteractionResponderPort responder, ResolverResult.ResolvedPlaylist playlist) {
        connectIfNeeded(command);
        GuildQueue.PlaylistResult result = queues.withLock(
                command.guild(), queue -> queue.enqueuePlaylist(playlist.tracks()));
        idleDisconnect.cancel(command.guild());
        result.startedNowOptional().ifPresent(track -> playback.play(command.guild(), track));
        responder.followUp(BotMessages.playlistAdded(
                result.added(), result.ignored(), result.startedNowOptional()));
    }

    private void connectIfNeeded(BotCommand.PlayCmd command) {
        if (command.voiceChannel() != null) {
            playback.ensureConnected(command.guild(), command.voiceChannel());
        }
    }
}
