package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.application.service.ListQueueService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListQueueServiceTest {

    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private final FakeResponder responder = new FakeResponder();
    private ListQueueService service;

    @BeforeEach
    void setUp() {
        service = new ListQueueService(queues);
    }

    private BotCommand.QueueCmd queueCommand() {
        return new BotCommand.QueueCmd(TestData.GUILD, TestData.USER);
    }

    private void seed(List<Track> tracks) {
        queues.withLock(TestData.GUILD, queue -> {
            tracks.forEach(queue::enqueue);
            return null;
        });
    }

    @Test
    void emptyQueueReportsExplicitlyEmptyMessage() {
        service.list(queueCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo(BotMessages.queueEmpty());
    }

    @Test
    void listsCurrentTrackAndUpcomingWithRequesters() {
        seed(List.of(
                TestData.track("t1", "Primeira", TestData.requester("Ana")),
                TestData.track("t2", "Segunda", TestData.otherRequester("Bia")),
                TestData.track("t3", "Terceira", TestData.requester("Ana"))));

        service.list(queueCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo("""
                🎵 Tocando agora: Primeira — pedido por Ana [2:05]
                📋 Próximas (2):
                1. Segunda — Bia
                2. Terceira — Ana""");
    }

    @Test
    void showsLiveDurationForStreams() {
        seed(List.of(TestData.liveTrack("t1", "Ao vivo")));

        service.list(queueCommand(), responder);

        assertThat(responder.lastReply()).isEqualTo("🎵 Tocando agora: Ao vivo — pedido por Ana [ao vivo]");
    }

    @Test
    void doesNotShowUpcomingSectionWhenQueueIsOnlyCurrentTrack() {
        seed(List.of(TestData.track("t1", "Única")));

        service.list(queueCommand(), responder);

        assertThat(responder.lastReply()).doesNotContain("Próximas");
    }

    @Test
    void paginatesBeyondTwentyUpcomingTracks() {
        List<Track> tracks = new ArrayList<>();
        tracks.add(TestData.track("t0", "Atual"));
        for (int i = 1; i <= 25; i++) {
            tracks.add(TestData.track("t" + i, "Faixa " + i));
        }
        seed(tracks);

        service.list(queueCommand(), responder);

        String message = responder.lastReply();
        assertThat(message).contains("📋 Próximas (25):");
        assertThat(message).contains("20. Faixa 20");
        assertThat(message).doesNotContain("21. Faixa 21");
        assertThat(message).contains("... e mais 5");
    }

    @Test
    void exactlyTwentyUpcomingTracksAreAllShown() {
        List<Track> tracks = new ArrayList<>();
        tracks.add(TestData.track("t0", "Atual"));
        for (int i = 1; i <= 20; i++) {
            tracks.add(TestData.track("t" + i, "Faixa " + i));
        }
        seed(tracks);

        service.list(queueCommand(), responder);

        String message = responder.lastReply();
        assertThat(message).contains("20. Faixa 20");
        assertThat(message).doesNotContain("... e mais");
    }

    @Test
    void queueWithoutCurrentTrackShowsOnlyUpcoming() {
        seed(List.of(TestData.track("t1", "Primeira")));
        queues.withLock(TestData.GUILD, queue -> {
            queue.skip(java.time.Instant.now());
            queue.enqueue(TestData.track("t9", "Nove"));
            return null;
        });

        service.list(queueCommand(), responder);

        assertThat(responder.lastReply()).startsWith("🎵 Tocando agora: Nove");
    }

    @Test
    void pageSizeIsTwenty() {
        assertThat(ListQueueService.PAGE_SIZE).isEqualTo(20);
    }
}
