package io.github.teamcreating.mtrnav.client.ui.hud;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Dynamic top status capsule for train arrival reminders.
 * Smoothly expands on arm/trigger/pass or when keep-expanded is enabled, reusing HudPositions reservation.
 */
public final class CapsuleReminderHud {
    private static boolean pending;
    private static String shownId = "", lastStage = "";
    private static long expandedUntil, lastFrame;
    private static double expansion;

    private CapsuleReminderHud() {}

    public static void armed() { reset(); pending = true; }
    public static void triggered() { pending = true; }
    public static void reset() {
        shownId = lastStage = "";
        pending = false;
        expandedUntil = lastFrame = 0;
        expansion = 0;
    }

    public static void render(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (reminder == null || mc.player == null || mc.options.hideGui || mc.screen != null) {
            lastFrame = 0;
            return;
        }
        long now = System.nanoTime();
        long serverNow = CLOCK.now(now);
        boolean stale = CLOCK.stale(now) || !reminder.fresh(serverNow, now);
        var event = reminder.event();
        String stage = stale ? "pending"
                : serverNow > event.departure() ? "passed"
                : serverNow >= event.arrival() ? "arrived"
                : reminder.notified() ? "due" : "armed";

        if (!shownId.equals(reminder.id())) {
            shownId = reminder.id();
            pending = true;
            expansion = 0;
        }
        if (pending || (stage.equals("passed") && !lastStage.equals(stage))) {
            expandedUntil = now + 6_000_000_000L;
            pending = false;
        }
        lastStage = stage;

        double step = Math.min(1.0, (lastFrame == 0 ? 16_000_000L : now - lastFrame) / 200_000_000.0);
        lastFrame = now;
        expansion = (UiBridge.reminderExpanded() || now < expandedUntil)
                ? Math.min(1.0, expansion + step)
                : Math.max(0.0, expansion - step);
        double ease = expansion * expansion * (3.0 - 2.0 * expansion);

        int w = (int) Math.round(236 + 44 * ease);
        int h = (int) Math.round(22 + 56 * ease);
        var placement = UiBridge.hudLayout(false).reminder();
        double scale = placement.scale();
        double x = placement.x() + (placement.width() - w * scale) / 2.0;
        double y = placement.y();
        int tint = NavFormat.routeColor(event.routeId());
        boolean alertColor = stage.equals("pending") || stage.equals("passed");

        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale((float) scale, (float) scale, 1f);

            float radius = h <= 26 ? 11f : UiTokens.RADIUS_MD;
            UiDraw.card(g, new UiRect(0, 0, w, h), radius, UiTokens.HUD_BG,
                    stage.equals("due") || stage.equals("arrived") ? UiTokens.STATUS_LIVE : UiTokens.HUD_BORDER);

            String time = stale || stage.equals("passed") ? "—" : countdown(event.arrival());
            if (h < 56) {
                // Compact Dynamic Capsule Pill
                UiDraw.circle(g, 11, h / 2f, 3.2f, tint);
                UiDraw.text(g, NavFormat.routeCode(event.routeId()) + " · " + NavFormat.station(reminder.stationId()),
                        20, (h - 8) / 2, w - 76, UiTokens.TEXT_PRIMARY);
                int tw = UiDraw.font().width(time);
                UiDraw.bold(g, time, w - tw - 10, (h - 8) / 2, tw + 2, alertColor ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE);
            } else {
                // Expanded Card
                UiDraw.roundedRect(g, 1, 1, w - 2, 18, UiTokens.RADIUS_SM, UiTokens.HUD_HEADER);
                UiDraw.roundedRect(g, 2, 4, 3, h - 8, 1.5f, tint);
                UiIcons.BELL.draw(g, 9, 5, 10, alertColor ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE);
                UiDraw.bold(g, tr("mtrnav.reminder_stage." + stage), 23, 6, w - 76,
                        alertColor ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE);
                int tw = UiDraw.font().width(time);
                UiDraw.bold(g, time, w - tw - 10, 6, tw + 2, UiTokens.TEXT_PRIMARY);

                UiDraw.bold(g, NavFormat.routeCode(event.routeId()) + " · " + NavFormat.station(reminder.stationId()),
                        10, 24, w - 20, UiTokens.TEXT_PRIMARY);
                String plat = tr("mtrnav.platform", NavFormat.platform(event.platformId()));
                int pw = Math.min((w - 24) / 2, UiDraw.font().width(plat) + 8);
                UiDraw.text(g, "→ " + NavFormat.primary(event.destination()), 10, 38, w - pw - 22, UiTokens.TEXT_SECONDARY);
                UiDraw.pill(g, plat, w - pw - 8, 36, pw, UiTokens.SURFACE_ELEVATED, UiTokens.TEXT_PRIMARY);
                if (h >= 70) {
                    UiDraw.text(g, UiBridge.stockCompact(event.consist(), w - 20), 10, 52, w - 20, UiTokens.TEXT_MUTED);
                }
                if (h >= 76) {
                    UiDraw.text(g, tr(event.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled")
                            + " · " + tr("mtrnav.reminder_manage_key", openKey.get()),
                            10, 64, w - 20, UiTokens.TEXT_MUTED);
                }
            }
        } finally {
            g.pose().popPose();
        }
    }

