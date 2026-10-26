package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.api.*;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Loader-neutral client state. All calls are made on the Minecraft client thread. */
public final class NavigationClient {
    private NavigationClient() {}
    public static Catalog catalog;
    public static List<Journey> journeys = List.of();
    public static ConnectionPlan referencePlan;
    public static String referenceReason = "";
    public static List<StopEvent> arrivals = List.of();
    public static JourneyTracker.State tracking;
    public static ArrivalReminder reminder;
    public static boolean boardLoading;
    public static long boardUpdatedNanos, boardSampleMillis;
    public static String status = "mtrnav.connecting";
    public static String origin = "", destination = "", boardStation = "";
    public static Sort sort = Sort.FASTEST;
    public static long fastestArrival;
    public static boolean partialResults;
    public static final ServerClock CLOCK = new ServerClock();
    static final StationLocator.Session originLocation = new StationLocator.Session();
    static Set<String> locatedStations() { return usingNative ? NativeMtrClient.locatedStations(dimension) : null; }
    static StationLocator.Position playerPosition() {
        var mc = Minecraft.getInstance();
        return mc.player == null || mc.level == null ? null : new StationLocator.Position(
                mc.level.dimension().location().getNamespace() + "/" + mc.level.dimension().location().getPath(), mc.player.getX(), mc.player.getZ());
    }
    private static final WireCodec.Assembly assembly = new WireCodec.Assembly();
    private static Consumer<NavRequest> sender = ignored -> {};
    private static BooleanSupplier available = () -> false;
    private static Object connection;
    private static String dimension = "";
    private static long nextId, queryId, lastPoll, startedNanos, lastTrackingId, lastSample;
    private static long oldestResponseId, lastCatalogId, lastHello, boardRequestId;
    private static long lastTickNanos;
    private static boolean helloSent;
    private record PollContext(String station, String reminderId) {}
    private static final Map<Long, PollContext> pollContexts = new LinkedHashMap<>();
    public static boolean usingNative;
    public static Supplier<String> openKey = () -> "N";

