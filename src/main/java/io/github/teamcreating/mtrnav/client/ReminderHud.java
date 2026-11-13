package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** A movable activity pill; the shared layout always reserves its expanded bounds. */
final class ReminderHud {
    private static boolean pending;
    private static String shownId = "", lastStage = "";
    private static long expandedUntil, lastFrame;
    private static double expansion;
    private ReminderHud() {}
    static void armed() { reset(); pending = true; }
    static void triggered() { pending = true; }
    static void reset() { shownId = lastStage = ""; pending = false; expandedUntil = lastFrame = 0; expansion = 0; }
    static void render(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (reminder == null || mc.player == null || mc.options.hideGui || mc.screen != null) { lastFrame = 0; return; }
        long now = System.nanoTime(), serverNow = CLOCK.now(now);
        boolean stale = CLOCK.stale(now) || !reminder.fresh(serverNow, now);
        var event = reminder.event();
        String stage = stale ? "pending" : serverNow > event.departure() ? "passed" : serverNow >= event.arrival() ? "arrived" : reminder.notified() ? "due" : "armed";
        if (!shownId.equals(reminder.id())) { shownId = reminder.id(); pending = true; expansion = 0; }
        if (pending || stage.equals("passed") && !lastStage.equals(stage)) { expandedUntil = now + 6_000_000_000L; pending = false; }
        lastStage = stage;
        double step = Math.min(1, (lastFrame == 0 ? 16_000_000L : now - lastFrame) / 220_000_000.0); lastFrame = now;
        expansion = NavigationPreferences.reminderExpanded() || now < expandedUntil ? Math.min(1, expansion + step) : Math.max(0, expansion - step);
        double ease = expansion * expansion * (3 - 2 * expansion);
        int width = (int)(248 + 32 * ease), height = (int)(22 + 56 * ease);
        var placement = HudPositions.layout(false).reminder();
        double scale = placement.scale();
        double x = placement.x() + (placement.width() - width * scale) / 2, y = placement.y();
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0); g.pose().scale((float)scale, (float)scale, 1);
            background(g, width, height, routeColor(event.routeId()));
            String time = stale || stage.equals("passed") ? "—" : countdown(event.arrival());
            if (height >= 58) {
                text(g, tr("mtrnav.reminder_stage." + stage), 9, 6, width - 65, stage.equals("pending") || stage.equals("passed") ? 0xFFFFB366 : 0xFF6CC349);
                text(g, time, width - 51, 6, 43, 0xFFFFFFFF);
                text(g, routeCode(event.routeId()) + " · " + station(reminder.stationId()), 9, 22, width - 18, 0xFFFFFFFF);
                direction(g, width, 37);
                if (height >= 70) text(g, VehicleNames.compact(event.consist(),width-18), 9, 52, width - 18, 0xFFD0D1D4);
                if (height >= 76) text(g, tr(event.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled") + " · " + tr("mtrnav.reminder_manage_key", openKey.get()), 9, 64, width - 18, 0xFFD0D1D4);
            } else {
                text(g,routeCode(event.routeId())+" · "+station(reminder.stationId()),8,7,width-65,0xFFFFFFFF);
                text(g,time,width-49,7,41,0xFF6CC349);

            }
        } finally { g.pose().popPose(); }
    }
    static void preview(GuiGraphics g) {
        var p = HudPositions.layout(true).reminder();
        g.pose().pushPose();
        try {
            g.pose().translate(p.x(), p.y(), 0); g.pose().scale((float)p.scale(), (float)p.scale(), 1);
            background(g, 280, 78, reminder == null ? 0xFF6CC349 : routeColor(reminder.event().routeId()));
            text(g, tr("mtrnav.hud_reminder_preview"), 9, 6, 215, 0xFF6CC349); text(g, "03:20", 229, 6, 43, 0xFFFFFFFF);
            text(g, reminder == null ? tr("mtrnav.hud_preview_line") : routeCode(reminder.event().routeId()) + " · " + station(reminder.stationId()), 9, 22, 262, 0xFFFFFFFF);
            if (reminder != null) direction(g, 280, 37);
            else text(g, tr("mtrnav.hud_reminder_direction"), 9, 37, 262, 0xFFD0D1D4);
            text(g, reminder == null ? tr("mtrnav.vehicle_preview") : VehicleNames.compact(reminder.event().consist(),262), 9, 52, 262, 0xFFD0D1D4);
            text(g, tr("mtrnav.hud_reminder_reserve"), 9, 64, 262, 0xFFD0D1D4);
        } finally { g.pose().popPose(); }
    }
    private static void background(GuiGraphics g, int width, int height, int tint) {
        hudFrame(g, width, height, tint);
    }
    private static void direction(GuiGraphics g, int width, int y) {
        var event = reminder.event();
        String platformText = tr("mtrnav.platform", platform(event.platformId()));
        int reserved = Math.min((width - 24) / 2, Minecraft.getInstance().font.width(platformText));
        text(g, "→ " + primary(event.destination()), 9, y, width - reserved - 27, 0xFFD0D1D4);
        text(g, platformText, width - reserved - 9, y, reserved, 0xFFFFFFFF);
    }
}
