package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** A compact top-centre activity pill; expands briefly on confirmation and notification. */
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
        expansion = now < expandedUntil ? Math.min(1, expansion + step) : Math.max(0, expansion - step);
        double ease = expansion * expansion * (3 - 2 * expansion);
        int width = (int)(190 + 90 * ease), height = (int)(28 + 38 * ease);
        var window = mc.getWindow();
        double scale = Math.min(1.75 * NavigationPreferences.hudPercent() / 150.0, Math.max(1, window.getWidth() - 24) / 280.0) / Math.max(1, window.getGuiScale());
        double x = (window.getGuiScaledWidth() - width * scale) / 2, y = 10 / Math.max(1, window.getGuiScale());
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0); g.pose().scale((float)scale, (float)scale, 1);
            g.fill(4, 0, width - 4, height, 0xEE161C1B); g.fill(2, 2, width - 2, height - 2, 0xEE161C1B); g.fill(0, 5, width, height - 5, 0xEE161C1B);
            g.fill(9, 0, width - 9, 1, ACCENT); g.fill(0, 7, 2, height - 7, routeColor(event.routeId()));
            String time = stale || stage.equals("passed") ? "—" : countdown(event.arrival());
            if (height >= 58) {
                text(g, tr("mtrnav.reminder_stage." + stage), 9, 6, width - 65, stage.equals("pending") || stage.equals("passed") ? WARNING : ACCENT);
                text(g, time, width - 51, 6, 43, TEXT);
                text(g, routeCode(event.routeId()) + " · " + station(reminder.stationId()), 9, 22, width - 18, TEXT);
                text(g, "→ " + primary(event.destination()) + " · " + tr("mtrnav.platform", platform(event.platformId())), 9, 37, width - 18, MUTED);
                if (height >= 64) text(g, tr(event.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled") + " · " + tr("mtrnav.reminder_manage_key", openKey.get()), 9, 52, width - 18, MUTED);
            } else {
                text(g, routeCode(event.routeId()) + " · " + station(reminder.stationId()), 8, 4, width - 63, TEXT);
                text(g, time, width - 49, 4, 41, ACCENT);
                String hint = stage.equals("armed") ? tr("mtrnav.reminder_compact", reminder.minutes(), openKey.get()) : tr("mtrnav.reminder_stage." + stage) + " · " + openKey.get();
                text(g, hint, 8, 16, width - 16, stage.equals("pending") || stage.equals("passed") ? WARNING : MUTED);
            }
        } finally { g.pose().popPose(); }
    }
}
