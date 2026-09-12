package com.mutrabot.application.port.out;

import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.result.ResolverResult;

public interface TrackResolverPort {
    ResolverResult resolve(String query, Requester requester);
}
