package com.mutrabot.bootstrap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ExecutorConfig {

    private static final ExecutorService COMMAND_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("cmd-", 0).factory());

    private static final ScheduledExecutorService IDLE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("idle-", 0).daemon(true).factory());

    private ExecutorConfig() {
    }

    public static ExecutorService commandExecutor() {
        return COMMAND_EXECUTOR;
    }

    public static ScheduledExecutorService idleScheduler() {
        return IDLE_SCHEDULER;
    }

    public static void shutdown() {
        COMMAND_EXECUTOR.shutdown();
        IDLE_SCHEDULER.shutdown();
    }
}
