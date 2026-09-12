package com.mutrabot.application.port.in;

import com.mutrabot.domain.model.GuildId;

public interface TrackFinishedUseCase {
    void onTrackFinished(GuildId guild);
}
