package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import java.util.Set;

/**
 * Clean public facade bridging package-private client services to the new client.ui.* subpackages.
 */
public final class UiBridge {
    private UiBridge() {}

    public record VehicleInfo(String name, String description, String tags) {}

    // Location & Native service
    public static StationLocator.Position playerPosition() { return NavigationClient.playerPosition(); }
    public static Set<String> locatedStations() { return NavigationClient.locatedStations(); }
    public static StationLocator.Session originLocation() { return NavigationClient.originLocation; }
    public static void notifyNativeOpened() { NativeMtrClient.opened(); }

    // Vehicles
    public static void refreshVehicles() { VehicleNames.refresh(); }
    public static VehicleInfo vehicleInfo(String id) {
        var i = VehicleNames.info(id);
        return new VehicleInfo(i.name(), i.description(), i.tags());
    }
    public static String routeCategory(Model.RouteType type) { return VehicleNames.category(type); }
    public static String stockSummary(Model.Consist consist) { return VehicleNames.stock(consist); }
    public static String stockCompact(Model.Consist consist, int width) { return VehicleNames.compact(consist, width); }
    public static String stockOrdered(Model.Consist consist) { return VehicleNames.ordered(consist); }

    // HUD preferences & layout
    public static HudArrangement hudLayout(boolean preview) { return HudPositions.layout(preview); }
    public static int hudPercent() { return NavigationPreferences.hudPercent(); }
    public static void setHudPercent(int value) { NavigationPreferences.setHudPercent(value); }
    public static void changeHudSize(int dir) { NavigationPreferences.changeHudSize(dir); }
    public static double hudX() { return NavigationPreferences.hudX(); }
    public static double hudY() { return NavigationPreferences.hudY(); }
    public static void setHudPosition(double x, double y, boolean persist) { NavigationPreferences.setHudPosition(x, y, persist); }
    public static void resetHud() { NavigationPreferences.resetHud(); }

    public static int reminderPercent() { return NavigationPreferences.reminderPercent(); }
    public static void setReminderPercent(int value) { NavigationPreferences.setReminderPercent(value); }
    public static void changeReminderSize(int dir) { NavigationPreferences.changeReminderSize(dir); }
    public static double reminderX() { return NavigationPreferences.reminderX(); }
    public static double reminderY() { return NavigationPreferences.reminderY(); }
    public static void setReminderPosition(double x, double y, boolean persist) { NavigationPreferences.setReminderPosition(x, y, persist); }
    public static void resetReminder() { NavigationPreferences.resetReminder(); }

    public static boolean autoFit() { return NavigationPreferences.autoFit(); }
    public static void toggleAutoFit() { NavigationPreferences.toggleAutoFit(); }
    public static boolean reminderExpanded() { return NavigationPreferences.reminderExpanded(); }
    public static void toggleReminderExpanded() { NavigationPreferences.toggleReminderExpanded(); }
    public static void savePreferences() { NavigationPreferences.save(); }

    // Overlay synergy detection & live map sync
    public static boolean overlayAvailable() { return OverlayBridge.isAvailable(); }
    public static boolean overlayApiAvailable() { return OverlayBridge.isApiAvailable(); }
    public static boolean overlayWorldReady(String dimension) { return OverlayBridge.isWorldReady(dimension); }
    public static boolean overlayHighlightActive() { return OverlayBridge.isHighlightActive(); }
    public static boolean overlayPreviewEnabled() { return OverlayBridge.isPreviewEnabled(); }
    public static void toggleOverlayPreview() { OverlayBridge.togglePreviewEnabled(); }
    public static void updateOverlayPreview(String dimension, Model.Journey journey, ConnectionPlan reference) {
        OverlayBridge.updatePreviewTarget(dimension, journey, reference);
    }
    public static void clearOverlayPreview() { OverlayBridge.clearPreviewTarget(); }
    public static java.util.List<java.util.List<double[]>> overlayJourneyCurves(String dimension, Model.Journey journey) {
        return OverlayBridge.resolveJourneyCurves(dimension, journey);
    }
    public static java.util.List<java.util.List<double[]>> overlayReferenceCurves(String dimension, ConnectionPlan plan) {
        return OverlayBridge.resolveReferenceCurves(dimension, plan);
    }
}
