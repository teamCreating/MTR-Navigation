package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Directed infrastructure reference. It deliberately contains no train times or run identities. */
public record ConnectionPlan(String origin, String destination, List<Leg> legs, List<WalkLeg> walks) {
    private static final double STOP_PENALTY_BLOCKS = 80.0;
    private static final double TRANSFER_PENALTY_BLOCKS = 850.0;
    private static final double WALK_BLOCKS_PER_SECOND = 5.0;

    public ConnectionPlan { legs = List.copyOf(legs); walks = List.copyOf(walks); }
    public ConnectionPlan(String origin, String destination, List<Leg> legs) { this(origin, destination, legs, List.of()); }

    public record Leg(String routeId, String fromStationId, String toStationId, String fromPlatformId,
                      String toPlatformId, int fromStopIndex, int toStopIndex, String direction, List<String> viaStations) {
        public Leg { viaStations = List.copyOf(viaStations); }
    }

    private record Boarding(Route route, int index, double[] cumDist, String lineKey) {}
    private record Label(String station, String platform, List<Leg> legs, List<WalkLeg> walks) {}
    private record AltLabel(
            String station,
            String platform,
            List<Leg> legs,
            List<WalkLeg> walks,
            Set<String> visitedStations,
            Set<String> usedLineKeys,
            int stops,
            int walkSeconds,
            double distanceBlocks,
            double score
    ) {}
    private record ScoredPlan(ConnectionPlan plan, double score, int transfers) {}

    public int transfers() { return Math.max(0, legs.size() - 1); }

    public int stopCount() {
        int total = 0;
        for (var leg : legs) total += leg.viaStations().size() + 1;
        return total;
    }

    public int walkSeconds() {
        int total = 0;
        for (var walk : walks) total += Math.max(0, walk.seconds());
        return total;
    }

