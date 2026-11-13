package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.UiLayout;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UiLayoutTest {
    private static final int[][] VIEWS={{320,180},{360,220},{427,240},{640,360},{960,540},{853,480},{1280,720}};
    @Test void allMainRegionsStayInsideThePanel() {
        for(var size:VIEWS) for(boolean expanded:new boolean[]{false,true}) {
            var view=new UiLayout.Rect(0,0,size[0],size[1]);var l=UiLayout.workspace(size[0],size[1],expanded);
            assertTrue(view.contains(l.panel()),"Panel outside viewport");
            for(var r:List.of(l.header(),l.query(),l.tabs(),l.list(),l.detail(),l.footer(),l.primary()))assertTrue(l.panel().contains(r),"Region outside panel: "+r);
            assertTrue(l.list().bottom()<=l.footer().y(),"Scrolling content overlaps footer");
            assertTrue(l.footer().contains(l.primary()),"Primary action leaves footer");
        }
    }
    @Test void fullHdAtTwoTimesShowsFiveDoubleLineJourneys() {
        var l=UiLayout.workspace(1920/2,1080/2,true);
        assertFalse(l.compact());assertTrue(l.visibleRows()>=5);
        for(int i=0;i<5;i++)assertTrue(l.list().contains(l.row(i)));
    }
    @Test void narrowResultsUseTheWholeContentWidth() {
        var l=UiLayout.workspace(854/2,480/2,false);
        assertTrue(l.compact());assertEquals(l.list(),l.detail());assertTrue(l.visibleRows()>=3);
    }
    @Test void hudSettingsAndPreviewsNeverOverlap() {
        for(var size:VIEWS) {
            var l=UiLayout.hud(size[0],size[1]);
            assertTrue(l.panel().contains(l.settings()));assertTrue(l.panel().contains(l.preview()));assertTrue(l.panel().contains(l.footer()));
            boolean overlap=l.settings().x()<l.preview().right()&&l.settings().right()>l.preview().x()
                    &&l.settings().y()<l.preview().bottom()&&l.settings().bottom()>l.preview().y();
            assertFalse(overlap,"HUD editor areas overlap");
            assertTrue(l.settings().bottom()<=l.footer().y());assertTrue(l.preview().bottom()<=l.footer().y());
        }
    }
}
