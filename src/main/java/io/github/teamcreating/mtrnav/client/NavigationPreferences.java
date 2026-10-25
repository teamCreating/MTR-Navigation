package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.HudScale;
import io.github.teamcreating.mtrnav.core.HudPlacement;
import net.minecraft.client.Minecraft;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Instance-local settings. Dragging updates memory; explicit changes persist once. */
final class NavigationPreferences {
    private static Path file;
    private static int hudPercent = HudScale.DEFAULT_PERCENT;
    private static double hudX = 1, hudY = 0;
    private static boolean autoFit = true;
    private NavigationPreferences() {}
    private static void load() {
        if (file != null) return;
        file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/mtrnav-client.properties");
        if (Files.isRegularFile(file)) try (var input = Files.newBufferedReader(file)) {
            var values = new Properties(); values.load(input);
            hudPercent = HudScale.clamp(Integer.parseInt(values.getProperty("hudScalePercent", "150")));
            hudX = HudPlacement.position(Double.parseDouble(values.getProperty("hudPositionX", "1")), 1);
            hudY = HudPlacement.position(Double.parseDouble(values.getProperty("hudPositionY", "0")), 0);
            autoFit = !values.getProperty("hudAutoFit", "true").equalsIgnoreCase("false");
        } catch (IOException | IllegalArgumentException error) {
            com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Could not load client preferences; using valid defaults", error);
        }
    }
    static int hudPercent() { load(); return hudPercent; }
    static double hudX() { load(); return hudX; }
    static double hudY() { load(); return hudY; }
    static boolean autoFit() { load(); return autoFit; }
    static void changeHudSize(int direction) { hudPercent = HudScale.step(hudPercent(), direction); save(); }
    static void setHudPosition(double x, double y, boolean persist) {
        load(); hudX = HudPlacement.position(x, 1); hudY = HudPlacement.position(y, 0); if (persist) save();
    }
    static void toggleAutoFit() { load(); autoFit = !autoFit; save(); }
    static void resetHud() { load(); hudPercent = HudScale.DEFAULT_PERCENT; hudX = 1; hudY = 0; autoFit = true; save(); }
    static void save() {
        load();
        try {
            Files.createDirectories(file.getParent());
            var values = new Properties(); values.setProperty("hudScalePercent", Integer.toString(hudPercent));
            values.setProperty("hudPositionX", Double.toString(hudX)); values.setProperty("hudPositionY", Double.toString(hudY));
            values.setProperty("hudAutoFit", Boolean.toString(autoFit));
            try (var output = Files.newBufferedWriter(file)) { values.store(output, "MTR Navigation: local HUD size, normalized position and automatic fit"); }
        } catch (IOException error) { com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Could not save client preferences", error); }
    }
}