    public static void preview(GuiGraphics g) {
        var p = UiBridge.hudLayout(true).reminder();
        renderPreviewAt(g, p.x(), p.y(), p.scale(), true);
    }

    public static void renderPreviewAt(GuiGraphics g, double x, double y, double scale, boolean expanded) {
        int tint = reminder == null ? 0xFF4FA3D9 : NavFormat.routeColor(reminder.event().routeId());
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale((float) scale, (float) scale, 1f);
            if (!expanded) {
                int w = 236, h = 22;
                UiDraw.card(g, new UiRect(0, 0, w, h), 11f, UiTokens.HUD_BG, UiTokens.HUD_BORDER);
                UiDraw.circle(g, 11, h / 2f, 3.2f, tint);
                String title = reminder == null ? "星汐快线 · 苔灯港"
                        : NavFormat.routeCode(reminder.event().routeId()) + " · " + NavFormat.station(reminder.stationId());
                UiDraw.text(g, title, 20, (h - 8) / 2, w - 76, UiTokens.TEXT_PRIMARY);
                int tw = UiDraw.font().width("03:20");
                UiDraw.bold(g, "03:20", w - tw - 10, (h - 8) / 2, tw + 2, UiTokens.STATUS_LIVE);
                return;
            }
            int w = 280, h = 78;
            UiDraw.card(g, new UiRect(0, 0, w, h), UiTokens.RADIUS_MD, UiTokens.HUD_BG, UiTokens.HUD_BORDER);
            UiDraw.roundedRect(g, 1, 1, w - 2, 18, UiTokens.RADIUS_SM, UiTokens.HUD_HEADER);
            UiDraw.roundedRect(g, 2, 4, 3, h - 8, 1.5f, tint);
            UiIcons.BELL.draw(g, 9, 5, 10, UiTokens.STATUS_LIVE);
            UiDraw.bold(g, tr("mtrnav.hud_reminder_preview"), 23, 6, 200, UiTokens.STATUS_LIVE);
            UiDraw.bold(g, "03:20", w - 38, 6, 30, UiTokens.TEXT_PRIMARY);
            UiDraw.bold(g, reminder == null ? tr("mtrnav.hud_preview_line")
                    : NavFormat.routeCode(reminder.event().routeId()) + " · " + NavFormat.station(reminder.stationId()),
                    10, 24, w - 20, UiTokens.TEXT_PRIMARY);
            UiDraw.text(g, reminder == null ? tr("mtrnav.hud_reminder_direction")
                    : "→ " + NavFormat.primary(reminder.event().destination()),
                    10, 38, w - 20, UiTokens.TEXT_SECONDARY);
            UiDraw.text(g, reminder == null ? tr("mtrnav.vehicle_preview")
                    : UiBridge.stockCompact(reminder.event().consist(), w - 20),
                    10, 52, w - 20, UiTokens.TEXT_MUTED);
            UiDraw.text(g, tr("mtrnav.hud_reminder_reserve"), 10, 64, w - 20, UiTokens.TEXT_MUTED);
        } finally {
            g.pose().popPose();
        }
    }
}
