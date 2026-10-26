package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

class StationDirectoryOrderTest {
    private static Station s(String id, String name) { return new Station(id, name, 0, 0, 0, List.of()); }
    @Test void bilingualEnglishAndChineseInitialsSupportFastBrowsing() {
        for (var entry : Map.of("北京|Beijing", "B", "戴安澜|Dai'anlan", "D", "甘地", "G", "北野", "B", "新海", "X", "南苑", "N", "École", "E", "１２３站", "#", "𠮷野", "…").entrySet())
            assertEquals(entry.getValue(), StationDirectoryOrder.group(s("1", entry.getKey())), entry.getKey());
    }
    @Test void ascendingAndDescendingKeepNativeIdentitiesAndStableTies() {
        var input = List.of(s("c", "广州"), s("b", "北京|Beijing"), s("a", "北京|Beijing"), s("d", "上海|Shanghai"), s("e", "★"));
        var ascending = StationDirectoryOrder.sort(input, StationDirectoryOrder.Mode.AZ, Map.of());
        assertEquals(List.of("a", "b", "c", "d", "e"), ascending.stream().map(Station::id).toList());
        var descending = StationDirectoryOrder.sort(input, StationDirectoryOrder.Mode.ZA, Map.of());
        assertEquals(List.of("d", "c", "a", "b", "e"), descending.stream().map(Station::id).toList());
        assertEquals(2, StationDirectoryOrder.first(ascending, "G")); assertEquals(-1, StationDirectoryOrder.first(ascending, "Z"));
        assertEquals(5, input.size());
    }
    @Test void distanceRetainsUnknownStationsAfterKnownDistancesAndOrdersTies() {
        var input = List.of(s("z", "Zulu"), s("b", "Beta"), s("a", "Alpha"), s("n", "NaN"));
        var sorted = StationDirectoryOrder.sort(input, StationDirectoryOrder.Mode.DISTANCE, Map.of("a", 3.0, "b", 3.0, "n", Double.NaN));
        assertEquals(List.of("a", "b", "n", "z"), sorted.stream().map(Station::id).toList());
    }
    @Test void filteringThenIndexingUsesCurrentMatchesAndKeepsAllGroupsAvailable() {
        var input = List.of(s("1", "2站"), s("2", "Airport North"), s("3", "Airport South"), s("4", "Beijing"));
        var filtered = StationDirectoryOrder.sort(StationSearch.matches(input, "airport"), StationDirectoryOrder.Mode.AZ, Map.of());
        assertEquals(2, filtered.size()); assertEquals(0, StationDirectoryOrder.first(filtered, "A")); assertEquals(-1, StationDirectoryOrder.first(filtered, "B"));
        assertEquals(28, StationDirectoryOrder.GROUPS.size());
        assertTrue(StationDirectoryOrder.sort(List.of(), StationDirectoryOrder.Mode.ZA, Map.of()).isEmpty());
    }
}
