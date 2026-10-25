package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.ArrivalReminder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class ArrivalReminderTest {
    private static final long NOW = 1_800_000_000_000L;
    private StopEvent event(long arrival, long deviation) {
        return new StopEvent(new RunKey("native-board", 0, 7, 0), "", "route", "platform", -1, "Destination",
                arrival + deviation, arrival + deviation + 30_000, deviation, deviation != 0);
    }
    private ArrivalReminder reminder(StopEvent event) { return new ArrivalReminder("dim", "station", event, 9, NOW, 0); }
    @Test void firesExactlyOnceAtNineMinutesAndRespectsMonotonicFreshness() {
        var e = event(NOW + 10 * 60_000L, 0); var r = reminder(e);
        r.observe("dim", "station", List.of(e), NOW + 59_999, 1_000_000_000L);
        assertFalse(r.tick(NOW + 59_999, 1_000_000_000L, true));
        assertTrue(r.tick(NOW + 60_000, 1_000_000_001L, true));
        assertFalse(r.tick(NOW + 60_001, 1_000_000_002L, true));
    }
    @Test void selectingACloserTrainNotifiesImmediatelyButNeverAfterDeparture() {
        var e = event(NOW + 120_000, 0);
        assertTrue(reminder(e).tick(NOW, 1, true));
        assertFalse(reminder(e).tick(e.departure() + 1, 1, true));
        assertTrue(reminder(e).expired(e.departure() + 600_001));
    }
    @Test void delayAdjustsThisEntryAndDoesNotReapplyDeviationOrRepeatTheAlert() {
        var e = event(NOW + 10 * 60_000L, 0); var r = reminder(e);
        var delayed = event(e.arrival(), 120_000);
        r.observe("dim", "station", List.of(delayed), NOW + 120_000, 1_000_000_000L);
        assertEquals(e.arrival() + 120_000, r.event().arrival()); assertTrue(r.event().realtime());
        assertFalse(r.tick(NOW + 120_000, 1_000_000_001L, true));
        r.observe("dim", "station", List.of(delayed), NOW + 180_000, 2_000_000_000L);
        assertTrue(r.tick(NOW + 180_000, 2_000_000_001L, true));
        r.observe("dim", "station", List.of(event(e.arrival(), 600_000)), NOW + 1_000_000, 3_000_000_000L);
        assertFalse(r.tick(NOW + 1_000_000, 3_000_000_001L, true));
    }
    @Test void staleClockOrMissingEntryCannotTriggerAndTheNextTrainIsNeverSubstituted() {
        var e = event(NOW + 100_000, 0); var r = reminder(e);
        assertFalse(r.tick(NOW, 1, false));
        assertFalse(r.tick(NOW, ArrivalReminder.STALE_NANOS, true));
        r.observe("dim", "station", List.of(event(e.arrival() + 240_000, 0)), NOW + 1, 6_000_000_000L);
        assertTrue(r.stale(6_000_000_001L)); assertEquals(e, r.event());
        assertFalse(r.tick(NOW, 6_000_000_001L, true));
        r.observe("dim", "station", List.of(e), NOW + 2, 7_000_000_000L);
        assertTrue(r.tick(NOW, 7_000_000_001L, true));
    }
    @Test void duplicateIndicesAndIdenticalPublicEntriesRequireUniqueConfirmation() {
        var e = event(NOW + 100_000, 0); var r = reminder(e);
        assertTrue(ArrivalReminder.unique(e, List.of(e, e)).isEmpty());
        r.observe("dim", "station", List.of(e, e), NOW + 1, 1);
        assertTrue(r.stale(2)); assertFalse(r.tick(NOW, 2, true));
        assertFalse(ArrivalReminder.sameEvent(e, event(e.arrival() + 60_000, 0)), "same public departureIndex is not a full run identity");
    }
    @Test void oldSamplesOtherStationsAndOtherDimensionsCannotRefreshAStaleReminder() {
        var e = event(NOW + 100_000, 0); var r = reminder(e);
        r.observe("dim", "station", List.of(e), NOW, 4_000_000_000L);
        r.observe("other", "station", List.of(e), NOW + 1, 4_000_000_000L);
        r.observe("dim", "other", List.of(e), NOW + 1, 4_000_000_000L);
        assertTrue(r.stale(5_000_000_000L));
        r.observe("dim", "station", List.of(e), NOW + 3, 6_000_000_000L);
        r.observe("dim", "station", List.of(event(e.arrival() + 100_000, 0)), NOW + 2, 7_000_000_000L);
        assertFalse(r.stale(7_000_000_001L)); assertEquals(e, r.event());
    }
    @Test void authoritativeIdentityKeepsSidingLoopAndStopOccurrence() {
        var e = new StopEvent(new RunKey("siding-a", 2, 7, 1234), "vehicle", "route", "platform", 4, "D", NOW + 100_000, NOW + 130_000, 0, true);
        var changedTime = new StopEvent(e.run(), "vehicle", e.routeId(), e.platformId(), 4, "D", e.arrival() + 50_000, e.departure() + 50_000, 50_000, true);
        assertTrue(ArrivalReminder.sameEvent(e, changedTime));
        assertFalse(ArrivalReminder.sameEvent(e, new StopEvent(new RunKey("siding-b", 2, 7, 1234), "vehicle", "route", "platform", 4, "D", e.arrival(), e.departure(), 0, true)));
        assertFalse(ArrivalReminder.sameEvent(e, new StopEvent(e.run(), "vehicle", "route", "platform", 6, "D", e.arrival(), e.departure(), 0, true)));
    }
    @Test void invalidThresholdAndBrokenTimesAreRejected() {
        var e = event(NOW + 100_000, 0);
        assertThrows(IllegalArgumentException.class, () -> new ArrivalReminder("dim", "station", e, 0, NOW, 0));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalReminder("dim", "station", e, 61, NOW, 0));
    }
    @Test void recentlyReceivedButOldServerDataIsNotFresh() {
        var e = event(NOW + 100_000, 0); var r = reminder(e);
        r.observe("dim", "station", List.of(e), NOW + 1, 10_000_000_000L);
        assertFalse(r.fresh(NOW + 10_000, 10_000_000_001L));
        assertFalse(r.tick(NOW + 10_000, 10_000_000_001L, true));
    }
}
