package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.PollingOrder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PollingOrderTest {
    @Test void slowRequestsDoNotStarveEitherStationBehindGlobalTimetables() {
        long now = 10_000_000_000L, departures = 0, board = 0, reminder = 0;
        var seen = new EnumMap<PollingOrder.Job, Long>(PollingOrder.Job.class);
        for (int i = 0; i < 16; i++, now += 1_500_000_000L) {
            var job = PollingOrder.next(now - departures >= 3_000_000_000L, departures, now - board >= 1_000_000_000L, board,
                    now - reminder >= 1_000_000_000L, reminder, false);
            assertNotEquals(PollingOrder.Job.NONE, job);
            if (seen.containsKey(job)) assertTrue(now - seen.get(job) < 5_000_000_000L, "source must update before its five-second stale limit");
            seen.put(job, now);
            switch (job) { case DEPARTURES -> departures = now; case BOARD -> board = now; case REMINDER -> reminder = now; default -> fail(); }
        }
        assertEquals(3, seen.size());
    }
    @Test void explicitQueryCanRequestItsMissingTimetableFirst() {
        assertEquals(PollingOrder.Job.DEPARTURES, PollingOrder.next(true, 100, true, 0, true, 0, true));
    }
    @Test void aBackgroundReminderDoesNotRequireGlobalTimetablesOrAVisibleBoard() {
        assertEquals(PollingOrder.Job.REMINDER, PollingOrder.next(false, 0, false, 0, true, 0, false));
        assertEquals(PollingOrder.Job.NONE, PollingOrder.next(false, 0, false, 0, false, 0, false));
    }
}
