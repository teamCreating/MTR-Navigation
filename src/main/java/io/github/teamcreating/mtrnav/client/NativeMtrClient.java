package io.github.teamcreating.mtrnav.client;

import com.google.gson.*;
import io.github.teamcreating.mtrnav.api.*;
import io.github.teamcreating.mtrnav.core.*;
import org.mtr.client.MinecraftClientData;
import org.mtr.packet.PacketForwardClientRequest;
import org.mtr.registry.RegistryClient;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Passenger-facing MTR requests travel over its existing game connection; no open client HTTP port. */
public final class NativeMtrClient {
    private NativeMtrClient() {}
    private record Pending(long token, long started, PacketForwardClientRequest packet) {}
    private record BoardData(Catalog catalog, List<StopEvent> events, long sample) {}
    private static final Map<String, Pending> pending = new HashMap<>();
    private static final NativeRequestGate gate = new NativeRequestGate();
    private static final ThreadPoolExecutor search = worker("mtrnav-native-search");
    private static final ThreadPoolExecutor decoding = worker("mtrnav-native-decode");
    private static Future<?> activeSearch;
    private static ThreadPoolExecutor worker(String name) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), r -> {
            var thread = new Thread(r, name); thread.setDaemon(true); thread.setPriority(Thread.NORM_PRIORITY - 1); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    private static String dimension = "";
    private static volatile long epoch;
    private static long queryGeneration, requestSequence, lastCatalog, lastDepartures, lastBoard, lastSample;
    private static int dimensionIndex;
    private static boolean dimensionResolved, waitingQuery;
    private static NativeTimetable.Network network;
    private static Snapshot timetable;
    private static JourneyTracker tracker;
    public static void opened() {
        boolean retry = !gate.enabled();
        if (gate.resume(System.nanoTime())) {
            if (network == null || retry) lastCatalog = 0;
            lastDepartures = 0;
            if (network == null || retry) NavigationClient.status = "mtrnav.loading_times";
        }
    }

    public static void reset() {
        epoch++; queryGeneration++; waitingQuery = false;
        pending.values().forEach(p -> release(p.packet())); pending.clear();
        gate.reset();
        if (activeSearch != null) activeSearch.cancel(true);
        search.getQueue().clear(); decoding.getQueue().clear();
        network = null; timetable = null; tracker = null; dimension = ""; dimensionResolved = false;
        dimensionIndex = 0; lastCatalog = lastDepartures = lastBoard = lastSample = 0;
    }
    public static void tick(String currentDimension) {
        if (!dimension.equals(currentDimension)) { reset(); dimension = currentDimension; }
        long now = System.nanoTime();
        boolean active = Minecraft.getInstance().screen instanceof NavigationScreen || NavigationClient.tracking != null;
        if (!active || !gate.enabled()) return;
        var expired = pending.entrySet().stream().filter(e -> now - e.getValue().started() > 8_000_000_000L).map(Map.Entry::getKey).toList();
        if (!expired.isEmpty()) { fail("Native timetable request timed out", null); return; }
        Catalog overlay = OverlayBridge.catalog(dimension);
        if (network == null && overlay != null && !overlay.stations().isEmpty()) {
            NavigationClient.catalog = overlay; NavigationClient.status = "mtrnav.loading_times";
        }
        if (lastCatalog == 0 || now - lastCatalog > 120_000_000_000L) catalog(overlay);
        if (network == null) return;
        boolean boardOnly = Minecraft.getInstance().screen instanceof NavigationScreen screen && screen.wantsBoard()
                && NavigationClient.tracking == null && !waitingQuery;
        if (!boardOnly && (lastDepartures == 0 || now - lastDepartures >= 3_000_000_000L)) departures();
        if (Minecraft.getInstance().screen instanceof NavigationScreen screen && screen.wantsBoard()
                && !NavigationClient.boardStation.isEmpty() && (lastBoard == 0 || now - lastBoard >= 1_000_000_000L)) arrivals();
    }
    private static void catalog(Catalog overlay) {
        var platforms = nativePlatforms(); String requestedDimension = dimension;
        if (request("stations-and-routes", null, json -> NativeTimetable.network(requestedDimension, json, overlay, platforms), parsed -> {
            if (!dimensionResolved) {
                dimensionIndex = parsed.dimensions().indexOf(dimension);
                dimensionResolved = true;
                if (dimensionIndex < 0) { NavigationClient.status = "mtrnav.native_dimension"; return; }
                if (dimensionIndex != 0) { lastCatalog = 0; return; }
            }
            if (network == null || network.catalog().revision() != parsed.catalog().revision()) {
                NavigationClient.journeys = List.of(); timetable = null;
            }
            network = parsed; NavigationClient.catalog = parsed.catalog().retainPlatformNames(NavigationClient.catalog);
            NavigationClient.status = parsed.catalog().stations().isEmpty() ? "mtrnav.no_stations" : "mtrnav.ready";
            lastDepartures = 0;
        })) lastCatalog = System.nanoTime();
    }
    private static Map<String, List<NativeTimetable.PlatformIdentity>> nativePlatforms() {
        var result = new HashMap<String, List<NativeTimetable.PlatformIdentity>>();
        for (var data : new MinecraftClientData[]{MinecraftClientData.getInstance(), MinecraftClientData.getDashboardInstance()}) if (data != null) {
            for (var route : data.simplifiedRoutes) result.put(Long.toHexString(route.getId()), route.getPlatforms().stream()
                    .map(p -> new NativeTimetable.PlatformIdentity(Long.toHexString(p.getPlatformId()), Long.toHexString(p.getStationId()))).toList());
        }
        return result;
    }
    private static void departures() {
        if (network == null) return;
        var expected = network;
        if (request("departures", null, json -> NativeTimetable.departures(expected, json), parsed -> {
            if (expected != network) return;
            timetable = parsed; sample(timetable.sampleMillis());
            if (waitingQuery) { waitingQuery = false; calculate(); }
            if (tracker != null) {
                var state = tracker.state(false);
                NavigationClient.tracking = new JourneyTracker.State(state.journey(), state.legIndex(), state.phase(),
                        state.phase() == JourneyTracker.Phase.COMPLETE ? "" : "manual_check", false);
            }
        })) lastDepartures = System.nanoTime();
    }
    private static void arrivals() {
        if (network == null || NavigationClient.boardStation.isEmpty()) return;
        String station = NavigationClient.boardStation;
        var expected = network;
        var currentCatalog = NavigationClient.catalog;
        var body = new JsonObject(); var stationIds = new JsonArray(); stationIds.add(station);
        body.add("stationIdsHex", stationIds); body.addProperty("maxCountPerPlatform", 3); body.addProperty("maxCountTotal", 60);
        if (request("arrivals", body.toString(), json -> {
            var data = NativeTimetable.data(json);
            var events = new ArrayList<StopEvent>(); var platforms = new LinkedHashMap<String, Platform>(); currentCatalog.platforms().forEach(p -> platforms.put(p.id(), p));
            for (var row : NativeTimetable.array(data, "arrivals")) {
                var e = row.getAsJsonObject();
                String route = Long.toHexString(NativeTimetable.number(e, "routeId")), platform = Long.toHexString(NativeTimetable.number(e, "platformId"));
                if (expected.catalog().routes().stream().noneMatch(r -> r.id().equals(route))) continue;
                var pos = expected.catalog().station(station);
                platforms.putIfAbsent(platform, new Platform(platform, station, NativeTimetable.text(e, "platformName"), pos == null ? 0 : pos.x(), pos == null ? 0 : pos.y(), pos == null ? 0 : pos.z()));
                events.add(new StopEvent(new RunKey("native-board", 0, NativeTimetable.number(e, "departureIndex"), 0), "", route, platform, -1,
                        NativeTimetable.text(e, "destination"), NativeTimetable.number(e, "arrival"), NativeTimetable.number(e, "departure"),
                        NativeTimetable.number(e, "deviation"), e.has("realtime") && e.get("realtime").getAsBoolean()));
            }
            var c = expected.catalog();
            return new BoardData(new Catalog(c.dimension(), c.revision(), c.stations(), List.copyOf(platforms.values()), c.routes()), List.copyOf(events), NativeTimetable.number(data, "currentTime"));
        }, parsed -> {
            if (network != expected || !station.equals(NavigationClient.boardStation)) return;
            sample(parsed.sample()); NavigationClient.catalog = parsed.catalog(); NavigationClient.arrivals = parsed.events();
            NavigationClient.boardLoading = false; NavigationClient.boardUpdatedNanos = System.nanoTime();
        })) lastBoard = System.nanoTime();
    }
    public static void query() {
        queryGeneration++;
        if (activeSearch != null) activeSearch.cancel(true);
        search.purge(); search.getQueue().clear();
        if (!gate.enabled()) { opened(); if (!gate.enabled()) return; }
        if (network == null) { waitingQuery = true; lastCatalog = 0; NavigationClient.status = "mtrnav.loading_times"; return; }
        if (timetable == null || NavigationClient.CLOCK.stale(System.nanoTime())) { waitingQuery = true; departures(); return; }
        calculate();
    }
    private static void calculate() {
        if (timetable == null || NavigationClient.origin.isEmpty() || NavigationClient.destination.isEmpty()) return;
        long generation = ++queryGeneration, session = epoch;
        var source = new Snapshot(timetable.catalog(), NavigationClient.CLOCK.now(System.nanoTime()), timetable.events(), Map.of());
        String origin = NavigationClient.origin, destination = NavigationClient.destination;
        var sort = NavigationClient.sort;
        NavigationClient.status = "mtrnav.searching";
        if (activeSearch != null) activeSearch.cancel(true);
        search.purge(); search.getQueue().clear();
        activeSearch = search.submit(() -> {
            List<Journey> results; String failure = "";
            try { results = new RoutePlanner().find(source, origin, destination, sort, Settings.defaults()); }
            catch (RoutePlanner.SearchLimitException error) { results = List.of(); failure = "mtrnav.search_budget";
                com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Query resource limit: {} events, {} -> {}", source.events().size(), origin, destination); }
            catch (IllegalArgumentException error) { results = List.of(); failure = "mtrnav.station_refreshed";
                com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Query station validation failed", error); }
            catch (RuntimeException error) { results = List.of(); failure = "mtrnav.search_failed";
                com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Query failed", error); }
            var completed = results; String failedStatus = failure;
            Minecraft.getInstance().execute(() -> {
                    if (epoch != session || generation != queryGeneration) return;
                    NavigationClient.journeys = completed;
                    NavigationClient.status = !failedStatus.isEmpty() ? failedStatus : completed.isEmpty() ? "mtrnav.no_route" : "mtrnav.native_results";
                });
        });
    }
    public static void select(Journey journey) { tracker = new JourneyTracker(journey); correct(""); }
    public static void correct(String action) {
        if (tracker == null) return;
        var state = tracker.correct(action);
        NavigationClient.tracking = new JourneyTracker.State(state.journey(), state.legIndex(), state.phase(), state.phase() == JourneyTracker.Phase.COMPLETE ? "" : "manual_check", false);
        publish(state.journey());
    }
    public static void cancel() { tracker = null; NavigationClient.tracking = null; NavigationApi.cancel(); }
    private static void publish(Journey journey) {
        if (journey.legs().stream().allMatch(l -> !l.fromPlatformId().startsWith("map:") && !l.toPlatformId().startsWith("map:"))) NavigationApi.select(JourneySelection.from(journey));
        else NavigationApi.cancel();
    }
    public static void refreshBoard() { lastBoard = 0; arrivals(); }
    private static void sample(long serverMillis) {
        if (serverMillis > lastSample) { lastSample = serverMillis; NavigationClient.CLOCK.sample(serverMillis, System.nanoTime()); }
    }
    private static <T> boolean request(String endpoint, String body, Function<String, T> parse, Consumer<T> result) {
        if (!pending.isEmpty() || dimensionIndex < 0 || !gate.start(System.nanoTime())) return false;
        long session = epoch, token = ++requestSequence;
        var packet = new PacketForwardClientRequest("/mtr/api/map/" + endpoint + "?dimension=" + dimensionIndex, body, (content, path) ->
                Minecraft.getInstance().execute(() -> {
                    if (!current(session, endpoint, token)) return;
                    try {
                        if (content == null || content.length() > 8 * 1024 * 1024) throw new IllegalArgumentException("Invalid MTR response");
                        decoding.execute(() -> {
                            if (epoch != session) return;
                            long started = System.nanoTime();
                            try {
                                var decoded = parse.apply(content);
                                Minecraft.getInstance().execute(() -> {
                                    if (!current(session, endpoint, token)) return;
                                    pending.remove(endpoint); gate.complete();
                                    try { result.accept(decoded); }
                                    catch (RuntimeException error) { fail("Native response could not be applied: " + endpoint, error); }
                                    long millis = (System.nanoTime() - started) / 1_000_000;
                                    if (millis > 250) com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] {} decoded off-thread: {} chars, {} ms", endpoint, content.length(), millis);
                                });
                            } catch (RuntimeException error) {
                                Minecraft.getInstance().execute(() -> { if (current(session, endpoint, token)) fail("Native response decoding failed: " + endpoint, error); });
                            }
                        });
                    } catch (RuntimeException error) { fail("Native request failed: " + endpoint, error); }
                }));
        pending.put(endpoint, new Pending(token, System.nanoTime(), packet));
        try { RegistryClient.sendPacketToServer(packet); return true; }
        catch (RuntimeException error) { fail("Native packet could not be sent", error); return false; }
    }
    private static boolean current(long session, String endpoint, long token) {
        var request = pending.get(endpoint);
        return epoch == session && request != null && request.token() == token;
    }
    private static void fail(String message, Throwable error) {
        pending.values().forEach(p -> release(p.packet())); pending.clear(); gate.fail(System.nanoTime());
        queryGeneration++;
        if (activeSearch != null) activeSearch.cancel(true);
        search.purge(); search.getQueue().clear();
        waitingQuery = false; NavigationClient.status = "mtrnav.native_unavailable";
        NavigationClient.boardLoading = false;
        if (error == null) com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] {}. Native polling suspended; explicit retry required.", message);
        else com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] " + message + ". Native polling suspended; explicit retry required.", error);
    }
    @SuppressWarnings("unchecked")
    private static void release(PacketForwardClientRequest packet) {
        // Only retire this adapter's own callbacks when MTR's HTTP service fails
        // to answer, so retrying/connection changes cannot leak callback entries.
        try {
            Field id = PacketForwardClientRequest.class.getDeclaredField("callbackId"), callbacks = PacketForwardClientRequest.class.getDeclaredField("CALLBACKS");
            id.setAccessible(true); callbacks.setAccessible(true);
            ((Map<Long, ?>) callbacks.get(null)).remove(id.getLong(packet));
        } catch (ReflectiveOperationException ignored) { }
    }
}
