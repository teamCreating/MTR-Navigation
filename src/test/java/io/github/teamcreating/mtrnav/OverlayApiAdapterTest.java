package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrnav.client.OverlayBridge;
import io.github.teamcreating.mtrnav.client.overlay.OverlayApiAdapter;
import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.Model;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-Java contract tests for the isolated MTR Map Overlay 1.6.0 (MtrMapApi 1.0.0) adapter. */
final class OverlayApiAdapterTest {

    private static final class FakeOverlayClient implements Client {
        final WorldRef world;
        Catalog catalog;
        Map<String, Track> tracksById = new HashMap<>();
        boolean rejectFourArgRegister;
        int registerCalls;
        int resolveCalls;
        int replaceCalls;
        int openViewCalls;
        int closeViewCalls;
        Status nextReplaceStatus = Status.READY;
        Predicate<NavigationTarget> registeredPredicate;
        Consumer<NavigationTarget> registeredHandler;
        ViewRequest lastViewRequest;
        boolean sessionClosed;

        FakeOverlayClient(WorldRef world, Catalog catalog) {
            this.world = world;
            this.catalog = catalog;
        }

        @Override
        public Optional<WorldRef> world(String dimensionId) {
            return world != null && world.dimensionId().equals(dimensionId) ? Optional.of(world) : Optional.empty();
        }

        @Override
        public Catalog catalog(WorldRef ref) {
            return catalog != null && catalog.world().equals(ref)
                    ? catalog
                    : new Catalog(ref, 0, 0, Status.STALE, List.of(), List.of(), List.of());
        }

        @Override
        public List<Track> tracks(WorldRef ref, Set<String> railIds) {
            var out = new ArrayList<Track>();
            for (String id : railIds) {
                if (tracksById.containsKey(id)) out.add(tracksById.get(id));
            }
            return out;
        }

        @Override
        public CompletionStage<Resolution> resolve(WorldRef ref, long revision, List<RouteSection> sections) {
            resolveCalls++;
            if (catalog.status() != Status.READY || catalog.revision() != revision) {
                return CompletableFuture.completedFuture(new Resolution(Status.STALE, catalog.revision(), List.of(), List.of()));
            }
            var spans = new ArrayList<SelectedSpan>();
            for (var section : sections) {
                var route = catalog.routes().stream().filter(r -> r.id().equals(section.routeId())).findFirst().orElse(null);
                if (route == null) return CompletableFuture.completedFuture(new Resolution(Status.INVALID, revision, List.of(), List.of()));
                var variants = route.paths().stream()
                        .filter(p -> section.pathVariant().isEmpty() || section.pathVariant().get().equals(p.id()))
                        .toList();
                if (variants.size() > 1) {
                    return CompletableFuture.completedFuture(new Resolution(Status.AMBIGUOUS, revision, List.of(),
                            variants.stream().map(PathVariant::id).toList()));
                }
                if (variants.isEmpty()) return CompletableFuture.completedFuture(new Resolution(Status.UNAVAILABLE, revision, List.of(), List.of()));
                for (var leg : variants.getFirst().legs()) {
                    if (leg.fromStop() >= section.fromStop() && leg.toStop() <= section.toStop()) {
                        for (var rail : leg.rails()) spans.add(new SelectedSpan(route.id(), rail));
                    }
                }
            }
            return CompletableFuture.completedFuture(new Resolution(Status.READY, revision, spans, List.of()));
        }

        @Override
        public ViewSession openView(String owner, String displayName, Purpose purpose, int priority) {
            openViewCalls++;
            sessionClosed = false;
            return new ViewSession() {
                @Override
                public CompletionStage<Status> replace(ViewRequest request) {
                    replaceCalls++;
                    if (sessionClosed) return CompletableFuture.completedFuture(Status.CLOSED);
                    if (nextReplaceStatus == Status.READY) lastViewRequest = request;
                    return CompletableFuture.completedFuture(nextReplaceStatus);
                }

                @Override public void clear() { lastViewRequest = null; }

                @Override
                public void close() {
                    if (!sessionClosed) {
                        sessionClosed = true;
                        closeViewCalls++;
                        lastViewRequest = null;
                    }
                }
            };
        }

