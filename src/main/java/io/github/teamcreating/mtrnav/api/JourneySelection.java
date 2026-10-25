package io.github.teamcreating.mtrnav.api;

import java.util.List;
import io.github.teamcreating.mtrnav.core.Model.Journey;

/** Version 1 optional map contract: stop indices disambiguate repeat platforms on circular routes. */
public record JourneySelection(int version, String dimension, long topologyRevision, String journeyId, List<Segment> segments) {
    public JourneySelection { segments = List.copyOf(segments); }
    public record Segment(String routeId, String fromPlatformId, String toPlatformId, int fromStopIndex, int toStopIndex, String direction) {}
    public static JourneySelection from(Journey journey) {
        return new JourneySelection(1, journey.dimension(), journey.revision(), journey.id(), journey.legs().stream()
                .map(l -> new Segment(l.routeId(), l.fromPlatformId(), l.toPlatformId(), l.fromStopIndex(), l.toStopIndex(), l.direction())).toList());
    }
}
