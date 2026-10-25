package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Multi-label timetable search. A ride follows a single native run and ordered stop occurrences. */
public final class RoutePlanner {
    private static final int MAX_EXPANSIONS = 100_000;
    private static final int MAX_QUEUED_LABELS = 10_000;
    public static final class SearchLimitException extends IllegalStateException {
        public SearchLimitException(String message) { super(message); }
    }
    private static final class Budget {
        private final long expires = System.nanoTime() + 5_000_000_000L;
        private int operations;
        void step() {
            if ((++operations & 1023) == 0 && (operations > 2_000_000 || System.nanoTime() > expires || Thread.currentThread().isInterrupted()))
                throw new SearchLimitException("Search budget exceeded");
        }
    }
    private record Label(String station, String platform, long time, int transfers, List<RideLeg> legs, Set<RunKey> usedRuns, String signature) {}
    private record Candidate(List<StopEvent> stops, int boardIndex) {}

    public List<Journey> find(Snapshot snapshot, String origin, String destination, Sort sort, Settings settings) {
        var budget = new Budget();
        var catalog = snapshot.catalog();
        if (catalog.station(origin) == null || catalog.station(destination) == null) throw new IllegalArgumentException("Unknown station");
        if (origin.equals(destination)) return List.of(new Journey(UUID.randomUUID().toString(), catalog.dimension(), catalog.revision(), origin, destination,
                snapshot.sampleMillis(), snapshot.sampleMillis(), 0, List.of()));
        var stations = new HashMap<String, Station>(); catalog.stations().forEach(s -> stations.put(s.id(), s));
        var platforms = new HashMap<String, Platform>(); catalog.platforms().forEach(p -> platforms.put(p.id(), p));
        var runs = new HashMap<RunKey, List<StopEvent>>();
        for (var event : snapshot.events()) { budget.step(); runs.computeIfAbsent(event.run(), ignored -> new ArrayList<>()).add(event); }
        var boarding = new HashMap<String, List<Candidate>>();
        for (var run : runs.values()) {
            run.sort(Comparator.comparingInt(StopEvent::stopIndex));
            for (int i = 0; i < run.size() - 1; i++) {
                budget.step();
                var p = platforms.get(run.get(i).platformId());
                if (p != null) boarding.computeIfAbsent(p.stationId(), ignored -> new ArrayList<>()).add(new Candidate(run, i));
            }
        }
        Comparator<Label> order = sort == Sort.FASTEST
                ? Comparator.comparingLong(Label::time).thenComparingInt(Label::transfers)
                : Comparator.comparingInt(Label::transfers).thenComparingLong(Label::time);
        var queue = new PriorityQueue<>(order);
        queue.add(new Label(origin, "", snapshot.sampleMillis(), 0, List.of(), Set.of(), ""));
        var labels = new HashMap<String, List<Label>>();
        var results = new ArrayList<Journey>(); var signatures = new HashSet<String>();
        long deadline = snapshot.sampleMillis() + settings.horizonHours() * 3_600_000L;
        int expansions = 0;
        try { while (!queue.isEmpty() && results.size() < 3) {
            if (++expansions > MAX_EXPANSIONS || Thread.currentThread().isInterrupted()) throw new SearchLimitException("Search budget exceeded");
            var label = queue.poll();
            // Labels displaced from the retained alternatives no longer need expanding.
            if (!label.legs().isEmpty() && !labels.getOrDefault(label.platform() + ":" + label.transfers(), List.of()).contains(label)) continue;
            if (label.station().equals(destination)) {
                String signature = label.signature();
                if (signatures.add(signature)) results.add(new Journey(UUID.randomUUID().toString(), catalog.dimension(), catalog.revision(), origin, destination,
                        snapshot.sampleMillis(), label.time(), label.transfers(), label.legs()));
                continue;
            }
            // Follow only MTR-declared connections. No geographic connection is invented.
            var reachable = connected(label.station(), stations);
            for (String stationId : reachable) for (var candidate : boarding.getOrDefault(stationId, List.of())) {
                budget.step();
                var from = candidate.stops().get(candidate.boardIndex());
                boolean continuation = !label.legs().isEmpty() && continues(label.legs().getLast(), from);
                int transfers = label.transfers() + (label.legs().isEmpty() || continuation ? 0 : 1);
                if (transfers > settings.maxTransfers()) continue;
                if (label.usedRuns().contains(from.run())) continue;
                long ready = label.time();
                if (!continuation && (!label.legs().isEmpty() || !stationId.equals(label.station()))) {
                    ready += walkingMillis(platforms.get(label.platform()), platforms.get(from.platformId()), stations.get(label.station()), stations.get(stationId));
                    ready += settings.transferBufferSeconds() * 1000L;
                }
                if (from.departure() < ready || from.departure() > deadline) continue;
                var via = new ArrayList<String>();
                for (int i = candidate.boardIndex() + 1; i < candidate.stops().size(); i++) {
                    budget.step();
                    var to = candidate.stops().get(i); var platform = platforms.get(to.platformId());
                    if (platform == null || to.arrival() < from.departure() || to.arrival() > deadline) continue;
                    var leg = new RideLeg(from.run(), from.vehicleId(), from.routeId(), stationId, platform.stationId(),
                            from.platformId(), to.platformId(), from.stopIndex(), to.stopIndex(), from.destination(), from.departure(), to.arrival(),
                            from.realtime() && to.realtime(), via);
                    var nextLegs = new ArrayList<>(label.legs()); nextLegs.add(leg);
                    var used = new HashSet<>(label.usedRuns()); used.add(from.run());
                    String signature = label.signature() + "/" + from.run() + ":" + from.stopIndex() + "-" + to.stopIndex();
                    var next = new Label(platform.stationId(), to.platformId(), to.arrival(), transfers, List.copyOf(nextLegs), Set.copyOf(used), signature);
                    if (admit(next, labels, order)) {
                        if (queue.size() >= MAX_QUEUED_LABELS) throw new SearchLimitException("Search queue budget exceeded");
                        queue.add(next);
                    }
                    via.add(platform.stationId());
                }
            }
        } } catch (SearchLimitException limit) {
            // Additional alternatives must never discard a usable connection already found.
            if (results.isEmpty()) throw limit;
        }
        return List.copyOf(results);
    }

