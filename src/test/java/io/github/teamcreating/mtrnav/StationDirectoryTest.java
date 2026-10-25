package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.StationSearch;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class StationDirectoryTest {
    @Test void blankSearchShowsAllSixHundredAndFiftyOneStationsWithoutAPageLimit() {
        var stations = new ArrayList<Station>();
        for (int i = 650; i >= 0; i--) stations.add(new Station(Integer.toHexString(i), String.format(Locale.ROOT, "车站%03d|Station %03d", i, i), i, 64, -i, List.of()));
        var matches = StationSearch.matches(stations, "  ");
        assertEquals(651, matches.size());
        assertEquals("0", matches.getFirst().id()); assertEquals(Integer.toHexString(650), matches.getLast().id());
        assertEquals(650, matches.getLast().x());
        assertThrows(UnsupportedOperationException.class, () -> matches.clear());
    }
    @Test void bilingualFilterPreservesBothSameNamedStationsAndTheirNativeIdentity() {
        var stations = List.of(new Station("a", "安平|Anping", 10, 64, 20, List.of()),
                new Station("b", "安平|Anping", 1000, 70, 2000, List.of()),
                new Station("c", "新海|Xinhai", 0, 0, 0, List.of()));
        assertEquals(List.of("a", "b"), StationSearch.matches(stations, " ANPING ").stream().map(Station::id).toList());
        assertEquals(List.of(10L, 1000L), StationSearch.matches(stations, "安平").stream().map(Station::x).toList());
        assertEquals("", StationSearch.exact(stations, "Anping"), "typing a duplicate name must still require an explicit directory selection");
    }
    @Test void noMatchesOrAnUnavailableDirectoryAreSafe() {
        assertTrue(StationSearch.matches(List.of(), "新海").isEmpty());
        assertTrue(StationSearch.matches(List.of(new Station("a", "安平|Anping", 0, 0, 0, List.of())), "北野").isEmpty());
    }
}
