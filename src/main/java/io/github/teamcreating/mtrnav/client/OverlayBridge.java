package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.Model.*;
import io.github.teamcreating.mtrnav.core.NativeTimetable;
import java.util.*;

/** Optional read-only access to Overlay 1.5.2. No hard dependency and no changes to its protocol. */
public final class OverlayBridge {
    private OverlayBridge() {}
    private static boolean checked;
    private static Class<?> cache;
    private static Object lastSnapshot;
    private static Catalog lastCatalog;
    public static Catalog catalog(String dimension) {
        if (!checked) {
            checked = true;
            try { cache = Class.forName("io.github.teamcreating.mtrmap.mapdata.MapDataCache"); }
            catch (ClassNotFoundException | LinkageError ignored) { }
        }
        if (cache == null) return null;
        try {
            if (!(boolean) cache.getMethod("hasServerData", String.class).invoke(null, dimension)) {
                // Overlay owns its synchronization lifecycle. Navigation only consumes
                // an existing snapshot; it must not repeatedly trigger bulk transfers.
                return null;
            }
            Object snapshot = cache.getMethod("get", String.class).invoke(null, dimension);
            if (snapshot == lastSnapshot && lastCatalog != null && lastCatalog.dimension().equals(dimension)) return lastCatalog;
            var stations = new ArrayList<Station>();
            for (Object landmark : (List<?>) snapshot.getClass().getField("landmarks").get(snapshot)) {
                Class<?> type = landmark.getClass();
                String identity = (String) type.getMethod("id").invoke(landmark);
                if (!identity.startsWith("station:")) continue;
                stations.add(new Station(NativeTimetable.id(identity.substring(8)), (String) type.getMethod("name").invoke(landmark),
                        ((Number) type.getMethod("x").invoke(landmark)).longValue(), ((Number) type.getMethod("y").invoke(landmark)).longValue(),
                        ((Number) type.getMethod("z").invoke(landmark)).longValue(), List.of()));
            }
            stations.sort(Comparator.comparing(Station::id));
            lastSnapshot = snapshot;
            lastCatalog = new Catalog(dimension, ((Number) snapshot.getClass().getField("version").get(snapshot)).longValue(), stations, List.of(), List.of());
            return lastCatalog;
        } catch (ReflectiveOperationException | LinkageError e) { return null; }
    }
}
