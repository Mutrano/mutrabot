package com.mutrabot.support;

import com.mutrabot.application.port.out.LivestreamControlPort;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.VoiceChannelId;

import java.util.ArrayList;
import java.util.List;

public final class FakeLivestreamControl implements LivestreamControlPort {

    public Result startResult = new Result.Ok(true);
    public Result stopResult = new Result.Ok(true);

    public final List<GuildId> startedGuilds = new ArrayList<>();
    public final List<VoiceChannelId> startedChannels = new ArrayList<>();
    public final List<String> startedWindows = new ArrayList<>();
    public final List<GuildId> stoppedGuilds = new ArrayList<>();

    @Override
    public Result start(GuildId guild, VoiceChannelId channel, String window) {
        startedGuilds.add(guild);
        startedChannels.add(channel);
        startedWindows.add(window);
        return startResult;
    }

    @Override
    public Result stop(GuildId guild) {
        stoppedGuilds.add(guild);
        return stopResult;
    }
}
