package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class StationLocatorTest {
    private Station station(String id, long x, long y, long z) { return new Station(id, "同名|Same", x, y, z, List.of()); }
    private Catalog catalog(Station... stations) { return new Catalog("dim", 1, List.of(stations), List.of(), List.of()); }
    private final StationLocator.Position origin = new StationLocator.Position("dim", 0, 0);

    @Test void nearbyUsesHorizontalDistanceAndNativeIds() {
        var list = StationLocator.nearby(catalog(station("a", -30, 999, -40), station("b", 0, 0, 130)), null, origin);
        assertEquals("a", list.getFirst().station().id()); assertEquals(50, list.getFirst().distance());
        assertTrue(StationLocator.unambiguous(list));
    }
    @Test void radiusAndAmbiguityBoundariesRequireConfirmation() {
        assertTrue(StationLocator.unambiguous(StationLocator.nearby(catalog(station("a", 512, 0, 0)), null, origin)));
        assertFalse(StationLocator.unambiguous(StationLocator.nearby(catalog(station("a", 513, 0, 0)), null, origin)));
        assertFalse(StationLocator.unambiguous(StationLocator.nearby(catalog(station("a", 10, 0, 0), station("b", 74, 0, 0)), null, origin)));
        assertTrue(StationLocator.unambiguous(StationLocator.nearby(catalog(station("a", 10, 0, 0), station("b", 75, 0, 0)), null, origin)));
        assertFalse(StationLocator.unambiguous(List.of()));
    }
    @Test void equidistantCandidatesHaveStableOrderWithoutDeduplicatingNames() {
        var list = StationLocator.nearby(catalog(station("b", 10, 0, 0), station("a", -10, 0, 0)), null, origin);
        assertEquals(List.of("a", "b"), list.stream().map(c -> c.station().id()).toList());
        assertFalse(StationLocator.unambiguous(list));
    }
    @Test void unknownCoordinatesAreExcludedButRealZeroIsValid() {
        var list = StationLocator.nearby(catalog(station("unknown", 0, 0, 0), station("real", 0, 0, 0)), Set.of("real"), origin);
        assertEquals(List.of("real"), list.stream().map(c -> c.station().id()).toList());
        assertEquals(0, list.getFirst().distance());
    }
    @Test void refusesOtherDimensionsAndInvalidPlayerPositions() {
        var c = catalog(station("a", 0, 0, 0));
        assertTrue(StationLocator.nearby(c, null, new StationLocator.Position("other", 0, 0)).isEmpty());
        assertTrue(StationLocator.nearby(c, null, new StationLocator.Position("dim", Double.NaN, 0)).isEmpty());
        assertTrue(StationLocator.nearby(null, null, origin).isEmpty());
    }
    @Test void delayedCatalogDoesNotConsumeAutomaticAttemptAndEditingCancelsIt() {
        var session = new StationLocator.Session();
        assertFalse(session.ready(false, true, true, false));
        assertTrue(session.ready(false, true, true, true));
        session.edited(); // includes typing, clearing, swapping and selecting manually
        assertFalse(session.ready(false, true, true, true));
        session.reset(); assertTrue(session.ready(false, true, true, true));
        session.attempted(); assertFalse(session.ready(false, true, true, true));
    }
    @Test void reopeningOrUserInputCannotOverwriteAnOriginAndHintsAreScoped() {
        var session = new StationLocator.Session();
        assertFalse(session.ready(true, false, true, true));
        assertFalse(session.ready(false, true, true, true));
        session.reset(); assertFalse(session.ready(false, false, true, false));
        assertFalse(session.ready(false, true, true, true));
        session.selected("a", origin);
        assertEquals(origin, session.positionFor("a", "dim"));
        assertNull(session.positionFor("b", "dim")); assertNull(session.positionFor("a", "other"));
        session.edited(); assertNull(session.positionFor("a", "dim"));
        session.selected("a", origin); session.reset(); assertNull(session.positionFor("a", "dim"));
    }
    @Test void catalogRevisionRemovesOldStationsWithoutChangingDistanceAnchor() {
        var position = new StationLocator.Position("dim", 100, 0);
        assertEquals(10, StationLocator.nearby(catalog(station("a", 110, 0, 0)), null, position).getFirst().distance());
        assertEquals("b", StationLocator.nearby(catalog(station("b", 200, 0, 0)), null, position).getFirst().station().id());
        assertEquals(100, position.x());
    }
}
