package com.mutrabot.support;

import com.mutrabot.application.port.out.TrackResolverPort;
import com.mutrabot.domain.model.Requester;
import com.mutrabot.domain.result.ResolverResult;

import java.util.ArrayList;
import java.util.List;

public final class FakeResolver implements TrackResolverPort {

    private final List<ResolverResult> results = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();

    public FakeResolver enqueue(ResolverResult result) {
        results.add(result);
        return this;
    }

    public List<String> queries() {
        return queries;
    }

    @Override
    public ResolverResult resolve(String query, Requester requester) {
        queries.add(query);
        if (results.isEmpty()) {
            return new ResolverResult.NotFound(query);
        }
        return results.remove(0);
    }
}
