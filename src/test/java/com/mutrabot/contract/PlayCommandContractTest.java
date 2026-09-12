package com.mutrabot.contract;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.PlayCommandService;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.domain.result.ResolverResult;
import com.mutrabot.support.FakePlaybackAdapter;
import com.mutrabot.support.FakeResponder;
import com.mutrabot.support.FakeResolver;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class PlayCommandContractTest {

    private final FakeResolver resolver = new FakeResolver();
    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final FakeResponder responder = new FakeResponder();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private ScheduledExecutorService scheduler;
    private PlayCommandService service;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        IdleDisconnectService idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        service = new PlayCommandService(resolver, playback, queues, idleDisconnect);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private BotCommand.PlayCmd play(String query) {
        return new BotCommand.PlayCmd(TestData.GUILD, TestData.requester("Ana"), new VoiceChannelId("777"), query);
    }

    @Test
    void emptyQueueStartsPlaybackWithContractMessage() {
        resolver.enqueue(new ResolverResult.ResolvedTrack(TestData.track("t1", "Minha Música")));

        service.play(play("link"), responder);

        assertThat(responder.lastFollowUp())
                .isEqualTo("▶ Tocando agora: **Minha Música** (YouTube) — pedido por Ana");
    }

    @Test
    void alreadyPlayingEnqueuesWithPositionAndContractMessage() {
        resolver.enqueue(new ResolverResult.ResolvedTrack(TestData.track("t1", "Primeira")));
        service.play(play("primeira"), responder);
        resolver.enqueue(new ResolverResult.ResolvedTrack(TestData.track("t2", "Segunda")));

        service.play(play("segunda"), responder);

        assertThat(responder.lastFollowUp())
                .isEqualTo("➕ Adicionado à fila (#1): **Segunda** — pedido por Ana");
    }

    @Test
    void playlistAnnouncesCountInOriginalOrder() {
        resolver.enqueue(new ResolverResult.ResolvedPlaylist(List.of(
                TestData.track("p1", "Faixa 1"),
                TestData.track("p2", "Faixa 2"),
                TestData.track("p3", "Faixa 3")), 3, 0));

        service.play(play("playlist"), responder);

        assertThat(responder.lastFollowUp()).isEqualTo(
                "➕ Playlist adicionada: **3** faixas (ordem original)\n"
                        + "▶ Tocando agora: **Faixa 1** — pedido por Ana");
        GuildQueue queue = queues.find(TestData.GUILD).orElseThrow();
        assertThat(queue.upcoming()).extracting(Track::title).containsExactly("Faixa 2", "Faixa 3");
    }

    @Test
    void noResultsMatchesContract() {
        resolver.enqueue(new ResolverResult.NotFound("banda inexistente"));

        service.play(play("banda inexistente"), responder);

        assertThat(responder.lastEphemeralFollowUp())
                .isEqualTo("🔍 Nada encontrado para \"banda inexistente\". Tente outro nome ou link.");
    }

    @Test
    void loadFailureMatchesContractAndKeepsQueue() {
        resolver.enqueue(new ResolverResult.ResolvedTrack(TestData.track("t1", "Primeira")));
        service.play(play("primeira"), responder);

        resolver.enqueue(new ResolverResult.LoadFailed("https://privado", "a fonte bloqueou ou restringiu o acesso", false));
        service.play(play("https://privado"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(
                "⚠️ Não consegui carregar \"https://privado\": a fonte bloqueou ou restringiu o acesso. "
                        + "A fila atual foi mantida.");
        assertThat(queues.find(TestData.GUILD).orElseThrow().current()).isPresent();
        assertThat(playback.played).hasSize(1);
    }

    @Test
    void playWithoutVoiceMatchesContract() {
        service.play(new BotCommand.PlayCmd(TestData.GUILD, TestData.requester("Ana"), null, "qualquer coisa"), responder);

        assertThat(responder.lastEphemeralFollowUp())
                .isEqualTo("🔇 Entre em um canal de voz primeiro e tente de novo.");
    }

    @Test
    void playlistTruncationAnnouncementMatchesContract() {
        List<Track> tracks = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> TestData.track("p" + i, "Faixa " + i))
                .toList();
        resolver.enqueue(new ResolverResult.ResolvedPlaylist(tracks, 101, 0));

        service.play(play("playlist"), responder);

        assertThat(responder.lastFollowUp())
                .contains("➕ Playlist adicionada: **100** faixas (ordem original), 1 ignoradas pelo limite de 100");
    }
}
