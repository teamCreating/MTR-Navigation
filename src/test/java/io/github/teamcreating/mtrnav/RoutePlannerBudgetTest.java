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
    @Test void unreachableDestinationIsRejectedBeforeExpandingLongBranchingRun() {
        var stations = List.of(new Station("a", "A", 0, 0, 0, List.of()), new Station("b", "B", 0, 0, 0, List.of()), new Station("c", "C", 0, 0, 0, List.of()));
        var platforms = new ArrayList<Platform>(); var events = new ArrayList<StopEvent>();
        var run = new RunKey("s", 0, 0, 0);
        for (int i = 0; i < 10_010; i++) {
            String platform = "p" + i;
            platforms.add(new Platform(platform, i == 0 ? "a" : "b", platform, 0, 0, 0));
            events.add(new StopEvent(run, "", "r", platform, i, "C", i * 1000L, i * 1000L, 0, false));
        }
        var snapshot = new Snapshot(new Catalog("dim", 1, stations, platforms, List.of()), 0, events, Map.of());
        assertTimeout(Duration.ofSeconds(3), () -> assertTrue(new RoutePlanner().find(snapshot, "a", "c", Sort.FASTEST, Settings.defaults()).isEmpty()));
    }

    @Test void highFrequencyNetworkWithFortyFiveThousandEventsFindsDistantConnections() {
        var stations = new ArrayList<Station>(); var platforms = new ArrayList<Platform>(); var events = new ArrayList<StopEvent>();
        for (int i = 0; i <= 75; i++) {
            stations.add(new Station("s" + i, "S" + i, 0, 0, 0, List.of()));
            platforms.add(new Platform("p" + i, "s" + i, "P" + i, 0, 0, 0));
        }
        // Two connected lines, each with 600 departures. The old search fans out
        // over every intermediate stop and departure before considering the goal.
        for (int train = 0; train < 600; train++) for (int line = 0; line < 2; line++) {
            var run = new RunKey("line" + line, 0, train, 0);
            int first = line == 0 ? 0 : 37, last = line == 0 ? 37 : 75;
            for (int stop = first; stop <= last; stop++) {
                long time = 60_000 + train * 10_000L + stop * 30_000L + line * 60_000L;
                events.add(new StopEvent(run, "", "r" + line, "p" + stop, stop - first, "S" + last, time, time, 0, false));
            }
        }
        var snapshot = new Snapshot(new Catalog("dim", 1, stations, platforms, List.of()), 0, events, Map.of());
        assertTrue(events.size() >= 45_000);
        for (var sort : Sort.values()) assertTimeout(Duration.ofSeconds(5), () -> {
            var results = new RoutePlanner().find(snapshot, "s0", "s75", sort, Settings.defaults());
            assertFalse(results.isEmpty()); assertEquals(1, results.getFirst().transfers());
            assertEquals(2, results.getFirst().legs().size());
        });
    }
}
