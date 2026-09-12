package com.mutrabot.application.service;

import com.mutrabot.application.port.in.ListQueueUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.port.out.MusicQueueRepository;
import com.mutrabot.domain.command.BotCommand;

import java.util.Objects;

public final class ListQueueService implements ListQueueUseCase {

    public static final int PAGE_SIZE = 20;

    private final MusicQueueRepository queues;

    public ListQueueService(MusicQueueRepository queues) {
        this.queues = Objects.requireNonNull(queues, "queues");
    }

    @Override
    public void list(BotCommand.QueueCmd command, InteractionResponderPort responder) {
        String message = queues.withLock(command.guild(), queue -> queue.isEmpty()
                ? BotMessages.queueEmpty()
                : BotMessages.queueView(queue.current(), queue.upcoming(), PAGE_SIZE));
        responder.reply(message);
    }
}
