package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.TrackFailureKind;

import java.util.Locale;

public final class TrackFailureClassifier {

    private TrackFailureClassifier() {
    }

    public static TrackFailureKind classify(String message) {
        if (message == null) {
            return TrackFailureKind.UNKNOWN;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("requires login")
                || lower.contains("sign in")
                || lower.contains("age-restricted")
                || lower.contains("age restricted")
                || lower.contains("confirm your age")) {
            return TrackFailureKind.RESTRICTED;
        }
        if (lower.contains("unavailable")
                || lower.contains("not available")
                || lower.contains("private")
                || lower.contains("removed")
                || lower.contains("deleted")) {
            return TrackFailureKind.UNAVAILABLE;
        }
        return TrackFailureKind.UNKNOWN;
    }
}
