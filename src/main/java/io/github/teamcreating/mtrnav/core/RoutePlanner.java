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
    private record Distance(String station, long millis) {}
    private record RideDistance(String station, int rides) {}

    public List<Journey> find(Snapshot snapshot, String origin, String destination, Sort sort, Settings settings) {
        if (Thread.currentThread().isInterrupted()) throw new SearchLimitException("Search cancelled");
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
        var reverse = new HashMap<String, Map<String, Long>>();
        for (var run : runs.values()) {
            run.sort(Comparator.comparingInt(StopEvent::stopIndex));
            for (int i = 0; i < run.size() - 1; i++) {
                budget.step();
                var p = platforms.get(run.get(i).platformId());
                if (p != null) boarding.computeIfAbsent(p.stationId(), ignored -> new ArrayList<>()).add(new Candidate(run, i));
                var next = platforms.get(run.get(i + 1).platformId());
                if (p != null && next != null) reverse.computeIfAbsent(next.stationId(), ignored -> new HashMap<>())
                        .merge(p.stationId(), Math.max(0, run.get(i + 1).arrival() - run.get(i).departure()), Math::min);
            }
        }
        for (var station : stations.values()) for (String connection : station.connections())
            if (stations.containsKey(connection)) reverse.computeIfAbsent(connection, ignored -> new HashMap<>()).put(station.id(), 0L);
        // An admissible remaining travel time: ignore all waiting, dwell and walking.
        // This also excludes branches that cannot reach the destination at all.
        var remaining = new HashMap<String, Long>(); remaining.put(destination, 0L);
        var distances = new PriorityQueue<Distance>(Comparator.comparingLong(Distance::millis));
        distances.add(new Distance(destination, 0));
        while (!distances.isEmpty()) {
            budget.step(); var current = distances.poll();
            if (current.millis() != remaining.get(current.station())) continue;
            for (var edge : reverse.getOrDefault(current.station(), Map.of()).entrySet()) {
                budget.step(); long cost = current.millis() + edge.getValue();
                if (cost < remaining.getOrDefault(edge.getKey(), Long.MAX_VALUE)) {
                    remaining.put(edge.getKey(), cost); distances.add(new Distance(edge.getKey(), cost));
                }
            }
        }
        if (!remaining.containsKey(origin)) return List.of();
        var rideReverse = new HashMap<String, Map<String, Integer>>();
        var patterns = new HashSet<List<String>>();
        boolean throughService = false;
        for (var run : runs.values()) {
            var first = run.getFirst(); var key = first.run();
            if (runs.containsKey(new RunKey(key.sidingId(), key.tripIndex() + 1, key.departureIndex(), key.departureOffset()))) throughService = true;
            var path = run.stream().map(e -> platforms.get(e.platformId())).filter(Objects::nonNull).map(Platform::stationId).filter(remaining::containsKey).toList();
            if (!patterns.add(path)) continue;
            for (int i = 0; i < path.size(); i++) for (int j = i + 1; j < path.size(); j++) {
                budget.step(); rideReverse.computeIfAbsent(path.get(j), ignored -> new HashMap<>()).put(path.get(i), 1);
            }
        }
        for (var station : stations.values()) for (String connection : station.connections())
            if (stations.containsKey(connection)) rideReverse.computeIfAbsent(connection, ignored -> new HashMap<>()).put(station.id(), 0);
        var ridesRemaining = new HashMap<String, Integer>(); ridesRemaining.put(destination, 0);
        var rideQueue = new PriorityQueue<RideDistance>(Comparator.comparingInt(RideDistance::rides));
        rideQueue.add(new RideDistance(destination, 0));
        while (!rideQueue.isEmpty()) {
            budget.step(); var current = rideQueue.poll();
            if (current.rides() != ridesRemaining.get(current.station())) continue;
            for (var edge : rideReverse.getOrDefault(current.station(), Map.of()).entrySet()) {
                budget.step(); int cost = current.rides() + edge.getValue();
                if (cost < ridesRemaining.getOrDefault(edge.getKey(), Integer.MAX_VALUE)) {
                    ridesRemaining.put(edge.getKey(), cost); rideQueue.add(new RideDistance(edge.getKey(), cost));
                }
            }
        }
        // A native through service may continue on another route without boarding.
        // In that case use the conservative zero lower bound for changes.
        final boolean hasThroughService = throughService;
        java.util.function.ToIntFunction<Label> changes = l -> l.transfers() + (hasThroughService ? 0
                : Math.max(0, ridesRemaining.getOrDefault(l.station(), 0) - (l.legs().isEmpty() ? 1 : 0)));
        java.util.function.ToLongFunction<Label> arrivalBound = l -> l.time() + remaining.get(l.station())
                + (changes.applyAsInt(l) - l.transfers()) * settings.transferBufferSeconds() * 1000L;
        for (var candidates : boarding.values()) candidates.sort(Comparator.comparingLong(c -> c.stops().get(c.boardIndex()).departure()));
        Comparator<Label> order = sort == Sort.FASTEST
                ? Comparator.comparingLong(arrivalBound).thenComparingInt(changes).thenComparingLong(l -> remaining.get(l.station()))
                : Comparator.comparingInt(changes).thenComparingLong(arrivalBound).thenComparingLong(l -> remaining.get(l.station()));
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
            for (String stationId : reachable) {
              var candidates = boarding.getOrDefault(stationId, List.of());
              for (int c = firstDeparture(candidates, label.time()); c < candidates.size(); c++) {
                var candidate = candidates.get(c);
                budget.step();
                var from = candidate.stops().get(candidate.boardIndex());
                if (from.departure() > deadline) break;
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
                    if (platform == null) continue;
                    if (!remaining.containsKey(platform.stationId()) || to.arrival() < from.departure() || to.arrival() + remaining.get(platform.stationId()) > deadline) {
                        via.add(platform.stationId()); continue;
                    }
                    var leg = new RideLeg(from.run(), from.vehicleId(), from.routeId(), stationId, platform.stationId(),
                            from.platformId(), to.platformId(), from.stopIndex(), to.stopIndex(), from.destination(), from.departure(), to.arrival(),
                            from.realtime() && to.realtime(), via);
                    var nextLegs = new ArrayList<>(label.legs()); nextLegs.add(leg);
                    var used = new HashSet<>(label.usedRuns()); used.add(from.run());
                    String signature = label.signature() + "/" + from.run() + ":" + from.stopIndex() + "-" + to.stopIndex();
                    var next = new Label(platform.stationId(), to.platformId(), to.arrival(), transfers, List.copyOf(nextLegs), Set.copyOf(used), signature);
                    if (changes.applyAsInt(next) <= settings.maxTransfers() && admit(next, labels, order)) {
                        if (queue.size() >= MAX_QUEUED_LABELS) {
                            queue.removeIf(old -> !labels.getOrDefault(old.platform() + ":" + old.transfers(), List.of()).contains(old));
                            if (queue.size() >= MAX_QUEUED_LABELS) throw new SearchLimitException("Search queue budget exceeded");
                        }
                        queue.add(next);
                    }
                    via.add(platform.stationId());
                }
              }
            }
        } } catch (SearchLimitException limit) {
            // Additional alternatives must never discard a usable connection already found.
            if (results.isEmpty()) throw limit;
        }
        return List.copyOf(results);
    }

    private static int firstDeparture(List<Candidate> candidates, long time) {
        int low = 0, high = candidates.size();
        while (low < high) {
            int mid = (low + high) >>> 1; var c = candidates.get(mid);
            if (c.stops().get(c.boardIndex()).departure() < time) low = mid + 1; else high = mid;
        }
        return low;
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
