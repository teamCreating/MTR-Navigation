package io.github.teamcreating.mtrnav.client;

import com.mojang.logging.LogUtils;
import io.github.teamcreating.mtrnav.client.ui.NavigationUi;
import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.Model.*;
import io.github.teamcreating.mtrnav.core.NativeTimetable;
import java.util.*;

/**
 * Optional, zero-hard-dependency facade to MTR Map Overlay.
 * <p>
 * Prefers Overlay 1.6.0+ official {@code MtrMapApi 1.0.0} via reflective loading of
 * {@code OverlayApiAdapter} behind the pure {@link ApiRuntime} interface so this class never
 * references any {@code io.github.teamcreating.mtrmap.*} type directly. Retains legacy
 * Overlay 1.5.2 read-only {@code MapDataCache} reflection as fallback.
 */
public final class OverlayBridge {
    private OverlayBridge() {}

    /** Pure Navigation-side host callbacks passed into the lazy-loaded Overlay API adapter. */
    public interface HostBridge {
        String currentDimension();
        Catalog currentCatalog();
        String displayName();
        void openPlanTarget(String navDimension, String stationId);
        void logInfo(String message);
        void logWarn(String message, Throwable error);
    }

    /** Pure Navigation-side runtime interface implemented by {@code OverlayApiAdapter}. */
    public interface ApiRuntime {
        boolean isClientPresent();
        boolean isWorldReady(String navDimension);
        boolean isHighlightActive();
        void ensureNavigationRegistered();
        void syncState(String currentNavDimension, JourneyTracker.State tracking, Journey previewJourney, ConnectionPlan previewReference);
        Catalog catalog(String navDimension);
        List<List<double[]>> resolveJourneyCurves(String navDimension, Journey journey);
        List<List<double[]>> resolveReferenceCurves(String navDimension, ConnectionPlan plan);
        void resetSession();
    }

    private static boolean apiChecked;
    private static boolean apiClassPresent;
    private static ApiRuntime runtime;

    private static boolean legacyChecked;
    private static Class<?> legacyCache;
    private static Object lastLegacySnapshot;
    private static Catalog lastLegacyCatalog;

    private static boolean previewEnabled;
    private static Journey previewJourney;
    private static ConnectionPlan previewReference;
    private static String previewDimension = "";

    public static boolean isApiAvailable() {
        if (!apiChecked) {
            apiChecked = true;
            try {
                Class.forName("io.github.teamcreating.mtrmap.api.MtrMapApi");
                apiClassPresent = true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                apiClassPresent = false;
            }
        }
        return apiClassPresent;
    }

    public static boolean isAvailable() {
        if (isApiAvailable()) {
            ApiRuntime rt = runtime();
            if (rt != null && rt.isClientPresent()) return true;
        }
        return isLegacyCacheAvailable();
    }

    public static boolean isHighlightActive() {
        if (!isApiAvailable()) return false;
        ApiRuntime rt = runtime();
        return rt != null && rt.isHighlightActive();
    }

    public static boolean isWorldReady(String dimension) {
        if (!isApiAvailable() || dimension == null || dimension.isBlank()) return false;
        ApiRuntime rt = runtime();
        return rt != null && rt.isWorldReady(dimension);
    }

    public static boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public static void setPreviewEnabled(boolean enabled) {
        previewEnabled = enabled;
        if (!enabled) clearPreviewTarget();
    }

    public static void togglePreviewEnabled() {
        setPreviewEnabled(!previewEnabled);
    }

    public static void updatePreviewTarget(String dimension, Journey journey, ConnectionPlan reference) {
        previewDimension = dimension == null ? "" : dimension;
        previewJourney = journey;
        previewReference = reference;
    }

    public static void clearPreviewTarget() {
        previewJourney = null;
        previewReference = null;
        previewDimension = "";
    }

