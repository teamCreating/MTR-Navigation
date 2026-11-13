package io.github.teamcreating.mtrnav.core;

import java.util.*;

/** Immutable, loader-independent navigation contract. IDs are native unsigned hex strings. */
public final class Model {
    private Model() {}
    public enum Sort { FASTEST, FEWEST_TRANSFERS }
    public enum RouteType { NORMAL, LIGHT_RAIL, HIGH_SPEED, UNKNOWN }
    public enum StockSource { ACTUAL, PLANNED, UNKNOWN }
    /** Ordered model resource IDs, never vehicle instance IDs. */
    public record Consist(List<String> cars, StockSource source) {
        public Consist {
            cars = cars == null ? List.of() : List.copyOf(cars);
            if (cars.size() > 256 || cars.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 256)) throw new IllegalArgumentException("Invalid consist");
            source = cars.isEmpty() || source == null ? StockSource.UNKNOWN : source;
        }
        public static Consist unknown() { return new Consist(List.of(), StockSource.UNKNOWN); }
        public int count() { return cars.size(); }
    }
    public enum VehicleMode { NONE, PREFER, REQUIRE }
    public record VehicleCondition(VehicleMode mode, List<String> models) {
        public VehicleCondition {
            Objects.requireNonNull(mode); models = List.copyOf(new LinkedHashSet<>(models));
            if (models.size() > 16 || models.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 256)) throw new IllegalArgumentException("Invalid vehicle condition");
            if (models.isEmpty()) mode = VehicleMode.NONE;
        }
        public static VehicleCondition none() { return new VehicleCondition(VehicleMode.NONE, List.of()); }
        public boolean active() { return mode != VehicleMode.NONE; }
        public boolean matches(Consist consist) { return active() && consist.cars().stream().anyMatch(models::contains); }
        public boolean matches(Journey journey) { return journey.legs().stream().anyMatch(l -> matches(l.consist())); }
        public boolean matchesCurrent(Journey journey, Snapshot snapshot) {
            return journey.legs().stream().anyMatch(l -> snapshot.events().stream().anyMatch(e -> e.run().equals(l.run())
                    && e.stopIndex() == l.fromStopIndex() && matches(e.consist())));
        }
    }
    public record Station(String id, String name, long x, long y, long z, List<String> connections) {
        public Station { connections = List.copyOf(connections); }
    }
    public record Platform(String id, String stationId, String name, long x, long y, long z) {}
    public record Route(String id, String name, int color, List<String> platforms, boolean circular, RouteType routeType) {
        public Route { platforms = List.copyOf(platforms); routeType = routeType == null ? RouteType.UNKNOWN : routeType; }
        public Route(String id, String name, int color, List<String> platforms, boolean circular) { this(id, name, color, platforms, circular, RouteType.UNKNOWN); }
    }
    public record Catalog(String dimension, long revision, List<Station> stations, List<Platform> platforms, List<Route> routes) {
        public Catalog { stations = List.copyOf(stations); platforms = List.copyOf(platforms); routes = List.copyOf(routes); }
        public Station station(String id) { return stations.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
        public Platform platform(String id) { return platforms.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }
        public String stationName(String id) { var s = station(id); return s == null ? id : displayName(s.name); }
        public String platformName(String id) { var p = platform(id); return p == null ? id : displayName(p.name); }
        public RouteType routeType(String id) { return routes.stream().filter(r -> r.id.equals(id)).map(Route::routeType).findFirst().orElse(RouteType.UNKNOWN); }
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
                            String destination, long arrival, long departure, long deviation, boolean realtime, Consist consist) {
        public StopEvent { consist = consist == null ? Consist.unknown() : consist; }
        public StopEvent(RunKey run, String vehicleId, String routeId, String platformId, int stopIndex, String destination, long arrival, long departure, long deviation, boolean realtime) {
            this(run, vehicleId, routeId, platformId, stopIndex, destination, arrival, departure, deviation, realtime, Consist.unknown());
        }
    }
    public record RideLeg(RunKey run, String vehicleId, String routeId, String fromStationId, String toStationId,
                          String fromPlatformId, String toPlatformId, int fromStopIndex, int toStopIndex,
                          String direction, long departure, long arrival, boolean realtime, List<String> viaStations, Consist consist) {
        public RideLeg { viaStations = List.copyOf(viaStations); consist = consist == null ? Consist.unknown() : consist; }
        public RideLeg(RunKey run, String vehicleId, String routeId, String fromStationId, String toStationId, String fromPlatformId, String toPlatformId, int fromStopIndex, int toStopIndex, String direction, long departure, long arrival, boolean realtime, List<String> viaStations) {
            this(run, vehicleId, routeId, fromStationId, toStationId, fromPlatformId, toPlatformId, fromStopIndex, toStopIndex, direction, departure, arrival, realtime, viaStations, Consist.unknown());
        }
    }
    public record Journey(String id, String dimension, long revision, String originStationId, String destinationStationId,
                          long queryTime, long arrival, int transfers, List<RideLeg> legs, List<WalkLeg> walks, long walkingRevision, int transferBufferSeconds) {
        public Journey { legs = List.copyOf(legs); walks = walks == null ? List.of() : List.copyOf(walks); }
        public Journey(String id, String dimension, long revision, String origin, String destination, long queryTime, long arrival, int transfers, List<RideLeg> legs, List<WalkLeg> walks, long walkingRevision) {
            this(id, dimension, revision, origin, destination, queryTime, arrival, transfers, legs, walks, walkingRevision, 30);
        }
        public Journey(String id, String dimension, long revision, String origin, String destination, long queryTime, long arrival, int transfers, List<RideLeg> legs) {
            this(id, dimension, revision, origin, destination, queryTime, arrival, transfers, legs, List.of(), 0, 30);
        }
        public List<Step> steps() {
            var result = new ArrayList<Step>();
            for (int i = 0; i <= legs.size(); i++) {
                for (int j = 0; j < walks.size(); j++) if (walks.get(j).beforeRideIndex() == i) result.add(new Step(true, j));
                if (i < legs.size()) result.add(new Step(false, i));
            }
            return List.copyOf(result);
        }
        public long walkingMillis() { return walks.stream().mapToLong(w -> w.seconds() * 1000L).sum(); }
    }
    public record Step(boolean walking, int index) {}
    public record WalkLeg(String connectionId, String fromStationId, String toStationId, String fromPlatformId,
                          String toPlatformId, int seconds, long departure, int beforeRideIndex, String source) {
        public long arrival() { return departure + seconds * 1000L; }
    }
    public record QueryOptions(int limit, int extraMinutes, boolean distinct) {
        public QueryOptions { if (limit < 1 || limit > 5 || !Set.of(-1, 15, 30, 60).contains(extraMinutes)) throw new IllegalArgumentException("Invalid query options"); }
        public static QueryOptions defaults() { return new QueryOptions(5, 30, true); }
        public static QueryOptions legacy() { return new QueryOptions(3, -1, false); }
    }
    public record SearchResult(List<Journey> journeys, long fastestArrival, boolean incomplete) {
        public SearchResult { journeys = List.copyOf(journeys); }
    }
    public record Riding(String vehicleId, String routeId, String platformId, int stopIndex, boolean atPlatform, RunKey run) {}
    public record Snapshot(Catalog catalog, long sampleMillis, List<StopEvent> events, Map<UUID, Riding> riders, List<String> fleet) {
        public Snapshot { events = List.copyOf(events); riders = Map.copyOf(riders); fleet = fleet == null ? List.of() : List.copyOf(fleet); }
        public Snapshot(Catalog catalog, long sampleMillis, List<StopEvent> events, Map<UUID, Riding> riders) { this(catalog, sampleMillis, events, riders, List.of()); }
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
