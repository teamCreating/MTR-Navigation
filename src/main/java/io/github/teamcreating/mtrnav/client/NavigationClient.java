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
    public static boolean boardLoading;
    public static long boardUpdatedNanos;
    public static String status = "mtrnav.connecting";
    public static String origin = "", destination = "", boardStation = "";
    public static Sort sort = Sort.FASTEST;
    public static final ServerClock CLOCK = new ServerClock();
    private static final WireCodec.Assembly assembly = new WireCodec.Assembly();
    private static Consumer<NavRequest> sender = ignored -> {};
    private static BooleanSupplier available = () -> false;
    private static Object connection;
    private static String dimension = "";
    private static long nextId, queryId, lastPoll, startedNanos, lastTrackingId, lastSample;
    private static long oldestResponseId, lastCatalogId, lastHello, boardRequestId;
    private static long lastTickNanos;
    private static boolean helloSent;
    public static boolean usingNative;
    public static Supplier<String> openKey = () -> "N";

    public static void transport(BooleanSupplier canSend, Consumer<NavRequest> send) { available = canSend; sender = send; }
    public static void open() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) { NativeMtrClient.opened(); mc.setScreen(new NavigationScreen()); }
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
        if (!available.getAsBoolean() || Boolean.getBoolean("mtrnav.forceNative")) {
            usingNative = true;
            NativeMtrClient.tick(dim);
            return;
        }
        if (usingNative) { NativeMtrClient.reset(); usingNative = false; catalog = null; }
        if (!helloSent || (catalog == null && now - lastHello > 3_000_000_000L)) { helloSent = true; lastHello = now; send("hello", "", ""); }
        if ((mc.screen instanceof NavigationScreen || tracking != null) && now - lastPoll > 1_000_000_000L) {
            lastPoll = now; send("poll", "", "");
        }
    }
    public static void reset() {
        catalog = null; journeys = List.of(); referencePlan = null; referenceReason = ""; arrivals = List.of(); tracking = null;
        boardLoading = false; boardUpdatedNanos = 0;
        origin = destination = boardStation = ""; status = "mtrnav.connecting";
        queryId = lastTrackingId = lastPoll = lastSample = 0; helloSent = false;
        oldestResponseId = nextId + 1; lastCatalogId = lastHello = 0;
        assembly.clear(); CLOCK.clear(); NavigationApi.cancel();
        NativeMtrClient.reset(); usingNative = false;
    }
    public static void query() {
        if (origin.isEmpty() || destination.isEmpty()) { status = "mtrnav.choose_stations"; return; }
        status = "mtrnav.searching"; journeys = List.of();
        referencePlan = ConnectionPlan.find(catalog, origin, destination).orElse(null);
        referenceReason = "mtrnav.reference_pending";
        if (usingNative) NativeMtrClient.query(); else queryId = send("query", "", "");
    }
    public static void select(Journey journey) { if (usingNative) NativeMtrClient.select(journey); else send("select", journey.id(), ""); }
    public static void correct(String action) { if (usingNative) NativeMtrClient.correct(action); else send("correct", tracking == null ? "" : tracking.journey().id(), action); }
    public static void cancel() { if (usingNative) NativeMtrClient.cancel(); else lastTrackingId = send("cancel", "", ""); tracking = null; NavigationApi.cancel(); }
    public static void board(String station) {
        boardStation = station; arrivals = List.of(); boardLoading = true; boardUpdatedNanos = 0;
        if (usingNative) NativeMtrClient.refreshBoard(); else boardRequestId = send("poll", "", "");
    }
    public static void stopBoard() { boardStation = ""; arrivals = List.of(); boardLoading = false; boardUpdatedNanos = 0; }
    private static long send(String action, String journeyId, String correction) {
        long id = ++nextId;
        if (available.getAsBoolean()) sender.accept(new NavRequest(WireCodec.GSON.toJson(new Messages.Request(action, dimension, id,
                catalog == null ? 0 : catalog.revision(), origin, destination, sort, journeyId, boardStation, correction))));
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
            journeys = response.journeys(); status = journeys.isEmpty() ? "mtrnav.no_route" : "mtrnav.ready";
            referencePlan = journeys.isEmpty() ? ConnectionPlan.find(catalog, origin, destination).orElse(null) : null;
            referenceReason = response.message().equals("reference_limit") ? "mtrnav.reference_limit"
                    : response.message().isEmpty() || response.message().equals("reference_no_service") ? "mtrnav.reference_no_service" : "mtrnav.reference_incomplete";
        } else if (response.kind().equals("hello")) status = "mtrnav.ready";
        if (response.kind().equals("poll") && response.id() >= Math.max(lastTrackingId, boardRequestId) && !boardStation.isEmpty()) {
            arrivals = response.arrivals(); boardLoading = false; boardUpdatedNanos = System.nanoTime();
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
    }
}
