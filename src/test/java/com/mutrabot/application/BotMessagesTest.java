package com.mutrabot.application;

import com.mutrabot.application.service.BotMessages;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BotMessagesTest {

    @Test
    void nowPlayingMatchesContract() {
        Track track = TestData.track("t1", "Música", TestData.requester("Ana"));
        assertThat(BotMessages.nowPlaying(track))
                .isEqualTo("▶ Tocando agora: **Música** (YouTube) — pedido por Ana");
    }

    @Test
    void addedToQueueMatchesContract() {
        Track track = TestData.track("t1", "Música", TestData.requester("Bia"));
        assertThat(BotMessages.addedToQueue(track, 3))
                .isEqualTo("➕ Adicionado à fila (#3): **Música** — pedido por Bia");
    }

    @Test
    void playlistAddedWithoutIgnoredTracks() {
        assertThat(BotMessages.playlistAdded(5, 0, Optional.empty()))
                .isEqualTo("➕ Playlist adicionada: **5** faixas (ordem original)");
    }

    @Test
    void playlistAddedWithIgnoredTracksAndStartedTrack() {
        Track first = TestData.track("p1", "Faixa 1", TestData.requester("Ana"));
        assertThat(BotMessages.playlistAdded(100, 25, Optional.of(first)))
                .isEqualTo("➕ Playlist adicionada: **100** faixas (ordem original), 25 ignoradas pelo limite de 100\n"
                        + "▶ Tocando agora: **Faixa 1** — pedido por Ana");
    }

    @Test
    void errorMessagesMatchContract() {
        assertThat(BotMessages.notFound("xyz")).isEqualTo("🔍 Nada encontrado para \"xyz\". Tente outro nome ou link.");
        assertThat(BotMessages.loadFailed("url", "acesso restrito"))
                .isEqualTo("⚠️ Não consegui carregar \"url\": acesso restrito. A fila atual foi mantida.");
        assertThat(BotMessages.voiceRequired()).isEqualTo("🔇 Entre em um canal de voz primeiro e tente de novo.");
        assertThat(BotMessages.queueEmpty()).isEqualTo("📭 A fila está vazia. Use /play para adicionar músicas.");
    }

    @Test
    void controlMessagesMatchContract() {
        Track track = TestData.track("t1", "Música");
        assertThat(BotMessages.paused(track)).isEqualTo("⏸ Pausado: **Música**. Use /resume para continuar.");
        assertThat(BotMessages.nothingPlaying()).isEqualTo("ℹ️ Nada tocando no momento.");
        assertThat(BotMessages.resumed(track)).isEqualTo("▶ Continuando: **Música**.");
        assertThat(BotMessages.nothingPaused()).isEqualTo("ℹ️ Nada pausado no momento.");
        assertThat(BotMessages.skippedTo(track))
                .isEqualTo("⏭ Pulado. ▶ Tocando agora: **Música** — pedido por Ana");
        assertThat(BotMessages.queueEndedAfterSkip()).isEqualTo("⏭ Pulado. 📭 A fila acabou.");
        assertThat(BotMessages.nothingToSkip()).isEqualTo("ℹ️ Nada para pular.");
        assertThat(BotMessages.queueEndedAnnouncement()).isEqualTo("📭 A fila acabou.");
    }

    @Test
    void miscMessages() {
        assertThat(BotMessages.emptyQuery()).isNotBlank();
        assertThat(BotMessages.idleDisconnected()).isNotBlank();
        assertThat(BotMessages.unknownCommand()).isEqualTo("❓ Comando não reconhecido.");
    }

    @Test
    void loginRequiredTrackFailureExplainsYouTubeVerification() {
        Track next = TestData.track("t2", "Próxima", TestData.requester("Bia"));
        assertThat(BotMessages.trackFailed("Restrita", com.mutrabot.domain.model.TrackFailureKind.LOGIN_REQUIRED, Optional.of(next)))
                .isEqualTo("""
                        🚫 Não consegui tocar **Restrita**: o YouTube exigiu login/verificação para esse vídeo (restrição do vídeo ou bloqueio anti-bot).
                        ▶ Tocando agora: **Próxima** — pedido por Bia""");
    }

    @Test
    void unavailableTrackFailureEndsQueueWhenNothingNext() {
        assertThat(BotMessages.trackFailed("Sumida", com.mutrabot.domain.model.TrackFailureKind.UNAVAILABLE, Optional.empty()))
                .isEqualTo("""
                        🚫 Não consegui tocar **Sumida**: a faixa está indisponível, privada ou removida.
                        📭 A fila acabou.""");
    }

    @Test
    void stuckAndUnknownFailuresHaveMessagesForMissingTitle() {
        assertThat(BotMessages.trackFailed(null, com.mutrabot.domain.model.TrackFailureKind.STUCK, Optional.empty()))
                .isEqualTo("""
                        ⚠️ essa faixa travou e foi pulada.
                        📭 A fila acabou.""");
        assertThat(BotMessages.trackFailed(null, com.mutrabot.domain.model.TrackFailureKind.UNKNOWN, Optional.empty()))
                .isEqualTo("""
                        ⚠️ Não consegui tocar essa faixa.
                        📭 A fila acabou.""");
    }

    @Test
    void sourceLabelsCoverAllKinds() {
        assertThat(BotMessages.sourceLabel(SourceKind.YOUTUBE)).isEqualTo("YouTube");
        assertThat(BotMessages.sourceLabel(SourceKind.SOUNDCLOUD)).isEqualTo("SoundCloud");
        assertThat(BotMessages.sourceLabel(SourceKind.SPOTIFY)).isEqualTo("Spotify");
        assertThat(BotMessages.sourceLabel(SourceKind.TIDAL)).isEqualTo("Tidal");
        assertThat(BotMessages.sourceLabel(SourceKind.SEARCH_RESULT)).isEqualTo("busca");
        assertThat(BotMessages.sourceLabel(SourceKind.HTTP)).isEqualTo("link");
    }

    @Test
    void durationLabels() {
        assertThat(BotMessages.durationLabel(TestData.liveTrack("t", "Live"))).isEqualTo("ao vivo");
        assertThat(BotMessages.durationLabel(trackWithDuration(Duration.ofSeconds(0)))).isEqualTo("ao vivo");
        assertThat(BotMessages.durationLabel(trackWithDuration(Duration.ofSeconds(65)))).isEqualTo("1:05");
        assertThat(BotMessages.durationLabel(trackWithDuration(Duration.ofSeconds(600)))).isEqualTo("10:00");
        assertThat(BotMessages.durationLabel(trackWithDuration(Duration.ofSeconds(3661)))).isEqualTo("1:01:01");
    }

    @Test
    void queueViewWithOnlyUpcomingTracks() {
        String view = BotMessages.queueView(
                Optional.empty(),
                List.of(TestData.track("t1", "Primeira")),
                20);
        assertThat(view).isEqualTo("📋 Próximas (1):\n1. Primeira — Ana");
    }

    @Test
    void queueViewPagesAndSummarizesRemainder() {
        List<Track> tracks = java.util.stream.IntStream.rangeClosed(1, 22)
                .mapToObj(i -> TestData.track("t" + i, "Faixa " + i))
                .toList();

        String view = BotMessages.queueView(Optional.empty(), tracks, 20);

        assertThat(view).contains("1. Faixa 1");
        assertThat(view).contains("20. Faixa 20");
        assertThat(view).doesNotContain("21. Faixa 21");
        assertThat(view).endsWith("... e mais 2");
    }

    @Test
    void queueViewWithoutUpcomingShowsOnlyCurrent() {
        String view = BotMessages.queueView(Optional.of(TestData.track("t1", "Atual")), List.of(), 20);
        assertThat(view).isEqualTo("🎵 Tocando agora: Atual — pedido por Ana [2:05]");
    }

    private Track trackWithDuration(Duration duration) {
        return new Track(
                new com.mutrabot.domain.model.TrackId("t"),
                "Música",
                "Artista",
                "https://example.com",
                duration,
                SourceKind.HTTP,
                TestData.requester("Ana"));
    }
}
