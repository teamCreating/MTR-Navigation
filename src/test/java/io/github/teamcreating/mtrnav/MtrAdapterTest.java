package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.NativeTimetable;
import io.github.teamcreating.mtrnav.core.RoutePlanner;
import io.github.teamcreating.mtrnav.server.MtrCoreReader;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Core objects and the actual private arrival callback, not a mock of its signature. */
class MtrAdapterTest {
    @Test void nativeCallbackPreservesSidingRunOccurrenceAndPredictions() throws Exception {
        var simulator = new Simulator("minecraft/overworld", new String[]{"minecraft/overworld"},
                Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "mtrnav-adapter-"), false);
        try {
            long now = 1_700_000_000_000L;
            var clock = Data.class.getDeclaredField("currentMillis"); clock.setAccessible(true); clock.setLong(simulator, now);
            var a = new Station(simulator); a.setName("滨海北|Binhai North");
            var b = new Station(simulator); b.setName("南苑|Nanyuan");
            a.connectedStations.add(b); b.connectedStations.add(a);
            var p1 = new Platform(new Position(0, 64, 0), new Position(80, 64, 0), TransportMode.TRAIN, simulator);
            var p2 = new Platform(new Position(160, 64, 0), new Position(240, 64, 0), TransportMode.TRAIN, simulator);
            p1.setName("1"); p2.setName("2"); p1.area = a; p2.area = b;
            a.savedRails.add(p1); b.savedRails.add(p2);
            simulator.stations.add(a); simulator.stations.add(b); simulator.platforms.add(p1); simulator.platforms.add(p2);
            var route = new Route(TransportMode.TRAIN, simulator); route.setName("C2"); route.setCircularState(Route.CircularState.CLOCKWISE);
            for (var platform : List.of(p1, p2, p1)) { var stop = new RoutePlatformData(platform.getId()); stop.platform = platform; route.getRoutePlatforms().add(stop); }
            simulator.routes.add(route);
            var depot = new Depot(TransportMode.TRAIN, simulator); depot.setUseRealTime(true); depot.routes.add(route); simulator.depots.add(depot);
            var siding = new Siding(new Position(300, 64, 0), new Position(400, 64, 0), 100, TransportMode.TRAIN, simulator);
            siding.setMaxVehicles(1);
            siding.area = depot; simulator.sidings.add(siding); depot.savedRails.add(siding);
            var departureField = Siding.class.getDeclaredField("departures"); departureField.setAccessible(true);
            ((LongArrayList) departureField.get(siding)).add(Math.floorMod(now + 10_000, 86_400_000L));
            var tripsField = Siding.class.getDeclaredField("trips"); tripsField.setAccessible(true);
            var trip = new Trip(route, 0, 0, siding);
            @SuppressWarnings("unchecked") var trips = (ObjectArrayList<Trip>) tripsField.get(siding); trips.add(trip);
            var stopTimesField = Siding.class.getDeclaredField("platformTripStopTimes"); stopTimesField.setAccessible(true);
            @SuppressWarnings("unchecked") var stopTimes = (Long2ObjectAVLTreeMap<ObjectArraySet<Trip.StopTime>>) stopTimesField.get(siding);
            int index = 0;
            for (var platform : List.of(p1, p2, p1)) {
                var stop = trip.addStopTime(index * 60_000L, index * 60_000L + 10_000, platform.getId(), index, "南苑");
                stopTimes.computeIfAbsent(platform.getId(), ignored -> new ObjectArraySet<>()).add(stop); index++;
            }
            var reader = new MtrCoreReader();
            var snapshot = reader.capture(simulator, Model.Settings.defaults());
            assertEquals(1, snapshot.catalog().routes().size(), "fixture route must be visible");
            assertEquals(3, snapshot.events().size());
            assertEquals(List.of(p1.getHexId(), p2.getHexId(), p1.getHexId()).stream().map(NativeTimetable::id).toList(), snapshot.catalog().routes().getFirst().platforms());
            assertEquals(Set.of(0, 1, 2), new HashSet<>(snapshot.events().stream().map(Model.StopEvent::stopIndex).toList()));
            for (var event : snapshot.events()) {
                assertEquals(NativeTimetable.id(siding.getHexId()), event.run().sidingId());
                assertEquals(0, event.run().tripIndex()); assertEquals(Math.floorDiv(now, 86_400_000L), event.run().departureOffset());
                assertEquals(now + 10_000 + event.stopIndex() * 60_000L, event.arrival());
                assertEquals(event.arrival() + 10_000, event.departure());
                assertFalse(event.realtime()); assertEquals("", event.vehicleId());
            }
            assertFalse(new RoutePlanner().find(snapshot, NativeTimetable.id(a.getHexId()), NativeTimetable.id(b.getHexId()), Model.Sort.FASTEST, Model.Settings.defaults()).isEmpty());
            var revision = snapshot.catalog().revision(); a.setName("renamed");
            assertNotEquals(revision, reader.capture(simulator, Model.Settings.defaults()).catalog().revision());
        } finally { simulator.stop(); }
    }
}
