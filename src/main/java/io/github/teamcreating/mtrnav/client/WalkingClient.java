package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;

/** Per-server local rules and consent, plus a separately assembled shared snapshot. */
public final class WalkingClient {
    private WalkingClient() {}
    private record Personal(int version, boolean consent, boolean consentDecided, int extraMinutes, List<WalkingConnection> connections) {
        Personal { if (consent) consentDecided = true; if (version != 1 || connections == null || connections.size() > 512) throw new IllegalArgumentException("Invalid personal data"); connections = List.copyOf(connections); new Model.QueryOptions(5, extraMinutes, true); }
    }
    public record Export(int version, List<WalkingConnection> connections) {
        public Export { if (version != 1 || connections == null || connections.size() > 128) throw new IllegalArgumentException("Import accepts at most 128 links"); connections = List.copyOf(connections); }
    }
    private static Path file;
    private static Personal personal = new Personal(1, false, false, 30, List.of());
    private static final WalkingWire.Assembly assembly = new WalkingWire.Assembly();
    private static BooleanSupplier available = () -> false, queryAvailable = () -> false;
    private static Consumer<WalkingRequest> sender = ignored -> {};
    private static Consumer<NavRequestV2> querySender = ignored -> {};
    private static long nextId, waitingId, started, retryAt, generation, revision, assemblingGeneration = -1, assemblingRevision = -1;
    private static int entriesExpected, proposalsExpected;
    private static boolean dirty = true;
    public static boolean admin, reviewMode;
    private static boolean pendingReview;
    public static String message = "";
    public static int page;
    public static List<WalkingLibrary.Entry> shared = List.of();
    private static String checkedJourney = "";
    private static Object checkedCatalog, checkedShared, checkedPersonal, checkedWalks;
    private static boolean invalidRules, checkedDirty;
    public static List<Long> backups = List.of();
    private static final WalkingSnapshot snapshot = new WalkingSnapshot();
    private static final Map<Integer, List<WalkingLibrary.Proposal>> proposalPages = new HashMap<>();
    public static void transport(BooleanSupplier sharedAvailable, Consumer<WalkingRequest> send, BooleanSupplier v2, Consumer<NavRequestV2> sendQuery) {
        available = sharedAvailable; sender = send; queryAvailable = v2; querySender = sendQuery;
    }
    public static void reset() {
        checkedJourney = ""; checkedCatalog = checkedShared = checkedPersonal = checkedWalks = null; invalidRules = false;
        file = null; personal = new Personal(1, false, false, 30, List.of()); shared = List.of(); backups = List.of();
        assembly.clear(); waitingId = generation = revision = 0; assemblingGeneration = assemblingRevision = -1;
        retryAt = 0; dirty = true; admin = reviewMode = false; message = ""; page = entriesExpected = proposalsExpected = 0; snapshot.invalidate(); proposalPages.clear();
    }
    private static String dimension() { var p = NavigationClient.playerPosition(); return p == null ? "" : p.dimension(); }
    private static void load() {
        if (file != null) return;
        var mc = Minecraft.getInstance(); if (mc.level == null) throw new IllegalStateException("No current world");
        String scope = mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip.strip().toLowerCase(Locale.ROOT)
                : mc.getSingleplayerServer() != null ? "world:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize() : null;
        if (scope == null) throw new IllegalStateException("Server identity unavailable");
        try {
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
            file = mc.gameDirectory.toPath().resolve("config/mtrnav/personal/" + key + ".json");
            if (Files.isRegularFile(file)) {
                if (Files.size(file) > 1024 * 1024) throw new IllegalArgumentException("Personal file too large");
                String text = Files.readString(file); NetworkLimits.structure(text, 16);
                personal = Objects.requireNonNull(WireCodec.GSON.fromJson(text, Personal.class)); validatePersonal(personal.connections());
            }
        } catch (Exception e) { personal = new Personal(1, false, false, 30, List.of()); message = "local_load_failed"; }
    }
    private static void validatePersonal(List<WalkingConnection> links) {
        if (links.size() > 512) throw new IllegalArgumentException("Personal link limit exceeded");
        var enabled = new HashMap<String, Integer>(); var identities = new HashSet<String>(); var endpoints = new HashSet<String>();
        for (var link : links) {
            if (!identities.add(link.id()) || !endpoints.add(link.endpoints())) throw new IllegalArgumentException("Duplicate personal link");
            if (link.enabled() && enabled.merge(link.dimension(), 1, Integer::sum) > 32) throw new IllegalArgumentException("At most 32 active personal links per dimension");
        }
    }
    private static void save(Personal next) throws Exception {
        if (file == null) throw new IllegalStateException("Local storage unavailable");
        validatePersonal(next.connections()); Files.createDirectories(file.getParent());
        var temp = Files.createTempFile(file.getParent(), "personal-", ".json");
        try { Files.writeString(temp, WireCodec.GSON.toJson(next)); Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); personal = next; }
        finally { Files.deleteIfExists(temp); }
    }
    public static List<WalkingConnection> local() { load(); return personal.connections().stream().filter(c -> c.dimension().equals(dimension())).toList(); }
    public static List<WalkingConnection> activeLocal() { return local().stream().filter(WalkingConnection::enabled).toList(); }
    public static List<WalkingConnection> sharedConnections() { return shared.stream().map(WalkingLibrary.Entry::connection).toList(); }
    public static Model.QueryOptions options() { load(); return new Model.QueryOptions(5, personal.extraMinutes(), true); }
    public static boolean consent() { load(); return personal.consent(); }
    public static boolean needsConsent() { load(); return !personal.consentDecided() && !activeLocal().isEmpty(); }
    public static void consent(boolean consent) { load(); try { save(new Personal(1, consent, true, personal.extraMinutes(), personal.connections())); message = "saved"; } catch (Exception e) { message = "save_failed"; } }
    public static void nextWindow() {
        load(); int next = switch (personal.extraMinutes()) { case 15 -> 30; case 30 -> 60; case 60 -> -1; default -> 15; };
        try { save(new Personal(1, personal.consent(), personal.consentDecided(), next, personal.connections())); NavigationClient.clearSearchResults(); } catch (Exception e) { message = "save_failed"; }
    }
    public static void putLocal(List<WalkingConnection> values) {
        load(); var all = new LinkedHashMap<String, WalkingConnection>(); personal.connections().forEach(c -> all.put(c.id(), c)); values.forEach(c -> all.put(c.id(), c));
        try { save(new Personal(1, personal.consent(), personal.consentDecided(), personal.extraMinutes(), List.copyOf(all.values()))); NavigationClient.clearSearchResults(); message = "saved"; }
        catch (Exception e) { message = "local_limit_or_save_failed"; }
    }
    public static void deleteLocal(String id) {
        load(); try { save(new Personal(1, personal.consent(), personal.consentDecided(), personal.extraMinutes(), personal.connections().stream().filter(c -> !c.id().equals(id)).toList())); NavigationClient.clearSearchResults(); message = "saved"; }
        catch (Exception e) { message = "save_failed"; }
    }
    public static boolean supportsShared() { return available.getAsBoolean(); }
    public static boolean supportsQuery() { return queryAvailable.getAsBoolean(); }
    public static void sendQuery(Messages.Request request) {
        var links = consent() ? activeLocal().stream().map(WalkingConnection::privateWire).toList() : List.<WalkingConnection>of();
        querySender.accept(new NavRequestV2(WireCodec.GSON.toJson(new QueryV2.Request(request, options(), revision, links))));
    }
    public static List<WalkingLibrary.Proposal> proposals() { return proposalPages.getOrDefault(page, List.of()); }
    public static int proposalPages() { return Math.max(1, (proposalsExpected + 24) / 25); }
    public static void refresh() { dirty = true; snapshot.invalidate(); proposalPages.clear(); read(0); }
    public static void read(int index) {
        if (!supportsShared() || waitingId != 0 || System.nanoTime() < retryAt) return;
        send(new WalkingExchange.Request("read", dimension(), ++nextId, generation, null, 0, index, reviewMode));
    }
    private static void send(WalkingExchange.Request request) {
        if (!supportsShared()) { message = "server_unavailable"; return; }
        if (waitingId != 0) { message = "busy"; return; }
        try { sender.accept(new WalkingRequest(WireCodec.GSON.toJson(request))); waitingId = request.id(); pendingReview = reviewMode; started = System.nanoTime(); message = "working"; }
        catch (RuntimeException e) { message = "upload_too_large"; }
    }
    public static void change(WalkingLibrary.Command command) { send(new WalkingExchange.Request("change", dimension(), ++nextId, generation, command, 0, 0, reviewMode)); }
    public static void rollback(long backup) { send(new WalkingExchange.Request("rollback", dimension(), ++nextId, generation, null, backup, 0, reviewMode)); }
    public static long entryRevision(String id) { return shared.stream().filter(e -> e.connection().id().equals(id)).mapToLong(WalkingLibrary.Entry::revision).findFirst().orElse(0); }
    public static void tick(boolean visible) {
        if (!supportsShared()) return;
        long now = System.nanoTime();
        if (waitingId != 0 && now - started > 10_000_000_000L) { waitingId = 0; assembly.clear(); retryAt = now + 15_000_000_000L; message = "timeout"; }
        if (!visible || waitingId != 0 || now < retryAt) return;
        if (dirty) {
            for (int i = 0; i < Math.max(1, (entriesExpected + 127) / 128); i++) if (!snapshot.hasPage(i)) { read(i); return; }
            if (snapshot.complete()) { shared = snapshot.published(); revision = assemblingRevision; dirty = false; }
        }
        if (!proposalPages.containsKey(page)) read(page);
    }
    public static void chunk(WalkingChunk chunk) {
        try { byte[] bytes = assembly.accept(chunk, System.nanoTime()); if (bytes != null) receive(WalkingWire.decode(bytes)); }
        catch (Exception e) { assembly.clear(); waitingId = 0; retryAt = System.nanoTime() + 15_000_000_000L; message = "invalid_response"; }
    }
    public static boolean rulesChanged() {
        var tracking = NavigationClient.tracking; var catalog = NavigationClient.catalog;
        if (tracking == null || catalog == null || tracking.journey().walks().isEmpty()) return false;
        load();
        if (checkedJourney.equals(tracking.journey().id()) && checkedCatalog == catalog && checkedShared == shared && checkedPersonal == personal && checkedWalks == tracking.journey().walks() && checkedDirty == dirty) return invalidRules;
        checkedJourney = tracking.journey().id(); checkedCatalog = catalog; checkedShared = shared; checkedPersonal = personal; checkedWalks = tracking.journey().walks(); checkedDirty = dirty;
        invalidRules = false;
        var graph = new WalkingGraph(catalog, sharedConnections(), activeLocal());
        for (var walk : tracking.journey().walks()) if (!walk.source().equals("mtr")) {
            if (walk.source().equals("shared") && dirty) continue;
            var current = graph.transfer(walk.fromStationId(), walk.fromPlatformId(), walk.toStationId(), walk.toPlatformId());
            if (current == null || !current.id().equals(walk.connectionId()) || current.seconds() != walk.seconds()) { invalidRules = true; break; }
        }
        return invalidRules;
    }
    private static void receive(WalkingExchange.Reply reply) {
        if (!dimension().equals(reply.dimension()) || reply.generation() < generation) return;
        if (reply.kind().equals("revision")) {
            generation = reply.generation();
            if (reply.revision() != revision) { dirty = true; snapshot.invalidate(); proposalPages.clear(); }
            if (!reply.message().isEmpty()) { proposalPages.clear(); message = reply.message(); }
            return;
        }
        if (reply.id() != waitingId) return; waitingId = 0;
        if (pendingReview != reviewMode) { dirty = true; snapshot.invalidate(); proposalPages.clear(); return; }
        generation = reply.generation(); if (admin && !reply.admin()) proposalPages.clear(); admin = reply.admin(); backups = reply.backups();
        if (reply.generation() != assemblingGeneration || reply.revision() != assemblingRevision) {
            snapshot.invalidate(); proposalPages.clear(); assemblingGeneration = reply.generation(); assemblingRevision = reply.revision(); dirty = true;
        }
        entriesExpected = reply.entryCount(); proposalsExpected = reply.proposalCount();
        if (entriesExpected < 0 || entriesExpected > 1024 || proposalsExpected < 0 || proposalsExpected > 2000 || reply.page() < 0 || reply.page() > 79) throw new IllegalArgumentException("Invalid shared counts");
        snapshot.accept(reply); proposalPages.put(reply.page(), reply.proposals());
        message = reply.message().isEmpty() ? "ready" : reply.message();
        if (!reply.message().isEmpty() && !reply.message().equals("saved")) retryAt = System.nanoTime() + 2_000_000_000L;

    }
    private static Path transferDirectory(String name) throws Exception {
        var path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/mtrnav/" + name); Files.createDirectories(path); return path.toRealPath();
    }
    public static List<Path> imports() {
        try { var root = transferDirectory("imports"); try (var paths = Files.list(root)) { return paths.filter(p -> p.getFileName().toString().endsWith(".json") && !Files.isSymbolicLink(p)).sorted().limit(128).toList(); } }
        catch (Exception e) { message = "import_failed"; return List.of(); }
    }
    public static List<WalkingConnection> preview(Path path) throws Exception {
        var root = transferDirectory("imports");
        if (!path.toRealPath().getParent().equals(root) || Files.size(path) > 256 * 1024) throw new IllegalArgumentException("Invalid import file");
        String text = Files.readString(path); NetworkLimits.structure(text, 16);
        var imported = Objects.requireNonNull(WireCodec.GSON.fromJson(text, Export.class));
        var result = new ArrayList<WalkingConnection>();
        for (var link : imported.connections()) {
            boolean valid = link.dimension().equals(dimension());
            try { link.validate(NavigationClient.catalog); } catch (RuntimeException e) { valid = false; }
            result.add(valid ? link : link.enabled(false));
        }
        return List.copyOf(result);
    }
    public static void exportLocal() {
        try {
            var root = transferDirectory("exports"); var links = local(); long stamp = System.currentTimeMillis();
            for (int i = 0; i < Math.max(1, (links.size() + 127) / 128); i++) Files.writeString(root.resolve("walking-" + stamp + "-" + (i + 1) + ".json"),
                    WireCodec.GSON.toJson(new Export(1, links.subList(i * 128, Math.min(links.size(), (i + 1) * 128)))), StandardOpenOption.CREATE_NEW);
            message = "exported";
        }
        catch (Exception e) { message = "export_failed"; }
    }
}
