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

    @Test void alternativesReturnMultipleDistinctCorridorsRespectingSortOrder() {
        // Origin A (0,0) -> Destination D (3000,0) via 3 distinct corridors:
        // 1) Long scenic direct line: A -> S_far (1500, 6000) -> D (0 transfers, ~12.4 km)
        // 2) Short 1-transfer hub route: A -> B (1500, 0) [L1] then B -> D [L2] (1 transfer, ~3.0 km)
        // 3) Medium 1-transfer north route: A -> C (1500, -1200) [L3] then C -> D [L4] (1 transfer, ~3.8 km)
        // Plus duplicate branch variant of L1/L2 (L1||Express -> L2||Local) that must be deduplicated.
        var stations = List.of(
                new Station("a", "Origin", 0, 64, 0, List.of()),
                new Station("b", "Center", 1500, 64, 0, List.of()),
                new Station("c", "North", 1500, 64, -1200, List.of()),
                new Station("far", "Detour", 1500, 64, 6000, List.of()),
                new Station("d", "Dest", 3000, 64, 0, List.of())
        );
        var platforms = List.of(
                new Platform("pa", "a", "1", 0, 64, 0),
                new Platform("pb1", "b", "1", 1500, 64, 0),
                new Platform("pb2", "b", "2", 1500, 64, 4),
                new Platform("pc1", "c", "1", 1500, 64, -1200),
                new Platform("pc2", "c", "2", 1500, 64, -1204),
                new Platform("pfar", "far", "1", 1500, 64, 6000),
                new Platform("pd", "d", "1", 3000, 64, 0)
        );
        var routes = List.of(
                new Route("scenic", "Scenic Loop", 0x3EC98F, List.of("pa", "pfar", "pd"), false),
                new Route("l1a", "Line 1||Main", 0x4FA3D9, List.of("pa", "pb1"), false),
                new Route("l1b", "Line 1||Branch", 0x4FA3D9, List.of("pa", "pb1"), false),
                new Route("l2", "Line 2", 0xE8A13C, List.of("pb2", "pd"), false),
                new Route("l3", "Line 3", 0xB78BE0, List.of("pa", "pc1"), false),
                new Route("l4", "Line 4", 0xE06C75, List.of("pc2", "pd"), false)
        );
        var catalog = new Catalog("dim", 1, stations, platforms, routes);

        var fastest = ConnectionPlan.findAlternatives(catalog, "a", "d", Sort.FASTEST, 5);
        assertEquals(3, fastest.size(), "Should return all 3 distinct corridors while deduplicating Line 1 variants");
        assertTrue(fastest.getFirst().legs().getFirst().routeId().startsWith("l1"), "Shortest 3 km hub corridor should rank first under FASTEST");
        assertEquals(2, fastest.getFirst().stopCount());
        assertTrue(fastest.getFirst().estimatedBlocks(catalog) < 3500.0);

        var fewest = ConnectionPlan.findAlternatives(catalog, "a", "d", Sort.FEWEST_TRANSFERS, 5);
        assertEquals(3, fewest.size());
        assertEquals(0, fewest.getFirst().transfers(), "0-transfer direct line must rank first under FEWEST_TRANSFERS");
        assertEquals("scenic", fewest.getFirst().legs().getFirst().routeId());
    }
}
