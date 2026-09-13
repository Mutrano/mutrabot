package com.mutrabot.domain;

import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackId;
import com.mutrabot.domain.model.UserId;
import com.mutrabot.domain.model.VoiceChannelId;
import com.mutrabot.domain.model.VoiceSession;
import com.mutrabot.domain.result.ResolverResult;
import com.mutrabot.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValueTest {

    @Test
    void idsRejectInvalidValues() {
        assertThatThrownBy(() -> new GuildId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GuildId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceChannelId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoiceChannelId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrackId("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new GuildId("1").value()).isEqualTo("1");
    }

    @Test
    void requesterFallsBackToUnknownDisplayName() {
        assertThat(new Requester(TestData.USER, null).displayName()).isEqualTo(Requester.UNKNOWN_DISPLAY_NAME);
        assertThat(new Requester(TestData.USER, "  ").displayName()).isEqualTo(Requester.UNKNOWN_DISPLAY_NAME);
        assertThat(new Requester(TestData.USER, "Ana").displayName()).isEqualTo("Ana");
        assertThatThrownBy(() -> new Requester(null, "Ana")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void trackSanitizesMissingMetadata() {
        Track track = new Track(new TrackId("t"), null, null, null, null, null, TestData.requester("Ana"));
        assertThat(track.title()).isEqualTo(Track.UNKNOWN_TITLE);
        assertThat(track.author()).isEmpty();
        assertThat(track.sourceUrl()).isEmpty();
        assertThat(track.duration()).isEqualTo(Duration.ZERO);
        assertThat(track.source()).isEqualTo(SourceKind.SEARCH_RESULT);
        assertThat(track.requestedBy().displayName()).isEqualTo("Ana");
        assertThat(track.isLive()).isTrue();
        assertThat(track.toString()).isNotBlank();
        assertThatThrownBy(() -> new Track(null, "T", "A", "U", Duration.ZERO, SourceKind.HTTP, TestData.requester("Ana")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Track(new TrackId("t"), "T", "A", "U", Duration.ZERO, SourceKind.HTTP, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void trackBlankTitleAndNegativeDurationAreNormalized() {
        Track track = new Track(
                new TrackId("t"),
                "   ",
                "Autor",
                "",
                Duration.ofSeconds(-5),
                SourceKind.YOUTUBE,
                TestData.requester("Ana"));
        assertThat(track.title()).isEqualTo(Track.UNKNOWN_TITLE);
        assertThat(track.duration()).isEqualTo(Duration.ZERO);
        assertThat(track.isLive()).isTrue();
        assertThat(track.author()).isEqualTo("Autor");
        assertThat(track.sourceUrl()).isEmpty();
    }

    @Test
    void trackEqualsAndHashCodeUseAllFields() {
        Track one = TestData.track("t1", "Título");
        Track same = TestData.track("t1", "Título");
        Track different = TestData.track("t2", "Título");
        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(different);
    }

    @Test
    void trackCanSwitchRequester() {
        Track original = TestData.track("t1", "Título", TestData.requester("Ana"));
        Track updated = original.requestedBy(TestData.otherRequester("Bia"));
        assertThat(updated.requestedBy().displayName()).isEqualTo("Bia");
        assertThat(original.requestedBy().displayName()).isEqualTo("Ana");
        assertThat(updated.id()).isEqualTo(original.id());
    }

    @Test
    void voiceSessionDefaultsLastActivityToConnectedAt() {
        Instant now = Instant.parse("2026-09-12T10:00:00Z");
        VoiceSession session = new VoiceSession(TestData.GUILD, new VoiceChannelId("7"), now, null);
        assertThat(session.lastActivityAt()).isEqualTo(now);
        assertThat(session.channel().value()).isEqualTo("7");
    }

    @Test
    void resolverResultsValidateTheirData() {
        Track track = TestData.track("t1", "Título");
        ResolverResult.ResolvedTrack resolved = new ResolverResult.ResolvedTrack(track);
        assertThat(resolved.track()).isSameAs(track);

        ResolverResult.ResolvedPlaylist playlist = new ResolverResult.ResolvedPlaylist(List.of(track), 1, 0);
        assertThat(playlist.tracks()).containsExactly(track);
        assertThat(playlist.totalReported()).isEqualTo(1);
        assertThat(playlist.ignored()).isZero();

        ResolverResult.ResolvedPlaylist emptyCounts = new ResolverResult.ResolvedPlaylist(List.of(), 0, 0);
        assertThat(emptyCounts.totalReported()).isZero();
        assertThat(emptyCounts.ignored()).isZero();

        assertThat(new ResolverResult.NotFound("q").query()).isEqualTo("q");
        ResolverResult.LoadFailed failed = new ResolverResult.LoadFailed("q", "motivo", true);
        assertThat(failed.reason()).isEqualTo("motivo");
        assertThat(failed.retryable()).isTrue();

        assertThatThrownBy(() -> new ResolverResult.ResolvedTrack(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResolverResult.ResolvedPlaylist(null, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResolverResult.ResolvedPlaylist(List.of(), -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverResult.ResolvedPlaylist(List.of(), 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverResult.NotFound(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResolverResult.LoadFailed(null, "x", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResolverResult.LoadFailed("x", null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void playCommandNormalizesNullQuery() {
        BotCommand.PlayCmd command = new BotCommand.PlayCmd(TestData.GUILD, TestData.requester("Ana"), null, null);
        assertThat(command.query()).isEmpty();
        assertThat(command.toString()).isNotBlank();
    }

    @Test
    void livestreamJoinCommandNormalizesNullWindow() {
        BotCommand.LivestreamJoinCmd join = new BotCommand.LivestreamJoinCmd(
                TestData.GUILD, TestData.USER, new VoiceChannelId("7"), null);

        assertThat(join.window()).isEmpty();
        assertThat(join.voiceChannel().value()).isEqualTo("7");
        assertThat(new BotCommand.LivestreamLeaveCmd(TestData.GUILD, TestData.USER).user()).isEqualTo(TestData.USER);
    }

    @Test
    void botCommandsCarryTheirContext() {
        assertThat(new BotCommand.StopCmd(TestData.GUILD, TestData.USER).guild()).isEqualTo(TestData.GUILD);
        assertThat(new BotCommand.ResumeCmd(TestData.GUILD, TestData.USER).user()).isEqualTo(TestData.USER);
        assertThat(new BotCommand.SkipCmd(TestData.GUILD, TestData.USER).user()).isEqualTo(TestData.USER);
        assertThat(new BotCommand.QueueCmd(TestData.GUILD, TestData.USER).guild()).isEqualTo(TestData.GUILD);
        assertThat(new BotCommand.PingCmd()).isEqualTo(new BotCommand.PingCmd());
        assertThat(new BotCommand.PingCmd()).isNotEqualTo(new BotCommand.QueueCmd(TestData.GUILD, TestData.USER));
    }
}
