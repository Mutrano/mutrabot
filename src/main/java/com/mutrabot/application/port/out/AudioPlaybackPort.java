package com.mutrabot.application.port.out;

import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.domain.model.VoiceSession;

import java.util.Optional;

public interface AudioPlaybackPort {
    Optional<VoiceSession> voiceSession(GuildId guild);

    void ensureConnected(GuildId guild, VoiceChannelId channel);

    void play(GuildId guild, Track track);

    void pause(GuildId guild);

    void resume(GuildId guild);

    void stop(GuildId guild);

    void disconnect(GuildId guild);
}
