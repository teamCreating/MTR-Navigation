package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollBarGeometryTest {
    @Test void draggingCandidatesReachesTheFirstAndLastStation() {
        var bar = ScrollBarGeometry.of(100, 66, 651, 3, 0, 8);
        assertEquals(648, bar.maxOffset());
        assertEquals(8, bar.thumbHeight());
        assertEquals(0, bar.offsetAtThumbTop(-100));
        assertEquals(648, bar.offsetAtThumbTop(166 - bar.thumbHeight()));
        assertEquals(648, bar.offsetAtThumbTop(9999));
    }
    @Test void pixelContentAndExpandedListsKeepTheirScrollPosition() {
        for (int content : new int[]{100, 1200, 14000}) {
            int visible = 80, max = content - visible;
            for (int offset : new int[]{0, max / 2, max}) {
                var bar = ScrollBarGeometry.of(37, 300, content, visible, offset, 8);
                assertEquals(offset, bar.offsetAtThumbTop(37 + 1.0 * (bar.height() - bar.thumbHeight()) * offset / max));
                assertTrue(bar.onThumb(bar.thumbTop() + bar.thumbHeight() / 2.0));
            }
        }
        var expanded = ScrollBarGeometry.of(100, 176, 651, 8, 643, 8);
        assertEquals(643, expanded.offsetAtThumbTop(expanded.thumbTop()));
    }
    @Test void fittingOrTinyContentNeverCreatesAnInvalidDragRange() {
        for (int content : new int[]{0, 1, 3}) {
            var bar = ScrollBarGeometry.of(10, 1, content, 3, 100, 8);
            assertEquals(1, bar.thumbHeight()); assertEquals(0, bar.maxOffset());
            assertEquals(0, bar.offsetAtThumbTop(Double.MAX_VALUE));
        }
    }
}