        @Override
        public BoardSubscription watchStation(WorldRef world, String stationId, Consumer<Board> listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration registerNavigation(String owner, String displayName, Consumer<NavigationTarget> handler) {
            registerCalls++;
            registeredPredicate = t -> true;
            registeredHandler = handler;
            return () -> { registeredPredicate = null; registeredHandler = null; };
        }

        @Override
        public Registration registerNavigation(String owner, String displayName, Predicate<NavigationTarget> available, Consumer<NavigationTarget> handler) {
            if (rejectFourArgRegister) throw new UnsupportedOperationException("Legacy 1.5.3 overlay");
            registerCalls++;
            registeredPredicate = available;
            registeredHandler = handler;
            return () -> { registeredPredicate = null; registeredHandler = null; };
        }
    }

    private static final class TestHost implements OverlayApiAdapter.Host {
        String dim = "minecraft/overworld";
        Model.Catalog navCatalog;
        String lastOpenedDim;
        String lastOpenedStation;

        @Override public String currentDimension() { return dim; }
        @Override public Model.Catalog currentCatalog() { return navCatalog; }
        @Override public String displayName() { return "MTR：站间物语"; }
        @Override public void openPlanTarget(String navDimension, String stationId) {
            lastOpenedDim = navDimension;
            lastOpenedStation = stationId;
        }
        @Override public void logWarn(String message, Throwable error) { }
    }

    private static FakeOverlayClient sampleClient() {
        var world = new WorldRef(UUID.randomUUID(), "minecraft:overworld");
        var stations = List.of(
                new Station("a1", "滨海北", new Point(0, 64, 0), List.of("pa1")),
                new Station("b2", "南苑", new Point(100, 64, 0), List.of("pb1", "pb2")),
                new Station("c3", "甘地", new Point(200, 64, 100), List.of("pc1"))
        );
        var platforms = List.of(
                new Platform("pa1", "a1", "1", new Point(0, 64, 0)),
                new Platform("pb1", "b2", "1", new Point(100, 64, 0)),
                new Platform("pb2", "b2", "2", new Point(100, 64, 4)),
                new Platform("pc1", "c3", "1", new Point(200, 64, 100))
        );
        // Route r1 has TWO depot path variants (tests AMBIGUOUS disambiguation!)
        var r1 = new Route("r1", "C2", "C2", 0x4FA3D9, "NORMAL",
                List.of(new Stop(0, "pa1", "a1", "南苑"), new Stop(1, "pb1", "b2", "南苑")),
                List.of(
                        new PathVariant("depot1:0", List.of(new Leg(0, 1, List.of(new RailSpan("rail-1", 0.5, 1.0))))),
                        new PathVariant("depot2:0", List.of(new Leg(0, 1, List.of(new RailSpan("rail-1", 0.5, 1.0)))))
                ));
        var r2 = new Route("r2", "2号线", "2", 0xE8A13C, "HIGH_SPEED",
                List.of(new Stop(0, "pb2", "b2", "甘地"), new Stop(1, "pc1", "c3", "甘地")),
                List.of(new PathVariant("depot3:0", List.of(new Leg(0, 1, List.of(new RailSpan("rail-2", 1.0, 0.25)))))));
        var cat = new Catalog(world, 42L, 10L, Status.READY, stations, platforms, List.of(r1, r2));
        var client = new FakeOverlayClient(world, cat);
        client.tracksById.put("rail-1", new Track("rail-1", List.of(
                new Point(0, 64, 0), new Point(50, 64, 20), new Point(100, 64, 0)), 108.0));
        client.tracksById.put("rail-2", new Track("rail-2", List.of(
                new Point(200, 64, 100), new Point(150, 64, 50), new Point(100, 64, 4)), 140.0));
        return client;
    }

    private static Model.Journey twoLegJourney() {
        var leg1 = new Model.RideLeg(new Model.RunKey("run1", 0, 0, 0), "v1", "r1",
                "a1", "b2", "pa1", "pb1", 0, 1, "南苑", 1000L, 2000L, true, List.of());
        var leg2 = new Model.RideLeg(new Model.RunKey("run2", 0, 1, 0), "v2", "r2",
                "b2", "c3", "pb2", "pc1", 0, 1, "甘地", 3000L, 4000L, true, List.of());
        return new Model.Journey("j-1", "minecraft/overworld", 1L, "a1", "c3",
                0L, 4000L, 1, List.of(leg1, leg2), List.of(), 0L);
    }

