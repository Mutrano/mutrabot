package com.mutrabot.adapter.in.discord;

import com.mutrabot.application.port.in.HelpUseCase;
import com.mutrabot.application.port.in.ListQueueUseCase;
import com.mutrabot.application.port.in.PingUseCase;
import com.mutrabot.application.port.in.PlayUseCase;
import com.mutrabot.application.port.in.ResumeUseCase;
import com.mutrabot.application.port.in.SkipUseCase;
import com.mutrabot.application.port.in.StopUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.TestData;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaCommandListenerTest {

    private final PlayUseCase playUseCase = mock(PlayUseCase.class);
    private final StopUseCase stopUseCase = mock(StopUseCase.class);
    private final ResumeUseCase resumeUseCase = mock(ResumeUseCase.class);
    private final SkipUseCase skipUseCase = mock(SkipUseCase.class);
    private final ListQueueUseCase listQueueUseCase = mock(ListQueueUseCase.class);
    private final PingUseCase pingUseCase = mock(PingUseCase.class);
    private final HelpUseCase helpUseCase = mock(HelpUseCase.class);
    private final JdaAnnouncer announcer = new JdaAnnouncer();
    private final FakeResponder responder = new FakeResponder();

    private JdaCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new JdaCommandListener(
                Runnable::run, playUseCase, stopUseCase, resumeUseCase, skipUseCase,
                listQueueUseCase, pingUseCase, helpUseCase, announcer);
    }

    @Test
    void dispatchRoutesEveryCommandType() {
        listener.dispatch(new BotCommand.PlayCmd(
                TestData.GUILD, TestData.requester("Ana"), new VoiceChannelId("7"), "q"), responder);
        listener.dispatch(new BotCommand.StopCmd(TestData.GUILD, TestData.USER), responder);
        listener.dispatch(new BotCommand.ResumeCmd(TestData.GUILD, TestData.USER), responder);
        listener.dispatch(new BotCommand.SkipCmd(TestData.GUILD, TestData.USER), responder);
        listener.dispatch(new BotCommand.QueueCmd(TestData.GUILD, TestData.USER), responder);
        listener.dispatch(new BotCommand.PingCmd(), responder);
        listener.dispatch(new BotCommand.HelpCmd(), responder);

        verify(playUseCase).play(any(BotCommand.PlayCmd.class), eq(responder));
        verify(stopUseCase).stop(any(BotCommand.StopCmd.class), eq(responder));
        verify(resumeUseCase).resume(any(BotCommand.ResumeCmd.class), eq(responder));
        verify(skipUseCase).skip(any(BotCommand.SkipCmd.class), eq(responder));
        verify(listQueueUseCase).list(any(BotCommand.QueueCmd.class), eq(responder));
        verify(pingUseCase).ping(any(BotCommand.PingCmd.class), eq(responder));
        verify(helpUseCase).help(any(BotCommand.HelpCmd.class), eq(responder));
    }

    @Test
    void playInteractionDefersThenDispatches() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        Member member = mock(Member.class);
        AudioChannelUnion channel = mock(AudioChannelUnion.class);
        MessageChannelUnion textChannel = mock(MessageChannelUnion.class);
        OptionMapping option = mock(OptionMapping.class);
        ReplyCallbackAction defer = mock(ReplyCallbackAction.class);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("100");
        when(event.getName()).thenReturn("play");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("42");
        when(user.getEffectiveName()).thenReturn("Ana");
        when(event.getMember()).thenReturn(member);
        when(member.getVoiceState()).thenReturn(null);
        when(event.getOption("query")).thenReturn(option);
        when(option.getAsString()).thenReturn("musica");
        when(event.getChannel()).thenReturn(textChannel);
        when(event.deferReply()).thenReturn(defer);

        listener.onSlashCommandInteraction(event);

        verify(defer).queue();
        verify(playUseCase).play(any(BotCommand.PlayCmd.class), any(InteractionResponderPort.class));
    }

    @Test
    void nonPlayInteractionDoesNotDefer() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("100");
        when(event.getName()).thenReturn("ping");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("42");
        when(user.getEffectiveName()).thenReturn("Ana");

        listener.onSlashCommandInteraction(event);

        verify(event, never()).deferReply();
        verify(pingUseCase).ping(any(BotCommand.PingCmd.class), any(InteractionResponderPort.class));
    }

    @Test
    void unknownCommandRepliesEphemeral() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.getGuild()).thenReturn(null);
        when(event.getName()).thenReturn("help");
        when(event.reply(BotMessages.unknownCommand())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);

        listener.onSlashCommandInteraction(event);

        verify(reply).setEphemeral(true);
        verify(reply).queue();
        verify(playUseCase, never()).play(any(), any());
        verify(pingUseCase, never()).ping(any(), any());
    }

    @Test
    void registersAnnouncementChannelFromInteraction() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        MessageChannelUnion textChannel = mock(MessageChannelUnion.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("100");
        when(event.getName()).thenReturn("ping");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("42");
        when(user.getEffectiveName()).thenReturn("Ana");
        when(event.getChannel()).thenReturn(textChannel);
        when(textChannel.sendMessage(anyString())).thenReturn(action);

        listener.onSlashCommandInteraction(event);
        announcer.announce(TestData.GUILD, "anúncio");

        verify(textChannel).sendMessage("anúncio");
        verify(action).queue();
    }
}
