package com.mutrabot.adapter.in.discord;

import com.mutrabot.application.port.out.InteractionResponderPort;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Objects;

public final class JdaResponderAdapter implements InteractionResponderPort {

    private final SlashCommandInteractionEvent event;

    public JdaResponderAdapter(SlashCommandInteractionEvent event) {
        this.event = Objects.requireNonNull(event, "event");
    }

    @Override
    public void reply(String message) {
        event.reply(message).queue();
    }

    @Override
    public void replyEphemeral(String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    @Override
    public void followUp(String message) {
        event.getHook().sendMessage(message).queue();
    }

    @Override
    public void followUpEphemeral(String message) {
        event.getHook().sendMessage(message).setEphemeral(true).queue();
    }
}
