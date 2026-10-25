package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.client.OverlayBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the optional bridge against the verified public Overlay release JAR. */
@EnabledIfSystemProperty(named = "mtrnav.testOverlay", matches = "true")
class OverlayCompatibilityTest {
    @Test void readsFullNetworkStationSnapshotWithoutDependingOnOverlayTypes() throws Exception {
        var cache = Class.forName("io.github.teamcreating.mtrmap.mapdata.MapDataCache");
        var dimension = Class.forName("io.github.teamcreating.mtrmap.mapdata.MapDataCache$DimensionData");
        var landmark = Class.forName("io.github.teamcreating.mtrmap.mapdata.MapLandmark");
        var type = Class.forName("io.github.teamcreating.mtrmap.mapdata.MapLandmark$Type");
        Object stationType = type.getField("STATION").get(null);
        Object station = landmark.getConstructor(String.class, type, int.class, int.class, int.class, String.class, String.class, String.class, boolean.class)
                .newInstance("station:ABCD1234", stationType, 2000, 64, -500, "南苑|Nanyuan", "南苑", "C2", true);
        Object snapshot = dimension.getConstructor(String.class, List.class, List.class, List.class, long.class)
                .newInstance("minecraft/overworld", List.of(), List.of(), List.of(station), 123L);
        cache.getMethod("putServerData", String.class, dimension).invoke(null, "minecraft/overworld", snapshot);
        try {
            var catalog = OverlayBridge.catalog("minecraft/overworld");
            assertNotNull(catalog); assertEquals(123, catalog.revision());
            var result = catalog.station("abcd1234"); assertNotNull(result);
            assertEquals("南苑|Nanyuan", result.name()); assertEquals(2000, result.x()); assertEquals(-500, result.z());
        } finally { cache.getMethod("clearServerData").invoke(null); }
    }
}