    public double estimatedBlocks(Catalog catalog) {
        if (catalog == null) return 0.0;
        double total = 0.0;
        for (var leg : legs) {
            Station prev = catalog.station(leg.fromStationId());
            for (String viaId : leg.viaStations()) {
                Station cur = catalog.station(viaId);
                if (prev != null && cur != null) total += Math.hypot(cur.x() - prev.x(), cur.z() - prev.z());
                if (cur != null) prev = cur;
            }
            Station end = catalog.station(leg.toStationId());
            if (prev != null && end != null) total += Math.hypot(end.x() - prev.x(), end.z() - prev.z());
        }
        for (var walk : walks) {
            Station a = catalog.station(walk.fromStationId());
            Station b = catalog.station(walk.toStationId());
            double d = a != null && b != null ? Math.hypot(b.x() - a.x(), b.z() - a.z()) : 0.0;
            total += d > 1.0 ? d : walk.seconds() * 4.0;
        }
        return total;
    }

    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(origin).append("->").append(destination);
        for (var leg : legs) {
            sb.append('|').append(leg.routeId()).append(':')
                    .append(leg.fromStationId()).append('>').append(leg.toStationId());
        }
        return sb.toString();
    }

    /**
     * Converts this untimed reference path into a manually guided {@link Journey} without inventing any
     * train departure/arrival timestamps ({@code queryTime = arrival = departure = 0}).
     */
    public Journey toGuidedJourney(Catalog catalog) {
        String dim = catalog == null ? "" : catalog.dimension();
        long rev = catalog == null ? 0L : catalog.revision();
        var rideLegs = new ArrayList<RideLeg>(legs.size());
        for (int i = 0; i < legs.size(); i++) {
            var leg = legs.get(i);
            rideLegs.add(new RideLeg(
                    new RunKey("ref", 0, i, 0),
                    "",
                    leg.routeId(),
                    leg.fromStationId(),
                    leg.toStationId(),
                    leg.fromPlatformId(),
                    leg.toPlatformId(),
                    leg.fromStopIndex(),
                    leg.toStopIndex(),
                    leg.direction(),
                    0L,
                    0L,
                    false,
                    leg.viaStations(),
                    new Consist(List.of(), StockSource.UNKNOWN)));
        }
        return new Journey("ref:" + UUID.randomUUID(), dim, rev, origin, destination,
                0L, 0L, transfers(), rideLegs, walks, 0L, 0);
    }

    public static boolean isGuidedJourney(Journey journey) {
        return journey != null && journey.id() != null && journey.id().startsWith("ref:");
    }

    public static Optional<ConnectionPlan> find(Catalog catalog, String origin, String destination) {
        return find(catalog, origin, destination, List.of(), List.of());
    }

    public static Optional<ConnectionPlan> find(Catalog catalog, String origin, String destination, List<WalkingConnection> shared, List<WalkingConnection> personal) {
        if (catalog == null || catalog.station(origin) == null || catalog.station(destination) == null) return Optional.empty();
        var walking = new WalkingGraph(catalog, shared, personal);
        var platforms = new HashMap<String, Platform>(); catalog.platforms().forEach(p -> platforms.put(p.id(), p));
        var stations = new HashMap<String, Station>(); catalog.stations().forEach(s -> stations.put(s.id(), s));
        var boarding = new HashMap<String, List<Boarding>>();
        for (var route : catalog.routes()) {
            double[] cumDist = cumulativeDistances(route, platforms, stations);
            String lineKey = normalizedLineKey(route);
            for (int i = 0; i < route.platforms().size() - 1; i++) {
                var p = platforms.get(route.platforms().get(i));
                if (p != null) boarding.computeIfAbsent(p.stationId(), ignored -> new ArrayList<>()).add(new Boarding(route, i, cumDist, lineKey));
            }
        }
        var queue = new PriorityQueue<Label>(Comparator.comparingInt(l -> l.legs().size()));
        var best = new HashMap<String, Integer>(); best.put(origin + ":", 0); queue.add(new Label(origin, "", List.of(), List.of()));
        int operations = 0;
        while (!queue.isEmpty()) {
            var label = queue.poll();
            if (label.legs().size() != best.get(label.station() + ":" + label.platform())) continue;
            if (label.station().equals(destination)) return Optional.of(new ConnectionPlan(origin, destination, label.legs(), label.walks()));
            var station = stations.get(label.station());
            if (station == null) continue;
            if (!label.legs().isEmpty()) {
                var tail = walking.transfer(label.station(), label.platform(), destination, "");
                if (tail != null && !label.station().equals(destination)) {
                    var walks = new ArrayList<>(label.walks()); if (tail.seconds() > 0) walks.add(tail.leg(label.station(), destination, 0, label.legs().size()));
                    return Optional.of(new ConnectionPlan(origin, destination, label.legs(), walks));
                }
            }
            for (String reachable : walking.reachable(label.station())) for (var start : boarding.getOrDefault(reachable, List.of())) {
                var route = start.route(); var from = platforms.get(route.platforms().get(start.index()));
                var transfer = walking.transfer(label.station(), label.platform(), from.stationId(), from.id());
                if (transfer == null) continue;
                var nextWalks = new ArrayList<>(label.walks());
                if (transfer.seconds() > 0) nextWalks.add(transfer.leg(label.station(), from.stationId(), 0, label.legs().size()));
                var last = platforms.get(route.platforms().getLast()); var via = new ArrayList<String>();
                for (int i = start.index() + 1; i < route.platforms().size(); i++) {
                    if (++operations > 500_000 || Thread.currentThread().isInterrupted()) return Optional.empty();
                    var to = platforms.get(route.platforms().get(i)); if (to == null) continue;
                    int rides = label.legs().size() + 1;
                    if (rides < best.getOrDefault(to.stationId() + ":" + to.id(), Integer.MAX_VALUE)) {
                        var legs = new ArrayList<>(label.legs());
                        legs.add(new Leg(route.id(), from.stationId(), to.stationId(), from.id(), to.id(), start.index(), i,
                                last == null ? "" : catalog.stationName(last.stationId()), via));
                        best.put(to.stationId() + ":" + to.id(), rides); queue.add(new Label(to.stationId(), to.id(), List.copyOf(legs), List.copyOf(nextWalks)));
                    }
                    via.add(to.stationId());
                }
            }
        }
        return Optional.empty();
    }

    public static List<ConnectionPlan> findAlternatives(Catalog catalog, String origin, String destination, Sort sort, int limit) {
        return findAlternatives(catalog, origin, destination, List.of(), List.of(), sort, limit);
    }

    /**
     * Finds up to {@code limit} diverse untimed route references, deduplicated by line combination
     * (and direction for ring/bi-directional corridors) and ordered according to {@code sort}.
     */
    public static List<ConnectionPlan> findAlternatives(
            Catalog catalog,
            String origin,
            String destination,
            List<WalkingConnection> shared,
            List<WalkingConnection> personal,
            Sort sort,
            int limit
    ) {
        if (catalog == null || origin == null || destination == null || limit <= 0
                || origin.equals(destination)
                || catalog.station(origin) == null || catalog.station(destination) == null) {
            return List.of();
        }
        Sort effectiveSort = sort == null ? Sort.FASTEST : sort;
        int targetCount = Math.clamp(limit, 1, 8);
        var walking = new WalkingGraph(catalog, shared, personal);
        var platforms = new HashMap<String, Platform>(catalog.platforms().size() * 2);
        catalog.platforms().forEach(p -> platforms.put(p.id(), p));
        var stations = new HashMap<String, Station>(catalog.stations().size() * 2);
        catalog.stations().forEach(s -> stations.put(s.id(), s));
        var routesById = new HashMap<String, Route>(catalog.routes().size() * 2);
        var boarding = new HashMap<String, List<Boarding>>();
        for (var route : catalog.routes()) {
            routesById.put(route.id(), route);
            double[] cumDist = cumulativeDistances(route, platforms, stations);
            String lineKey = normalizedLineKey(route);
            for (int i = 0; i < route.platforms().size() - 1; i++) {
                var p = platforms.get(route.platforms().get(i));
                if (p != null) {
                    boarding.computeIfAbsent(p.stationId(), ignored -> new ArrayList<>())
                            .add(new Boarding(route, i, cumDist, lineKey));
                }
            }
        }

        Comparator<AltLabel> comparator = effectiveSort == Sort.FEWEST_TRANSFERS
                ? Comparator.comparingInt((AltLabel l) -> l.legs().size()).thenComparingDouble(AltLabel::score)
                : Comparator.comparingDouble(AltLabel::score).thenComparingInt(l -> l.legs().size());
        var queue = new PriorityQueue<>(comparator);
        queue.add(new AltLabel(origin, "", List.of(), List.of(), Set.of(origin), Set.of(), 0, 0, 0.0, 0.0));

        var platformVisits = new HashMap<String, Integer>();
        var stationVisits = new HashMap<String, Integer>();
        int maxPerPlatform = Math.min(4, Math.max(2, targetCount));
        int maxPerStation = Math.min(12, Math.max(4, targetCount * 2 + 2));

        var bestByPattern = new LinkedHashMap<String, ScoredPlan>();
        int minRidesFound = Integer.MAX_VALUE;
        double bestScoreFound = Double.POSITIVE_INFINITY;
        int operations = 0;
        final int maxOperations = 350_000;

        while (!queue.isEmpty()) {
            if (++operations > maxOperations || Thread.currentThread().isInterrupted()) break;
            var label = queue.poll();

            if (!bestByPattern.isEmpty()) {
                int rideCap = Math.max(minRidesFound + 2, 4);
                if (label.legs().size() > rideCap) continue;
                if (effectiveSort == Sort.FASTEST && bestByPattern.size() >= targetCount
                        && label.score() > bestScoreFound * 2.2 + 2_500.0) {
                    break;
                }
                if (effectiveSort == Sort.FEWEST_TRANSFERS && bestByPattern.size() >= targetCount
                        && label.legs().size() > minRidesFound + 1) {
                    break;
                }
            }

            if (label.station().equals(destination) && !label.legs().isEmpty()) {
                var plan = new ConnectionPlan(origin, destination, label.legs(), label.walks());
                double planScore = computeScore(label.distanceBlocks(), label.stops(), plan.transfers(), label.walkSeconds(), effectiveSort);
                if (recordAlternative(bestByPattern, plan, planScore, routesById)) {
                    minRidesFound = Math.min(minRidesFound, label.legs().size());
                    bestScoreFound = Math.min(bestScoreFound, planScore);
                    if (bestByPattern.size() >= targetCount && effectiveSort == Sort.FASTEST) break;
                }
                continue;
            }

            // Direct walking finish from current alighted station to destination
            if (!label.legs().isEmpty()) {
                var tail = walking.transfer(label.station(), label.platform(), destination, "");
                if (tail != null && !label.station().equals(destination)) {
                    var walks = new ArrayList<>(label.walks());
                    int nextWalkSec = label.walkSeconds();
                    if (tail.seconds() > 0) {
                        walks.add(tail.leg(label.station(), destination, 0, label.legs().size()));
                        nextWalkSec += tail.seconds();
                    }
                    var plan = new ConnectionPlan(origin, destination, label.legs(), walks);
                    double planScore = computeScore(label.distanceBlocks(), label.stops(), plan.transfers(), nextWalkSec, effectiveSort);
                    if (recordAlternative(bestByPattern, plan, planScore, routesById)) {
                        minRidesFound = Math.min(minRidesFound, label.legs().size());
                        bestScoreFound = Math.min(bestScoreFound, planScore);
                    }
                }
            }

            String pKey = label.station() + ":" + label.platform();
            int pv = platformVisits.getOrDefault(pKey, 0);
            int sv = stationVisits.getOrDefault(label.station(), 0);
            if (pv >= maxPerPlatform || sv >= maxPerStation) continue;
            platformVisits.put(pKey, pv + 1);
            stationVisits.put(label.station(), sv + 1);

            if (!bestByPattern.isEmpty() && label.legs().size() >= Math.max(minRidesFound + 2, 4)) continue;

            for (String reachable : walking.reachable(label.station())) {
                for (var start : boarding.getOrDefault(reachable, List.of())) {
                    if (label.usedLineKeys().contains(start.lineKey())) continue;
                    var route = start.route();
                    var from = platforms.get(route.platforms().get(start.index()));
                    if (from == null) continue;
                    var transfer = walking.transfer(label.station(), label.platform(), from.stationId(), from.id());
                    if (transfer == null) continue;

                    var nextWalks = new ArrayList<>(label.walks());
                    int nextWalkSec = label.walkSeconds();
                    if (transfer.seconds() > 0) {
                        nextWalks.add(transfer.leg(label.station(), from.stationId(), 0, label.legs().size()));
                        nextWalkSec += transfer.seconds();
                    }
                    var last = platforms.get(route.platforms().getLast());
                    String direction = last == null ? "" : catalog.stationName(last.stationId());
                    var via = new ArrayList<String>();
                    double startDist = start.cumDist()[start.index()];

                    for (int i = start.index() + 1; i < route.platforms().size(); i++) {
                        if (++operations > maxOperations || Thread.currentThread().isInterrupted()) break;
                        var to = platforms.get(route.platforms().get(i));
                        if (to == null) continue;
                        String toStation = to.stationId();

                        if (!toStation.equals(from.stationId()) && !label.visitedStations().contains(toStation)) {
                            int nextRides = label.legs().size() + 1;
                            int nextStops = label.stops() + (i - start.index());
                            double legDist = Math.max(0.0, start.cumDist()[i] - startDist);
                            double nextDist = label.distanceBlocks() + legDist;
                            double nextScore = computeScore(nextDist, nextStops, Math.max(0, nextRides - 1), nextWalkSec, effectiveSort);

                            boolean allowPush = bestByPattern.isEmpty()
                                    || (nextRides <= Math.max(minRidesFound + 2, 4)
                                    && nextScore <= bestScoreFound * 2.4 + 3_000.0);
                            if (allowPush) {
                                int targetPv = platformVisits.getOrDefault(toStation + ":" + to.id(), 0);
                                int targetSv = stationVisits.getOrDefault(toStation, 0);
                                if (toStation.equals(destination) || (targetPv < maxPerPlatform && targetSv < maxPerStation)) {
                                    var nextLegs = new ArrayList<Leg>(label.legs().size() + 1);
                                    nextLegs.addAll(label.legs());
                                    nextLegs.add(new Leg(route.id(), from.stationId(), toStation, from.id(), to.id(),
                                            start.index(), i, direction, List.copyOf(via)));
                                    var nextVisited = new HashSet<String>(label.visitedStations());
                                    nextVisited.add(from.stationId());
                                    nextVisited.add(toStation);
                                    var nextLines = new HashSet<String>(label.usedLineKeys());
                                    nextLines.add(start.lineKey());
                                    queue.add(new AltLabel(toStation, to.id(), List.copyOf(nextLegs), List.copyOf(nextWalks),
                                            nextVisited, nextLines, nextStops, nextWalkSec, nextDist, nextScore));
                                }
                            }
                        }
                        via.add(toStation);
                    }
                }
            }
        }

        if (bestByPattern.isEmpty()) {
            return find(catalog, origin, destination, shared, personal).map(List::of).orElse(List.of());
        }

        Comparator<ScoredPlan> resultOrder = effectiveSort == Sort.FEWEST_TRANSFERS
                ? Comparator.comparingInt(ScoredPlan::transfers).thenComparingDouble(ScoredPlan::score)
                : Comparator.comparingDouble(ScoredPlan::score).thenComparingInt(ScoredPlan::transfers);

        return bestByPattern.values().stream()
                .sorted(resultOrder)
                .limit(targetCount)
                .map(ScoredPlan::plan)
                .toList();
    }

    private static boolean recordAlternative(
            Map<String, ScoredPlan> bestByPattern,
            ConnectionPlan plan,
            double score,
            Map<String, Route> routesById
    ) {
        String key = patternKey(plan, routesById);
        ScoredPlan existing = bestByPattern.get(key);
        if (existing == null || score < existing.score() - 1e-6) {
            bestByPattern.put(key, new ScoredPlan(plan, score, plan.transfers()));
            return existing == null;
        }
        return false;
    }

    private static String patternKey(ConnectionPlan plan, Map<String, Route> routesById) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plan.legs().size(); i++) {
            if (i > 0) sb.append('>');
            var leg = plan.legs().get(i);
            Route route = routesById.get(leg.routeId());
            sb.append(route == null ? leg.routeId() : normalizedLineKey(route));
            if (route != null && route.circular()) {
                sb.append('@').append(leg.direction());
            }
        }
        return sb.toString();
    }

    private static String normalizedLineKey(Route route) {
        if (route == null) return "";
        String name = route.name();
        if (name == null || name.isBlank()) return route.id();
        int split = name.indexOf("||");
        String base = (split >= 0 ? name.substring(0, split) : name).trim();
        return base.isEmpty() ? route.id() : base.toLowerCase(Locale.ROOT);
    }

    private static double computeScore(double distanceBlocks, int stops, int transfers, int walkSeconds, Sort sort) {
        double transferWeight = sort == Sort.FEWEST_TRANSFERS ? 1200.0 : TRANSFER_PENALTY_BLOCKS;
        return distanceBlocks + stops * STOP_PENALTY_BLOCKS + transfers * transferWeight + walkSeconds * WALK_BLOCKS_PER_SECOND;
    }

    private static double[] cumulativeDistances(Route route, Map<String, Platform> platforms, Map<String, Station> stations) {
        int n = route.platforms().size();
        double[] cum = new double[n];
        Station prev = null;
        for (int i = 0; i < n; i++) {
            Platform p = platforms.get(route.platforms().get(i));
            Station cur = p == null ? null : stations.get(p.stationId());
            if (i > 0) {
                double step = (prev != null && cur != null) ? Math.hypot(cur.x() - prev.x(), cur.z() - prev.z()) : 0.0;
                cum[i] = cum[i - 1] + step;
            }
            if (cur != null) prev = cur;
        }
        return cum;
    }
}
