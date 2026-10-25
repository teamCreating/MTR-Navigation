package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Advances only from reliable native identities or an explicit player correction. */
public final class JourneyTracker {
    public enum Phase { WAITING, RIDING, TRANSFER, COMPLETE }
    public record State(Journey journey, int legIndex, Phase phase, String notice, boolean reliable) {}
    private Journey journey;
    private int legIndex;
    private Phase phase = Phase.WAITING;
    private String notice = "";
    public JourneyTracker(Journey journey) { this.journey = journey; }
    public State update(Snapshot snapshot, UUID player) {
        if (journey.revision() != snapshot.catalog().revision()) {
            notice = "network_changed";
            return state(false);
        }
        if (journey.legs().isEmpty() || legIndex >= journey.legs().size()) { phase = Phase.COMPLETE; return state(true); }
        var leg = journey.legs().get(legIndex);
        var riding = snapshot.riders().get(player);
        var from = event(snapshot, leg, leg.fromStopIndex());
        var to = event(snapshot, leg, leg.toStopIndex());
        // Refresh times of this exact run, never substitute the next departure on the same route.
        if (to != null) {
            var legs = new ArrayList<>(journey.legs());
            legs.set(legIndex, new RideLeg(leg.run(), to.vehicleId(), leg.routeId(), leg.fromStationId(), leg.toStationId(),
                    leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex(), leg.direction(),
                    from == null ? leg.departure() : from.departure(), to.arrival(), to.realtime(), leg.viaStations()));
            journey = new Journey(journey.id(), journey.dimension(), journey.revision(), journey.originStationId(), journey.destinationStationId(),
                    journey.queryTime(), legs.getLast().arrival(), journey.transfers(), legs);
            leg = legs.get(legIndex);
        }
        boolean matched = riding != null && !leg.vehicleId().isEmpty() && leg.vehicleId().equals(riding.vehicleId()) && leg.run().equals(riding.run());
        if (matched && riding.routeId().equals(leg.routeId()) && riding.stopIndex() >= leg.fromStopIndex() && riding.stopIndex() <= leg.toStopIndex()) {
            phase = Phase.RIDING;
            notice = riding.stopIndex() == leg.toStopIndex() ? "alight_next" : "on_route";
            if (riding.stopIndex() == leg.toStopIndex() && riding.atPlatform()) notice = "alight_now";
        } else if (riding != null && (riding.run() != null || !leg.vehicleId().equals(riding.vehicleId()))) {
            notice = "wrong_train";
        } else if (riding != null) {
            notice = "manual_check";
        } else if (phase == Phase.RIDING && notice.equals("alight_now")) {
            advance();
        } else if (phase != Phase.RIDING && snapshot.sampleMillis() > leg.departure() + 10_000) {
            notice = "missed_train";
        } else if (riding == null && phase == Phase.RIDING) {
            notice = "manual_check";
        }
        return state(matched);
    }
    public State correct(String action) {
        notice = "";
        switch (action) {
            case "board" -> { if (phase != Phase.COMPLETE) phase = Phase.RIDING; }
            case "next" -> advance();
            case "previous" -> { legIndex = Math.max(0, legIndex - 1); phase = Phase.WAITING; }
            default -> { }
        }
        return state(false);
    }
    public State state(boolean reliable) { return new State(journey, legIndex, phase, notice, reliable); }
    private void advance() { legIndex = Math.min(legIndex + 1, journey.legs().size()); phase = legIndex >= journey.legs().size() ? Phase.COMPLETE : Phase.TRANSFER; notice = ""; }
    private static StopEvent event(Snapshot snapshot, RideLeg leg, int index) {
        return snapshot.events().stream().filter(e -> e.run().equals(leg.run()) && e.stopIndex() == index).findFirst().orElse(null);
    }
}
