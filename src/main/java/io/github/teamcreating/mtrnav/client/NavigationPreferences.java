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
    private static boolean reminderExpanded;
    private static int reminderPercent = HudScale.DEFAULT_PERCENT;
    private static double reminderX = .5, reminderY;
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
            reminderExpanded = Boolean.parseBoolean(values.getProperty("reminderKeepExpanded", "false"));
            reminderPercent = HudScale.clamp(Integer.parseInt(values.getProperty("reminderScalePercent", Integer.toString(hudPercent))));
            reminderX = HudPlacement.position(Double.parseDouble(values.getProperty("reminderPositionX", ".5")), .5);
            reminderY = HudPlacement.position(Double.parseDouble(values.getProperty("reminderPositionY", "0")), 0);
        } catch (IOException | IllegalArgumentException error) {
            com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Could not load client preferences; using valid defaults", error);
        }
    }
    static int hudPercent() { load(); return hudPercent; }
    static double hudX() { load(); return hudX; }
    static double hudY() { load(); return hudY; }
    static boolean autoFit() { load(); return autoFit; }
    static boolean reminderExpanded() { load(); return reminderExpanded; }
    static int reminderPercent() { load(); return reminderPercent; }
    static double reminderX() { load(); return reminderX; }
    static double reminderY() { load(); return reminderY; }
    static void changeReminderSize(int direction) { reminderPercent = HudScale.step(reminderPercent(), direction); save(); }
    static void setReminderPosition(double x, double y, boolean persist) { load(); reminderX = HudPlacement.position(x, .5); reminderY = HudPlacement.position(y, 0); if (persist) save(); }
    static void resetReminder() { load(); reminderPercent = HudScale.DEFAULT_PERCENT; reminderX = .5; reminderY = 0; save(); }
    static void toggleReminderExpanded() { load(); reminderExpanded = !reminderExpanded; save(); }
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
            values.setProperty("reminderKeepExpanded", Boolean.toString(reminderExpanded));
            values.setProperty("reminderScalePercent", Integer.toString(reminderPercent));
            values.setProperty("reminderPositionX", Double.toString(reminderX)); values.setProperty("reminderPositionY", Double.toString(reminderY));
            try (var output = Files.newBufferedWriter(file)) { values.store(output, "MTR Navigation: local HUD size, normalized position and automatic fit"); }
        } catch (IOException error) { com.mojang.logging.LogUtils.getLogger().warn("[MTR Navigation] Could not save client preferences", error); }
    }
}
