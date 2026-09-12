package com.mutrabot.application.port.out;

public interface InteractionResponderPort {
    void reply(String message);

    void replyEphemeral(String message);

    void followUp(String message);

    void followUpEphemeral(String message);
}