    @Test
    void registersNavigationProviderWithAvailabilityAndLegacyFallback() {
        var client = sampleClient();
        var host = new TestHost();
        var adapter = new OverlayApiAdapter(() -> Optional.of(client), host);

        adapter.ensureNavigationRegistered();
        adapter.ensureNavigationRegistered();
        assertEquals(1, client.registerCalls, "Provider must register once");

        // When navCatalog is still null (user hasn't opened N yet), same-dimension targets are navigable
        assertTrue(client.registeredPredicate.test(new NavigationTarget(client.world, "a1")));
        assertFalse(client.registeredPredicate.test(new NavigationTarget(new WorldRef(client.world.serverEpoch(), "minecraft:the_nether"), "a1")));

        // Once navCatalog loads, unknown station IDs are rejected cleanly
        host.navCatalog = adapter.catalog("minecraft/overworld");
        assertNotNull(host.navCatalog);
        assertEquals(3, host.navCatalog.stations().size());
        assertEquals(4, host.navCatalog.platforms().size());
        assertEquals(2, host.navCatalog.routes().size());
        assertTrue(client.registeredPredicate.test(new NavigationTarget(client.world, "c3")));
        assertFalse(client.registeredPredicate.test(new NavigationTarget(client.world, "unknown")));

        // Clicking Navigate invokes host deep-link with normalized dimension
        client.registeredHandler.accept(new NavigationTarget(client.world, "c3"));
        assertEquals("minecraft/overworld", host.lastOpenedDim);
        assertEquals("c3", host.lastOpenedStation);

        // Legacy Overlay 1.5.3 fallback (3-arg overload)
        var legacyClient = sampleClient();
        legacyClient.rejectFourArgRegister = true;
        var legacyAdapter = new OverlayApiAdapter(() -> Optional.of(legacyClient), host);
        legacyAdapter.ensureNavigationRegistered();
        assertEquals(1, legacyClient.registerCalls);
        assertNotNull(legacyClient.registeredHandler);
    }

    @Test
    void highlightsActiveJourneyWithProgressDimmingAmbiguousResolutionAndCancelCleanup() {
        var client = sampleClient();
        var host = new TestHost();
        var adapter = new OverlayApiAdapter(() -> Optional.of(client), host);
        var journey = twoLegJourney();

        // Leg 0 WAITING: both legs remaining (opacity 1.0), 0 completed
        var state0 = new JourneyTracker.State(journey, 0, JourneyTracker.Phase.WAITING, "", true);
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromTracking(state0));
        assertTrue(adapter.isHighlightActive());
        assertEquals(1, client.replaceCalls);
        assertNotNull(client.lastViewRequest);
        assertEquals(2, client.lastViewRequest.spans().size(), "Both legs (including 2-depot r1) must resolve");
        assertEquals(0.08, client.lastViewRequest.unmatchedOpacity(), 1e-6);
        assertTrue(client.lastViewRequest.spanStyles().stream().allMatch(s -> s.style().opacity() == 1.0 && s.style().widthScale() > 2.0));

        // Identical tick must NOT re-resolve or re-replace
        int resolvesBefore = client.resolveCalls;
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromTracking(state0));
        assertEquals(1, client.replaceCalls);
        assertEquals(resolvesBefore, client.resolveCalls);

