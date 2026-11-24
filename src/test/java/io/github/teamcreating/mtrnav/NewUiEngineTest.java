package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.TopologyProjection;
import io.github.teamcreating.mtrnav.core.ui.UiMotion;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NewUiEngineTest {
    @Test
    void rectSlicingAndIntersectionStayWithinBounds() {
        var root = new UiRect(10, 20, 400, 300);
        var inner = root.inset(12, 8);
        assertEquals(new UiRect(22, 28, 376, 284), inner);

        var left = inner.sliceLeft(120);
        var right = inner.trimLeft(130);
        assertEquals(120, left.width());
        assertEquals(246, right.width());
        assertFalse(left.intersects(right));
        assertTrue(root.contains(left.centerX(), left.centerY()));
        assertEquals(left, root.intersect(left));
    }

    @Test
    void motionInterpolatesSmoothlyAndSnapsCleanly() {
        var motion = new UiMotion(0.0, 200);
        motion.snap(0.0);
        assertEquals(0.0, motion.value(0L), 1e-6);

        motion.animateTo(1.0, 1_000_000_000L, 200);
        assertTrue(motion.active(1_050_000_000L));
        double mid = motion.value(1_100_000_000L);
        assertTrue(mid > 0.5 && mid < 1.0, "Cubic ease-out should be past 0.5 at halfway");
        assertEquals(1.0, motion.value(1_300_000_000L), 1e-6);
        assertFalse(motion.active(1_300_000_000L));
    }

    @Test
    void scrollStateClampsAndEnsuresVisibleItem() {
        var scroll = new ScrollState();
        scroll.update(50, 100, 400, 14);
        assertTrue(scroll.scrollable());
        assertNotNull(scroll.geometry());

        scroll.ensureVisible(260, 40);
        assertEquals(200, scroll.offset());

        scroll.scrollBy(-100.0, 50);
        assertEquals(300, scroll.offset());

        scroll.scrollBy(100.0, 50);
        assertEquals(0, scroll.offset());
    }

    @Test
    void topologyProjectionMapsStationsEdgesAndHitTestsAccurately() {
        var catalog = new Model.Catalog("minecraft/overworld", 1,
                List.of(
                        new Model.Station("a", "苔灯港|Mosslight", 0, 64, 0, List.of()),
                        new Model.Station("b", "风铃渡|Windchime", 200, 64, 100, List.of()),
                        new Model.Station("c", "星沼湾|Starfen", 400, 64, 200, List.of())
                ),
                List.of(
                        new Model.Platform("p1", "a", "1", 0, 64, 0),
                        new Model.Platform("p2", "b", "1", 200, 64, 100),
                        new Model.Platform("p3", "b", "2", 200, 64, 104),
                        new Model.Platform("p4", "c", "1", 400, 64, 200)
                ),
                List.of(
                        new Model.Route("r1", "星汐快线", 0x4FA3D9, List.of("p1", "p2"), false, Model.RouteType.HIGH_SPEED),
                        new Model.Route("r2", "萤环线", 0xE8A13C, List.of("p3", "p4"), false, Model.RouteType.NORMAL)
                ));

        var proj = new TopologyProjection();
        var viewport = new UiRect(0, 0, 320, 240);
        var snap = proj.project(catalog, viewport);

        assertEquals(3, snap.nodes().size());
        assertEquals(2, snap.edges().size());
        var hub = snap.nodes().stream().filter(n -> n.stationId().equals("b")).findFirst().orElseThrow();
        assertTrue(hub.hub(), "Station served by 2 distinct lines should be marked as a hub");

        var hit = TopologyProjection.hitNode(snap, hub.screenX() + 2, hub.screenY() - 2, 10.0);
        assertNotNull(hit);
        assertEquals("b", hit.stationId());

        proj.zoomAt(2.0, viewport.centerX(), viewport.centerY(), viewport);
        assertEquals(2.0, proj.zoom(), 1e-6);
        proj.resetView();
        assertEquals(1.0, proj.zoom(), 1e-6);
    }

    @Test
    void topologyAutoFocusesRouteAndCoreClusterDespiteFarOutliers() {
        var catalog = new Model.Catalog("minecraft/overworld", 1,
                List.of(
                        new Model.Station("c1", "世纪大道", 0, 64, 0, List.of()),
                        new Model.Station("c2", "奥体中心", -800, 64, 50, List.of()),
                        new Model.Station("c3", "炼化大道", -1600, 64, 80, List.of()),
                        new Model.Station("c4", "天安大街", -200, 64, 600, List.of()),
                        new Model.Station("c5", "高井", 500, 64, -200, List.of()),
                        new Model.Station("far1", "黑山机场", 3500, 64, -9000, List.of()),
                        new Model.Station("far2", "调试机场", 1000, 64, 9500, List.of())
                ),
                List.of(),
                List.of());

        var proj = new TopologyProjection();
        var viewport = new UiRect(0, 0, 360, 280);
        proj.fitCoreCluster(catalog, viewport);
        assertTrue(proj.zoom() > 2.5, "Core cluster fit should zoom past distant outliers");

        proj.fitStations(List.of("c1", "c2", "c3"), catalog, viewport);
        var snap = proj.project(catalog, viewport);
        var s1 = snap.nodes().stream().filter(n -> n.stationId().equals("c1")).findFirst().orElseThrow();
        var s3 = snap.nodes().stream().filter(n -> n.stationId().equals("c3")).findFirst().orElseThrow();
        double screenSpan = Math.hypot(s3.screenX() - s1.screenX(), s3.screenY() - s1.screenY());
        assertTrue(screenSpan > 120.0, "Focused 1.6 km route should span >120px instead of being squashed");
        assertTrue(proj.maxZoom() > 20.0, "Large world maps must allow deep zoom-in to street scale");
    }
}
