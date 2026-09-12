package com.mutrabot.support;

import com.mutrabot.application.port.out.AudioPlaybackPort;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.domain.model.VoiceSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FakePlaybackAdapter implements AudioPlaybackPort {

    public final List<Track> played = new ArrayList<>();
    public final List<GuildId> paused = new ArrayList<>();
    public final List<GuildId> resumed = new ArrayList<>();
    public final List<GuildId> stopped = new ArrayList<>();
    public final List<GuildId> disconnected = new ArrayList<>();
    public final List<VoiceChannelId> connectedTo = new ArrayList<>();

    public VoiceChannelId currentChannel;

    @Override
    public Optional<VoiceSession> voiceSession(GuildId guild) {
        if (currentChannel == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return Optional.of(new VoiceSession(guild, currentChannel, now, now));
    }

    @Override
    public void ensureConnected(GuildId guild, VoiceChannelId channel) {
        currentChannel = channel;
        connectedTo.add(channel);
    }

    @Override
    public void play(GuildId guild, Track track) {
        played.add(track);
    }

    @Override
    public void pause(GuildId guild) {
        paused.add(guild);
    }

    @Override
    public void resume(GuildId guild) {
        resumed.add(guild);
    }

    @Override
    public void stop(GuildId guild) {
        stopped.add(guild);
    }

    @Override
    public void disconnect(GuildId guild) {
        disconnected.add(guild);
        currentChannel = null;
    }

    public Track lastPlayed() {
        return played.isEmpty() ? null : played.get(played.size() - 1);
    }
}
