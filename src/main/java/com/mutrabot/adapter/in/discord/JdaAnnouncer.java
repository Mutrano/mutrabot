package com.mutrabot.adapter.in.discord;

import com.mutrabot.application.port.out.GuildAnnouncerPort;
import com.mutrabot.domain.model.GuildId;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class JdaAnnouncer implements GuildAnnouncerPort {

    private final ConcurrentHashMap<GuildId, MessageChannelUnion> channels = new ConcurrentHashMap<>();

    public void register(GuildId guild, MessageChannelUnion channel) {
        Objects.requireNonNull(guild, "guild");
        if (channel != null) {
            channels.put(guild, channel);
        }
    }

    @Override
    public void announce(GuildId guild, String message) {
        MessageChannelUnion channel = channels.get(guild);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }
}
