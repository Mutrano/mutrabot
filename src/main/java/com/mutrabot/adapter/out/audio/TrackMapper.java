package com.mutrabot.adapter.out.audio;

import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;
import com.mutrabot.domain.model.TrackId;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.time.Duration;
import java.util.UUID;

public final class TrackMapper {

    private TrackMapper() {
    }

    public static Track toTrack(
            AudioTrack audioTrack,
            Requester requester,
            SourceKind sourceKind,
            String sourceUrlOverride,
            TrackAudioRegistry registry) {
        AudioTrackInfo info = audioTrack.getInfo();
        String identifier = (info.identifier == null || info.identifier.isBlank())
                ? UUID.randomUUID().toString()
                : info.identifier;
        Duration duration = (info.isStream || info.length <= 0)
                ? Duration.ZERO
                : Duration.ofMillis(info.length);
        String sourceUrl = sourceUrlOverride != null ? sourceUrlOverride : info.uri;
        Track track = new Track(
                new TrackId(identifier),
                info.title,
                info.author,
                sourceUrl,
                duration,
                sourceKind,
                requester);
        registry.register(track.id(), audioTrack);
        return track;
    }
}
