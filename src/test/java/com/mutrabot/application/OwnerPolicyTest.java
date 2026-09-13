package com.mutrabot.application;

import com.mutrabot.application.service.OwnerPolicy;
import com.mutrabot.domain.model.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerPolicyTest {

    @Test
    void configuredOwnerIsAuthorized() {
        OwnerPolicy policy = OwnerPolicy.from("42");

        assertThat(policy.isConfigured()).isTrue();
        assertThat(policy.isOwner(new UserId("42"))).isTrue();
    }

    @Test
    void otherUserIsNotAuthorized() {
        OwnerPolicy policy = OwnerPolicy.from("42");

        assertThat(policy.isOwner(new UserId("7"))).isFalse();
    }

    @Test
    void trimsConfiguredId() {
        OwnerPolicy policy = OwnerPolicy.from("  42  ");

        assertThat(policy.isOwner(new UserId("42"))).isTrue();
    }

    @Test
    void blankIdDisablesPolicy() {
        OwnerPolicy policy = OwnerPolicy.from("   ");

        assertThat(policy.isConfigured()).isFalse();
        assertThat(policy.isOwner(new UserId("42"))).isFalse();
    }

    @Test
    void nullIdDisablesPolicy() {
        OwnerPolicy policy = OwnerPolicy.from(null);

        assertThat(policy.isConfigured()).isFalse();
    }
}
