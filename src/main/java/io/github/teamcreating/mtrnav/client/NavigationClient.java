package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.api.*;
import io.github.teamcreating.mtrnav.client.ui.NavigationUi;
import io.github.teamcreating.mtrnav.client.ui.workspace.NewUiFixtures;
import io.github.teamcreating.mtrnav.client.ui.workspace.WorkspaceScreen;
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
    public static List<String> fleet = List.of();
    public static VehicleCondition vehicleCondition = VehicleCondition.none();
    private static BooleanSupplier vehicleAvailable = () -> false;
    private static Consumer<NavRequestV3> vehicleSender = ignored -> {};
    public static void vehicleTransport(BooleanSupplier available, Consumer<NavRequestV3> sender) { vehicleAvailable = available; vehicleSender = sender; }
    public static boolean supportsVehicles() { return !usingNative && vehicleAvailable.getAsBoolean(); }
    public static void vehicleCondition(VehicleCondition value) { if (!vehicleCondition.equals(value)) { vehicleCondition = value; clearSearchResults(); } }
    public static List<Journey> journeys = List.of();
    public static ConnectionPlan referencePlan;
    public static List<ConnectionPlan> referencePlans = List.of();
    public static String referenceReason = "";

    public static void setReferencePlans(List<ConnectionPlan> plans) {
        referencePlans = plans == null ? List.of() : List.copyOf(plans);
        referencePlan = referencePlans.isEmpty() ? null : referencePlans.getFirst();
    }
    public static List<StopEvent> arrivals = List.of();
    public static JourneyTracker.State tracking;
    public static ArrivalReminder reminder;
    public static boolean boardLoading;
    public static long boardUpdatedNanos, boardSampleMillis;
    public static String status = "mtrnav.connecting";
    public static String origin = "", destination = "", boardStation = "";
    public static String pendingExternalDimension = "", pendingExternalDestination = "";
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
    private static JourneyTracker localGuidedTracker;
    private record PollContext(String station, String reminderId) {}
    private static final Map<Long, PollContext> pollContexts = new LinkedHashMap<>();
    public static boolean usingNative;
    public static Supplier<String> openKey = () -> "N";
    public static BiPredicate<Integer, Integer> isOpenKey = (key, scan) -> false;

    public static String currentDimension() { return dimension; }
    public static void transport(BooleanSupplier canSend, Consumer<NavRequest> send) { available = canSend; sender = send; }
    public static void open() {
        NavigationUi.openWorkspace();
    }
    public static void tick() {
        NavigationUi.tickFixtures();
        if (NewUiFixtures.enabled()) return;
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
        WalkingClient.tick(mc.screen instanceof WorkspaceScreen || tracking != null);
        OverlayBridge.tick(dim, tracking);
        if (!available.getAsBoolean() || Boolean.getBoolean("mtrnav.forceNative")) {
            usingNative = true;
            NativeMtrClient.tick(dim);
            return;
        }
        if (usingNative) { NativeMtrClient.reset(); usingNative = false; catalog = null; }
        if (!helloSent || (catalog == null && now - lastHello > 3_000_000_000L)) { helloSent = true; lastHello = now; send("hello", "", ""); }
        if ((mc.screen instanceof WorkspaceScreen || tracking != null || reminder != null) && now - lastPoll > 1_000_000_000L) {
            lastPoll = now;
            String primary = !boardStation.isEmpty() ? boardStation : reminder == null ? "" : reminder.stationId();
            send("poll", "", "", primary);
            if (reminder != null && !primary.equals(reminder.stationId())) send("poll", "", "", reminder.stationId());
        }
    }
    public static void reset() {
        fleet = List.of(); vehicleCondition = VehicleCondition.none();
        originLocation.reset(); WalkingClient.reset(); fastestArrival = 0; partialResults = false;
        catalog = null; journeys = List.of(); setReferencePlans(List.of()); referenceReason = ""; arrivals = List.of(); tracking = null; localGuidedTracker = null; reminder = null;
        boardLoading = false; boardUpdatedNanos = boardSampleMillis = 0; pollContexts.clear();
        NavigationUi.reset();
        origin = destination = boardStation = ""; pendingExternalDimension = pendingExternalDestination = ""; status = "mtrnav.connecting";
        queryId = lastTrackingId = lastPoll = lastSample = 0; helloSent = false;
        oldestResponseId = nextId + 1; lastCatalogId = lastHello = 0;
        assembly.clear(); CLOCK.clear(); NavigationApi.cancel(); OverlayBridge.resetSession();
        NativeMtrClient.reset(); usingNative = false;
    }
    public static void query() {
        if (origin.isEmpty() || destination.isEmpty()) { status = "mtrnav.choose_stations"; return; }
        if (!usingNative && WalkingClient.supportsQuery() && WalkingClient.needsConsent()) {
            NavigationUi.confirmWalkingConsent(NavigationClient::query); return;
        }
        if (vehicleCondition.active() && !supportsVehicles()) { status = "mtrnav.vehicle_unsupported"; return; }
        status = "mtrnav.searching"; journeys = List.of(); partialResults = false; fastestArrival = 0;
        setReferencePlans(ConnectionPlan.findAlternatives(catalog, origin, destination,
                usingNative ? List.of() : WalkingClient.sharedConnections(),
                usingNative || WalkingClient.consent() ? WalkingClient.activeLocal() : List.of(),
                sort, 5));
        referenceReason = "mtrnav.reference_pending";
        if (usingNative) NativeMtrClient.query(); else queryId = send("query", "", "");
    }
    public static void clearSearchResults() {
        if (queryId != 0 && !usingNative && WalkingClient.supportsQuery()) send("cancel_query", "", "");
        journeys = List.of(); setReferencePlans(List.of()); referenceReason = ""; queryId = 0;
        OverlayBridge.clearPreviewTarget();
        status = "mtrnav.choose_stations";
        NativeMtrClient.cancelQuery();
    }
    public static void select(Journey journey) {
        localGuidedTracker = null;
        if (usingNative) NativeMtrClient.select(journey);
        else send("select", journey.id(), "");
    }
    public static void selectReference(ConnectionPlan plan) {
        if (plan == null || catalog == null) return;
        NativeMtrClient.cancel();
        if (!usingNative) lastTrackingId = send("cancel", "", "");
        OverlayBridge.clearPreviewTarget();
        var guided = plan.toGuidedJourney(catalog);
        localGuidedTracker = new JourneyTracker(guided);
        var state = localGuidedTracker.correct("");
        tracking = new JourneyTracker.State(state.journey(), state.legIndex(), state.phase(),
                state.phase() == JourneyTracker.Phase.COMPLETE ? "" : state.phase() == JourneyTracker.Phase.WALKING ? state.notice() : "manual_check",
                false, state.stepIndex());
        if (guided.legs().stream().allMatch(l -> !l.fromPlatformId().startsWith("map:") && !l.toPlatformId().startsWith("map:"))) {
            NavigationApi.select(JourneySelection.from(guided));
        } else {
            NavigationApi.cancel();
        }
    }
    public static void correct(String action) {
        if (localGuidedTracker != null) {
            var state = localGuidedTracker.correct(action);
            tracking = new JourneyTracker.State(state.journey(), state.legIndex(), state.phase(),
                    state.phase() == JourneyTracker.Phase.COMPLETE ? "" : state.phase() == JourneyTracker.Phase.WALKING ? state.notice() : "manual_check",
                    false, state.stepIndex());
            return;
        }
        if (usingNative) NativeMtrClient.correct(action);
        else send("correct", tracking == null ? "" : tracking.journey().id(), action);
    }
    public static void cancel() {
        localGuidedTracker = null;
        if (usingNative) NativeMtrClient.cancel();
        else lastTrackingId = send("cancel", "", "");
        tracking = null;
        NavigationApi.cancel();
    }
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
        NavigationUi.onReminderArmed(); tickReminder(System.nanoTime()); return true;
    }
    public static void cancelReminder() { reminder = null; NavigationUi.onReminderCleared(); }
    public static void observeReminder(String station, List<StopEvent> events, long sampleMillis, String expectedId) {
        if (reminder != null && reminder.id().equals(expectedId)) reminder.observe(dimension, station, events, sampleMillis, System.nanoTime());
    }
    private static void tickReminder(long now) {
        if (reminder == null) return;
        if (!reminder.dimension().equals(dimension) || reminder.expired(CLOCK.now(now))) { cancelReminder(); return; }
        if (reminder.tick(CLOCK.now(now), now, !CLOCK.stale(now))) {
            NavigationUi.onReminderTriggered();
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
            if (action.equals("query") && supportsVehicles()) vehicleSender.accept(new NavRequestV3(WireCodec.GSON.toJson(new QueryV3.Request(WalkingClient.queryEnvelope(request), vehicleCondition))));
            else if (action.equals("query") && WalkingClient.supportsQuery()) WalkingClient.sendQuery(request);
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
                journeys = List.of(); setReferencePlans(List.of()); catalog = response.catalog();
            }
        }
        if (catalog != null && response.revision() != catalog.revision()) return;
        if (response.sampleMillis() > 0) fleet = response.fleet();
        if (response.sampleMillis() > lastSample) {
            lastSample = response.sampleMillis(); CLOCK.sample(lastSample, System.nanoTime());
        }
        if (response.kind().equals("query")) {
            journeys = response.journeys();
            fastestArrival = response.report() == null ? journeys.stream().mapToLong(Journey::arrival).min().orElse(0) : response.report().fastestArrival();
            partialResults = response.report() != null && response.report().incomplete();
            status = journeys.isEmpty() ? "mtrnav.no_route" : "mtrnav.ready";
            setReferencePlans(journeys.isEmpty() ? ConnectionPlan.findAlternatives(catalog, origin, destination,
                    usingNative ? List.of() : WalkingClient.sharedConnections(),
                    usingNative || WalkingClient.consent() ? WalkingClient.activeLocal() : List.of(),
                    sort, 5) : List.of());
            referenceReason = response.message().startsWith("vehicle_") ? "mtrnav." + response.message() : response.message().equals("reference_limit") ? "mtrnav.reference_limit"
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
            lastTrackingId = response.id();
            if (localGuidedTracker == null || response.tracking() != null) {
                if (response.tracking() != null) localGuidedTracker = null;
                tracking = response.tracking();
                if (tracking != null) {
                    var selection = JourneySelection.from(tracking.journey());
                    if (NavigationApi.current().isEmpty() || !NavigationApi.current().get().equals(selection)) NavigationApi.select(selection);
                } else NavigationApi.cancel();
            }
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
        NavigationUi.renderHud(graphics);
    }
}
