package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.UiComparison.Variant;
import io.github.teamcreating.mtrnav.core.UiComparisonLayout;
import io.github.teamcreating.mtrnav.core.UiLayout.Rect;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UiComparisonLayoutTest {
    @Test void everyDesignKeepsFiveFullHdJourneysAndThreeNarrowJourneysVisible() {
        for (var variant : Variant.values()) {
            var wide = UiComparisonLayout.at(960, 540, variant);
            assertFalse(wide.compact()); assertTrue(wide.visibleRows(wide.list(false)) >= 5, variant.toString());
            for (int i = 0; i < 5; i++) assertTrue(wide.list(false).contains(wide.row(wide.list(false), i)));
            var narrow = UiComparisonLayout.at(427, 240, variant);
            assertTrue(narrow.compact()); assertEquals(narrow.list(true), narrow.details(true));
            assertTrue(narrow.visibleRows(narrow.list(true)) >= 3, variant.toString());
        }
    }
    @Test void allMeasuredRegionsStayWithinTheFrameAndKeepTheFooterClear() {
        for (var variant : Variant.values()) for (int[] size : List.of(new int[]{320, 180}, new int[]{427, 240}, new int[]{640, 360}, new int[]{960, 540}, new int[]{1280, 720})) {
            var l = UiComparisonLayout.at(size[0], size[1], variant);
            assertTrue(new Rect(0, 0, size[0], size[1]).contains(l.panel()));
            for (var r : List.of(l.header(), l.navigation(), l.content(), l.footer(), l.rail(), l.hudSettings(), l.hudPreview()))
                assertTrue(l.panel().contains(r), variant + " outside panel: " + r);
            assertTrue(l.content().bottom() < l.footer().y());
            assertFalse(overlaps(l.hudSettings(), l.hudPreview()));
            for (boolean collapsed : new boolean[]{false, true}) {
                for (var r : List.of(l.query(collapsed), l.list(collapsed), l.details(collapsed))) assertTrue(l.content().contains(r));
                assertFalse(overlaps(l.query(collapsed), l.list(collapsed)));
            }
        }
    }
    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
