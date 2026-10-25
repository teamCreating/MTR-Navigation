package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.DepartureBoard;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class DepartureBoardTest {
    private Catalog catalog() {
        return new Catalog("dim", 1, List.of(), List.of(
                new Platform("a", "s", "Inner", 0, 0, 0), new Platform("b", "s", "Outer", 0, 0, 0), new Platform("c", "other", "Other", 1, 0, 0)),
                List.of(new Route("inner", "C2|Circle", 0x0088FF, List.of("a", "b"), true),
                        new Route("outer", "C2|Circle", 0x0088FF, List.of("b", "a"), true),
                        new Route("2", "2号线", 0x22CC22, List.of("a", "c"), false),
                        new Route("other", "Other station", 0, List.of("c", "c"), false)));
    }
    private StopEvent event(String route, String platform, int stop, long departure) {
        return new StopEvent(new RunKey(route, 0, departure, 0), "", route, platform, stop, route + " direction", departure - 20_000, departure, 0, stop % 2 == 0);
    }
    @Test void sameLineDirectionsGroupTogetherWithoutLosingAnyTrips() {
        var events = new ArrayList<StopEvent>();
        for (int i = 20; i > 0; i--) events.add(event(i % 2 == 0 ? "inner" : "outer", i % 2 == 0 ? "a" : "b", i, i * 10_000L));
        var group = DepartureBoard.groups(catalog(), "s", events).stream().filter(g -> g.name().startsWith("C2")).findFirst().orElseThrow();
        assertEquals(20, group.events().size());
        assertEquals(10_000, group.events().getFirst().departure());
        assertEquals(200_000, group.events().getLast().departure());
        assertEquals(2, group.events().stream().map(StopEvent::routeId).distinct().count());
        assertEquals(2, group.events().stream().map(StopEvent::platformId).distinct().count());
        assertTrue(group.events().stream().anyMatch(StopEvent::realtime));
        assertTrue(group.events().stream().anyMatch(e -> !e.realtime()));
        assertThrows(UnsupportedOperationException.class, () -> group.events().clear());
    }
    @Test void emptyLinesRemainVisibleAndRefreshDoesNotChangeGroupIdentity() {
        var first = DepartureBoard.groups(catalog(), "s", List.of());
        assertEquals(2, first.size()); assertTrue(first.stream().allMatch(g -> g.events().isEmpty()));
        var refreshed = DepartureBoard.groups(catalog(), "s", List.of(event("outer", "b", 1, 50_000)));
        assertEquals(first.stream().map(DepartureBoard.Group::key).toList(), refreshed.stream().map(DepartureBoard.Group::key).toList());
        assertTrue(DepartureBoard.groups(catalog(), "missing", List.of()).isEmpty());
    }
    @Test void repeatedPlatformOccurrencesAndUnknownLinesAreNotMergedByTrainNumber() {
        var one = event("inner", "a", 0, 10_000);
        var repeat = new StopEvent(one.run(), "", one.routeId(), one.platformId(), 2, "Other destination", 20_000, 30_000, 0, false);
        var groups = DepartureBoard.groups(catalog(), "s", List.of(one, repeat, event("unresolved", "b", 0, 15_000)));
        assertEquals(3, groups.stream().mapToInt(g -> g.events().size()).sum());
        assertEquals(List.of(0, 2), groups.stream().filter(g -> g.name().startsWith("C2")).findFirst().orElseThrow().events().stream().map(StopEvent::stopIndex).toList());
    }
}