    public static void transport(BooleanSupplier canSend, Consumer<NavRequest> send) { available = canSend; sender = send; }
    public static void open() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            NativeMtrClient.opened(); var navigation = new NavigationScreen();
            if (reminder != null && tracking == null) mc.setScreen(new ReminderSettingsScreen(navigation, reminder.stationId(), reminder.event(), reminder.minutes()));
            else mc.setScreen(navigation);
        }
    }
    public static void tick() {
        var mc = Minecraft.getInstance();
        long tickTime = System.nanoTime();
        if (lastTickNanos != 0 && connection != null && connection == mc.getConnection() && tickTime - lastTickNanos > 5_000_000_000L)
            com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Client tick paused {} ms", (tickTime - lastTickNanos) / 1_000_000);
        lastTickNanos = tickTime;
        Object current = mc.getConnection();
        String dim = mc.level == null ? "" : mc.level.dimension().location().getNamespace() + "/" + mc.level.dimension().location().getPath();
        if (connection != current || !dimension.equals(dim)) {
            reset(); connection = current; dimension = dim; startedNanos = System.nanoTime();
        }
        if (mc.player == null || mc.level == null) return;
        long now = System.nanoTime();
        tickReminder(now);
        WalkingClient.tick(mc.screen instanceof NavigationScreen || mc.screen instanceof WalkingListScreen || mc.screen instanceof WalkingTextScreen || tracking != null);
        if (!available.getAsBoolean() || Boolean.getBoolean("mtrnav.forceNative")) {
            usingNative = true;
            NativeMtrClient.tick(dim);
            return;
        }
        if (usingNative) { NativeMtrClient.reset(); usingNative = false; catalog = null; }
        if (!helloSent || (catalog == null && now - lastHello > 3_000_000_000L)) { helloSent = true; lastHello = now; send("hello", "", ""); }
        if ((mc.screen instanceof NavigationScreen || mc.screen instanceof ReminderSettingsScreen || tracking != null || reminder != null) && now - lastPoll > 1_000_000_000L) {
            lastPoll = now;
            String primary = !boardStation.isEmpty() ? boardStation : reminder == null ? "" : reminder.stationId();
            send("poll", "", "", primary);
            if (reminder != null && !primary.equals(reminder.stationId())) send("poll", "", "", reminder.stationId());
        }
    }
    public static void reset() {
        originLocation.reset(); WalkingClient.reset(); fastestArrival = 0; partialResults = false;
        catalog = null; journeys = List.of(); referencePlan = null; referenceReason = ""; arrivals = List.of(); tracking = null; reminder = null;
        boardLoading = false; boardUpdatedNanos = boardSampleMillis = 0; pollContexts.clear(); ReminderHud.reset();
        origin = destination = boardStation = ""; status = "mtrnav.connecting";
        queryId = lastTrackingId = lastPoll = lastSample = 0; helloSent = false;
        oldestResponseId = nextId + 1; lastCatalogId = lastHello = 0;
        assembly.clear(); CLOCK.clear(); NavigationApi.cancel();
        NativeMtrClient.reset(); usingNative = false;
    }
    public static void query() {
        if (origin.isEmpty() || destination.isEmpty()) { status = "mtrnav.choose_stations"; return; }
        if (!usingNative && WalkingClient.supportsQuery() && WalkingClient.needsConsent()) {
            WalkingManagementScreen.confirmConsent(Minecraft.getInstance().screen, NavigationClient::query); return;
        }
        status = "mtrnav.searching"; journeys = List.of(); partialResults = false; fastestArrival = 0;
        referencePlan = ConnectionPlan.find(catalog, origin, destination, usingNative ? List.of() : WalkingClient.sharedConnections(), usingNative || WalkingClient.consent() ? WalkingClient.activeLocal() : List.of()).orElse(null);
        referenceReason = "mtrnav.reference_pending";
        if (usingNative) NativeMtrClient.query(); else queryId = send("query", "", "");
    }
    public static void clearSearchResults() {
        if (queryId != 0 && !usingNative && WalkingClient.supportsQuery()) send("cancel_query", "", "");
        journeys = List.of(); referencePlan = null; referenceReason = ""; queryId = 0;
        status = "mtrnav.choose_stations";
        NativeMtrClient.cancelQuery();
    }
    public static void select(Journey journey) { if (usingNative) NativeMtrClient.select(journey); else send("select", journey.id(), ""); }
    public static void correct(String action) { if (usingNative) NativeMtrClient.correct(action); else send("correct", tracking == null ? "" : tracking.journey().id(), action); }
    public static void cancel() { if (usingNative) NativeMtrClient.cancel(); else lastTrackingId = send("cancel", "", ""); tracking = null; NavigationApi.cancel(); }
    public static void board(String station) {
        boardStation = station; arrivals = List.of(); boardLoading = true; boardUpdatedNanos = boardSampleMillis = 0;
        if (usingNative) NativeMtrClient.refreshBoard(); else boardRequestId = send("poll", "", "");
    }
    public static void stopBoard() { boardStation = ""; arrivals = List.of(); boardLoading = false; boardUpdatedNanos = boardSampleMillis = 0; }
    public static boolean boardFresh() {
        long now = System.nanoTime();
        return boardSampleMillis > 0 && boardUpdatedNanos != 0 && now - boardUpdatedNanos < ArrivalReminder.STALE_NANOS
                && !CLOCK.stale(now) && CLOCK.now(now) - boardSampleMillis < 5000;
    }
    public static boolean armReminder(String station, StopEvent selected, int minutes) {
        var event = ArrivalReminder.unique(selected, arrivals);
        if (!station.equals(boardStation) || !boardFresh() || event.isEmpty() || event.get().departure() < CLOCK.now(System.nanoTime()) || catalog == null) return false;
        reminder = new ArrivalReminder(catalog.dimension(), station, event.get(), minutes, boardSampleMillis, boardUpdatedNanos);
        ReminderHud.armed(); tickReminder(System.nanoTime()); return true;
    }
    public static void cancelReminder() { reminder = null; ReminderHud.reset(); }
    public static void observeReminder(String station, List<StopEvent> events, long sampleMillis, String expectedId) {
        if (reminder != null && reminder.id().equals(expectedId)) reminder.observe(dimension, station, events, sampleMillis, System.nanoTime());
    }
    private static void tickReminder(long now) {
        if (reminder == null) return;
        if (!reminder.dimension().equals(dimension) || reminder.expired(CLOCK.now(now))) { cancelReminder(); return; }
        if (reminder.tick(CLOCK.now(now), now, !CLOCK.stale(now))) {
            ReminderHud.triggered();
            var player = Minecraft.getInstance().player;
            if (player != null) player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, .4F, 1.2F);
        }
    }
    private static long send(String action, String journeyId, String correction) {
        return send(action, journeyId, correction, boardStation);
    }
    private static long send(String action, String journeyId, String correction, String station) {
        long id = ++nextId;
        if (action.equals("poll")) {
            pollContexts.put(id, new PollContext(station, reminder == null ? "" : reminder.id()));
            while (pollContexts.size() > 16) pollContexts.remove(pollContexts.keySet().iterator().next());
        }
        if (available.getAsBoolean()) {
            var request = new Messages.Request(action, dimension, id, catalog == null ? 0 : catalog.revision(), origin, destination, sort, journeyId, station, correction);
            if (action.equals("query") && WalkingClient.supportsQuery()) WalkingClient.sendQuery(request);
            else sender.accept(new NavRequest(WireCodec.GSON.toJson(request)));
        }
        return id;
    }
    public static void chunk(NavChunk chunk) {
        try {
            var bytes = assembly.accept(chunk.transfer(), chunk.index(), chunk.total(), chunk.data(), System.nanoTime());
            if (bytes != null) receive(WireCodec.decode(bytes));
        } catch (Exception e) { status = "mtrnav.invalid_response"; assembly.clear(); }
    }
    public static void receive(Messages.Response response) {
        if (!dimension.equals(response.dimension()) || response.id() < oldestResponseId) return;
        if (response.kind().equals("query") && response.id() != queryId) return;
        if (response.catalog() != null && response.id() >= lastCatalogId) {
            lastCatalogId = response.id();
            if (catalog == null || response.revision() != catalog.revision()) {
                journeys = List.of(); referencePlan = null; catalog = response.catalog();
            }
        }
        if (catalog != null && response.revision() != catalog.revision()) return;
        if (response.sampleMillis() > lastSample) {
            lastSample = response.sampleMillis(); CLOCK.sample(lastSample, System.nanoTime());
        }
        if (response.kind().equals("query")) {
            journeys = response.journeys();
            fastestArrival = response.report() == null ? journeys.stream().mapToLong(Journey::arrival).min().orElse(0) : response.report().fastestArrival();
            partialResults = response.report() != null && response.report().incomplete();
            status = journeys.isEmpty() ? "mtrnav.no_route" : "mtrnav.ready";
            referencePlan = journeys.isEmpty() ? ConnectionPlan.find(catalog, origin, destination, usingNative ? List.of() : WalkingClient.sharedConnections(), usingNative || WalkingClient.consent() ? WalkingClient.activeLocal() : List.of()).orElse(null) : null;
            referenceReason = response.message().equals("reference_limit") ? "mtrnav.reference_limit"
                    : response.message().isEmpty() || response.message().equals("reference_no_service") ? "mtrnav.reference_no_service" : "mtrnav.reference_incomplete";
        } else if (response.kind().equals("hello")) status = "mtrnav.ready";
        var poll = response.kind().equals("poll") ? pollContexts.remove(response.id()) : null;
        if (poll != null && response.sampleMillis() > 0 && response.message().isEmpty()) {
            observeReminder(poll.station(), response.arrivals(), response.sampleMillis(), poll.reminderId());
            if (response.id() >= boardRequestId && !boardStation.isEmpty() && boardStation.equals(poll.station())) {
                boardRequestId = response.id(); arrivals = response.arrivals(); boardLoading = false;
                if (response.sampleMillis() > boardSampleMillis) { boardUpdatedNanos = System.nanoTime(); boardSampleMillis = response.sampleMillis(); }
            }
        }
        if ((response.kind().equals("poll") || response.kind().equals("select") || response.kind().equals("correct") || response.kind().equals("cancel"))
                && response.id() >= lastTrackingId) {
            lastTrackingId = response.id(); tracking = response.tracking();
            if (tracking != null) {
                var selection = JourneySelection.from(tracking.journey());
                if (NavigationApi.current().isEmpty() || !NavigationApi.current().get().equals(selection)) NavigationApi.select(selection);
            } else NavigationApi.cancel();
        }
        if (!response.message().isEmpty()) status = "mtrnav." + response.message();
    }
    public static String countdown(long timestamp) {
        long now = System.nanoTime();
        if (CLOCK.stale(now)) return tr("mtrnav.stale");
        long seconds = Math.max(0, (timestamp - CLOCK.now(now) + 999) / 1000);
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }
    public static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }
    public static void renderHud(GuiGraphics graphics) {
        NavigationHud.render(graphics);
        ReminderHud.render(graphics);
    }
}
