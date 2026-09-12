package com.mutrabot.application.port.out;

import com.mutrabot.domain.model.GuildId;

public interface GuildAnnouncerPort {
    void announce(GuildId guild, String message);
}
