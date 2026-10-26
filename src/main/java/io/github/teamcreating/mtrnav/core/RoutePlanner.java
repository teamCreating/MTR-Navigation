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
    private record Label(String station, String platform, long time, int transfers, List<RideLeg> legs, Set<RunKey> usedRuns, String signature, List<WalkLeg> walks, RunKey continuation) {}
    private record Candidate(List<StopEvent> stops, int boardIndex) {}
    private record Distance(String station, long millis) {}
    private record RideDistance(String station, int rides) {}

    public List<Journey> find(Snapshot snapshot, String origin, String destination, Sort sort, Settings settings) {
        return search(snapshot, origin, destination, sort, settings, QueryOptions.legacy(), List.of(), List.of(), 0).journeys();
    }
    public SearchResult search(Snapshot snapshot, String origin, String destination, Sort sort, Settings settings,
                               QueryOptions options, List<WalkingConnection> shared, List<WalkingConnection> personal, long walkingRevision) {
        if (Thread.currentThread().isInterrupted()) throw new SearchLimitException("Search cancelled");
        var budget = new Budget();
        var catalog = snapshot.catalog();
        if (catalog.station(origin) == null || catalog.station(destination) == null) throw new IllegalArgumentException("Unknown station");
        if (origin.equals(destination)) return new SearchResult(List.of(new Journey(UUID.randomUUID().toString(), catalog.dimension(), catalog.revision(), origin, destination,
                snapshot.sampleMillis(), snapshot.sampleMillis(), 0, List.of())), snapshot.sampleMillis(), false);
        var walking = new WalkingGraph(catalog, shared, personal);
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
        for (var station : stations.values()) for (String connection : walking.reachable(station.id()))
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
        if (!remaining.containsKey(origin)) return new SearchResult(List.of(), 0, false);
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
        for (var station : stations.values()) for (String connection : walking.reachable(station.id()))
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
        Comparator<Label> requestedOrder = sort == Sort.FASTEST
                ? Comparator.comparingLong(arrivalBound).thenComparingInt(changes).thenComparingLong(l -> remaining.get(l.station()))
                : Comparator.comparingInt(changes).thenComparingLong(arrivalBound).thenComparingLong(l -> remaining.get(l.station()));
        Comparator<Label> order = Comparator.comparingLong(arrivalBound).thenComparingInt(changes).thenComparingLong(l -> remaining.get(l.station()));
        if (!options.distinct()) order = requestedOrder;
        var queue = new PriorityQueue<>(order);
        queue.add(new Label(origin, "", snapshot.sampleMillis(), 0, List.of(), Set.of(), "", List.of(), null));
        var labels = new HashMap<String, List<Label>>();
        var results = new ArrayList<Journey>(); var signatures = new HashSet<String>();
        long deadline = snapshot.sampleMillis() + settings.horizonHours() * 3_600_000L;
        int expansions = 0; boolean incomplete = false, baselineFound = false; long fastest = 0;
        int wanted = options.limit() + (sort == Sort.FEWEST_TRANSFERS && options.distinct() ? 1 : 0);
        try { while (!queue.isEmpty() && results.size() < wanted) {
            if (++expansions > MAX_EXPANSIONS || Thread.currentThread().isInterrupted()) throw new SearchLimitException("Search budget exceeded");
            var label = queue.poll();
            if (label.time() > deadline) continue;
            // Labels displaced from the retained alternatives no longer need expanding.
            if (!label.station().equals(destination) && !label.legs().isEmpty() && !labels.getOrDefault(label.platform() + ":" + label.transfers(), List.of()).contains(label)) continue;
            if (label.station().equals(destination)) {
                String signature = label.signature();
                if (signatures.add(signature)) {
                    results.add(new Journey(UUID.randomUUID().toString(), catalog.dimension(), catalog.revision(), origin, destination,
                            snapshot.sampleMillis(), label.time(), label.transfers(), label.legs(), label.walks(), walkingRevision, settings.transferBufferSeconds()));
                    if (!baselineFound) {
                        baselineFound = true; fastest = label.time();
                        if (options.extraMinutes() >= 0) deadline = Math.min(deadline, fastest + options.extraMinutes() * 60_000L);
                        if (options.distinct()) { order = requestedOrder; var reordered = new PriorityQueue<Label>(order); reordered.addAll(queue); queue = reordered; }
                    }
                }
                continue;
            }
            // Follow only MTR-declared connections. No geographic connection is invented.
            if (options.distinct() && !label.legs().isEmpty()) {
                var tail = walking.transfer(label.station(), label.platform(), destination, "");
                if (tail != null && tail.seconds() > 0 && label.time() + tail.seconds() * 1000L <= deadline) {
                    var walks = new ArrayList<>(label.walks()); walks.add(tail.leg(label.station(), destination, label.time(), label.legs().size()));
                    if (queue.size() >= MAX_QUEUED_LABELS) throw new SearchLimitException("Search queue budget exceeded");
                    queue.add(new Label(destination, label.platform(), label.time() + tail.seconds() * 1000L, label.transfers(), label.legs(), label.usedRuns(),
                            label.signature() + "/walk:" + tail.id(), List.copyOf(walks), null));
                }
            }
            var reachable = walking.reachable(label.station());
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
                var transfer = continuation ? null : walking.transfer(label.station(), label.platform(), stationId, from.platformId());
                if (!continuation && transfer == null) continue;
                var nextWalks = new ArrayList<>(label.walks());
                if (!continuation && (!label.legs().isEmpty() || !stationId.equals(label.station()))) {
                    ready += transfer.seconds() * 1000L;
                    if (options.distinct() && transfer.seconds() > 0) nextWalks.add(transfer.leg(label.station(), stationId, label.time(), label.legs().size()));
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
                    String pattern = from.routeId() + ":" + from.platformId() + ":" + from.stopIndex() + ">" + to.platformId() + ":" + to.stopIndex();
                    String signature = options.distinct() ? pathPattern(nextLegs, nextWalks) : label.signature() + "/" + from.run() + ":" + from.stopIndex() + "-" + to.stopIndex();
                    var nextRun = new RunKey(from.run().sidingId(), from.run().tripIndex() + 1, from.run().departureIndex(), from.run().departureOffset());
                    var continuationKey = runs.containsKey(nextRun) ? nextRun : null;
                    var next = new Label(platform.stationId(), to.platformId(), to.arrival(), transfers, List.copyOf(nextLegs), Set.copyOf(used), signature, List.copyOf(nextWalks), continuationKey);
                    if (changes.applyAsInt(next) <= settings.maxTransfers() && admit(next, labels, order)) {
                        if (queue.size() >= MAX_QUEUED_LABELS) {
                            queue.removeIf(old -> !old.station().equals(destination) && !labels.getOrDefault(old.platform() + ":" + old.transfers(), List.of()).contains(old));
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
            incomplete = true;
        }
        long baseline = fastest;
        Comparator<Journey> arrivalOrder = Comparator.comparingLong(Journey::arrival).thenComparingInt(Journey::transfers).thenComparingLong(Journey::walkingMillis).thenComparing(RoutePlanner::pattern);
        Comparator<Journey> resultOrder = sort == Sort.FASTEST ? arrivalOrder : Comparator.comparingInt(Journey::transfers).thenComparing(arrivalOrder);
        return new SearchResult(results.stream().filter(j -> options.extraMinutes() < 0 || j.arrival() <= baseline + options.extraMinutes() * 60_000L)
                .sorted(resultOrder).limit(options.limit()).toList(), baseline, incomplete);
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
        var same = bucket.stream().filter(l -> l.signature().equals(candidate.signature()) && Objects.equals(l.continuation(), candidate.continuation())).toList();
        if (same.stream().anyMatch(l -> l.time() <= candidate.time())) return false;
        bucket.removeAll(same);
        bucket.add(candidate); bucket.sort(order);
        // Distinct paths get their own share; repeated trains cannot consume all path slots.
        var paths = new LinkedHashMap<String, List<Label>>();
        for (var label : bucket) paths.computeIfAbsent(label.signature(), ignored -> new ArrayList<>()).add(label);
        var retained = new ArrayList<Label>(); int patterns = 0;
        for (var group : paths.values()) { if (++patterns > 12) break; retained.addAll(group.stream().limit(2).toList()); }
        bucket.retainAll(retained);
        return bucket.contains(candidate);
    }
    public static boolean continues(RideLeg previous, StopEvent next) {
        return previous.toPlatformId().equals(next.platformId()) && previous.run().sidingId().equals(next.run().sidingId())
                && previous.run().departureIndex() == next.run().departureIndex() && previous.run().departureOffset() == next.run().departureOffset()
                && previous.run().tripIndex() + 1 == next.run().tripIndex();
    }
    public static String pattern(Journey journey) { return pathPattern(journey.legs(), journey.walks()); }
    private static String pathPattern(List<RideLeg> legs, List<WalkLeg> walks) {
        var parts = new ArrayList<String>();
        RideLeg merged = null;
        for (int i = 0; i <= legs.size(); i++) {
            final int index = i;
            var before = walks.stream().filter(w -> w.beforeRideIndex() == index).toList();
            if (!before.isEmpty()) { merged = null; for (var walk : before) parts.add("walk:" + walk.connectionId()); }
            if (i == legs.size()) break;
            var ride = legs.get(i);
            if (merged != null && merged.routeId().equals(ride.routeId()) && merged.toPlatformId().equals(ride.fromPlatformId()) && merged.toStopIndex() == ride.fromStopIndex()) {
                parts.removeLast();
                merged = new RideLeg(ride.run(), "", ride.routeId(), merged.fromStationId(), ride.toStationId(), merged.fromPlatformId(), ride.toPlatformId(), merged.fromStopIndex(), ride.toStopIndex(), ride.direction(), merged.departure(), ride.arrival(), false, List.of());
            } else merged = ride;
            parts.add(merged.routeId() + ":" + merged.fromPlatformId() + ":" + merged.fromStopIndex() + ">" + merged.toPlatformId() + ":" + merged.toStopIndex());
        }
        return String.join("/", parts);
    }
}
