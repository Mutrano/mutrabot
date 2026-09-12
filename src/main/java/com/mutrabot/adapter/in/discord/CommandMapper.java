package com.mutrabot.adapter.in.discord;

import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.UserId;
import com.mutrabot.domain.model.VoiceChannelId;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public final class CommandMapper {

    private CommandMapper() {
    }

    public static Optional<BotCommand> map(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return Optional.empty();
        }
        GuildId guildId = new GuildId(guild.getId());
        User user = event.getUser();
        UserId userId = new UserId(user.getId());
        Requester requester = new Requester(userId, user.getEffectiveName());
        return switch (event.getName()) {
            case "play" -> Optional.of(
                    new BotCommand.PlayCmd(guildId, requester, voiceChannelOf(event), queryOf(event)));
            case "stop" -> Optional.of(new BotCommand.StopCmd(guildId, userId));
            case "resume" -> Optional.of(new BotCommand.ResumeCmd(guildId, userId));
            case "skip" -> Optional.of(new BotCommand.SkipCmd(guildId, userId));
            case "queue" -> Optional.of(new BotCommand.QueueCmd(guildId, userId));
            case "ping" -> Optional.of(new BotCommand.PingCmd());
            case "help" -> Optional.of(new BotCommand.HelpCmd());
            default -> Optional.empty();
        };
    }

    static String queryOf(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("query");
        return option == null ? "" : option.getAsString();
    }

    static VoiceChannelId voiceChannelOf(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return null;
        }
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null) {
            return null;
        }
        AudioChannelUnion channel = voiceState.getChannel();
        return channel == null ? null : new VoiceChannelId(channel.getId());
    }
}
