package com.mutrabot.domain.command;

import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.UserId;
import com.mutrabot.domain.model.VoiceChannelId;

public sealed interface BotCommand {

    record PlayCmd(GuildId guild, Requester requester, VoiceChannelId voiceChannel, String query)
            implements BotCommand {
        public PlayCmd {
            query = query == null ? "" : query;
        }
    }

    record StopCmd(GuildId guild, UserId user) implements BotCommand {
    }

    record ResumeCmd(GuildId guild, UserId user) implements BotCommand {
    }

    record SkipCmd(GuildId guild, UserId user) implements BotCommand {
    }

    record QueueCmd(GuildId guild, UserId user) implements BotCommand {
    }

    record PingCmd() implements BotCommand {
    }

    record HelpCmd() implements BotCommand {
    }
}
