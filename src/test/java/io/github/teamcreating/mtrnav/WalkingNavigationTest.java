package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.api.JourneySelection;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkingNavigationTest {
    private static final String DIM = "minecraft/overworld";
    private Station station(String id) { return new Station(id, id, 0, 64, 0, List.of()); }
    private Platform platform(String id, String station) { return new Platform(id, station, id, 0, 64, 0); }
    private StopEvent stop(RunKey run, String route, String platform, int index, long time) { return new StopEvent(run, "", route, platform, index, "destination", time, time, 0, false); }
    private WalkingConnection link(String from, String fp, String to, String tp, int seconds) {
        return new WalkingConnection(UUID.randomUUID().toString(), UUID.randomUUID().toString(), DIM, from, fp, to, tp, seconds, "walk", "", true);
    }
    private Snapshot network(long secondDeparture) {
        var catalog = new Catalog(DIM, 1, List.of(station("1"), station("2"), station("3"), station("4")),
                List.of(platform("11", "1"), platform("12", "2"), platform("13", "3"), platform("14", "4")),
                List.of(new Route("21", "C2", 1, List.of("11", "12"), false), new Route("22", "9", 2, List.of("13", "14"), false)));
        var a = new RunKey("a", 0, 0, 0); var b = new RunKey("b", 0, 0, 0);
        return new Snapshot(catalog, 0, List.of(stop(a, "21", "11", 0, 60_000), stop(a, "21", "12", 1, 120_000),
                stop(b, "22", "13", 0, secondDeparture), stop(b, "22", "14", 1, secondDeparture + 60_000)), Map.of());
    }
    private SearchResult search(Snapshot snapshot, String from, String to, List<WalkingConnection> shared, List<WalkingConnection> personal) {
        return new RoutePlanner().search(snapshot, from, to, Sort.FASTEST, Settings.defaults(), QueryOptions.defaults(), shared, personal, 5);
    }
    @Test void fiveDifferentRoutesDoNotRepeatFrequentDepartures() {
        var stations = List.of(station("1"), station("2")); var platforms = List.of(platform("11", "1"), platform("12", "2"));
        var events = new ArrayList<StopEvent>();
        for (int line = 0; line < 8; line++) for (int train = 0; train < 20; train++) {
            var run = new RunKey("s" + line, 0, train, 0); String route = Integer.toHexString(32 + line);
            events.add(stop(run, route, "11", 0, 60_000 + train * 1000)); events.add(stop(run, route, "12", 1, 120_000 + line * 60_000 + train * 1000));
        }
        var snapshot = new Snapshot(new Catalog(DIM, 1, stations, platforms, List.of()), 0, events, Map.of());
        for (var sort : Sort.values()) {
            var result = new RoutePlanner().search(snapshot, "1", "2", sort, Settings.defaults(), QueryOptions.defaults(), List.of(), List.of(), 0);
            assertEquals(5, result.journeys().size());
            assertEquals(5, result.journeys().stream().map(RoutePlanner::pattern).distinct().count());
            assertEquals(120_000, result.fastestArrival());
        }
    }
    @Test void arrivalWindowIsInclusiveAndCanBeDisabled() {
        var events = new ArrayList<StopEvent>();
        for (int i = 0; i < 3; i++) { var run = new RunKey("r" + i, 0, 0, 0); events.add(stop(run, "r" + i, "11", 0, 60_000)); events.add(stop(run, "r" + i, "12", 1, 120_000 + i * 900_000)); }
        var sample = network(240_000); var snap = new Snapshot(sample.catalog(), 0, events, Map.of());
        for (var sort : Sort.values()) {
            assertEquals(2, new RoutePlanner().search(snap, "1", "2", sort, Settings.defaults(), new QueryOptions(5, 15, true), List.of(), List.of(), 0).journeys().size());
            assertEquals(3, new RoutePlanner().search(snap, "1", "2", sort, Settings.defaults(), new QueryOptions(5, -1, true), List.of(), List.of(), 0).journeys().size());
        }
    }
    @Test void declaredWalkingTransferIncludesBufferOnceAndNeverGuessesNearbyStations() {
        var snapshot = network(210_000); var link = link("2", "12", "3", "13", 60);
        assertTrue(search(snapshot, "1", "4", List.of(), List.of()).journeys().isEmpty());
        var found = search(snapshot, "1", "4", List.of(), List.of(link)).journeys().getFirst();
        assertEquals(2, found.legs().size()); assertEquals(1, found.walks().size()); assertEquals(1, found.transfers());
        assertEquals(60_000, found.walkingMillis()); assertEquals(270_000, found.arrival()); assertEquals(3, found.steps().size());
        assertTrue(search(network(209_999), "1", "4", List.of(), List.of(link)).journeys().isEmpty());
        assertEquals(2, JourneySelection.from(found).segments().size(), "map v1 only receives exact rail intervals");
    }
    @Test void sharedRulesOverridePersonalAndSpecificityAppliesWithinLayer() {
        var snapshot = network(210_000); var general = link("2", "", "3", "", 120); var specific = link("2", "12", "3", "13", 60);
        assertTrue(search(snapshot, "1", "4", List.of(general), List.of(specific)).journeys().isEmpty());
        assertFalse(search(snapshot, "1", "4", List.of(general, specific), List.of()).journeys().isEmpty());
        assertTrue(search(snapshot, "1", "4", List.of(), List.of(specific.enabled(false))).journeys().isEmpty());
    }
    @Test void walkingIsManualAndPreviousReturnsToTheWalk() {
        var found = search(network(240_000), "1", "4", List.of(), List.of(link("2", "12", "3", "13", 60))).journeys().getFirst();
        var tracker = new JourneyTracker(found);
        assertEquals(JourneyTracker.Phase.RIDING, tracker.correct("board").phase());
        var walking = tracker.correct("next"); assertEquals(JourneyTracker.Phase.WALKING, walking.phase()); assertEquals(1, walking.legIndex());
        assertEquals(JourneyTracker.Phase.WALKING, tracker.correct("board").phase());
        assertEquals(JourneyTracker.Phase.WALKING, tracker.update(network(240_000), UUID.randomUUID()).phase());
        assertEquals(JourneyTracker.Phase.TRANSFER, tracker.correct("next").phase());
        assertEquals(JourneyTracker.Phase.WALKING, tracker.correct("previous").phase());
    }
    @Test void originAndFinalWalksAreStepsButPureWalkingAndWalkChainsAreExcluded() {
        var snapshot = network(240_000);
        var first = link("2", "", "3", "13", 60);
        var found = search(snapshot, "2", "4", List.of(), List.of(first)).journeys().getFirst();
        assertEquals(0, found.transfers()); assertTrue(found.steps().getFirst().walking());
        assertTrue(search(snapshot, "2", "3", List.of(), List.of(first)).journeys().isEmpty());
        var tail = link("2", "12", "4", "", 60);
        found = search(snapshot, "1", "4", List.of(), List.of(tail)).journeys().getFirst();
        assertEquals(1, found.legs().size()); assertTrue(found.steps().getLast().walking());
        var tracker = new JourneyTracker(found); tracker.correct("board"); assertEquals(JourneyTracker.Phase.WALKING, tracker.correct("next").phase());
        assertEquals(JourneyTracker.Phase.COMPLETE, tracker.correct("next").phase());
        assertTrue(search(snapshot, "2", "4", List.of(), List.of(link("2", "", "1", "", 10), link("1", "", "3", "", 10))).journeys().isEmpty());
    }
    @Test void noTimetableReferenceUsesTheSameDeclaredWalkingEdges() {
        var catalog = network(240_000).catalog();
        assertTrue(ConnectionPlan.find(catalog, "1", "4").isEmpty());
        var plan = ConnectionPlan.find(catalog, "1", "4", List.of(), List.of(link("2", "12", "3", "13", 60))).orElseThrow();
        assertEquals(2, plan.legs().size()); assertEquals(1, plan.walks().size());
    }
    @Test void aLaterTrainWithNativeThroughServiceSurvivesAnEarlierPrefix() {
        var sample = network(240_000); RunKey a = new RunKey("early", 0, 1, 0), b = new RunKey("through", 0, 1, 0), c = new RunKey("through", 1, 1, 0);
        var events = List.of(stop(a, "21", "11", 0, 60_000), stop(a, "21", "12", 1, 110_000),
                stop(b, "21", "11", 0, 70_000), stop(b, "21", "12", 1, 120_000), stop(c, "22", "12", 0, 130_000), stop(c, "22", "14", 1, 200_000));
        var result = search(new Snapshot(sample.catalog(), 0, events, Map.of()), "1", "4", List.of(), List.of()).journeys().getFirst();
        assertEquals(b, result.legs().getFirst().run()); assertEquals(0, result.transfers()); assertEquals(200_000, result.arrival());
    }
    @Test void customServerBufferIsRetainedForDisplayAndWalkingDelaysRefreshTheNextTrain() {
        var snapshot = network(240_000); var link = link("2", "12", "3", "13", 60);
        var journey = new RoutePlanner().search(snapshot, "1", "4", Sort.FASTEST, new Settings(6, 4, 45), QueryOptions.defaults(), List.of(), List.of(link), 0).journeys().getFirst();
        assertEquals(45, journey.transferBufferSeconds());
        var tracker = new JourneyTracker(journey); tracker.correct("board"); tracker.correct("next");
        var later = network(300_000); var sampled = new Snapshot(later.catalog(), 250_000, later.events(), Map.of());
        var state = tracker.update(sampled, UUID.randomUUID());
        assertEquals(JourneyTracker.Phase.WALKING, state.phase()); assertNotEquals("missed_train", state.notice());
        assertEquals(300_000, state.journey().legs().get(1).departure());
    }
}
