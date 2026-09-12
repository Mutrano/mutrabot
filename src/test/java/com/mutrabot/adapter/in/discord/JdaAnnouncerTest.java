package com.mutrabot.adapter.in.discord;

import com.mutrabot.support.TestData;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaAnnouncerTest {

    private final JdaAnnouncer announcer = new JdaAnnouncer();

    @Test
    void announcesToLastRegisteredChannel() {
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(action);
        announcer.register(TestData.GUILD, channel);

        announcer.announce(TestData.GUILD, "Tocando agora");

        verify(channel).sendMessage("Tocando agora");
        verify(action).queue();
    }

    @Test
    void announceWithoutRegisteredChannelIsNoOp() {
        announcer.announce(TestData.GUILD, "nada");
    }

    @Test
    void registeringNullChannelIsIgnored() {
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        announcer.register(TestData.GUILD, null);

        announcer.announce(TestData.GUILD, "nada");

        verify(channel, never()).sendMessage(anyString());
    }

    @Test
    void registrationIsPerGuild() {
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(action);
        announcer.register(TestData.GUILD, channel);

        announcer.announce(TestData.OTHER_GUILD, "nada");

        verify(channel, never()).sendMessage(anyString());
    }
}
