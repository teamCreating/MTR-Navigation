package io.github.teamcreating.mtrnav.core;

import java.util.*;

/** Immutable, loader-independent navigation contract. IDs are native unsigned hex strings. */
public final class Model {
    private Model() {}
    public enum Sort { FASTEST, FEWEST_TRANSFERS }
    public record Station(String id, String name, long x, long y, long z, List<String> connections) {
        public Station { connections = List.copyOf(connections); }
    }
    public record Platform(String id, String stationId, String name, long x, long y, long z) {}
    public record Route(String id, String name, int color, List<String> platforms, boolean circular) {
        public Route { platforms = List.copyOf(platforms); }
    }
    public record Catalog(String dimension, long revision, List<Station> stations, List<Platform> platforms, List<Route> routes) {
        public Catalog { stations = List.copyOf(stations); platforms = List.copyOf(platforms); routes = List.copyOf(routes); }
        public Station station(String id) { return stations.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
        public Platform platform(String id) { return platforms.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }
        public String stationName(String id) { var s = station(id); return s == null ? id : displayName(s.name); }
        public String platformName(String id) { var p = platform(id); return p == null ? id : displayName(p.name); }
        public String routeName(String id) { return routes.stream().filter(r -> r.id.equals(id)).findFirst().map(r -> displayName(r.name)).orElse(id); }
        /** Display aliases keep already-selected journeys readable across a native ID enrichment. */
        public Catalog retainPlatformNames(Catalog previous) {
            if (previous == null || !dimension.equals(previous.dimension)) return this;
            var known = new LinkedHashMap<String, Platform>();
            previous.platforms.forEach(p -> known.put(p.id, p)); platforms.forEach(p -> known.put(p.id, p));
            return new Catalog(dimension, revision, stations, List.copyOf(known.values()), routes);
        }
    }
    /** A complete run identity; departureIndex alone is not unique across sidings or loops. */
    public record RunKey(String sidingId, int tripIndex, long departureIndex, long departureOffset) {}
    public record StopEvent(RunKey run, String vehicleId, String routeId, String platformId, int stopIndex,
                            String destination, long arrival, long departure, long deviation, boolean realtime) {}
    public record RideLeg(RunKey run, String vehicleId, String routeId, String fromStationId, String toStationId,
                          String fromPlatformId, String toPlatformId, int fromStopIndex, int toStopIndex,
                          String direction, long departure, long arrival, boolean realtime, List<String> viaStations) {
        public RideLeg { viaStations = List.copyOf(viaStations); }
    }
    public record Journey(String id, String dimension, long revision, String originStationId, String destinationStationId,
                          long queryTime, long arrival, int transfers, List<RideLeg> legs) {
        public Journey { legs = List.copyOf(legs); }
    }
    public record Riding(String vehicleId, String routeId, String platformId, int stopIndex, boolean atPlatform, RunKey run) {}
    public record Snapshot(Catalog catalog, long sampleMillis, List<StopEvent> events, Map<UUID, Riding> riders) {
        public Snapshot { events = List.copyOf(events); riders = Map.copyOf(riders); }
    }
    public record Settings(int horizonHours, int maxTransfers, int transferBufferSeconds) {
        public Settings {
            if (horizonHours < 1 || horizonHours > 24 || maxTransfers < 0 || maxTransfers > 12
                    || transferBufferSeconds < 0 || transferBufferSeconds > 3600) throw new IllegalArgumentException("Invalid navigation settings");
        }
        public static Settings defaults() { return new Settings(6, 4, 30); }
    }
    public static String displayName(String raw) {
        return raw == null ? "" : raw.replace("||", " · ").replace('|', ' ').trim();
    }
}
