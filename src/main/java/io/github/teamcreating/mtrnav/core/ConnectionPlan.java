package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Directed infrastructure reference. It deliberately contains no train times or run identities. */
public record ConnectionPlan(String origin, String destination, List<Leg> legs) {
    public ConnectionPlan { legs = List.copyOf(legs); }
    public record Leg(String routeId, String fromStationId, String toStationId, String fromPlatformId,
                      String toPlatformId, int fromStopIndex, int toStopIndex, String direction, List<String> viaStations) {
        public Leg { viaStations = List.copyOf(viaStations); }
    }
    private record Boarding(Route route, int index) {}
    private record Label(String station, List<Leg> legs) {}
    public int transfers() { return Math.max(0, legs.size() - 1); }

    public static Optional<ConnectionPlan> find(Catalog catalog, String origin, String destination) {
        if (catalog == null || catalog.station(origin) == null || catalog.station(destination) == null) return Optional.empty();
        var platforms = new HashMap<String, Platform>(); catalog.platforms().forEach(p -> platforms.put(p.id(), p));
        var stations = new HashMap<String, Station>(); catalog.stations().forEach(s -> stations.put(s.id(), s));
        var boarding = new HashMap<String, List<Boarding>>();
        for (var route : catalog.routes()) for (int i = 0; i < route.platforms().size() - 1; i++) {
            var p = platforms.get(route.platforms().get(i));
            if (p != null) boarding.computeIfAbsent(p.stationId(), ignored -> new ArrayList<>()).add(new Boarding(route, i));
        }
        var queue = new PriorityQueue<Label>(Comparator.comparingInt(l -> l.legs().size()));
        var best = new HashMap<String, Integer>(); best.put(origin, 0); queue.add(new Label(origin, List.of()));
        int operations = 0;
        while (!queue.isEmpty()) {
            var label = queue.poll();
            if (label.legs().size() != best.get(label.station())) continue;
            if (label.station().equals(destination)) return Optional.of(new ConnectionPlan(origin, destination, label.legs()));
            var station = stations.get(label.station());
            if (station == null) continue;
            for (String connected : station.connections()) if (stations.containsKey(connected) && label.legs().size() < best.getOrDefault(connected, Integer.MAX_VALUE)) {
                best.put(connected, label.legs().size()); queue.add(new Label(connected, label.legs()));
            }
            for (var start : boarding.getOrDefault(label.station(), List.of())) {
                var route = start.route(); var from = platforms.get(route.platforms().get(start.index()));
                var last = platforms.get(route.platforms().getLast()); var via = new ArrayList<String>();
                for (int i = start.index() + 1; i < route.platforms().size(); i++) {
                    if (++operations > 500_000 || Thread.currentThread().isInterrupted()) return Optional.empty();
                    var to = platforms.get(route.platforms().get(i)); if (to == null) continue;
                    int rides = label.legs().size() + 1;
                    if (rides < best.getOrDefault(to.stationId(), Integer.MAX_VALUE)) {
                        var legs = new ArrayList<>(label.legs());
                        legs.add(new Leg(route.id(), from.stationId(), to.stationId(), from.id(), to.id(), start.index(), i,
                                last == null ? "" : catalog.stationName(last.stationId()), via));
                        best.put(to.stationId(), rides); queue.add(new Label(to.stationId(), List.copyOf(legs)));
                    }
                    via.add(to.stationId());
                }
            }
        }
        return Optional.empty();
    }
}
