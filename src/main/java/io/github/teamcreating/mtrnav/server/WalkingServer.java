package io.github.teamcreating.mtrnav.server;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Server-thread authority, single background preparer, final authenticated atomic commit. */
public final class WalkingServer {
    private static MinecraftServer server;
    private static WalkingStorage storage;
    private static WalkingLibrary.State state = WalkingLibrary.State.empty();
    private static ThreadPoolExecutor io;
    private static boolean ready, busy;
    private static BiConsumer<ServerPlayer, WalkingChunk> sender;
    private static Function<String, CompletableFuture<Model.Snapshot>> snapshots;
    private static long transfer;
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<UUID, Limits> limits = new HashMap<>();
    private static final class Limits {
        long seen;
        final WalkingLibrary.Rate reads = new WalkingLibrary.Rate(60, 10), submits = new WalkingLibrary.Rate(2, 2), changes = new WalkingLibrary.Rate(10, 5);
    }
    private static final class Session {
        ServerPlayer playerIdentity; String dimension; long seen; boolean revisionPending, review; String pendingMessage = "";
        final ArrayDeque<WalkingChunk> outbound = new ArrayDeque<>();
    }
    private WalkingServer() {}
    public static void start(MinecraftServer owner, BiConsumer<ServerPlayer, WalkingChunk> send, Function<String, CompletableFuture<Model.Snapshot>> capture) {
        stop(); server = owner; sender = send; snapshots = capture;
        storage = new WalkingStorage(owner.getWorldPath(LevelResource.ROOT).resolve("data/mtrnav"));
        var currentStorage = storage;
        io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), r -> { var t = new Thread(r, "mtrnav-walking-storage"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
        io.execute(() -> {
            try { var loaded = currentStorage.load(); owner.execute(() -> { if (server == owner) { state = loaded; ready = true; } }); }
            catch (Exception e) { System.getLogger("MTR Navigation").log(System.Logger.Level.ERROR, "Walking storage unavailable; writes disabled, files retained", e); }
        });
    }
    public static void stop() { if (io != null) io.shutdownNow(); io = null; server = null; ready = busy = false; sessions.clear(); limits.clear(); state = WalkingLibrary.State.empty(); }
    public static boolean ready() { return ready; }
    public static void touch(ServerPlayer player) { var session = sessions.get(player.getUUID()); if (session != null) session.seen = System.nanoTime(); }
    public static long revision() { return state.revision(); }
    public static List<WalkingConnection> connections(String dimension) { return ready ? state.connections(dimension) : List.of(); }
    public static boolean changed(Model.Journey journey, Model.Catalog catalog) {
        var graph = new WalkingGraph(catalog, connections(catalog.dimension()), List.of());
        for (var walk : journey.walks()) {
            var current = graph.transfer(walk.fromStationId(), walk.fromPlatformId(), walk.toStationId(), walk.toPlatformId());
            if (walk.source().equals("shared") && (current == null || !current.source().equals("shared") || !current.id().equals(walk.connectionId()) || current.seconds() != walk.seconds())) return true;
            if (!walk.source().equals("shared") && current != null && current.source().equals("shared") && (!current.id().equals(walk.connectionId()) || current.seconds() != walk.seconds())) return true;
        }
        return false;
    }
    public static void tick() {
        if (server == null) return;
        long now = System.nanoTime();
        limits.entrySet().removeIf(e -> now - e.getValue().seen > 600_000_000_000L && server.getPlayerList().getPlayer(e.getKey()) == null);
        sessions.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey()) != e.getValue().playerIdentity || now - e.getValue().seen > 600_000_000_000L);
        for (var e : sessions.entrySet()) {
            var player = server.getPlayerList().getPlayer(e.getKey()); var session = e.getValue();
            if (!dimension(player).equals(session.dimension)) { session.outbound.clear(); continue; }
            if (session.revisionPending && session.outbound.isEmpty()) { session.revisionPending = false; reply(player, session, 0, 0, "revision", session.pendingMessage); session.pendingMessage = ""; }
            for (int i = 0; i < 4 && !session.outbound.isEmpty(); i++) sender.accept(player, session.outbound.removeFirst());
        }
    }
    public static void receive(ServerPlayer player, WalkingRequest packet) {
        if (server == null || player.getServer() != server) return;
        var session = sessions.get(player.getUUID());
        if (session == null || session.playerIdentity != player) { session = new Session(); session.playerIdentity = player; sessions.put(player.getUUID(), session); }
        receive(player, packet, session);
    }
    private static void receive(ServerPlayer player, WalkingRequest packet, Session session) {
        long now = System.nanoTime(); session.seen = now;
        var budget = limits.computeIfAbsent(player.getUUID(), ignored -> new Limits()); budget.seen = now;
        // Charge before parsing untrusted JSON. Invalid packets cannot bypass the read limiter.
        if (!budget.reads.allow(now)) return;
        final WalkingExchange.Request request;
        try { request = WireCodec.GSON.fromJson(packet.json(), WalkingExchange.Request.class); if (request == null) return; }
        catch (RuntimeException e) { return; }
        if (!request.dimension().equals(dimension(player))) return;
        if (!Objects.equals(session.dimension, request.dimension())) { session.outbound.clear(); session.dimension = request.dimension(); }
        if (request.review() && !player.hasPermissions(2)) { reply(player, session, request.id(), 0, "result", "permission_denied"); return; }
        session.review = request.review();
        if (!ready) { reply(player, session, request.id(), 0, "result", "storage_unavailable"); return; }
        if (request.action().equals("read")) { reply(player, session, request.id(), request.page(), "snapshot", ""); return; }
        boolean privileged = request.action().equals("rollback") || request.command().adminOnly();
        if (privileged && !player.hasPermissions(2)) { reply(player, session, request.id(), 0, "result", "permission_denied"); return; }
        var rate = request.action().equals("change") && request.command().action().equals("submit") ? budget.submits : budget.changes;
        if (!rate.allow(now)) { reply(player, session, request.id(), 0, "result", "rate_limited"); return; }
        if (busy) { reply(player, session, request.id(), 0, "result", "busy"); return; }
        if (request.generation() != state.generation()) { reply(player, session, request.id(), 0, "result", "revision_conflict"); return; }
        busy = true; var owner = server; var before = state;
        try {
            snapshots.apply(request.dimension()).orTimeout(8, TimeUnit.SECONDS).whenComplete((snapshot, error) -> owner.execute(() -> {
                if (server != owner) return;
                if (error != null || !authorized(player, session, privileged) || state != before) { busy = false; reply(player, session, request.id(), 0, "result", "state_changed"); return; }
                if (!request.action().equals("rollback")) {
                    try { prepare(owner, player, session, request, before, WalkingLibrary.change(before, player.getUUID(), player.hasPermissions(2), request.command(), snapshot.catalog(), System.currentTimeMillis()), privileged); }
                    catch (RuntimeException e) { busy = false; reply(player, session, request.id(), 0, "result", message(e)); }
                } else {
                    execute(() -> {
                        try {
                            var next = storage.rollback(request.backup(), before, request.dimension(), player.getUUID());
                            for (var entry : next.published().values()) if (entry.connection().dimension().equals(snapshot.catalog().dimension())) entry.connection().validate(snapshot.catalog());
                            prepare(owner, player, session, request, before, next, true);
                        } catch (Exception e) { owner.execute(() -> { if (server == owner) { busy = false; reply(player, session, request.id(), 0, "result", "rollback_failed"); } }); }
                    });
                }
            }));
        } catch (RuntimeException e) { busy = false; reply(player, session, request.id(), 0, "result", "core_unavailable"); }
    }
    private static void prepare(MinecraftServer owner, ServerPlayer player, Session session, WalkingExchange.Request request,
                                WalkingLibrary.State before, WalkingLibrary.State next, boolean privileged) {
        var targetStorage = storage;
        execute(() -> {
            try {
                Path prepared = targetStorage.prepare(before, next);
                owner.execute(() -> {
                    if (server != owner) { deletePrepared(prepared); return; }
                    snapshots.apply(request.dimension()).orTimeout(8, TimeUnit.SECONDS).whenComplete((latest, error) -> owner.execute(() -> {
                    if (error != null) { busy = false; deletePrepared(prepared); reply(player, session, request.id(), 0, "result", "core_unavailable"); return; }
                    if (server != owner) { deletePrepared(prepared); return; }
                    if (!authorized(player, session, privileged) || state != before) { busy = false; deletePrepared(prepared); reply(player, session, request.id(), 0, "result", "state_changed"); return; }
                    try {
                        if (request.action().equals("rollback")) for (var entry : next.published().values()) {
                            if (entry.connection().dimension().equals(request.dimension())) entry.connection().validate(latest.catalog());
                        }
                        if (request.command() != null && request.command().action().equals("approve")) next.proposals().get(request.command().recordId()).value().validate(latest.catalog());
                        if (request.command() != null && request.command().value() != null) request.command().value().validate(latest.catalog());
                        targetStorage.commit(prepared); state = next; busy = false;
                        reply(player, session, request.id(), 0, "result", "saved");
                        if (next.revision() != before.revision()) sessions.values().forEach(s -> s.revisionPending = true);
                        var changedProposal = request.command() == null ? null : next.proposals().get(request.command().recordId());
                        for (var subscriber : sessions.entrySet()) {
                            var actor = owner.getPlayerList().getPlayer(subscriber.getKey());
                            if (actor != null && (changedProposal != null && changedProposal.author().equals(subscriber.getKey())
                                    || request.command() != null && request.command().action().equals("submit") && actor.hasPermissions(2))) {
                                subscriber.getValue().revisionPending = true; subscriber.getValue().pendingMessage = "proposal_updated";
                            }
                        }
                        String audit = WireCodec.GSON.toJson(Map.of("at", System.currentTimeMillis(), "actor", player.getUUID().toString(), "operation", request.action(), "command", request.command() == null ? "rollback" : request.command(), "publishedValue", request.command() != null && request.command().action().equals("approve") ? next.proposals().get(request.command().recordId()).value() : request.command() != null && request.command().action().equals("remove") ? before.published().get(request.command().recordId()).connection() : "", "generation", next.generation(), "revision", next.revision()));
                        execute(() -> { try { targetStorage.housekeeping(audit); } catch (Exception e) { System.getLogger("MTR Navigation").log(System.Logger.Level.WARNING, "Walking audit write failed", e); } });
                    } catch (Exception e) { busy = false; deletePrepared(prepared); reply(player, session, request.id(), 0, "result", "save_failed"); }
                    }));
                });
            } catch (Exception e) { owner.execute(() -> { if (server == owner) { busy = false; reply(player, session, request.id(), 0, "result", "save_failed"); } }); }
        });
    }
    private static void deletePrepared(Path p) { try { Files.deleteIfExists(p); } catch (Exception ignored) { } }
    private static boolean authorized(ServerPlayer p, Session session, boolean privileged) {
        return p.getServer() == server && server.getPlayerList().getPlayer(p.getUUID()) == p && sessions.get(p.getUUID()) == session && dimension(p).equals(session.dimension) && (!privileged || p.hasPermissions(2));
    }
    private static String message(RuntimeException e) { return e instanceof SecurityException ? "permission_denied" : Set.of("revision_conflict", "proposal_limit", "connection_limit", "duplicate_endpoints", "duplicate_proposal", "reason_required").contains(e.getMessage()) ? e.getMessage() : "invalid_connection"; }
    private static void reply(ServerPlayer p, Session session, long requestId, int page, String kind, String message) {
        if (server == null || !authorized(p, session, false)) return;
        boolean admin = p.hasPermissions(2);
        var entries = state.published().values().stream().filter(e -> e.connection().dimension().equals(session.dimension)).sorted(Comparator.comparing(e -> e.connection().id())).toList();
        var proposals = state.visible(p.getUUID(), admin && session.review, session.dimension, System.currentTimeMillis());
        List<Long> backups = List.of();
        // Disk enumeration is only used for the small admin backup list; no user-controlled path.
        if (admin && ready) try { backups = storage.backups(); } catch (Exception ignored) { }
        var response = new WalkingExchange.Reply(kind, session.dimension, requestId, state.generation(), state.revision(), admin,
                kind.equals("revision") ? List.of() : entries.stream().skip(page * 128L).limit(128).toList(),
                kind.equals("revision") ? List.of() : proposals.stream().skip(page * 25L).limit(25).toList(), backups, message, page, entries.size(), proposals.size());
        try {
            byte[] bytes = WalkingWire.encode(response); int total = (bytes.length + WalkingWire.CHUNK - 1) / WalkingWire.CHUNK;
            if (session.outbound.size() + total > 32) { session.revisionPending = true; return; }
            long id = ++transfer;
            for (int i = 0; i < total; i++) session.outbound.add(new WalkingChunk(id, i, total, Arrays.copyOfRange(bytes, i * WalkingWire.CHUNK, Math.min(bytes.length, (i + 1) * WalkingWire.CHUNK))));
        } catch (Exception e) { System.getLogger("MTR Navigation").log(System.Logger.Level.WARNING, "Could not encode walking reply", e); }
    }
    private static void execute(Runnable task) {
        try { io.execute(task); } catch (RejectedExecutionException e) { var owner = server; if (owner != null) owner.execute(() -> { if (server == owner) busy = false; }); System.getLogger("MTR Navigation").log(System.Logger.Level.WARNING, "Walking storage queue full"); }
    }
    private static String dimension(ServerPlayer p) { var id = p.serverLevel().dimension().location(); return id.getNamespace() + "/" + id.getPath(); }
}
