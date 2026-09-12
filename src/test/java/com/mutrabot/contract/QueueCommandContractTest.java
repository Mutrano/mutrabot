package com.mutrabot.contract;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.ListQueueService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueueCommandContractTest {

    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private final FakeResponder responder = new FakeResponder();
    private ListQueueService service;

    @BeforeEach
    void setUp() {
        service = new ListQueueService(queues);
    }

    @Test
    void queueListsCurrentAndUpcomingWithRequestersPerContract() {
        queues.withLock(TestData.GUILD, queue -> {
            queue.enqueue(TestData.track("t1", "Primeira", TestData.requester("Ana")));
            queue.enqueue(TestData.track("t2", "Segunda", TestData.otherRequester("Bia")));
            queue.enqueue(TestData.track("t3", "Terceira", TestData.requester("Ana")));
            return null;
        });

        service.list(new BotCommand.QueueCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo("""
                🎵 Tocando agora: Primeira — pedido por Ana [2:05]
                📋 Próximas (2):
                1. Segunda — Bia
                2. Terceira — Ana""");
    }

    @Test
    void emptyQueueMatchesContract() {
        service.list(new BotCommand.QueueCmd(TestData.GUILD, TestData.USER), responder);

        assertThat(responder.lastReply()).isEqualTo("📭 A fila está vazia. Use /play para adicionar músicas.");
    }

    @Test
    void queuePaginationMatchesContract() {
        queues.withLock(TestData.GUILD, queue -> {
            queue.enqueue(TestData.track("t0", "Atual"));
            for (int i = 1; i <= 21; i++) {
                queue.enqueue(TestData.track("t" + i, "Faixa " + i));
            }
            return null;
        });

        service.list(new BotCommand.QueueCmd(TestData.GUILD, TestData.USER), responder);

        String message = responder.lastReply();
        assertThat(message).contains("📋 Próximas (21):");
        assertThat(message).contains("20. Faixa 20");
        assertThat(message).doesNotContain("21. Faixa 21");
        assertThat(message).endsWith("... e mais 1");
        assertThat(List.of(message.split("\n"))).hasSize(23);
    }
}
