package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.RoutePlanner;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionPlanTest {
    private Catalog network(int count) {
        var stations = new ArrayList<Station>(); var platforms = new ArrayList<Platform>(); var routes = new ArrayList<Route>();
        for (int i = 0; i <= count; i++) {
            stations.add(new Station("s" + i, "Same name", i, 0, 0, List.of()));
            platforms.add(new Platform("p" + i, "s" + i, "P" + i, i, 0, 0));
            if (i > 0) routes.add(new Route("r" + i, "R" + i, i, List.of("p" + (i - 1), "p" + i), false));
        }
        return new Catalog("dim", 1, stations, platforms, routes);
    }
    @Test void noOperatingTrainsStillHasAnUntimedConnection() {
        var catalog = network(2);
        assertTrue(new RoutePlanner().find(new Snapshot(catalog, 0, List.of(), Map.of()), "s0", "s2", Sort.FASTEST, Settings.defaults()).isEmpty());
        var plan = ConnectionPlan.find(catalog, "s0", "s2").orElseThrow();
        assertEquals(1, plan.transfers()); assertEquals("r1", plan.legs().getFirst().routeId());
        assertEquals("p2", plan.legs().getLast().toPlatformId());
        assertTrue(ConnectionPlan.find(catalog, "s2", "s0").isEmpty(), "respect one-way routes and distinct native IDs despite identical names");
    }
    @Test void referenceCanCrossMoreThanFourTransfersWithoutInventingTimes() {
        var plan = ConnectionPlan.find(network(9), "s0", "s9").orElseThrow();
        assertEquals(8, plan.transfers()); assertEquals(9, plan.legs().size());
    }
    @Test void onlyDeclaredConnectionsAllowTransfersAndRepeatedOccurrencesSurvive() {
        var catalog = network(3);
        var routes = List.of(new Route("circle", "C2", 0, List.of("p0", "p1", "p0", "p2"), true));
        var source = new Catalog("dim", 1, catalog.stations(), catalog.platforms(), routes);
        var plan = ConnectionPlan.find(source, "s1", "s2").orElseThrow();
        assertEquals(1, plan.legs().getFirst().fromStopIndex());
        assertEquals(3, plan.legs().getFirst().toStopIndex());
        assertEquals(List.of("s0"), plan.legs().getFirst().viaStations());
        assertTrue(ConnectionPlan.find(source, "s0", "s3").isEmpty(), "nearby disconnected station is unreachable");
        var stations = new ArrayList<>(source.stations());
        stations.set(2, new Station("s2", "S2", 2, 0, 0, List.of("s3")));
        assertTrue(ConnectionPlan.find(new Catalog("dim", 2, stations, source.platforms(), routes), "s0", "s3").isPresent());
    }
}
