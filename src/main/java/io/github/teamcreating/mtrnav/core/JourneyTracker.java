package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Advances only from reliable native identities or an explicit player correction. */
public final class JourneyTracker {
    public enum Phase { WAITING, RIDING, TRANSFER, WALKING, COMPLETE }
    public record State(Journey journey, int legIndex, Phase phase, String notice, boolean reliable, int stepIndex) {
        public State {
            if (journey.walks().isEmpty() && stepIndex == 0 && legIndex > 0) stepIndex = rideStep(journey, legIndex);
            if (stepIndex < 0 || stepIndex > journey.steps().size() || phase == null) throw new IllegalArgumentException("Invalid journey step");
        }
        public State(Journey journey, int legIndex, Phase phase, String notice, boolean reliable) { this(journey, legIndex, phase, notice, reliable, rideStep(journey, legIndex)); }
        public WalkLeg walk() { var steps = journey.steps(); return phase == Phase.WALKING && stepIndex < steps.size() ? journey.walks().get(steps.get(stepIndex).index()) : null; }
    }
    private static int rideStep(Journey journey, int leg) { var steps = journey.steps(); for (int i = 0; i < steps.size(); i++) if (!steps.get(i).walking() && steps.get(i).index() == leg) return i; return steps.size(); }
    private int stepIndex;
    private Journey journey;
    private int legIndex;
    private Phase phase = Phase.WAITING;
    private String notice = "";
    public JourneyTracker(Journey journey) { this.journey = journey; phaseForStep(); }
    public State update(Snapshot snapshot, UUID player) {
        if (journey.revision() != snapshot.catalog().revision()) {
            notice = "network_changed";
            return state(false);
        }
        if (phase == Phase.WALKING) {
            var walk = state(false).walk();
            notice = walk != null && snapshot.sampleMillis() > walk.arrival() + 30_000 ? "walk_overdue" : "manual_walk";
            if (legIndex < journey.legs().size()) {
                refresh(snapshot);
                if (snapshot.sampleMillis() > journey.legs().get(legIndex).departure()) notice = "missed_train";
            }
            return state(false);
        }
        if (journey.legs().isEmpty() || legIndex >= journey.legs().size()) { phase = Phase.COMPLETE; return state(true); }
        var leg = journey.legs().get(legIndex);
        var riding = snapshot.riders().get(player);
        var from = event(snapshot, leg, leg.fromStopIndex());
        var to = event(snapshot, leg, leg.toStopIndex());
        refresh(snapshot); leg = journey.legs().get(legIndex);
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
            case "board" -> { if (phase != Phase.COMPLETE && phase != Phase.WALKING) phase = Phase.RIDING; }
            case "next" -> advance();
            case "previous" -> { stepIndex = Math.max(0, stepIndex - 1); phaseForStep(); }
            default -> { }
        }
        return state(false);
    }
    public State state(boolean reliable) { return new State(journey, legIndex, phase, notice, reliable, stepIndex); }
    private void advance() { stepIndex = Math.min(stepIndex + 1, journey.steps().size()); phaseForStep(); notice = ""; }
    private void phaseForStep() {
        var steps = journey.steps();
        if (stepIndex >= steps.size()) { legIndex = journey.legs().size(); phase = Phase.COMPLETE; return; }
        var step = steps.get(stepIndex);
        if (step.walking()) { legIndex = journey.walks().get(step.index()).beforeRideIndex(); phase = Phase.WALKING; }
        else { legIndex = step.index(); phase = stepIndex == 0 ? Phase.WAITING : Phase.TRANSFER; }
    }
    private void refresh(Snapshot snapshot) {
        var leg = journey.legs().get(legIndex);
        var from = event(snapshot, leg, leg.fromStopIndex()); var to = event(snapshot, leg, leg.toStopIndex());
        if (to == null) return;
        var legs = new ArrayList<>(journey.legs());
        legs.set(legIndex, new RideLeg(leg.run(), to.vehicleId(), leg.routeId(), leg.fromStationId(), leg.toStationId(), leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex(), leg.direction(), from == null ? leg.departure() : from.departure(), to.arrival(), to.realtime(), leg.viaStations()));
        var walks = journey.walks().stream().map(w -> w.beforeRideIndex() == legIndex + 1
                ? new WalkLeg(w.connectionId(), w.fromStationId(), w.toStationId(), w.fromPlatformId(), w.toPlatformId(), w.seconds(), to.arrival(), w.beforeRideIndex(), w.source()) : w).toList();
        long tail = walks.stream().filter(w -> w.beforeRideIndex() == legs.size()).mapToLong(w -> w.seconds() * 1000L).sum();
        journey = new Journey(journey.id(), journey.dimension(), journey.revision(), journey.originStationId(), journey.destinationStationId(), journey.queryTime(), legs.getLast().arrival() + tail, journey.transfers(), legs, walks, journey.walkingRevision(), journey.transferBufferSeconds());
    }
    private static StopEvent event(Snapshot snapshot, RideLeg leg, int index) {
        return snapshot.events().stream().filter(e -> e.run().equals(leg.run()) && e.stopIndex() == index).findFirst().orElse(null);
    }
}
