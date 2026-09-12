package com.mutrabot.adapter.out.audio;

import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.domain.model.VoiceSession;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LavaplayerPlaybackAdapter implements AudioPlaybackPort {

    private final AudioPlayerManager manager;
    private final Supplier<JDA> jda;
    private final TrackAudioRegistry registry;
    private final ConcurrentHashMap<String, GuildPlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VoiceSession> sessions = new ConcurrentHashMap<>();
    private volatile Consumer<GuildId> trackFinishedListener = guild -> {
    };

    public LavaplayerPlaybackAdapter(AudioPlayerManager manager, Supplier<JDA> jda, TrackAudioRegistry registry) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.jda = Objects.requireNonNull(jda, "jda");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void setTrackFinishedListener(Consumer<GuildId> listener) {
        this.trackFinishedListener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public Optional<VoiceSession> voiceSession(GuildId guild) {
        Guild voiceGuild = guild(guild);
        if (voiceGuild == null) {
            return Optional.empty();
        }
        AudioManager audioManager = voiceGuild.getAudioManager();
        if (!audioManager.isConnected()) {
            return Optional.empty();
        }
        AudioChannelUnion channel = audioManager.getConnectedChannel();
        if (channel == null) {
            return Optional.empty();
        }
        VoiceChannelId channelId = new VoiceChannelId(channel.getId());
        Instant now = Instant.now();
        VoiceSession session = sessions.compute(
                guild.value(),
                (id, existing) -> existing == null || !existing.channel().equals(channelId)
                        ? new VoiceSession(guild, channelId, now, now)
                        : existing);
        return Optional.of(session);
    }

    @Override
    public void ensureConnected(GuildId guild, VoiceChannelId channel) {
        Guild voiceGuild = guild(guild);
        if (voiceGuild == null) {
            return;
        }
        AudioManager audioManager = voiceGuild.getAudioManager();
        AudioChannelUnion connected = audioManager.getConnectedChannel();
        if (audioManager.isConnected() && connected != null && connected.getId().equals(channel.value())) {
            return;
        }
        VoiceChannel voiceChannel = jda.get().getVoiceChannelById(channel.value());
        if (voiceChannel == null) {
            return;
        }
        audioManager.setSendingHandler(playerFor(guild).handler());
        audioManager.openAudioConnection(voiceChannel);
        Instant now = Instant.now();
        sessions.put(guild.value(), new VoiceSession(guild, channel, now, now));
    }

    @Override
    public void play(GuildId guild, Track track) {
        AudioTrack audioTrack = registry.find(track.id()).orElse(null);
        if (audioTrack == null) {
            return;
        }
        GuildPlayer guildPlayer = playerFor(guild);
        Guild voiceGuild = guild(guild);
        if (voiceGuild != null) {
            voiceGuild.getAudioManager().setSendingHandler(guildPlayer.handler());
        }
        guildPlayer.player().setPaused(false);
        guildPlayer.player().playTrack(audioTrack);
        touch(guild);
    }

    @Override
    public void pause(GuildId guild) {
        withPlayer(guild, player -> player.setPaused(true));
    }

    @Override
    public void resume(GuildId guild) {
        withPlayer(guild, player -> player.setPaused(false));
    }

    @Override
    public void stop(GuildId guild) {
        withPlayer(guild, AudioPlayer::stopTrack);
    }

    @Override
    public void disconnect(GuildId guild) {
        Guild voiceGuild = guild(guild);
        if (voiceGuild != null) {
            voiceGuild.getAudioManager().closeAudioConnection();
        }
        sessions.remove(guild.value());
        players.remove(guild.value());
    }

    private void touch(GuildId guild) {
        sessions.computeIfPresent(guild.value(), (id, session) -> new VoiceSession(
                session.guild(), session.channel(), session.connectedAt(), Instant.now()));
    }

    private void withPlayer(GuildId guild, Consumer<AudioPlayer> action) {
        GuildPlayer guildPlayer = players.get(guild.value());
        if (guildPlayer != null) {
            action.accept(guildPlayer.player());
        }
    }

    private GuildPlayer playerFor(GuildId guild) {
        return players.computeIfAbsent(guild.value(), id -> createPlayer(guild));
    }

    private GuildPlayer createPlayer(GuildId guild) {
        AudioPlayer player = manager.createPlayer();
        player.addListener(event -> {
            if (event instanceof TrackEndEvent end && end.endReason == AudioTrackEndReason.FINISHED) {
                trackFinishedListener.accept(guild);
            }
        });
        return new GuildPlayer(player, new AudioPlayerSendHandler(player));
    }

    private Guild guild(GuildId guild) {
        return jda.get().getGuildById(guild.value());
    }

    private record GuildPlayer(AudioPlayer player, AudioPlayerSendHandler handler) {
    }
}
