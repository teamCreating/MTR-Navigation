package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.RoutePlanner;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerBudgetTest {
    @Test void budgetForMoreAlternativesDoesNotDiscardAConnectionAlreadyFound() {
        var stations = new ArrayList<Station>();
        for (String id : List.of("a", "b", "c", "x")) stations.add(new Station(id, id, 0, 0, 0, List.of()));
        var platforms = new ArrayList<Platform>(); var events = new ArrayList<StopEvent>();
        for (String id : List.of("a", "b", "c")) platforms.add(new Platform(id, id, id, 0, 0, 0));
        var direct = new RunKey("direct", 0, 0, 0);
        events.add(new StopEvent(direct, "", "r", "a", 0, "b", 0, 0, 0, false));
        events.add(new StopEvent(direct, "", "r", "b", 1, "b", 1000, 1000, 0, false));
        var branch = new RunKey("branch", 0, 0, 0);
        events.add(new StopEvent(branch, "", "q", "a", 0, "c", 0, 0, 0, false));
        events.add(new StopEvent(branch, "", "q", "c", 1, "c", 2000, 2000, 0, false));
        var longRun = new RunKey("long", 0, 0, 0);
        events.add(new StopEvent(longRun, "", "long", "c", 0, "x", 50_000, 50_000, 0, false));
        for (int i = 1; i <= 10_010; i++) {
            platforms.add(new Platform("x" + i, "x", "x", 0, 0, 0));
            events.add(new StopEvent(longRun, "", "long", "x" + i, i, "x", 50_000 + i * 1000L, 50_000 + i * 1000L, 0, false));
        }
        var snapshot = new Snapshot(new Catalog("dim", 1, stations, platforms, List.of()), 0, events, Map.of());
        var results = new RoutePlanner().find(snapshot, "a", "b", Sort.FASTEST, Settings.defaults());
        assertEquals(1, results.size()); assertEquals(1000, results.getFirst().arrival());
    }
    @Test void cancelledQueryStopsInsteadOfHoldingTheWorker() {
        var catalog = new Catalog("dim", 1, List.of(new Station("a", "A", 0, 0, 0, List.of()), new Station("b", "B", 0, 0, 0, List.of())), List.of(), List.of());
        Thread.currentThread().interrupt();
        try { assertThrows(IllegalStateException.class, () -> new RoutePlanner().find(new Snapshot(catalog, 0, List.of(), Map.of()), "a", "b", Sort.FASTEST, Settings.defaults())); }
        finally { Thread.interrupted(); }
    }
    @Test void veryLongBranchingRunCannotGrowAnUnboundedSearchQueue() {
        var stations = List.of(new Station("a", "A", 0, 0, 0, List.of()), new Station("b", "B", 0, 0, 0, List.of()), new Station("c", "C", 0, 0, 0, List.of()));
        var platforms = new ArrayList<Platform>(); var events = new ArrayList<StopEvent>();
        var run = new RunKey("s", 0, 0, 0);
        for (int i = 0; i < 10_010; i++) {
            String platform = "p" + i;
            platforms.add(new Platform(platform, i == 0 ? "a" : "b", platform, 0, 0, 0));
            events.add(new StopEvent(run, "", "r", platform, i, "C", i * 1000L, i * 1000L, 0, false));
        }
        var snapshot = new Snapshot(new Catalog("dim", 1, stations, platforms, List.of()), 0, events, Map.of());
        assertTimeout(Duration.ofSeconds(3), () -> assertThrows(IllegalStateException.class,
                () -> new RoutePlanner().find(snapshot, "a", "c", Sort.FASTEST, Settings.defaults())));
    }
}
