package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** A local horizontal-distance estimate, never an entrance or walking-path estimate. */
public final class StationLocator {
    public static final double AUTO_RADIUS = 512, AMBIGUITY_GAP = 64;
    private StationLocator() {}
    public record Position(String dimension, double x, double z) {}
    public record Candidate(Station station, double distance) {}

    /** A null knownPositions means the dedicated server supplied genuine station coordinates. */
    public static List<Candidate> nearby(Catalog catalog, Set<String> knownPositions, Position position) {
        if (catalog == null || position == null || !catalog.dimension().equals(position.dimension())
                || !Double.isFinite(position.x()) || !Double.isFinite(position.z())) return List.of();
        return catalog.stations().stream().filter(s -> knownPositions == null || knownPositions.contains(s.id()))
                .map(s -> new Candidate(s, Math.hypot((double)s.x() - position.x(), (double)s.z() - position.z())))
                .sorted(Comparator.comparingDouble(Candidate::distance).thenComparing(c -> c.station().id())).toList();
    }
    public static boolean unambiguous(List<Candidate> candidates) {
        return !candidates.isEmpty() && candidates.getFirst().distance() <= AUTO_RADIUS
                && (candidates.size() == 1 || candidates.get(1).distance() - candidates.getFirst().distance() > AMBIGUITY_GAP);
    }

    /** Connection-scoped intent: clearing or editing must not immediately refill the origin. */
    public static final class Session {
        private boolean automatic = true;
        private String station = "";
        private Position position;
        public void reset() { automatic = true; station = ""; position = null; }
        public void edited() { automatic = false; station = ""; position = null; }
        public void attempted() { automatic = false; }
        public boolean ready(boolean originPresent, boolean inputEmpty, boolean playerPresent, boolean catalogReady) {
            if (originPresent || !inputEmpty) automatic = false;
            return automatic && playerPresent && catalogReady;
        }
        public void selected(String stationId, Position sampledPosition) { attempted(); station = stationId; position = sampledPosition; }
        public Position positionFor(String stationId, String dimension) {
            return position != null && station.equals(stationId) && position.dimension().equals(dimension) ? position : null;
        }
    }
}
