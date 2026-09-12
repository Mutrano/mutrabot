package com.mutrabot.support;

import com.mutrabot.domain.model.GuildId;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackId;
import com.mutrabot.domain.model.UserId;

import java.time.Duration;

public final class TestData {

    public static final GuildId GUILD = new GuildId("100");
    public static final GuildId OTHER_GUILD = new GuildId("200");
    public static final UserId USER = new UserId("42");
    public static final UserId OTHER_USER = new UserId("43");

    private TestData() {
    }

    public static Requester requester(String displayName) {
        return new Requester(USER, displayName);
    }

    public static Requester otherRequester(String displayName) {
        return new Requester(OTHER_USER, displayName);
    }

    public static Track track(String id, String title) {
        return track(id, title, requester("Ana"));
    }

    public static Track track(String id, String title, Requester requester) {
        return new Track(
                new TrackId(id),
                title,
                "Artista",
                "https://youtu.be/" + id,
                Duration.ofSeconds(125),
                SourceKind.YOUTUBE,
                requester);
    }

    public static Track liveTrack(String id, String title) {
        return new Track(
                new TrackId(id),
                title,
                "Artista",
                "https://youtu.be/" + id,
                Duration.ZERO,
                SourceKind.YOUTUBE,
                requester("Ana"));
    }
}
