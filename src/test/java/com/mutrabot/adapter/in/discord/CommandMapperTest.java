package com.mutrabot.adapter.in.discord;

import com.mutrabot.domain.command.BotCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMapperTest {

    private final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    private final Guild guild = mock(Guild.class);
    private final User user = mock(User.class);

    private void stubCommon(String commandName) {
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("100");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("42");
        when(user.getEffectiveName()).thenReturn("Ana");
        when(event.getName()).thenReturn(commandName);
    }

    private void stubVoice() {
        Member member = mock(Member.class);
        GuildVoiceState voiceState = mock(GuildVoiceState.class);
        AudioChannelUnion channel = mock(AudioChannelUnion.class);
        when(event.getMember()).thenReturn(member);
        when(member.getVoiceState()).thenReturn(voiceState);
        when(voiceState.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn("777");
    }

    private void stubQuery(String value) {
        OptionMapping option = mock(OptionMapping.class);
        when(event.getOption("query")).thenReturn(option);
        when(option.getAsString()).thenReturn(value);
    }

    private void stubWindow(String value) {
        OptionMapping option = mock(OptionMapping.class);
        when(event.getOption("janela")).thenReturn(option);
        when(option.getAsString()).thenReturn(value);
    }

    @Test
    void mapsPlayWithQueryAndVoiceChannel() {
        stubCommon("play");
        stubVoice();
        stubQuery("minha musica");

        Optional<BotCommand> mapped = CommandMapper.map(event);

        assertThat(mapped).containsInstanceOf(BotCommand.PlayCmd.class);
        BotCommand.PlayCmd play = (BotCommand.PlayCmd) mapped.orElseThrow();
        assertThat(play.guild().value()).isEqualTo("100");
        assertThat(play.requester().displayName()).isEqualTo("Ana");
        assertThat(play.voiceChannel().value()).isEqualTo("777");
        assertThat(play.query()).isEqualTo("minha musica");
    }

    @Test
    void mapsControlCommands() {
        stubCommon("stop");
        assertThat(CommandMapper.map(event)).containsInstanceOf(BotCommand.StopCmd.class);
        stubCommon("resume");
        assertThat(CommandMapper.map(event)).containsInstanceOf(BotCommand.ResumeCmd.class);
        stubCommon("skip");
        assertThat(CommandMapper.map(event)).containsInstanceOf(BotCommand.SkipCmd.class);
        stubCommon("queue");
        assertThat(CommandMapper.map(event)).containsInstanceOf(BotCommand.QueueCmd.class);
        stubCommon("ping");
        assertThat(CommandMapper.map(event)).contains(new BotCommand.PingCmd());
        stubCommon("help");
        assertThat(CommandMapper.map(event)).contains(new BotCommand.HelpCmd());
    }

    @Test
    void unknownCommandReturnsEmpty() {
        stubCommon("unknown");

        assertThat(CommandMapper.map(event)).isEmpty();
    }

    @Test
    void directMessagesReturnEmpty() {
        when(event.getGuild()).thenReturn(null);

        assertThat(CommandMapper.map(event)).isEmpty();
    }

    @Test
    void missingMemberYieldsNullVoiceChannel() {
        stubCommon("play");
        stubQuery("x");
        when(event.getMember()).thenReturn(null);

        BotCommand.PlayCmd play = (BotCommand.PlayCmd) CommandMapper.map(event).orElseThrow();

        assertThat(play.voiceChannel()).isNull();
    }

    @Test
    void missingVoiceStateYieldsNullVoiceChannel() {
        stubCommon("play");
        stubQuery("x");
        Member member = mock(Member.class);
        when(event.getMember()).thenReturn(member);
        when(member.getVoiceState()).thenReturn(null);

        BotCommand.PlayCmd play = (BotCommand.PlayCmd) CommandMapper.map(event).orElseThrow();

        assertThat(play.voiceChannel()).isNull();
    }

    @Test
    void disconnectedFromVoiceYieldsNullVoiceChannel() {
        stubCommon("play");
        stubQuery("x");
        Member member = mock(Member.class);
        GuildVoiceState voiceState = mock(GuildVoiceState.class);
        when(event.getMember()).thenReturn(member);
        when(member.getVoiceState()).thenReturn(voiceState);
        when(voiceState.getChannel()).thenReturn(null);

        BotCommand.PlayCmd play = (BotCommand.PlayCmd) CommandMapper.map(event).orElseThrow();

        assertThat(play.voiceChannel()).isNull();
    }

    @Test
    void missingQueryOptionYieldsEmptyString() {
        stubCommon("play");
        when(event.getOption("query")).thenReturn(null);

        BotCommand.PlayCmd play = (BotCommand.PlayCmd) CommandMapper.map(event).orElseThrow();

        assertThat(play.query()).isEmpty();
    }

    @Test
    void mapsLivestreamJoinWithWindowAndVoiceChannel() {
        stubCommon("livestream-join");
        stubVoice();
        stubWindow("Notepad");

        BotCommand.LivestreamJoinCmd join =
                (BotCommand.LivestreamJoinCmd) CommandMapper.map(event).orElseThrow();

        assertThat(join.guild().value()).isEqualTo("100");
        assertThat(join.user().value()).isEqualTo("42");
        assertThat(join.voiceChannel().value()).isEqualTo("777");
        assertThat(join.window()).isEqualTo("Notepad");
    }

    @Test
    void mapsLivestreamLeave() {
        stubCommon("livestream-leave");

        assertThat(CommandMapper.map(event)).containsInstanceOf(BotCommand.LivestreamLeaveCmd.class);
    }

    @Test
    void livestreamJoinWithoutVoiceYieldsNullVoiceChannel() {
        stubCommon("livestream-join");
        stubWindow("Notepad");
        when(event.getMember()).thenReturn(null);

        BotCommand.LivestreamJoinCmd join =
                (BotCommand.LivestreamJoinCmd) CommandMapper.map(event).orElseThrow();

        assertThat(join.voiceChannel()).isNull();
    }

    @Test
    void missingWindowOptionYieldsEmptyString() {
        stubCommon("livestream-join");
        when(event.getOption("janela")).thenReturn(null);

        BotCommand.LivestreamJoinCmd join =
                (BotCommand.LivestreamJoinCmd) CommandMapper.map(event).orElseThrow();

        assertThat(join.window()).isEmpty();
    }
}
