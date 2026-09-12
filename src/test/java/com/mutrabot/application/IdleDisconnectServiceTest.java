package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.support.FakePlaybackAdapter;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IdleDisconnectServiceTest {

    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private final List<String> announcements = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    private IdleDisconnectService service;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        service = new IdleDisconnectService(
                scheduler, queues, playback,
                (guild, message) -> announcements.add(guild + ":" + message),
                Duration.ofMillis(40));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private void seedQueue() {
        queues.withLock(TestData.GUILD, queue -> {
            queue.enqueue(TestData.track("t1", "Primeira"));
            return null;
        });
    }

    @Test
    void scheduleThenCancelPreventsDisconnect() throws InterruptedException {
        queues.getOrCreate(TestData.GUILD);
        service.scheduleDisconnect(TestData.GUILD);
        assertThat(service.isScheduled(TestData.GUILD)).isTrue();

        service.cancel(TestData.GUILD);
        TimeUnit.MILLISECONDS.sleep(120);

        assertThat(service.isScheduled(TestData.GUILD)).isFalse();
        assertThat(playback.disconnected).isEmpty();
        assertThat(announcements).isEmpty();
    }

    @Test
    void cancelCancelsScheduledFuture() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        doReturn(future).when(mockScheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        IdleDisconnectService mockedService = new IdleDisconnectService(
                mockScheduler, queues, playback, (guild, message) -> {
                }, Duration.ofMinutes(5));
        mockedService.scheduleDisconnect(TestData.GUILD);

        mockedService.cancel(TestData.GUILD);

        verify(future).cancel(false);
    }

    @Test
    void scheduleDisconnectCancelsPreviousFuture() {
        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        doReturn(first, second).when(mockScheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        IdleDisconnectService mockedService = new IdleDisconnectService(
                mockScheduler, queues, playback, (guild, message) -> {
                }, Duration.ofMinutes(5));

        mockedService.scheduleDisconnect(TestData.GUILD);
        mockedService.scheduleDisconnect(TestData.GUILD);

        verify(first).cancel(false);
        verify(second, never()).cancel(false);
    }

    @Test
    void disconnectsOnlyWhenQueueIsIdle() throws InterruptedException {
        queues.getOrCreate(TestData.GUILD);
        service.scheduleDisconnect(TestData.GUILD);

        waitUntil(() -> !playback.disconnected.isEmpty() || !announcements.isEmpty());

        assertThat(playback.disconnected).containsExactly(TestData.GUILD);
        assertThat(announcements).hasSize(1);
        assertThat(announcements.get(0)).startsWith(TestData.GUILD.toString());
        assertThat(service.isScheduled(TestData.GUILD)).isFalse();
        assertThat(queues.find(TestData.GUILD)).isEmpty();
    }

    @Test
    void keepsBotConnectedWhenQueueHasTracks() throws InterruptedException {
        seedQueue();

        service.scheduleDisconnect(TestData.GUILD);
        TimeUnit.MILLISECONDS.sleep(150);

        assertThat(playback.disconnected).isEmpty();
        assertThat(announcements).isEmpty();
        assertThat(queues.find(TestData.GUILD)).isPresent();
    }

    @Test
    void ignoresTimerForRemovedQueue() throws InterruptedException {
        service.scheduleDisconnect(TestData.GUILD);
        queues.remove(TestData.GUILD);

        TimeUnit.MILLISECONDS.sleep(150);

        assertThat(playback.disconnected).isEmpty();
        assertThat(announcements).isEmpty();
    }

    @Test
    void reschedulingReplacesPreviousTimer() throws InterruptedException {
        queues.getOrCreate(TestData.GUILD);
        service.scheduleDisconnect(TestData.GUILD);
        service.scheduleDisconnect(TestData.GUILD);

        waitUntil(() -> !playback.disconnected.isEmpty());
        TimeUnit.MILLISECONDS.sleep(80);

        assertThat(playback.disconnected).containsExactly(TestData.GUILD);
        assertThat(announcements).hasSize(1);
    }

    @Test
    void cancelWhenNothingScheduledIsSafe() {
        service.cancel(TestData.GUILD);
        assertThat(service.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void defaultTimeoutIsFiveMinutes() {
        assertThat(IdleDisconnectService.DEFAULT_IDLE_TIMEOUT).isEqualTo(Duration.ofMinutes(5));
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }
}
