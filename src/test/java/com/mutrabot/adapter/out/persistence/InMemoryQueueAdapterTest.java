package com.mutrabot.adapter.out.persistence;

import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryQueueAdapterTest {

    private final InMemoryQueueAdapter adapter = new InMemoryQueueAdapter();

    @Test
    void getOrCreateReturnsSameInstance() {
        GuildQueue first = adapter.getOrCreate(TestData.GUILD);
        GuildQueue second = adapter.getOrCreate(TestData.GUILD);

        assertThat(first).isSameAs(second);
        assertThat(first.guildId()).isEqualTo(TestData.GUILD);
    }

    @Test
    void isolatesQueuesPerGuild() {
        GuildQueue one = adapter.getOrCreate(TestData.GUILD);
        GuildQueue other = adapter.getOrCreate(TestData.OTHER_GUILD);

        assertThat(one).isNotSameAs(other);
        assertThat(adapter.find(TestData.GUILD)).contains(one);
        assertThat(adapter.find(TestData.OTHER_GUILD)).contains(other);
    }

    @Test
    void findReturnsEmptyForUnknownGuild() {
        assertThat(adapter.find(TestData.GUILD)).isEmpty();
    }

    @Test
    void removeDropsQueue() {
        adapter.getOrCreate(TestData.GUILD);

        adapter.remove(TestData.GUILD);

        assertThat(adapter.find(TestData.GUILD)).isEmpty();
    }

    @Test
    void withLockAppliesActionAndReturnsValue() {
        AtomicInteger calls = new AtomicInteger();

        String result = adapter.withLock(TestData.GUILD, queue -> {
            calls.incrementAndGet();
            queue.enqueue(TestData.track("t1", "Primeira"));
            return queue.guildId().value();
        });

        assertThat(result).isEqualTo("100");
        assertThat(calls).hasValue(1);
        assertThat(adapter.find(TestData.GUILD).orElseThrow().current()).isPresent();
    }

    @Test
    void withLockIsReentrant() {
        String result = adapter.withLock(TestData.GUILD, outer -> adapter.withLock(TestData.GUILD, inner -> {
            assertThat(inner).isSameAs(outer);
            return "ok";
        }));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void withLockCreatesQueueOnDemand() {
        assertThat(adapter.find(TestData.GUILD)).isEmpty();

        Boolean empty = adapter.withLock(TestData.GUILD, GuildQueue::isEmpty);

        assertThat(empty).isTrue();
        assertThat(adapter.find(TestData.GUILD)).isPresent();
    }

    @Test
    void propagatesActionFailureAndReleasesLock() {
        assertThatThrownBy(() -> adapter.withLock(TestData.GUILD, queue -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        adapter.withLock(TestData.GUILD, queue -> {
            assertThat(queue).isNotNull();
            return null;
        });
    }
}
