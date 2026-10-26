package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Shared > personal > MTR; specificity is resolved within each layer, never added twice. */
public final class WalkingGraph {
    public record Transfer(String id, int seconds, String source, String fromPlatform, String toPlatform) {
        public WalkLeg leg(String from, String to, long departure, int beforeRide) { return new WalkLeg(id, from, to, fromPlatform, toPlatform, seconds, departure, beforeRide, source); }
    }
    private final Catalog catalog;
    private final Map<String, Station> stations = new HashMap<>();
    private final Map<String, Platform> platforms = new HashMap<>();
    private final List<WalkingConnection> shared, personal;
    public WalkingGraph(Catalog catalog, List<WalkingConnection> shared, List<WalkingConnection> personal) {
        this.catalog = catalog; catalog.stations().forEach(s -> stations.put(s.id(), s)); catalog.platforms().forEach(p -> platforms.put(p.id(), p));
        this.shared = valid(shared); this.personal = valid(personal);
    }
    private List<WalkingConnection> valid(List<WalkingConnection> values) {
        var result = new ArrayList<WalkingConnection>();
        for (var v : values) if (v.enabled()) try { v.validate(catalog); result.add(v); } catch (IllegalArgumentException ignored) { }
        result.sort(Comparator.comparingInt(WalkingConnection::specificity).reversed().thenComparing(WalkingConnection::id));
        return List.copyOf(result);
    }
    public Set<String> reachable(String from) {
        var result = new LinkedHashSet<String>(); result.add(from);
        var s = stations.get(from); if (s != null) s.connections().stream().filter(stations::containsKey).forEach(result::add);
        for (var layer : List.of(shared, personal)) for (var edge : layer) if (edge.fromStation().equals(from)) result.add(edge.toStation());
        return result;
    }
    public Transfer transfer(String from, String fromPlatform, String to, String toPlatform) {
        if (!stations.containsKey(from) || !stations.containsKey(to)) return null;
        for (var layer : List.of(shared, personal)) for (var edge : layer)
            if (edge.fromStation().equals(from) && edge.toStation().equals(to)
                    && (edge.fromPlatform().isEmpty() || fromPlatform.isEmpty() || edge.fromPlatform().equals(fromPlatform))
                    && (edge.toPlatform().isEmpty() || toPlatform.isEmpty() || edge.toPlatform().equals(toPlatform)))
                return new Transfer(edge.id(), edge.seconds(), layer == shared ? "shared" : "personal",
                        edge.fromPlatform().isEmpty() ? fromPlatform : edge.fromPlatform(), edge.toPlatform().isEmpty() ? toPlatform : edge.toPlatform());
        if (!from.equals(to) && !stations.get(from).connections().contains(to)) return null;
        if (from.equals(to) && fromPlatform.isEmpty()) return new Transfer("", 0, "mtr", fromPlatform, toPlatform);
        var a = platforms.get(fromPlatform); var b = platforms.get(toPlatform); var s = stations.get(from); var e = stations.get(to);
        double dx = (double)(a == null ? s.x() : a.x()) - (b == null ? e.x() : b.x());
        double dy = (double)(a == null ? s.y() : a.y()) - (b == null ? e.y() : b.y());
        double dz = (double)(a == null ? s.z() : a.z()) - (b == null ? e.z() : b.z());
        return new Transfer("mtr:" + from + ":" + fromPlatform + ">" + to + ":" + toPlatform,
                (int)Math.min(Integer.MAX_VALUE, Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 4.3)), "mtr", fromPlatform, toPlatform);
    }
}
