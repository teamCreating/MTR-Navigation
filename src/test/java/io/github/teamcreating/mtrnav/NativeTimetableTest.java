package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.map.StationAndRoutes;
import org.mtr.core.map.Departures;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeTimetableTest {
    @Test void missingNativeCoordinatesNeverBecomeARealOriginStation() {
        String json = """
                {"stations":[{"id":"a","name":"real"},{"id":"b","name":"unknown"},{"id":"c","name":"partial"}],
                 "routes":[{"id":"1","name":"line","type":"train","circularState":"NONE","stations":[
                   {"id":"a","name":"1","x":0,"y":0,"z":0},
                   {"id":"b","name":"2"},{"id":"c","name":"3","x":10}]}]}
                """;
        var network = NativeTimetable.network("dim", json, null, Map.of());
        assertEquals(Set.of("a"), network.locatedStations());
        assertEquals(3, network.catalog().stations().size(), "unknown positions remain available for manual search");
        var overlay = new Model.Catalog("dim", 1, List.of(new Model.Station("b", "known", 20, 0, 30, List.of())), List.of(), List.of());
        var enriched = NativeTimetable.network("dim", json, overlay, Map.of());
        assertEquals(Set.of("a", "b"), enriched.locatedStations());
        assertEquals(20, enriched.catalog().station("b").x());
    }
    @Test void enrichingPlatformIdsPreservesNamesOfAnAlreadySelectedJourney() {
        var previous = new Model.Catalog("dim", 1, List.of(), List.of(new Model.Platform("map:r:0", "s", "C2 内环", 1, 2, 3)), List.of());
        var enriched = new Model.Catalog("dim", 2, List.of(), List.of(new Model.Platform("abc", "s", "C2 内环", 1, 2, 3)), List.of());
        var display = enriched.retainPlatformNames(previous);
        assertEquals("C2 内环", display.platformName("map:r:0"));
        assertEquals("C2 内环", display.platformName("abc"));
        assertNull(new Model.Catalog("other", 1, List.of(), List.of(), List.of()).retainPlatformNames(previous).platform("map:r:0"));
    }
    @Test void publishedMapDtosProduceCorrectStopTimesWithoutNavigationServer() {
        var data = new ClientData();
        var a = new Station(data); a.setName("滨海北|Binhai North");
        var b = new Station(data); b.setName("南苑|Nanyuan"); a.connectedStations.add(b);
        var p1 = new Platform(new Position(0, 64, 0), new Position(80, 64, 0), TransportMode.TRAIN, data); p1.setName("1"); p1.area = a;
        var p2 = new Platform(new Position(160, 64, 0), new Position(240, 64, 0), TransportMode.TRAIN, data); p2.setName("2"); p2.area = b;
        var route = new Route(TransportMode.TRAIN, data); route.setName("C2");
        for (var p : List.of(p1, p2, p1)) { var stop = new RoutePlatformData(p.getId()); stop.platform = p; route.getRoutePlatforms().add(stop); }
        route.durations.add(60_000); route.durations.add(75_000);
        var map = new StationAndRoutes(new String[]{"minecraft/overworld"}); map.addStation(a); map.addStation(b); map.addRoute(route);
        var identities = List.of(new NativeTimetable.PlatformIdentity(p1.getHexId(), a.getHexId()), new NativeTimetable.PlatformIdentity(p2.getHexId(), b.getHexId()), new NativeTimetable.PlatformIdentity(p1.getHexId(), a.getHexId()));
        var network = NativeTimetable.network("minecraft/overworld", Utilities.getJsonObjectFromData(map).toString(), null, Map.of(route.getHexId(), identities));
        assertEquals(1, network.lines().size(), "accept actual native route type key");
        assertEquals(2, network.catalog().stations().size());
        assertEquals(List.of(p1.getHexId(), p2.getHexId(), p1.getHexId()).stream().map(NativeTimetable::id).toList(), network.catalog().routes().getFirst().platforms());
        var scheduled = new Object2ObjectAVLTreeMap<String, Long2ObjectAVLTreeMap<LongArrayList>>();
        var grouped = new Long2ObjectAVLTreeMap<LongArrayList>(); var times = new LongArrayList(); times.add(300_000);
        grouped.put(15_000L, times); scheduled.put(route.getHexId(), grouped);
        long sample = 1_700_000_000_000L;
        var snapshot = NativeTimetable.departures(network, Utilities.getJsonObjectFromData(new Departures(sample, scheduled)).toString());
        assertEquals(3, snapshot.events().size());
        var events = snapshot.events().stream().sorted(Comparator.comparingInt(Model.StopEvent::stopIndex)).toList();
        assertEquals(sample + 315_000, events.getLast().departure());
        assertEquals(events.getLast().arrival() - 75_000, events.get(1).departure());
        assertEquals(events.get(1).arrival() - 60_000, events.getFirst().departure());
        assertTrue(events.stream().noneMatch(Model.StopEvent::realtime), "map DTO lacks a realtime identity flag");
        assertTrue(events.stream().allMatch(e -> e.vehicleId().isEmpty()));
        assertFalse(new RoutePlanner().find(snapshot, NativeTimetable.id(a.getHexId()), NativeTimetable.id(b.getHexId()), Model.Sort.FASTEST, Model.Settings.defaults()).isEmpty());
    }
    @Test void uniqueBilingualNamesCanBeTypedAndDuplicateNamesRequireChoice() {
        var a = new Model.Station("a", "滨海北|Binhai North", 0, 0, 0, List.of());
        var b = new Model.Station("b", "南苑|Nanyuan", 10, 0, 0, List.of());
        assertEquals("a", StationSearch.exact(List.of(a, b), "滨海北"));
        assertEquals("a", StationSearch.exact(List.of(a, b), "binhai north"));
        assertEquals("", StationSearch.exact(List.of(a, b), "滨海"));
        assertEquals("", StationSearch.exact(List.of(a, new Model.Station("c", a.name(), 100, 0, 0, List.of())), "滨海北"));
    }
    @Test void normalStationSelectionBurstIsNotSilentlyDropped() {
        var budget = new RequestBudget();
        for (int i = 0; i < 6; i++) assertTrue(budget.allow(1_000_000_000L + i));
        assertFalse(budget.allow(1_000_000_007L)); assertTrue(budget.allow(1_250_000_007L));
    }
}
