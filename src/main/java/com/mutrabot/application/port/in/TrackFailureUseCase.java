package com.mutrabot.application.port.in;

import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.TrackFailureKind;

public interface TrackFailureUseCase {
    void onTrackFailed(GuildId guild, TrackFailureKind kind);
}
