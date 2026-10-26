package io.github.teamcreating.mtrnav.network;

import java.util.List;
import io.github.teamcreating.mtrnav.core.Model.*;
import io.github.teamcreating.mtrnav.core.JourneyTracker;

public final class Messages {
    private Messages() {}
    public record Request(String action, String dimension, long id, long revision, String origin, String destination,
                          Sort sort, String journeyId, String stationId, String correction) {
        public Request {
            if (action == null || dimension == null || origin == null || destination == null || journeyId == null || stationId == null
                    || correction == null || sort == null || id < 0 || dimension.length() > 256 || action.length() > 24
                    || origin.length() > 32 || destination.length() > 32 || journeyId.length() > 64 || stationId.length() > 32
                    || correction.length() > 24) throw new IllegalArgumentException("Invalid navigation request");
        }
    }
    public record Response(String kind, String dimension, long id, long revision, long sampleMillis, Catalog catalog,
                           List<Journey> journeys, List<StopEvent> arrivals, JourneyTracker.State tracking, String message, QueryV2.Report report) {
        public Response { journeys = List.copyOf(journeys); arrivals = List.copyOf(arrivals); }
        public Response(String kind, String dimension, long id, long revision, long sampleMillis, Catalog catalog,
                        List<Journey> journeys, List<StopEvent> arrivals, JourneyTracker.State tracking, String message) {
            this(kind, dimension, id, revision, sampleMillis, catalog, journeys, arrivals, tracking, message, null);
        }
    }
}
