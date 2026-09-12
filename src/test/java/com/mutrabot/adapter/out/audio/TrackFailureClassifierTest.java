package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.TrackFailureKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackFailureClassifierTest {

    @Test
    void detectsLoginAndAgeRestrictions() {
        assertThat(TrackFailureClassifier.classify("This video requires login."))
                .isEqualTo(TrackFailureKind.LOGIN_REQUIRED);
        assertThat(TrackFailureClassifier.classify("Sign in to confirm you're not a bot"))
                .isEqualTo(TrackFailureKind.LOGIN_REQUIRED);
        assertThat(TrackFailureClassifier.classify("AGE-RESTRICTED video"))
                .isEqualTo(TrackFailureKind.LOGIN_REQUIRED);
        assertThat(TrackFailureClassifier.classify("Please confirm your age"))
                .isEqualTo(TrackFailureKind.LOGIN_REQUIRED);
    }

    @Test
    void detectsUnavailableVideos() {
        assertThat(TrackFailureClassifier.classify("Video unavailable"))
                .isEqualTo(TrackFailureKind.UNAVAILABLE);
        assertThat(TrackFailureClassifier.classify("This video is not available in your country"))
                .isEqualTo(TrackFailureKind.UNAVAILABLE);
        assertThat(TrackFailureClassifier.classify("Private video"))
                .isEqualTo(TrackFailureKind.UNAVAILABLE);
        assertThat(TrackFailureClassifier.classify("This video was removed"))
                .isEqualTo(TrackFailureKind.UNAVAILABLE);
    }

    @Test
    void fallsBackToUnknown() {
        assertThat(TrackFailureClassifier.classify("Some weird message"))
                .isEqualTo(TrackFailureKind.UNKNOWN);
        assertThat(TrackFailureClassifier.classify("loading failed"))
                .isEqualTo(TrackFailureKind.UNKNOWN);
        assertThat(TrackFailureClassifier.classify(null))
                .isEqualTo(TrackFailureKind.UNKNOWN);
    }
}
