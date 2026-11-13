package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class VehicleQueryTest {
    static final long NOW = 1_000_000;
    static final Catalog CATALOG = new Catalog("minecraft/overworld", 7,
            List.of(station("a"), station("b"), station("c")),
            List.of(platform("a"), platform("b"), platform("c")),
            List.of(new Route("r", "Line", 123, List.of("a", "b", "c"), false, RouteType.HIGH_SPEED)));
    static Station station(String id) { return new Station(id, id, 0, 0, 0, List.of()); }
    static Platform platform(String id) { return new Platform(id, id, id, 0, 0, 0); }
    static Consist stock(String... ids) { return new Consist(List.of(ids), StockSource.PLANNED); }
    static List<StopEvent> run(String id, String route, String from, String to, int depart, int arrive, Consist consist) {
        var key = new RunKey(id, 0, 0, 0);
        return List.of(new StopEvent(key, "instance:" + id, route, from, 0, to, NOW + depart * 1000L, NOW + depart * 1000L, 0, false, consist),
                new StopEvent(key, "instance:" + id, route, to, 1, to, NOW + arrive * 1000L, NOW + arrive * 1000L, 0, false, consist));
    }
    static Snapshot snapshot(List<StopEvent> events) { return new Snapshot(CATALOG, NOW, events, Map.of()); }
    static SearchResult search(Snapshot snapshot, VehicleMode mode, int minutes, String... ids) {
        return new RoutePlanner().search(snapshot, "a", "c", Sort.FASTEST, Settings.defaults(), new QueryOptions(5, minutes, true),
                List.of(), List.of(), 0, new VehicleCondition(mode, List.of(ids)));
    }
    @Test void strictSearchFindsSeventhTrainOnTheSamePathInsteadOfPostFilteringTopFive() {
        var events = new ArrayList<StopEvent>();
        for (int i = 0; i < 7; i++) events.addAll(run("s" + i, "r", "a", "c", 10 + i * 60, 50 + i * 60, stock(i == 6 ? "target" : "other")));
        var source = snapshot(events);
        assertEquals("other", search(source, VehicleMode.NONE, 30).journeys().getFirst().legs().getFirst().consist().cars().getFirst());
        var result = search(source, VehicleMode.REQUIRE, 30, "target");
        assertEquals(1, result.journeys().size()); assertEquals("s6", result.journeys().getFirst().legs().getFirst().run().sidingId());
    }
    @Test void mixedConsistMatchesAnySelectedCarButNotVehicleInstanceId() {
        var source = snapshot(run("vehicle", "r", "a", "c", 10, 50, stock("cab", "target", "tail")));
        assertEquals(1, search(source, VehicleMode.REQUIRE, 30, "absent", "target").journeys().size());
        assertTrue(search(source, VehicleMode.REQUIRE, 30, "instance:vehicle").journeys().isEmpty());
        assertEquals(List.of("cab", "target", "tail"), search(source, VehicleMode.NONE, 30).journeys().getFirst().legs().getFirst().consist().cars());
    }
    @Test void feederWithOtherModelMayLeadToMatchingLeg() {
        var events = new ArrayList<>(run("feeder", "r", "a", "b", 10, 50, stock("other")));
        events.addAll(run("target", "r2", "b", "c", 90, 150, stock("target")));
        var result = search(snapshot(events), VehicleMode.REQUIRE, 30, "target");
        assertEquals(2, result.journeys().getFirst().legs().size()); assertEquals(1, result.journeys().getFirst().transfers());
    }
    @Test void matchingStateSurvivesDominanceAtInterchange() {
        var events = new ArrayList<>(run("early", "r", "a", "b", 10, 30, stock("other")));
        events.addAll(run("late", "r", "a", "b", 40, 60, stock("target")));
        events.addAll(run("last", "r2", "b", "c", 100, 160, stock("other")));
        assertEquals("late", search(snapshot(events), VehicleMode.REQUIRE, 30, "target").journeys().getFirst().legs().getFirst().run().sidingId());
    }
    @Test void preferenceUsesUnfilteredFastestArrivalAndRetainsOtherPathFallback() {
        var events = new ArrayList<>(run("fast", "r", "a", "c", 10, 50, stock("other")));
        events.addAll(run("target", "r2", "a", "c", 100, 500, stock("target")));
        var result = search(snapshot(events), VehicleMode.PREFER, 30, "target");
        assertEquals(NOW + 50_000, result.fastestArrival());
        assertEquals(List.of("target", "fast"), result.journeys().stream().map(j -> j.legs().getFirst().run().sidingId()).toList());
    }
    @Test void preferenceHonoursLateWindowButStrictWindowStartsAtFirstMatchingArrival() {
        var events = new ArrayList<>(run("fast", "r", "a", "c", 10, 50, stock("other")));
        events.addAll(run("late", "r2", "a", "c", 1900, 2000, stock("target")));
        assertEquals("fast", search(snapshot(events), VehicleMode.PREFER, 30, "target").journeys().getFirst().legs().getFirst().run().sidingId());
        assertEquals("late", search(snapshot(events), VehicleMode.PREFER, -1, "target").journeys().getFirst().legs().getFirst().run().sidingId());
        assertEquals(NOW + 2_000_000, search(snapshot(events), VehicleMode.REQUIRE, 15, "target").fastestArrival());
    }
    @Test void unknownConsistNeverInventsAMatchAndWalkingOnlyDoesNotMatch() {
        assertTrue(search(snapshot(run("unknown", "r", "a", "c", 10, 50, Consist.unknown())), VehicleMode.REQUIRE, 30, "target").journeys().isEmpty());
        var result = new RoutePlanner().search(snapshot(List.of()), "a", "a", Sort.FASTEST, Settings.defaults(), QueryOptions.defaults(), List.of(), List.of(), 0, new VehicleCondition(VehicleMode.REQUIRE, List.of("target")));
        assertTrue(result.journeys().isEmpty());
    }
    @Test void revalidationUsesFreshConsistAndCompleteRunIdentity() {
        var source = snapshot(run("s", "r", "a", "c", 10, 50, stock("target")));
        var condition = new VehicleCondition(VehicleMode.REQUIRE, List.of("target"));
        var journey = search(source, VehicleMode.REQUIRE, 30, "target").journeys().getFirst();
        assertTrue(condition.matchesCurrent(journey, source));
        assertFalse(condition.matchesCurrent(journey, snapshot(run("s", "r", "a", "c", 10, 50, stock("changed")))));
        assertFalse(condition.matchesCurrent(journey, snapshot(run("different", "r", "a", "c", 10, 50, stock("target")))));
    }
    @Test void oldWireDataDefaultsToUnknownAndV3RoundTripsWithWalkingOptions() {
        var old = WireCodec.GSON.fromJson("{\"id\":\"r\",\"name\":\"Line\",\"color\":1,\"platforms\":[],\"circular\":false}", Route.class);
        assertEquals(RouteType.UNKNOWN, old.routeType());
        var event = WireCodec.GSON.fromJson("{\"run\":{\"sidingId\":\"s\",\"tripIndex\":0},\"vehicleId\":\"instance\",\"routeId\":\"r\",\"platformId\":\"a\"}", StopEvent.class);
        assertEquals(Consist.unknown(), event.consist());
        var request = new Messages.Request("query", CATALOG.dimension(), 1, 7, "a", "c", Sort.FASTEST, "", "", "");
        var query = new QueryV3.Request(new QueryV2.Request(request, QueryOptions.defaults(), 17, List.of()), new VehicleCondition(VehicleMode.PREFER, List.of("cab", "target")));
        assertEquals(query, WireCodec.GSON.fromJson(WireCodec.GSON.toJson(query), QueryV3.Request.class));
    }
    @Test void conditionValidatesWireBoundsAndCopiesData() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleCondition(VehicleMode.REQUIRE, java.util.stream.IntStream.range(0, 17).mapToObj(String::valueOf).toList()));
        assertThrows(IllegalArgumentException.class, () -> new VehicleCondition(VehicleMode.PREFER, List.of(" ")));
        assertEquals(VehicleMode.NONE, new VehicleCondition(VehicleMode.REQUIRE, List.of()).mode());
        var cars = new ArrayList<>(List.of("one", "two")); var value = new Consist(cars, StockSource.ACTUAL); cars.clear(); assertEquals(2, value.count());
    }
    @Test void sameNameAndColorWithDifferentRouteCategoriesRemainSeparateBoardGroups() {
        var routes = List.of(new Route("r", "Line", 123, List.of("a", "c"), false, RouteType.NORMAL), new Route("r2", "Line", 123, List.of("a", "c"), false, RouteType.HIGH_SPEED));
        var c = new Catalog(CATALOG.dimension(), 7, CATALOG.stations(), CATALOG.platforms(), routes);
        assertEquals(2, DepartureBoard.groups(c, "a", List.of()).size());
    }
}
