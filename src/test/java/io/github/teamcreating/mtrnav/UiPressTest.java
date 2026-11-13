package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.UiPress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiPressTest {
    @Test void holdDoesNotActivateAndReleaseActivatesOnce() {
        var press=new UiPress();
        assertTrue(press.press(1_000_000_000));assertTrue(press.down());
        assertFalse(press.press(1_100_000_000)); // Keyboard auto-repeat must not re-arm.
        assertEquals(1,press.depth(1_100_000_000));
        assertTrue(press.release(true,1_200_000_000));
        assertFalse(press.release(true,1_300_000_000));assertFalse(press.down());
    }
    @Test void releaseOutsideOrLosingFocusCancelsActivation() {
        var press=new UiPress();press.press(1_000_000_000);
        assertFalse(press.release(false,1_100_000_000));
        press.press(1_300_000_000);press.cancel(1_400_000_000);
        assertFalse(press.release(true,1_500_000_000));
    }
    @Test void interruptedFaceTravelRemainsContinuousAndReturnsToRest() {
        var press=new UiPress();press.press(1_000_000_000);
        double halfway=press.depth(1_020_000_000);
        assertTrue(halfway>0&&halfway<1);
        press.release(true,1_020_000_000);
        assertEquals(halfway,press.depth(1_020_000_000));
        assertTrue(press.depth(1_060_000_000)<halfway);
        assertEquals(0,press.depth(1_120_000_000));
    }
}
