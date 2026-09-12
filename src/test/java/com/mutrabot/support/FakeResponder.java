package com.mutrabot.support;

import com.mutrabot.application.port.out.InteractionResponderPort;

import java.util.ArrayList;
import java.util.List;

public final class FakeResponder implements InteractionResponderPort {

    public final List<String> replies = new ArrayList<>();
    public final List<String> ephemeralReplies = new ArrayList<>();
    public final List<String> followUps = new ArrayList<>();
    public final List<String> ephemeralFollowUps = new ArrayList<>();

    @Override
    public void reply(String message) {
        replies.add(message);
    }

    @Override
    public void replyEphemeral(String message) {
        ephemeralReplies.add(message);
    }

    @Override
    public void followUp(String message) {
        followUps.add(message);
    }

    @Override
    public void followUpEphemeral(String message) {
        ephemeralFollowUps.add(message);
    }

    public String lastReply() {
        return replies.isEmpty() ? null : replies.get(replies.size() - 1);
    }

    public String lastEphemeralReply() {
        return ephemeralReplies.isEmpty() ? null : ephemeralReplies.get(ephemeralReplies.size() - 1);
    }

    public String lastFollowUp() {
        return followUps.isEmpty() ? null : followUps.get(followUps.size() - 1);
    }

    public String lastEphemeralFollowUp() {
        return ephemeralFollowUps.isEmpty() ? null : ephemeralFollowUps.get(ephemeralFollowUps.size() - 1);
    }
}
