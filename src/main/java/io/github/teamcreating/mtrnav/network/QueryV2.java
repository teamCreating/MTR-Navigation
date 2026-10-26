package io.github.teamcreating.mtrnav.network;

import io.github.teamcreating.mtrnav.core.*;
import java.util.*;

public final class QueryV2 {
    private QueryV2() {}
    public record Request(Messages.Request request, Model.QueryOptions options, long walkingRevision, List<WalkingConnection> personal) {
        public Request {
            Objects.requireNonNull(request); Objects.requireNonNull(options);
            personal = List.copyOf(personal);
            if (!options.distinct() || personal.size() > 32 || walkingRevision < 0 || !request.action().equals("query")) throw new IllegalArgumentException("Invalid v2 query");
            for (var edge : personal) if (!edge.dimension().equals(request.dimension()) || !edge.name().isEmpty() || !edge.description().isEmpty()) throw new IllegalArgumentException("Private query contains extra data");
        }
    }
    public record Report(long fastestArrival, boolean incomplete, long walkingRevision) {}
}
