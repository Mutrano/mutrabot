package com.mutrabot.adapter.out.audio;

import java.util.Optional;

public interface MetadataLookup {
    Optional<String> lookupTitle(String sourceUrl);
}