        // Advance to leg 1 RIDING: leg 0 completed (0.35), leg 1 remaining (1.0)
        var state1 = new JourneyTracker.State(journey, 1, JourneyTracker.Phase.RIDING, "", true);
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromTracking(state1));
        assertEquals(2, client.replaceCalls);
        assertEquals(1, client.lastViewRequest.spanStyles().stream().filter(s -> Math.abs(s.style().opacity() - 0.35) < 1e-6).count());
        assertEquals(1, client.lastViewRequest.spanStyles().stream().filter(s -> Math.abs(s.style().opacity() - 1.0) < 1e-6).count());

        // Cancel trip -> closes ViewSession and clears highlightActive
        adapter.syncHighlight("minecraft/overworld", null);
        assertFalse(adapter.isHighlightActive());
        assertEquals(1, client.closeViewCalls);
    }

    @Test
    void rejectsSyntheticMapPlaceholderIdsAndResolvesRealCurvedTrackGeometry() {
        var client = sampleClient();
        var host = new TestHost();
        var adapter = new OverlayApiAdapter(() -> Optional.of(client), host);

        // Synthetic map: placeholder platform IDs can still verify when exact routeId + stop ordinals + station IDs match Overlay's catalog,
        // while mismatched station IDs are strictly rejected.
        var mismatchedSyntheticLeg = new Model.RideLeg(new Model.RunKey("r", 0, 0, 0), "", "r1",
                "a1", "wrong-station", "map:r1:0", "map:r1:1", 0, 1, "南苑", 1000L, 2000L, false, List.of());
        var mismatchedJourney = new Model.Journey("j-syn-bad", "minecraft/overworld", 1L, "a1", "wrong-station",
                0L, 2000L, 0, List.of(mismatchedSyntheticLeg), List.of(), 0L);
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromJourneyPreview(mismatchedJourney));
        assertFalse(adapter.isHighlightActive(), "Mismatched station IDs must never highlight");
        assertEquals(0, client.replaceCalls);

        var validSyntheticLeg = new Model.RideLeg(new Model.RunKey("r", 0, 0, 0), "", "r1",
                "a1", "b2", "map:r1:0", "map:r1:1", 0, 1, "南苑", 1000L, 2000L, false, List.of());
        var validSyntheticJourney = new Model.Journey("j-syn-ok", "minecraft/overworld", 1L, "a1", "b2",
                0L, 2000L, 0, List.of(validSyntheticLeg), List.of(), 0L);
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromJourneyPreview(validSyntheticJourney));
        assertTrue(adapter.isHighlightActive(), "Verified routeId + stop ordinals + stationIds should highlight cleanly");
        assertEquals(1, client.replaceCalls);

        // Real curved tracks: rail-1 (from 0.5 -> 1.0) and reversed rail-2 (from 1.0 -> 0.25)
        var curves = adapter.resolveJourneyCurves("minecraft/overworld", twoLegJourney());
        assertEquals(2, curves.size());
        assertTrue(curves.get(0).size() >= 2);
        assertEquals(100.0, curves.get(0).getLast()[0], 1e-3);
        assertTrue(curves.get(1).size() >= 2);
        // Reversed rail-2 starts near (100, 4) (fraction 1.0) and heads towards (200, 100) (fraction 0.25)
        assertEquals(100.0, curves.get(1).getFirst()[0], 1e-3);
        assertTrue(curves.get(1).getLast()[0] > 160.0);

        // Reference plan preview also produces valid curved tracks
        var refPlan = new ConnectionPlan("a1", "c3", List.of(
                new ConnectionPlan.Leg("r1", "a1", "b2", "pa1", "pb1", 0, 1, "南苑", List.of()),
                new ConnectionPlan.Leg("r2", "b2", "c3", "pb2", "pc1", 0, 1, "甘地", List.of())
        ));
        assertEquals(2, adapter.resolveReferenceCurves("minecraft/overworld", refPlan).size());
        assertTrue(OverlayBridge.isApiAvailable());

        // Guided reference journey converts cleanly without inventing timestamps and drives Overlay progress highlighting
        var navCat = adapter.catalog("minecraft/overworld");
        var guided = refPlan.toGuidedJourney(navCat);
        assertTrue(ConnectionPlan.isGuidedJourney(guided));
        assertEquals(0L, guided.arrival());
        assertEquals(0L, guided.queryTime());
        assertTrue(guided.legs().stream().allMatch(l -> l.departure() == 0L && l.arrival() == 0L));

        var tracker = new JourneyTracker(guided);
        var s0 = tracker.correct("");
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromTracking(s0));
        assertTrue(adapter.isHighlightActive());
        assertEquals(2, client.lastViewRequest.spans().size());

        var s1 = tracker.correct("next");
        adapter.syncHighlight("minecraft/overworld", OverlayApiAdapter.fromTracking(s1));
        assertEquals(1, client.lastViewRequest.spanStyles().stream().filter(s -> Math.abs(s.style().opacity() - 0.35) < 1e-6).count());
        assertEquals(1, client.lastViewRequest.spanStyles().stream().filter(s -> Math.abs(s.style().opacity() - 1.0) < 1e-6).count());
    }
}
