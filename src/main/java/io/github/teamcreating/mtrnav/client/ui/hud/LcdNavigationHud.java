package io.github.teamcreating.mtrnav.client.ui.hud;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Dynamic train-door LCD line-strip navigation HUD.
 * Reuses HudScale dimensions and HudArrangement avoidance so saved positions remain 100% compatible.
 */
public final class LcdNavigationHud {
    private LcdNavigationHud() {}

    public static void render(GuiGraphics g) { render(g, false); }
    public static void preview(GuiGraphics g) { render(g, true); }

    public static void renderPreviewAt(GuiGraphics g, double x, double y, double scale) {
        renderSample(g, x, y, scale);
    }

    private static void renderSample(GuiGraphics g, double x, double y, double scale) {
        int w = HudScale.WIDTH;
        int h = HudScale.HEIGHT;
        int tint = 0xFF4FA3D9;
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale((float) scale, (float) scale, 1f);
            UiDraw.card(g, new UiRect(0, 0, w, h), UiTokens.RADIUS_MD, UiTokens.HUD_BG, UiTokens.HUD_BORDER);
            UiDraw.roundedRect(g, 1, 1, w - 2, 18, UiTokens.RADIUS_SM, UiTokens.HUD_HEADER);
            UiDraw.roundedRect(g, 2, 4, 3, h - 8, 1.5f, tint);
            int bx = 8 + UiDraw.lineBadge(g, "星汐快线", 8, 3, 76, tint) + 6;
            int rtW = UiDraw.font().width("01:42") + 4;
            UiDraw.text(g, tr("mtrnav.hud_preview"), bx, 6, Math.max(0, w - bx - rtW - 8), UiTokens.TEXT_SECONDARY);
            UiDraw.bold(g, "01:42", w - rtW - 6, 6, rtW, UiTokens.STATUS_LIVE);

            int railY = 29, rx0 = 14, rx1 = w - 14;
            UiDraw.line(g, rx0, railY, rx1, railY, 2.2f, UiTokens.BORDER_SUBTLE);
            float curX = rx0 + (rx1 - rx0) * 0.62f;
            UiDraw.line(g, rx0, railY, curX, railY, 2.6f, tint);
            UiDraw.circle(g, curX, railY, 4.2f, UiTokens.withAlpha(tint, 95));
            UiDraw.circle(g, curX, railY, 2.8f, 0xFFFFFFFF);
            UiDraw.circle(g, rx0, railY, 3.0f, tint);
            UiDraw.circle(g, rx1, railY, 3.4f, UiTokens.HUD_BG);
            UiDraw.ring(g, rx1, railY, 1.8f, 3.4f, tint);

            UiDraw.text(g, tr("mtrnav.origin"), 10, 35, w / 2 - 14, UiTokens.TEXT_MUTED);
            String end = tr("mtrnav.destination");
            int endW = Math.min(w / 2 - 14, UiDraw.font().width(end));
            UiDraw.text(g, end, w - 10 - endW, 35, endW, UiTokens.TEXT_SECONDARY);
            UiDraw.scaledText(g, tr("mtrnav.hud_preview_line"), 10, 47, w - 18, UiTokens.TEXT_PRIMARY, 1.15f);
            UiDraw.text(g, tr("mtrnav.hud_reminder_direction"), 10, 62, w - 18, UiTokens.TEXT_SECONDARY);
            UiDraw.text(g, tr("mtrnav.hud_preview_hint"), 10, 76, w - 18, UiTokens.STATUS_LIVE);
        } finally {
            g.pose().popPose();
        }
    }

    private static void render(GuiGraphics g, boolean preview) {
        var mc = Minecraft.getInstance();
        boolean example = tracking == null || catalog == null;
        if (!preview && (example || mc.options.hideGui || mc.screen != null)) return;

        var p = UiBridge.hudLayout(preview).navigation();
        int w = HudScale.WIDTH;
        int h = HudScale.HEIGHT;

        boolean complete = !example && (tracking.phase() == JourneyTracker.Phase.COMPLETE || tracking.journey().legs().isEmpty());
        boolean riding = !example && tracking.phase() == JourneyTracker.Phase.RIDING;
        boolean walking = !example && tracking.walk() != null;
        boolean untimed = !example && ConnectionPlan.isGuidedJourney(tracking.journey());
        var leg = example || complete ? null
                : tracking.journey().legs().get(Math.min(tracking.legIndex(), tracking.journey().legs().size() - 1));

        int tint = walking ? UiTokens.STATUS_WARN
                : example ? 0xFF4FA3D9
                : leg == null ? UiTokens.PRIMARY : NavFormat.routeColor(leg.routeId());

        g.pose().pushPose();
        try {
            g.pose().translate(p.x(), p.y(), 0);
            g.pose().scale((float) p.scale(), (float) p.scale(), 1f);

            // Outer translucent rounded LCD chassis
            UiRect frame = new UiRect(0, 0, w, h);
            UiDraw.card(g, frame, UiTokens.RADIUS_MD, UiTokens.HUD_BG, UiTokens.HUD_BORDER);
            // Header strip
            UiDraw.roundedRect(g, 1, 1, w - 2, 18, UiTokens.RADIUS_SM, UiTokens.HUD_HEADER);
            // Left line accent bar
            UiDraw.roundedRect(g, 2, 4, 3, h - 8, 1.5f, tint);

            // Header content: route pill + phase + live countdown
            String routeBadge = example ? "星汐快线"
                    : walking ? tr("mtrnav.phase.walking")
                    : leg == null ? "" : NavFormat.routeCode(leg.routeId());
            int bx = 8;
            if (!routeBadge.isEmpty()) {
                bx += UiDraw.lineBadge(g, routeBadge, bx, 3, 76, tint) + 6;
            }
            String phaseTxt = example ? tr("mtrnav.hud_preview") : NavFormat.phaseLabel(tracking.phase());
            String rightTime = example ? "01:42"
                    : complete ? ""
                    : walking ? tr("mtrnav.walk_seconds", tracking.walk().seconds())
                    : untimed ? tr("mtrnav.ui.ref_nav_tag")
                    : leg == null ? "" : NavFormat.remaining(riding ? leg.arrival() : leg.departure());
            int rtW = rightTime.isEmpty() ? 0 : UiDraw.font().width(rightTime) + 4;
            UiDraw.text(g, phaseTxt, bx, 6, Math.max(0, w - bx - rtW - 8), UiTokens.TEXT_SECONDARY);
            if (!rightTime.isEmpty()) {
                UiDraw.bold(g, rightTime, w - rtW - 6, 6, rtW, untimed ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE);
            }

            // Dynamic horizontal LCD rail strip
            int railY = 29;
            int rx0 = 14, rx1 = w - 14;
            UiDraw.line(g, rx0, railY, rx1, railY, 2.2f, UiTokens.BORDER_SUBTLE);
            boolean hasProgress = example || (!untimed && leg != null && riding && !CLOCK.stale(System.nanoTime()) && leg.arrival() > leg.departure());
            if (hasProgress) {
                double progress = example ? 0.62
                        : Math.clamp((double) (CLOCK.now(System.nanoTime()) - leg.departure()) / (leg.arrival() - leg.departure()), 0.0, 1.0);
                float curX = (float) (rx0 + (rx1 - rx0) * progress);
                UiDraw.line(g, rx0, railY, curX, railY, 2.6f, tint);
                UiDraw.circle(g, curX, railY, 4.2f, UiTokens.withAlpha(tint, 95));
                UiDraw.circle(g, curX, railY, 2.8f, 0xFFFFFFFF);
            } else if (untimed && riding && leg != null) {
                UiDraw.line(g, rx0, railY, rx1, railY, 2.6f, tint);
            } else if (walking) {
                UiDraw.dashedLine(g, rx0, railY, rx1, railY, 2.2f, 4f, 3f, UiTokens.STATUS_WARN);
            }
            // Start & end station nodes
            UiDraw.circle(g, rx0, railY, 3.0f, tint);
            UiDraw.circle(g, rx1, railY, 3.4f, UiTokens.HUD_BG);
            UiDraw.ring(g, rx1, railY, 1.8f, 3.4f, tint);

            String fromName = example ? tr("mtrnav.origin")
                    : walking ? NavFormat.station(tracking.walk().fromStationId())
                    : leg == null ? "" : NavFormat.station(leg.fromStationId());
            String toName = example ? tr("mtrnav.destination")
                    : walking ? NavFormat.station(tracking.walk().toStationId())
                    : leg == null ? "" : NavFormat.station(leg.toStationId());
            UiDraw.text(g, fromName, 10, 35, w / 2 - 14, UiTokens.TEXT_MUTED);
            int endW = Math.min(w / 2 - 14, UiDraw.font().width(toName));
            UiDraw.text(g, toName, w - 10 - endW, 35, endW, UiTokens.TEXT_SECONDARY);

            // Primary action headline
            String action = example ? tr("mtrnav.hud_preview_line")
                    : complete ? tr("mtrnav.complete")
                    : walking ? tr("mtrnav.walk_to", NavFormat.station(tracking.walk().toStationId()))
                    : tr(riding ? "mtrnav.hud_alight" : "mtrnav.hud_board") + " " + NavFormat.station(riding ? leg.toStationId() : leg.fromStationId());
            UiDraw.scaledText(g, action, 10, 47, w - 18, UiTokens.TEXT_PRIMARY, 1.15f);

            // Direction + platform + consist
            String sub = example ? tr("mtrnav.hud_reminder_direction")
                    : walking ? (tracking.walk().toPlatformId().isEmpty() ? tr("mtrnav.walk_source." + tracking.walk().source()) : tr("mtrnav.platform", NavFormat.platform(tracking.walk().toPlatformId())))
                    : leg == null ? "" : tr("mtrnav.direction", NavFormat.primary(leg.direction())) + " · " + NavFormat.platform(riding ? leg.toPlatformId() : leg.fromPlatformId());
            UiDraw.text(g, sub, 10, 62, w - 18, UiTokens.TEXT_SECONDARY);

            // Guidance footer line
            String hint = example ? tr("mtrnav.hud_preview_hint")
                    : NavFormat.guidance().isEmpty() ? tr("mtrnav.hud_short_key", openKey.get()) : NavFormat.guidance();
            if (!example && !untimed && CLOCK.stale(System.nanoTime())) hint = tr("mtrnav.stale");
            UiDraw.text(g, hint, 10, 76, w - 18, untimed ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE);
        } finally {
            g.pose().popPose();
        }
    }
}
