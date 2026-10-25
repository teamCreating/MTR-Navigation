package io.github.teamcreating.mtrnav.server;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.mixin.*;
import io.github.teamcreating.mtrnav.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.simulation.Simulator;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Server-thread sessions, simulator-thread reads, bounded background search/encoding. */
public final class NavigationServer {
    private NavigationServer() {}
    private static MinecraftServer owner;
    private static Settings settings = Settings.defaults();
    private static ThreadPoolExecutor workers;
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<String, Source> sources = new HashMap<>();
    private static final AtomicLong transferIds = new AtomicLong(System.currentTimeMillis());
    private static BiConsumer<ServerPlayer, NavChunk> sender = (p, c) -> {};
    private static final System.Logger LOG = System.getLogger("MTR Navigation");

    private static final class Session {
        long seen, queryId;
        final RequestBudget budget = new RequestBudget();
        String dimension;
        Map<String, Journey> offered = Map.of();
        JourneyTracker tracker;
        final ArrayDeque<NavChunk> outbound = new ArrayDeque<>();
        Session(String dimension) { this.dimension = dimension; }
    }
    private static final class Source {
        final Simulator simulator;
        final MtrCoreReader reader = new MtrCoreReader();
        CompletableFuture<Snapshot> pending;
        Snapshot latest;
        long capturedNanos;
        Source(Simulator simulator) { this.simulator = simulator; }
    }
    public static void start(MinecraftServer server, Path configDirectory, BiConsumer<ServerPlayer, NavChunk> send) {
        stop(); owner = server; sender = send; settings = NavigationConfig.load(configDirectory);
        workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            var thread = new Thread(runnable, "mtrnav-query"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public static void stop() {
        if (workers != null) workers.shutdownNow();
        sources.values().forEach(s -> { if (s.pending != null) s.pending.cancel(false); });
        sources.clear(); sessions.clear(); owner = null;
    }
    public static void tick() {
        if (owner == null) return;
        long now = System.nanoTime();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().seen > 10_000_000_000L || owner.getPlayerList().getPlayer(entry.getKey()) == null);
        for (var entry : sessions.entrySet()) {
            var player = owner.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !dimension(player).equals(entry.getValue().dimension)) continue;
            for (int i = 0; i < 4 && !entry.getValue().outbound.isEmpty(); i++) sender.accept(player, entry.getValue().outbound.removeFirst());
        }
        sources.entrySet().removeIf(e -> sessions.values().stream().noneMatch(s -> s.dimension.equals(e.getKey())));
    }
    public static void receive(ServerPlayer player, NavRequest packet) {
        if (owner == null || player.getServer() != owner) return;
        final Messages.Request request;
        try { request = WireCodec.GSON.fromJson(packet.json(), Messages.Request.class); }
        catch (RuntimeException e) { return; }
        if (request == null || !dimension(player).equals(request.dimension())) return;
        var session = sessions.computeIfAbsent(player.getUUID(), ignored -> new Session(request.dimension()));
        long now = System.nanoTime(); session.seen = now;
        if (!session.budget.allow(now) && !request.action().equals("cancel")) {
            if (!request.action().equals("poll")) fail(player, session, request, "rate_limited");
            return;
        }
        if (!session.dimension.equals(request.dimension())) {
            sessions.remove(player.getUUID()); session = new Session(request.dimension()); sessions.put(player.getUUID(), session);
        }
        if (request.action().equals("query")) session.queryId = request.id();
        var context = session;
        var server = owner;
        try {
            snapshot(request.dimension()).orTimeout(8, TimeUnit.SECONDS).whenComplete((snapshot, error) -> server.execute(() -> {
                if (owner != server || sessions.get(player.getUUID()) != context || !dimension(player).equals(request.dimension())) return;
                if (error != null) { fail(player, context, request, "core_unavailable"); LOG.log(System.Logger.Level.WARNING, "MTR read failed", error); return; }
                handle(player, context, request, snapshot);
            }));
        } catch (RuntimeException e) { fail(player, context, request, "core_unavailable"); }
    }
    private static CompletableFuture<Snapshot> snapshot(String dimension) {
        var main = MtrAccessor.navigationMain();
        if (main == null) return CompletableFuture.failedFuture(new IllegalStateException("MTR not ready"));
        var simulator = ((MainAccessor) main).navigationSimulators().stream().filter(s -> s.dimension.equals(dimension)).findFirst().orElseThrow();
        var source = sources.computeIfAbsent(dimension, ignored -> new Source(simulator));
        if (source.latest != null && System.nanoTime() - source.capturedNanos < 900_000_000L) return CompletableFuture.completedFuture(source.latest);
        if (source.pending != null && !source.pending.isDone()) return source.pending;
        var result = new CompletableFuture<Snapshot>(); source.pending = result;
        var server = owner; var config = settings;
        simulator.run(() -> {
            try {
                var snapshot = source.reader.capture(simulator, config);
                server.execute(() -> {
                    if (owner == server) { source.latest = snapshot; source.capturedNanos = System.nanoTime(); result.complete(snapshot); }
                    else result.cancel(false);
                });
            } catch (Throwable e) { result.completeExceptionally(e); }
        });
        return result;
    }
    private static void handle(ServerPlayer player, Session session, Messages.Request request, Snapshot snapshot) {
        boolean catalogChanged = request.revision() != snapshot.catalog().revision();
        switch (request.action()) {
            case "hello" -> reply(player, session, response(request, snapshot, snapshot.catalog(), List.of(), List.of(), null, ""));
            case "query" -> {
                var server = owner; var config = settings;
                execute(() -> {
                    try {
                        var results = new RoutePlanner().find(snapshot, request.origin(), request.destination(), request.sort(), config);
                        var reference = results.isEmpty() ? io.github.teamcreating.mtrnav.core.ConnectionPlan.find(snapshot.catalog(), request.origin(), request.destination()).orElse(null) : null;
                        String message = reference == null ? "" : reference.transfers() > config.maxTransfers() ? "reference_limit" : "reference_no_service";
                        server.execute(() -> {
                            if (owner != server || sessions.get(player.getUUID()) != session || session.queryId != request.id()) return;
                            var offered = new HashMap<String, Journey>(); results.forEach(j -> offered.put(j.id(), j)); session.offered = Map.copyOf(offered);
                            reply(player, session, response(request, snapshot, catalogChanged ? snapshot.catalog() : null, results, List.of(), null, message));
                        });
                    } catch (RoutePlanner.SearchLimitException e) { server.execute(() -> fail(player, session, request, "search_budget")); }
                    catch (Exception e) { server.execute(() -> fail(player, session, request, "search_failed")); }
                }, () -> fail(player, session, request, "busy"));
            }
            case "select" -> {
                var journey = session.offered.get(request.journeyId());
                if (journey == null || journey.revision() != snapshot.catalog().revision()) { fail(player, session, request, "network_changed"); return; }
                session.tracker = new JourneyTracker(journey);
                reply(player, session, response(request, snapshot, catalogChanged ? snapshot.catalog() : null, List.of(), List.of(), session.tracker.update(snapshot, player.getUUID()), ""));
            }
            case "cancel" -> {
                session.tracker = null;
                reply(player, session, response(request, snapshot, catalogChanged ? snapshot.catalog() : null, List.of(), List.of(), null, ""));
            }
            case "correct", "poll" -> {
                JourneyTracker.State tracking = null;
                if (session.tracker != null) {
                    if (request.action().equals("correct") && session.tracker.state(false).journey().id().equals(request.journeyId())) session.tracker.correct(request.correction());
                    tracking = session.tracker.update(snapshot, player.getUUID());
                }
                var platformIds = new HashSet<String>();
                snapshot.catalog().platforms().stream().filter(p -> p.stationId().equals(request.stationId())).forEach(p -> platformIds.add(p.id()));
                var counts = new HashMap<String, Integer>();
                var arrivals = snapshot.events().stream().filter(e -> platformIds.contains(e.platformId()) && e.departure() >= snapshot.sampleMillis())
                        .sorted(Comparator.comparingLong(StopEvent::departure)).filter(e -> counts.merge(e.platformId(), 1, Integer::sum) <= DepartureBoard.PER_PLATFORM).limit(DepartureBoard.TOTAL).toList();
                reply(player, session, response(request, snapshot, catalogChanged ? snapshot.catalog() : null, List.of(), arrivals, tracking, ""));
            }
            default -> { }
        }
    }
    private static Messages.Response response(Messages.Request request, Snapshot snapshot, Catalog catalog, List<Journey> journeys,
                                               List<StopEvent> arrivals, JourneyTracker.State tracking, String message) {
        return new Messages.Response(request.action(), request.dimension(), request.id(), snapshot.catalog().revision(), snapshot.sampleMillis(), catalog, journeys, arrivals, tracking, message);
    }
    private static void fail(ServerPlayer player, Session session, Messages.Request request, String message) {
        if (owner == null || sessions.get(player.getUUID()) != session) return;
        reply(player, session, new Messages.Response(request.action(), request.dimension(), request.id(), request.revision(), 0, null, List.of(), List.of(),
                session.tracker == null ? null : session.tracker.state(false), message));
    }
    private static void reply(ServerPlayer player, Session session, Messages.Response response) {
        var server = owner;
        execute(() -> {
            try {
                byte[] bytes = WireCodec.encode(response);
                int total = (bytes.length + WireCodec.CHUNK_BYTES - 1) / WireCodec.CHUNK_BYTES;
                server.execute(() -> {
                    if (owner == server && sessions.get(player.getUUID()) == session && dimension(player).equals(response.dimension())) {
                        long transfer = transferIds.incrementAndGet();
                        if (session.outbound.size() + total > 700) session.outbound.clear();
                        for (int i = 0; i < total; i++) session.outbound.add(new NavChunk(transfer, i, total,
                                Arrays.copyOfRange(bytes, i * WireCodec.CHUNK_BYTES, Math.min(bytes.length, (i + 1) * WireCodec.CHUNK_BYTES))));
                    }
                });
            } catch (Exception e) { LOG.log(System.Logger.Level.WARNING, "Cannot encode navigation response", e); }
        }, () -> LOG.log(System.Logger.Level.WARNING, "Navigation response queue full"));
    }
    private static void execute(Runnable work, Runnable rejected) {
        try { if (workers == null || workers.isShutdown()) rejected.run(); else workers.execute(work); }
        catch (RejectedExecutionException e) { rejected.run(); }
    }
    private static String dimension(ServerPlayer player) {
        var location = player.serverLevel().dimension().location(); return location.getNamespace() + "/" + location.getPath();
    }
}
