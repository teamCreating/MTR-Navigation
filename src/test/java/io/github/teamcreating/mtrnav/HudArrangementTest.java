package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudArrangementTest {
    @Test void bothHudsStaySeparatedAndOnScreenAcrossSizesScalesAndAnchors() {
        for (int width : new int[]{320, 640, 854, 2560}) for (int height : new int[]{100, 240, 480, 1351})
            for (int gui : new int[]{1, 2, 4}) for (int percent : new int[]{75, 150, 200}) for (boolean fit : new boolean[]{false, true})
                for (double nx : new double[]{0, .5, 1}) for (double ny : new double[]{0, .5, 1})
                    for (double rx : new double[]{0, .5, 1}) for (double ry : new double[]{0, .5, 1}) {
                        var nav = HudPlacement.at(gui, width, height, percent, nx, ny, fit);
                        var reminder = HudPlacement.reminder(gui, width, height, percent, rx, ry, fit);
                        var pair = HudArrangement.arrange(nav, reminder, width / (double)gui, height / (double)gui, 6.0 / gui);
                        assertFalse(HudArrangement.overlaps(pair.navigation(), pair.reminder(), 0));
                        for (var p : new HudPlacement[]{pair.navigation(), pair.reminder()}) {
                            assertTrue(p.scale() > 0 && Double.isFinite(p.scale()));
                            assertTrue(p.x() >= 0 && p.y() >= 0);
                            assertTrue(p.x() + p.width() <= width / (double)gui + .00001);
                            assertTrue(p.y() + p.height() <= height / (double)gui + .00001);
                        }
                    }
    }
    @Test void collisionMovesReminderWithoutMovingNavigationWhenThereIsSpace() {
        var nav = HudPlacement.at(4, 2560, 1351, 150, 1, 0, true);
        var reminder = HudPlacement.reminder(4, 2560, 1351, 150, 1, 0, true);
        var pair = HudArrangement.arrange(nav, reminder, 640, 1351 / 4.0, 1.5);
        assertEquals(nav.x(), pair.navigation().x(), .00001); assertEquals(nav.y(), pair.navigation().y(), .00001);
        assertFalse(HudArrangement.overlaps(pair.navigation(), pair.reminder(), 1.5));
        assertEquals(pair, HudArrangement.arrange(nav, reminder, 640, 1351 / 4.0, 1.5), "stable inputs do not jitter");
    }
    @Test void crowdedViewportCanShrinkBothEvenWhenAutomaticFitIsOff() {
        var nav = HudPlacement.at(1, 320, 100, 200, .5, .5, false);
        var reminder = HudPlacement.reminder(1, 320, 100, 200, .5, .5, false);
        var pair = HudArrangement.arrange(nav, reminder, 320, 100, 6);
        assertTrue(pair.navigation().scale() < nav.scale()); assertTrue(pair.reminder().scale() < reminder.scale());
        assertFalse(HudArrangement.overlaps(pair.navigation(), pair.reminder(), 6));
    }
    @Test void expandedReservationContainsEveryAnimationFrameAndDragRatiosRoundTrip() {
        var p = HudPlacement.reminder(3, 1280, 720, 175, .3, .7, true);
        assertEquals(.3, p.relativeX(p.x()), .00001); assertEquals(.7, p.relativeY(p.y()), .00001);
        for (int frame = 0; frame <= 100; frame++) {
            double amount = frame / 100.0;
            double width = (int)(248 + 32 * amount) * p.scale(), height = (int)(42 + 24 * amount) * p.scale();
            double x = p.x() + (p.width() - width) / 2;
            assertTrue(x >= p.x() - .00001 && x + width <= p.x() + p.width() + .00001);
            assertTrue(height <= p.height() + .00001);
        }
    }
}