    public static void tick(String currentDimension, JourneyTracker.State tracking) {
        if (!isApiAvailable()) return;
        ApiRuntime rt = runtime();
        if (rt == null) return;
        rt.ensureNavigationRegistered();
        boolean usePreview = tracking == null && previewEnabled
                && currentDimension != null && currentDimension.equals(previewDimension);
        rt.syncState(currentDimension, tracking,
                usePreview ? previewJourney : null,
                usePreview && previewJourney == null ? previewReference : null);
    }

    public static void resetSession() {
        clearPreviewTarget();
        if (runtime != null) runtime.resetSession();
    }

    public static Catalog catalog(String dimension) {
        if (dimension == null || dimension.isBlank()) return null;
        if (isApiAvailable()) {
            ApiRuntime rt = runtime();
            if (rt != null) {
                Catalog official = rt.catalog(dimension);
                if (official != null) return official;
            }
        }
        return legacyCatalog(dimension);
    }

    public static List<List<double[]>> resolveJourneyCurves(String dimension, Journey journey) {
        if (!isApiAvailable() || journey == null || journey.legs().isEmpty()) return List.of();
        ApiRuntime rt = runtime();
        return rt == null ? List.of() : rt.resolveJourneyCurves(dimension, journey);
    }

    public static List<List<double[]>> resolveReferenceCurves(String dimension, ConnectionPlan plan) {
        if (!isApiAvailable() || plan == null || plan.legs().isEmpty()) return List.of();
        ApiRuntime rt = runtime();
        return rt == null ? List.of() : rt.resolveReferenceCurves(dimension, plan);
    }

    private static ApiRuntime runtime() {
        if (!isApiAvailable()) return null;
        if (runtime == null) {
            try {
                Class<?> impl = Class.forName("io.github.teamcreating.mtrnav.client.overlay.OverlayApiAdapter");
                runtime = (ApiRuntime) impl.getConstructor(HostBridge.class).newInstance(new HostBridge() {
                    @Override public String currentDimension() { return NavigationClient.currentDimension(); }
                    @Override public Catalog currentCatalog() { return NavigationClient.catalog; }
                    @Override public String displayName() { return NavigationClient.tr("mtrnav.title"); }
                    @Override public void openPlanTarget(String navDimension, String stationId) {
                        NavigationUi.openPlanFromOverlay(navDimension, stationId);
                    }
                    @Override public void logInfo(String message) {
                        LogUtils.getLogger().info(message);
                    }
                    @Override public void logWarn(String message, Throwable error) {
                        LogUtils.getLogger().warn(message, error);
                    }
                });
            } catch (ReflectiveOperationException | LinkageError e) {
                apiClassPresent = false;
                LogUtils.getLogger().warn("[MTR Navigation] Could not initialize Overlay API adapter", e);
                return null;
            }
        }
        return runtime;
    }

    private static boolean isLegacyCacheAvailable() {
        if (!legacyChecked) {
            legacyChecked = true;
            try { legacyCache = Class.forName("io.github.teamcreating.mtrmap.mapdata.MapDataCache"); }
            catch (ClassNotFoundException | LinkageError ignored) { }
        }
        return legacyCache != null;
    }

    private static Catalog legacyCatalog(String dimension) {
        if (!isLegacyCacheAvailable()) return null;
        try {
            if (!(boolean) legacyCache.getMethod("hasServerData", String.class).invoke(null, dimension)) {
                return null;
            }
            Object snapshot = legacyCache.getMethod("get", String.class).invoke(null, dimension);
            if (snapshot == lastLegacySnapshot && lastLegacyCatalog != null && lastLegacyCatalog.dimension().equals(dimension)) {
                return lastLegacyCatalog;
            }
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
            lastLegacySnapshot = snapshot;
            lastLegacyCatalog = new Catalog(dimension, ((Number) snapshot.getClass().getField("version").get(snapshot)).longValue(), stations, List.of(), List.of());
            return lastLegacyCatalog;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