    private static boolean admit(Label candidate, Map<String, List<Label>> labels, Comparator<Label> order) {
        // Keep several distinct paths at each platform/boarding count so alternative results survive.
        String key = candidate.platform() + ":" + candidate.transfers();
        var bucket = labels.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (bucket.stream().anyMatch(l -> l.signature().equals(candidate.signature()))) return false;
        bucket.add(candidate); bucket.sort(order);
        if (bucket.size() > 12) return bucket.removeLast() != candidate;
        return true;
    }
    private static Set<String> connected(String station, Map<String, Station> stations) {
        var found = new LinkedHashSet<String>(); found.add(station);
        var s = stations.get(station);
        if (s != null) for (String id : s.connections()) if (stations.containsKey(id)) found.add(id);
        return found;
    }
    public static boolean continues(RideLeg previous, StopEvent next) {
        return previous.toPlatformId().equals(next.platformId()) && previous.run().sidingId().equals(next.run().sidingId())
                && previous.run().departureIndex() == next.run().departureIndex() && previous.run().departureOffset() == next.run().departureOffset()
                && previous.run().tripIndex() + 1 == next.run().tripIndex();
    }
    private static long walkingMillis(Platform a, Platform b, Station start, Station end) {
        double dx = (a == null ? start.x() : a.x()) - (b == null ? end.x() : b.x());
        double dy = (a == null ? start.y() : a.y()) - (b == null ? end.y() : b.y());
        double dz = (a == null ? start.z() : a.z()) - (b == null ? end.z() : b.z());
        return (long) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 4.3 * 1000);
    }
}
