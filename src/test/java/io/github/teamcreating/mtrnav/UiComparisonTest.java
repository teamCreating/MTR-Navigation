package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.UiComparison;
import io.github.teamcreating.mtrnav.core.UiComparison.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiComparisonTest {
    @Test void switchingEveryDesignPreservesTheWholeReviewSession() {
        var s = new UiComparison();
        s.scenario(Scenario.MANY); s.origin = "编辑中的起点 ABC"; s.destination = "测试终点";
        s.selectedJourney = 7; s.listScroll.put(View.ROUTES, 4); s.detailScroll = 48;
        s.stationSearch = "Central"; s.selectedStation = "central"; s.stationScroll = 3;
        s.hud.position(.4, .6); s.hud.size(200); s.saveHud();
        var hud = s.hud.snapshot();
        for (var variant : Variant.values()) {
            s.variant(variant);
            assertEquals("编辑中的起点 ABC", s.origin); assertEquals("测试终点", s.destination);
            assertEquals(7, s.selectedJourney); assertEquals(4, s.listScroll.get(View.ROUTES));
            assertEquals(48, s.detailScroll); assertEquals(3, s.stationScroll);
            assertEquals("Central", s.stationSearch); assertEquals("central", s.selectedStation);
            assertEquals(hud, s.hud.snapshot()); assertTrue(s.hasSavedHud());
        }
    }
    @Test void stationSelectionReturnsToQueryWithoutLosingTheJourneyOrOtherInput() {
        var s = new UiComparison(); String previousOrigin = s.origin;
        s.selectedJourney = 3; s.detailScroll = 72; s.listScroll.put(View.ROUTES, 2);
        s.openStations(false); s.stationSearch = "central"; s.selectedStation = "central";
        assertTrue(s.pickStation()); assertEquals(Page.QUERY, s.page);
        assertEquals(previousOrigin, s.origin); assertEquals("中央车站 Central", s.destination);
        assertEquals(3, s.selectedJourney); assertEquals(72, s.detailScroll); assertEquals(2, s.listScroll.get(View.ROUTES));
    }
    @Test void filteredOutStationsCannotBeApplied() {
        var s = new UiComparison(); s.openStations(true); String origin = s.origin;
        s.selectedStation = "airport"; s.nearby = true;
        assertFalse(s.pickStation()); assertEquals(origin, s.origin); assertEquals(Page.STATIONS, s.page);
        s.nearby = false; s.index = "C";
        assertFalse(s.pickStation()); assertEquals(1, s.stations().size());
        s.index = ""; s.stationSearch = "机场"; assertEquals("airport", s.stations().getFirst().id());
        s.stationSearch = "AIRPORT"; assertEquals(1, s.stations().size());
    }
    @Test void nonRideableStatesCannotStartAndRetryRestoresTheCoreFlow() {
        for (var scenario : new Scenario[]{Scenario.EMPTY, Scenario.LOADING, Scenario.ERROR, Scenario.REFERENCE}) {
            var s = new UiComparison(); s.scenario(scenario);
            assertFalse(s.canStart(), scenario.toString()); s.start(); assertFalse(s.activeTrip);
            s.query(); assertTrue(s.canStart()); s.start(); assertEquals(View.ACTIVE, s.view); assertTrue(s.activeTrip);
        }
        var s = new UiComparison(); s.origin = ""; s.query(); assertFalse(s.canStart());
    }
    @Test void hudSettingsAreIndependentAndSavingIsSessionLocal() {
        var s = new UiComparison(); s.hud.size(200); s.hud.position(1.5, -.5); s.saveHud();
        assertEquals(1, s.hud.navigationX); assertEquals(0, s.hud.navigationY);
        s.hud.reminder = true; s.hud.size(100); s.hud.position(.2, .8);
        assertEquals(200, s.hud.navigationPercent); assertEquals(100, s.hud.reminderPercent);
        s.reloadHud(); assertEquals(150, s.hud.reminderPercent); assertEquals(200, s.hud.navigationPercent);
        var anotherSession = new UiComparison(); assertFalse(anotherSession.hasSavedHud());
        assertEquals(150, anotherSession.hud.navigationPercent);
    }
}
