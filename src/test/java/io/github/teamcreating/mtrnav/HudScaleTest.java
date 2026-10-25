package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.HudScale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudScaleTest {
    @Test void defaultIsFiftyPercentLargerAtTheReportedResolution() {
        double old = HudScale.guiScale(4, 2560, 1351, 100);
        double current = HudScale.guiScale(4, 2560, 1351, HudScale.DEFAULT_PERCENT);
        assertEquals(1.5, current / old, .0001);
        assertEquals(405, current * 4 * HudScale.WIDTH, .0001);
        assertEquals(96.75, current * 4 * HudScale.HEIGHT, .0001);
    }
    @Test void guiScalingDoesNotChangePhysicalSizeAndSmallWindowsStayUsable() {
        for (int width : new int[]{640, 854, 1280, 2560}) for (int percent : new int[]{75, 100, 150, 200}) {
            double expected = HudScale.guiScale(1, width, 720, percent);
            for (int gui = 1; gui <= 4; gui++) {
                double scale = HudScale.guiScale(gui, width, 720, percent);
                assertEquals(expected, scale * gui, .0001);
                assertTrue(scale * gui * HudScale.WIDTH <= width * .30 + .0001);
                assertTrue(scale * gui * HudScale.HEIGHT <= 720 * .20 + .0001);
            }
        }
    }
    @Test void sizeControlsStopAtTheBoundsAndInvalidConfigIsBounded() {
        assertEquals(175, HudScale.step(150, 1)); assertEquals(125, HudScale.step(150, -1));
        assertEquals(200, HudScale.step(200, 1)); assertEquals(75, HudScale.step(75, -1));
        assertEquals(75, HudScale.clamp(-1)); assertEquals(200, HudScale.clamp(9999));
    }
}
