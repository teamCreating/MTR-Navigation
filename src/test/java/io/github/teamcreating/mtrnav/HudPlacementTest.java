package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.HudPlacement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudPlacementTest {
    @Test void everyCornerStaysInsideTheViewportAtEveryGuiScale() {
        for (int width : new int[]{640, 854, 1280, 2560}) for (int height : new int[]{480, 720, 1351})
            for (int gui = 1; gui <= 4; gui++) for (int percent : new int[]{75, 150, 200})
                for (boolean fit : new boolean[]{false, true}) for (double x : new double[]{0, .5, 1}) for (double y : new double[]{0, .5, 1}) {
                    var p = HudPlacement.at(gui, width, height, percent, x, y, fit);
                    assertTrue(p.x() >= 0 && p.y() >= 0);
                    assertTrue(p.x() + p.width() <= width / (double)gui + .001);
                    assertTrue(p.y() + p.height() <= height / (double)gui + .001);
                }
    }
    @Test void draggingAndRestoringPositionUseNormalizedCoordinates() {
        var original = HudPlacement.at(4, 2560, 1351, 150, .3, .7, true);
        assertEquals(.3, original.relativeX(original.x()), .0001);
        assertEquals(.7, original.relativeY(original.y()), .0001);
        var resized = HudPlacement.at(2, 854, 480, 150, .3, .7, true);
        assertEquals(.3, resized.relativeX(resized.x()), .0001);
        assertEquals(.7, resized.relativeY(resized.y()), .0001);
        assertEquals(0, original.relativeX(-999)); assertEquals(1, original.relativeY(9999));
    }
    @Test void invalidSavedPositionCannotMoveTheHudOffscreen() {
        var p = HudPlacement.at(4, 2560, 1351, 150, Double.NaN, Double.POSITIVE_INFINITY, true);
        assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()));
        assertEquals(1, p.relativeX(p.x()), .0001); assertEquals(0, p.relativeY(p.y()), .0001);
    }
}
