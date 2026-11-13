package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.UiToggle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiToggleTest {
    @Test void loadingAnExistingPreferenceDoesNotAnimateOrChangeIt() {
        var toggle=new UiToggle();toggle.update(true,1_000_000_000);
        assertEquals(new UiToggle.Frame(14,16),toggle.frame(1_000_000_000));
        toggle.update(true,2_000_000_000);
        assertEquals(new UiToggle.Frame(14,16),toggle.frame(2_000_000_000));
    }
    @Test void bothDirectionsStayWithinTheTrackAndFinishAtTheChosenEnd() {
        var toggle=new UiToggle();toggle.update(false,1_000_000_000);
        for(boolean on:new boolean[]{true,false}){
            toggle.update(on,2_000_000_000L);
            for(long t=0;t<=250_000_000;t+=10_000_000){
                var f=toggle.frame(2_000_000_000L+t);
                assertTrue(f.offset()>=0&&f.offset()+f.width()<=30);
            }
            assertEquals(new UiToggle.Frame(on?14:0,16),toggle.frame(2_250_000_000L));
        }
    }
}
