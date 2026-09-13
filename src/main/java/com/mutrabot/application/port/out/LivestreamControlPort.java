package com.mutrabot.application.port.out;

import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.VoiceChannelId;

public interface LivestreamControlPort {

    Result start(GuildId guild, VoiceChannelId channel, String window);

    Result stop(GuildId guild);

    enum FailureKind {
        WINDOW_NOT_FOUND,
        TRANSMITTER_UNAVAILABLE,
        CAPTURE_FAILED,
        UNKNOWN
    }

    sealed interface Result {

        record Ok(boolean changed) implements Result {
        }

        record Unavailable(String detail) implements Result {
        }

        record Failed(FailureKind kind, String detail) implements Result {
        }
    }
}
