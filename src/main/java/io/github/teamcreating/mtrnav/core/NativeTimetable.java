package io.github.teamcreating.mtrnav.core;

import com.google.gson.*;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Read-only adapter for the PUBLIC map endpoints shipped in MTR 4.1.0-beta.2. */
public final class NativeTimetable {
    private NativeTimetable() {}
    public record Stop(String stationId, String platformId, String name, long x, long y, long z, long dwell) {}
    public record PlatformIdentity(String platformId, String stationId) {}
    public record Line(String id, List<Stop> stops, List<Long> durations) {
        public Line { stops = List.copyOf(stops); durations = List.copyOf(durations); }
    }
    public record Network(Catalog catalog, Map<String, Line> lines, List<String> dimensions, Set<String> locatedStations) {
        public Network { lines = Map.copyOf(lines); dimensions = List.copyOf(dimensions); locatedStations = Set.copyOf(locatedStations); }
    }
    public static JsonObject data(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("code") && root.get("code").getAsInt() != 200) throw new IllegalArgumentException("MTR map request failed");
        if (root.has("data")) {
            if (!root.get("data").isJsonObject()) throw new IllegalArgumentException("MTR returned no map data");
            return root.getAsJsonObject("data");
        }
        return root;
    }
    public static String id(String value) {
        try { return Long.toHexString(Long.parseUnsignedLong(value, 16)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid MTR ID", e); }
    }
    public static Network network(String dimension, String json, Catalog overlay, Map<String, List<PlatformIdentity>> nativePlatformIds) {
        var object = data(json); var stationRows = new LinkedHashMap<String, JsonObject>();
        var resolvedRoutes = new HashMap<String, List<PlatformIdentity>>();
        nativePlatformIds.forEach((key, values) -> resolvedRoutes.put(id(key), values));
        for (var row : array(object, "stations")) {
            var station = row.getAsJsonObject(); stationRows.put(id(text(station, "id")), station);
        }
        var platforms = new LinkedHashMap<String, Platform>(); var routes = new ArrayList<Route>(); var lines = new LinkedHashMap<String, Line>();
        var positions = new HashMap<String, long[]>();
        for (var row : array(object, "routes")) {
            var route = row.getAsJsonObject();
            String type = text(route, "type");
            if ((route.has("hidden") && route.get("hidden").getAsBoolean()) || !(type.equals("train_normal") || type.equals("train_light_rail") || type.equals("train_high_speed")
                    || type.equals("train") || type.equals("normal") || type.equals("light_rail") || type.equals("high_speed"))) continue;
            String routeId = id(text(route, "id")); var stops = new ArrayList<Stop>(); var platformIds = new ArrayList<String>();
            var resolved = resolvedRoutes.getOrDefault(routeId, List.of());
            boolean matchingIdentity = resolved.size() == array(route, "stations").size();
            for (int i = 0; matchingIdentity && i < resolved.size(); i++) matchingIdentity = id(resolved.get(i).stationId()).equals(id(text(array(route, "stations").get(i).getAsJsonObject(), "id")));
            int index = 0;
            for (var stopRow : array(route, "stations")) {
                var stop = stopRow.getAsJsonObject(); String stationId = id(text(stop, "id"));
                // A public map stop has a station ID, coordinates and platform label,
                // but no platform ID. Keep unresolved identity explicitly namespaced.
                String platformId = matchingIdentity ? id(resolved.get(index).platformId()) : "map:" + routeId + ":" + index;
                long x = number(stop, "x"), y = number(stop, "y"), z = number(stop, "z");
                stops.add(new Stop(stationId, platformId, text(stop, "name"), x, y, z, number(stop, "dwellTime")));
                platforms.putIfAbsent(platformId, new Platform(platformId, stationId, text(stop, "name"), x, y, z));
                if (hasCoordinate(stop, "x") && hasCoordinate(stop, "z")) positions.putIfAbsent(stationId, new long[]{x, y, z});
                platformIds.add(platformId); index++;
            }
            if (stops.size() < 2) continue;
            var durations = new ArrayList<Long>(); for (var duration : array(route, "durations")) durations.add(duration.getAsLong());
            lines.put(routeId, new Line(routeId, stops, durations));
            routes.add(new Route(routeId, text(route, "name"), (int) number(route, "color"), platformIds, !text(route, "circularState").equals("NONE")));
        }
        var stations = new ArrayList<Station>();
        for (var entry : stationRows.entrySet()) {
            var row = entry.getValue(); var connections = new ArrayList<String>();
            for (var c : array(row, "connections")) connections.add(id(c.getAsString()));
            var pos = positions.getOrDefault(entry.getKey(), new long[]{0, 0, 0});
            var known = overlay == null ? null : overlay.station(entry.getKey());
            stations.add(new Station(entry.getKey(), text(row, "name"), known == null ? pos[0] : known.x(), known == null ? pos[1] : known.y(), known == null ? pos[2] : known.z(), connections));
        }
        // Overlay is authoritative for station identity/position, never for guessed schedules.
        if (overlay != null) for (var station : overlay.stations()) if (!stationRows.containsKey(station.id())) stations.add(station);
        stations.sort(Comparator.comparing(Station::id)); routes.sort(Comparator.comparing(Route::id));
        var platformList = platforms.values().stream().sorted(Comparator.comparing(Platform::id)).toList();
        long revision = Objects.hash(stations, platformList, routes);
        var dimensions = new ArrayList<String>(); for (var d : array(object, "dimensions")) dimensions.add(d.getAsString());
        var located = new HashSet<>(positions.keySet());
        if (overlay != null) overlay.stations().forEach(s -> located.add(s.id()));
        return new Network(new Catalog(dimension, revision, stations, platformList, routes), lines, dimensions, located);
    }
    private static boolean hasCoordinate(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isNumber()
                && Double.isFinite(object.get(key).getAsDouble());
    }
    public static Snapshot departures(Network network, String json) {
        var object = data(json); long sample = number(object, "cachedResponseTime");
        if (sample <= 0) throw new IllegalArgumentException("Missing MTR server clock");
        var events = new ArrayList<StopEvent>();
        for (var routeRow : array(object, "departures")) {
            var route = routeRow.getAsJsonObject(); String routeId = id(text(route, "id")); var line = network.lines().get(routeId);
            if (line == null || line.durations().size() != line.stops().size() - 1 || line.durations().stream().anyMatch(d -> d < 0)) continue;
            for (var deviationRow : array(route, "departures")) {
                var group = deviationRow.getAsJsonObject(); long deviation = number(group, "deviation");
                for (var departureRow : array(group, "departures")) {
                    if (events.size() + line.stops().size() > 200_000) throw new IllegalArgumentException("MTR map timetable too large");
                    long offset = departureRow.getAsLong();
                    var run = new RunKey("map:" + routeId, 0, sample + offset, deviation);
                    long departure = sample + offset + deviation;
                    var last = line.stops().getLast();
                    String destination = network.catalog().stationName(last.stationId());
                    for (int i = line.stops().size() - 1; i >= 0; i--) {
                        var stop = line.stops().get(i); long arrival = departure - stop.dwell();
                        // The endpoint doesn't expose realtime/vehicle identity. These
                        // are map timetable estimates, never asserted as a matched train.
                        events.add(new StopEvent(run, "", routeId, stop.platformId(), i, destination, arrival, departure, deviation, false));
                        if (i > 0) departure = arrival - line.durations().get(i - 1);
                    }
                }
            }
        }
        return new Snapshot(network.catalog(), sample, events, Map.of());
    }
    public static JsonArray array(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray(); }
    public static String text(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
    public static long number(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0; }
}
