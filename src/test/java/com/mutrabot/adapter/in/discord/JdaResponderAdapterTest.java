package com.mutrabot.adapter.in.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaResponderAdapterTest {

    private final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    private final ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
    private final InteractionHook hook = mock(InteractionHook.class);
    private final WebhookMessageCreateAction<Message> messageAction = mock(WebhookMessageCreateAction.class);

    private JdaResponderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JdaResponderAdapter(event);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(true)).thenReturn(replyAction);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.setEphemeral(true)).thenReturn(messageAction);
    }

    @Test
    void replySendsPlainMessage() {
        adapter.reply("olá");

        verify(event).reply("olá");
        verify(replyAction).queue();
    }

    @Test
    void replyEphemeralMarksMessageEphemeral() {
        adapter.replyEphemeral("erro");

        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    void followUpUsesInteractionHook() {
        adapter.followUp("resultado");

        verify(hook).sendMessage("resultado");
        verify(messageAction).queue();
    }

    @Test
    void followUpEphemeralUsesHookAndMarksEphemeral() {
        adapter.followUpEphemeral("erro após defer");

        verify(hook).sendMessage("erro após defer");
        verify(messageAction).setEphemeral(true);
        verify(messageAction).queue();
    }
}
