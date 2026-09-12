package com.mutrabot.application;

import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.BotMessages;
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

class PlayCommandServiceTest {

    private final FakeResolver resolver = new FakeResolver();
    private final FakePlaybackAdapter playback = new FakePlaybackAdapter();
    private final FakeResponder responder = new FakeResponder();
    private final InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
    private ScheduledExecutorService scheduler;
    private IdleDisconnectService idleDisconnect;
    private PlayCommandService service;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        idleDisconnect = new IdleDisconnectService(
                scheduler, queues, playback, (guild, message) -> {
                }, Duration.ofHours(1));
        service = new PlayCommandService(resolver, playback, queues, idleDisconnect);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private BotCommand.PlayCmd playCommand(String query) {
        return new BotCommand.PlayCmd(TestData.GUILD, TestData.requester("Ana"), new VoiceChannelId("777"), query);
    }

    private BotCommand.PlayCmd playCommandWithoutVoice(String query) {
        return new BotCommand.PlayCmd(TestData.GUILD, TestData.requester("Ana"), null, query);
    }

    @Test
    void startingPlaybackConnectsEnqueuesAndAnnounces() {
        Track track = TestData.track("t1", "Música");
        resolver.enqueue(new ResolverResult.ResolvedTrack(track));

        service.play(playCommand("musica"), responder);

        assertThat(responder.lastFollowUp()).isEqualTo(BotMessages.nowPlaying(track));
        assertThat(playback.connectedTo).containsExactly(new VoiceChannelId("777"));
        assertThat(playback.lastPlayed()).isEqualTo(track);
        GuildQueue queue = queues.find(TestData.GUILD).orElseThrow();
        assertThat(queue.current()).contains(track);
        assertThat(queue.state()).isEqualTo(com.mutrabot.domain.model.PlaybackState.PLAYING);
    }

    @Test
    void queuingBehindCurrentTrackDoesNotStartPlayback() {
        Track first = TestData.track("t1", "Primeira");
        resolver.enqueue(new ResolverResult.ResolvedTrack(first));
        service.play(playCommand("primeira"), responder);

        Track second = TestData.track("t2", "Segunda");
        resolver.enqueue(new ResolverResult.ResolvedTrack(second));
        service.play(playCommand("segunda"), responder);

        assertThat(responder.lastFollowUp()).isEqualTo(BotMessages.addedToQueue(second, 1));
        assertThat(playback.played).containsExactly(first);
        assertThat(queues.find(TestData.GUILD).orElseThrow().upcoming()).containsExactly(second);
    }

    @Test
    void playlistStartsPlaybackAndReportsCount() {
        Track first = TestData.track("p1", "Faixa 1");
        Track second = TestData.track("p2", "Faixa 2");
        resolver.enqueue(new ResolverResult.ResolvedPlaylist(List.of(first, second), 2, 0));

        service.play(playCommand("playlist"), responder);

        assertThat(responder.lastFollowUp())
                .isEqualTo(BotMessages.playlistAdded(2, 0, java.util.Optional.of(first)));
        assertThat(playback.lastPlayed()).isEqualTo(first);
        assertThat(playback.connectedTo).containsExactly(new VoiceChannelId("777"));
        assertThat(queues.find(TestData.GUILD).orElseThrow().upcoming()).containsExactly(second);
    }

    @Test
    void playlistCancelsIdleDisconnectTimer() {
        idleDisconnect.scheduleDisconnect(TestData.GUILD);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();
        resolver.enqueue(new ResolverResult.ResolvedPlaylist(
                List.of(TestData.track("p1", "Faixa 1")), 1, 0));

        service.play(playCommand("playlist"), responder);

        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void playlistReportsIgnoredTracksAboveLimit() {
        List<Track> tracks = new java.util.ArrayList<>();
        for (int i = 0; i < 120; i++) {
            tracks.add(TestData.track("p" + i, "Faixa " + i));
        }
        resolver.enqueue(new ResolverResult.ResolvedPlaylist(tracks, 120, 0));

        service.play(playCommand("playlist grande"), responder);

        assertThat(responder.lastFollowUp()).isEqualTo(
                BotMessages.playlistAdded(100, 20, java.util.Optional.of(tracks.get(0))));
    }

    @Test
    void notFoundKeepsQueueUntouched() {
        resolver.enqueue(new ResolverResult.NotFound("xyz"));

        service.play(playCommand("xyz"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.notFound("xyz"));
        assertThat(playback.played).isEmpty();
        assertThat(queues.find(TestData.GUILD)).isEmpty();
    }

    @Test
    void loadFailedKeepsQueueUntouched() {
        resolver.enqueue(new ResolverResult.LoadFailed("url", "falha temporária na fonte", true));

        service.play(playCommand("url"), responder);

        assertThat(responder.lastEphemeralFollowUp())
                .isEqualTo(BotMessages.loadFailed("url", "falha temporária na fonte"));
        assertThat(playback.played).isEmpty();
    }

    @Test
    void rejectsPlayWithoutVoiceAndWithoutSession() {
        service.play(playCommandWithoutVoice("musica"), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.voiceRequired());
        assertThat(resolver.queries()).isEmpty();
    }

    @Test
    void allowsPlayWithoutVoiceWhenBotAlreadyConnected() {
        playback.currentChannel = new VoiceChannelId("777");
        Track track = TestData.track("t1", "Música");
        resolver.enqueue(new ResolverResult.ResolvedTrack(track));
        BotCommand.PlayCmd command = new BotCommand.PlayCmd(
                TestData.GUILD, TestData.requester("Ana"), null, "musica");

        service.play(command, responder);

        assertThat(responder.lastFollowUp()).isEqualTo(BotMessages.nowPlaying(track));
        assertThat(playback.connectedTo).isEmpty();
        assertThat(playback.lastPlayed()).isEqualTo(track);
    }

    @Test
    void rejectsBlankQuery() {
        service.play(playCommand("   "), responder);

        assertThat(responder.lastEphemeralFollowUp()).isEqualTo(BotMessages.emptyQuery());
        assertThat(resolver.queries()).isEmpty();
    }

    @Test
    void trimsQueryBeforeResolving() {
        resolver.enqueue(new ResolverResult.NotFound("musica"));
        service.play(playCommand("  musica  "), responder);

        assertThat(resolver.queries()).containsExactly("musica");
    }

    @Test
    void successfulPlayCancelsIdleDisconnectTimer() {
        idleDisconnect.scheduleDisconnect(TestData.GUILD);
        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();
        resolver.enqueue(new ResolverResult.ResolvedTrack(TestData.track("t1", "Música")));

        service.play(playCommand("musica"), responder);

        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isFalse();
    }

    @Test
    void failedPlayKeepsIdleDisconnectTimer() {
        idleDisconnect.scheduleDisconnect(TestData.GUILD);
        resolver.enqueue(new ResolverResult.NotFound("xyz"));

        service.play(playCommand("xyz"), responder);

        assertThat(idleDisconnect.isScheduled(TestData.GUILD)).isTrue();
    }
}
