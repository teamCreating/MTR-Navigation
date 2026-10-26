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
        ServerPlayer playerIdentity;
        long seen, queryId;
        final RequestBudget budget = new RequestBudget(), v2Uploads = new RequestBudget();
        String dimension;
        Map<String, Journey> offered = Map.of();
        JourneyTracker tracker;
        FutureTask<Void> searchTask;
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
    public static void start(MinecraftServer server, Path configDirectory, BiConsumer<ServerPlayer, NavChunk> send, BiConsumer<ServerPlayer, WalkingChunk> walkingSend) {
        stop(); owner = server; sender = send; settings = NavigationConfig.load(configDirectory);
        WalkingServer.start(server, walkingSend, NavigationServer::snapshot);
        workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            var thread = new Thread(runnable, "mtrnav-query"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public static void stop() {
        WalkingServer.stop();
        sessions.values().forEach(NavigationServer::cancelSearch);
        if (workers != null) workers.shutdownNow();
        sources.values().forEach(s -> { if (s.pending != null) s.pending.cancel(false); });
        sources.clear(); sessions.clear(); owner = null;
    }
    public static void tick() {
        if (owner == null) return;
        WalkingServer.tick();
        long now = System.nanoTime();
        sessions.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue().seen > 10_000_000_000L || owner.getPlayerList().getPlayer(entry.getKey()) != entry.getValue().playerIdentity;
            if (expired) cancelSearch(entry.getValue());
            return expired;
        });
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
        try { NetworkLimits.upload(packet.json()); request = WireCodec.GSON.fromJson(packet.json(), Messages.Request.class); }
        catch (RuntimeException e) { return; }
        receive(player, request, null);
    }
    public static void receiveV2(ServerPlayer player, NavRequestV2 packet) {
        if (owner == null || player.getServer() != owner) return;
        var upload = session(player, dimension(player));
        upload.seen = System.nanoTime(); if (!upload.v2Uploads.allow(upload.seen)) return;
        try { var request = WireCodec.GSON.fromJson(packet.json(), QueryV2.Request.class); if (request != null) receive(player, request.request(), request); }
        catch (RuntimeException ignored) { }
    }
    private static void receive(ServerPlayer player, Messages.Request request, QueryV2.Request extended) {
        if (owner == null || player.getServer() != owner || request == null || !dimension(player).equals(request.dimension())) return;
        WalkingServer.touch(player);
        var session = session(player, request.dimension());
        long now = System.nanoTime(); session.seen = now;
        if (!session.budget.allow(now) && !request.action().equals("cancel")) {
            if (!request.action().equals("poll")) fail(player, session, request, "rate_limited");
            return;
        }
        if (!session.dimension.equals(request.dimension())) {
            cancelSearch(session);
            sessions.remove(player.getUUID()); session = new Session(request.dimension()); session.playerIdentity = player; sessions.put(player.getUUID(), session);
        }
        if (request.action().equals("cancel_query") || request.action().equals("cancel")) {
            cancelSearch(session); session.offered = Map.of(); session.queryId = Math.max(session.queryId, request.id());
            if (request.action().equals("cancel_query")) {
                reply(player, session, new Messages.Response("cancel_query", request.dimension(), request.id(), request.revision(), 0, null, List.of(), List.of(), null, ""));
                return;
            }
        }
        if (request.action().equals("query")) { if (request.id() <= session.queryId) return; cancelSearch(session); session.offered = Map.of(); session.queryId = request.id(); }
        var context = session;
        var server = owner;
        try {
            snapshot(request.dimension()).orTimeout(8, TimeUnit.SECONDS).whenComplete((snapshot, error) -> server.execute(() -> {
                if (owner != server || sessions.get(player.getUUID()) != context || !dimension(player).equals(request.dimension())) return;
                if (request.action().equals("query") && context.queryId != request.id()) return;
                if (error != null) { fail(player, context, request, "core_unavailable"); LOG.log(System.Logger.Level.WARNING, "MTR read failed", error); return; }
                handle(player, context, request, snapshot, extended);
            }));
        } catch (RuntimeException e) { fail(player, context, request, "core_unavailable"); }
    }
    static CompletableFuture<Snapshot> snapshot(String dimension) {
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
    private static void handle(ServerPlayer player, Session session, Messages.Request request, Snapshot snapshot, QueryV2.Request extended) {
        boolean catalogChanged = request.revision() != snapshot.catalog().revision();
        switch (request.action()) {
            case "hello" -> reply(player, session, response(request, snapshot, snapshot.catalog(), List.of(), List.of(), null, ""));
            case "query" -> {
                var server = owner; var config = settings;
                if (extended != null && !WalkingServer.ready()) { fail(player, session, request, "storage_unavailable"); return; }
                List<WalkingConnection> personal = extended == null ? List.of() : extended.personal();
                try { for (var edge : personal) edge.validate(snapshot.catalog()); }
                catch (RuntimeException error) { fail(player, session, request, "invalid_connection"); return; }
                var shared = extended == null ? List.<WalkingConnection>of() : WalkingServer.connections(request.dimension());
                long walkRevision = extended == null ? 0 : WalkingServer.revision();
                var task = new FutureTask<Void>(() -> {
                    try {
                        var search = new RoutePlanner().search(snapshot, request.origin(), request.destination(), request.sort(), config,
                                extended == null ? QueryOptions.legacy() : extended.options(), shared, personal, walkRevision);
                        var results = search.journeys();
                        var reference = results.isEmpty() ? io.github.teamcreating.mtrnav.core.ConnectionPlan.find(snapshot.catalog(), request.origin(), request.destination(), shared, personal).orElse(null) : null;
                        String message = reference == null ? "" : reference.transfers() > config.maxTransfers() ? "reference_limit" : "reference_no_service";
                        server.execute(() -> {
                            if (owner != server || sessions.get(player.getUUID()) != session || session.queryId != request.id()) return;
                            if (extended != null && WalkingServer.revision() != walkRevision) { fail(player, session, request, "walking_changed"); return; }
                            var offered = new HashMap<String, Journey>(); results.forEach(j -> offered.put(j.id(), j)); session.offered = Map.copyOf(offered);
                            var reply = response(request, snapshot, catalogChanged ? snapshot.catalog() : null, results, List.of(), null, search.incomplete() ? "partial_results" : message);
                            reply(player, session, new Messages.Response(reply.kind(), reply.dimension(), reply.id(), reply.revision(), reply.sampleMillis(), reply.catalog(), reply.journeys(), reply.arrivals(), reply.tracking(), reply.message(),
                                    extended == null ? null : new QueryV2.Report(search.fastestArrival(), search.incomplete(), walkRevision)));
                        });
                    } catch (RoutePlanner.SearchLimitException e) { server.execute(() -> fail(player, session, request, "search_budget")); }
                    catch (Exception e) { server.execute(() -> fail(player, session, request, "search_failed")); }
                }, null);
                session.searchTask = task;
                try { workers.execute(task); } catch (RejectedExecutionException e) { task.cancel(false); fail(player, session, request, "busy"); }
            }
            case "select" -> {
                var journey = session.offered.get(request.journeyId());
                if (journey == null || journey.revision() != snapshot.catalog().revision()) { fail(player, session, request, "network_changed"); return; }
                if (WalkingServer.changed(journey, snapshot.catalog())) { fail(player, session, request, "walking_changed"); return; }
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
                    if (WalkingServer.changed(tracking.journey(), snapshot.catalog())) tracking = new JourneyTracker.State(tracking.journey(), tracking.legIndex(), tracking.phase(), "walking_changed", false, tracking.stepIndex());
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
        if (owner == null || sessions.get(player.getUUID()) != session || request.action().equals("query") && session.queryId != request.id()) return;
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
    private static Session session(ServerPlayer player, String dimension) {
        var current = sessions.get(player.getUUID());
        if (current == null || current.playerIdentity != player) { if (current != null) cancelSearch(current); current = new Session(dimension); current.playerIdentity = player; sessions.put(player.getUUID(), current); }
        return current;
    }
    private static void cancelSearch(Session session) {
        if (session.searchTask == null) return;
        var task = session.searchTask; session.searchTask = null; task.cancel(true);
        if (workers != null) workers.remove(task);
    }
    private static String dimension(ServerPlayer player) {
        var location = player.serverLevel().dimension().location(); return location.getNamespace() + "/" + location.getPath();
    }
}
