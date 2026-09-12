package com.mutrabot.domain.result;

import com.mutrabot.domain.model.Track;

import java.util.List;
import java.util.Objects;

public sealed interface ResolverResult {

    record ResolvedTrack(Track track) implements ResolverResult {
        public ResolvedTrack {
            Objects.requireNonNull(track, "track");
        }
    }

    record ResolvedPlaylist(List<Track> tracks, int totalReported, int ignored) implements ResolverResult {
        public ResolvedPlaylist {
            Objects.requireNonNull(tracks, "tracks");
            if (totalReported < 0 || ignored < 0) {
                throw new IllegalArgumentException("contadores não podem ser negativos");
            }
        }
    }

    record NotFound(String query) implements ResolverResult {
        public NotFound {
            Objects.requireNonNull(query, "query");
        }
    }

    record LoadFailed(String query, String reason, boolean retryable) implements ResolverResult {
        public LoadFailed {
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
