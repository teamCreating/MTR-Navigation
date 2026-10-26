package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import net.minecraft.client.Minecraft;

/** Shared by actual rendering and the two-box editor; never writes automatic avoidance back. */
final class HudPositions {
    private HudPositions() {}
    private record Key(double gui, int width, int height, int navPercent, double navX, double navY,
                       int reminderPercent, double reminderX, double reminderY, boolean fit, boolean both) {}
    private static Key previous;
    private static HudArrangement cached;
    static HudArrangement layout(boolean preview) {
        var window = Minecraft.getInstance().getWindow();
        double gui = window.getGuiScale();
        boolean both = preview || NavigationClient.tracking != null && NavigationClient.catalog != null && NavigationClient.reminder != null;
        var key = new Key(gui, window.getWidth(), window.getHeight(), NavigationPreferences.hudPercent(), NavigationPreferences.hudX(), NavigationPreferences.hudY(),
                NavigationPreferences.reminderPercent(), NavigationPreferences.reminderX(), NavigationPreferences.reminderY(), NavigationPreferences.autoFit(), both);
        if (key.equals(previous)) return cached;
        var nav = HudPlacement.at(gui, window.getWidth(), window.getHeight(), NavigationPreferences.hudPercent(),
                NavigationPreferences.hudX(), NavigationPreferences.hudY(), NavigationPreferences.autoFit());
        var reminder = HudPlacement.reminder(gui, window.getWidth(), window.getHeight(), NavigationPreferences.reminderPercent(),
                NavigationPreferences.reminderX(), NavigationPreferences.reminderY(), NavigationPreferences.autoFit());
        previous = key;
        return cached = both ? HudArrangement.arrange(nav, reminder, window.getWidth() / gui, window.getHeight() / gui, 6 / gui) : new HudArrangement(nav, reminder);
    }
}
