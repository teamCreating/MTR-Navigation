package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Presentation groups: equal line names/colours share a heading; native identities stay on each row. */
public final class DepartureBoard {
    public static final int PER_PLATFORM = 32, TOTAL = 512;
    private DepartureBoard() {}
    public record Group(String key, String name, int color, List<StopEvent> events) {
        public Group { events = List.copyOf(events); }
    }
    public static List<Group> groups(Catalog catalog, String stationId, List<StopEvent> events) {
        var routes = new HashMap<String, Route>(); catalog.routes().forEach(r -> routes.put(r.id(), r));
        var stationPlatforms = new HashSet<String>();
        catalog.platforms().stream().filter(p -> p.stationId().equals(stationId)).forEach(p -> stationPlatforms.add(p.id()));
        var names = new HashMap<String, String>(); var colors = new HashMap<String, Integer>();
        var grouped = new HashMap<String, List<StopEvent>>();
        for (var route : catalog.routes()) if (route.platforms().stream().anyMatch(stationPlatforms::contains)) {
            String key = key(route, route.id()); grouped.computeIfAbsent(key, ignored -> new ArrayList<>());
            names.put(key, displayName(route.name())); colors.put(key, route.color());
        }
        for (var event : events) {
            var route = routes.get(event.routeId()); String key = key(route, event.routeId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
            names.put(key, route == null ? "" : displayName(route.name())); colors.put(key, route == null ? 0xD4B478 : route.color());
        }
        var result = new ArrayList<Group>();
        grouped.forEach((key, rows) -> {
            rows.sort(Comparator.comparingLong(StopEvent::departure).thenComparingLong(StopEvent::arrival)
                    .thenComparing(StopEvent::routeId).thenComparing(StopEvent::platformId).thenComparingInt(StopEvent::stopIndex));
            result.add(new Group(key, names.get(key), colors.get(key), rows));
        });
        result.sort(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Group::key));
        return List.copyOf(result);
    }
    private static String key(Route route, String id) {
        return route == null || route.name().isBlank() ? "id:" + id : "line:" + displayName(route.name()) + ":" + route.color();
    }
}
