package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.JourneyTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;
import static io.github.teamcreating.mtrnav.core.Model.displayName;

/** Three-line HUD with a physical-size cap independent of Minecraft GUI scaling. */
final class NavigationHud {
    private NavigationHud() {}
    static void render(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (tracking == null || catalog == null || mc.options.hideGui || mc.screen != null) return;
        double guiScale = mc.getWindow().getGuiScale();
        float scale = (float)Math.min(1, 1.5 / guiScale);
        int w = 180, h = 43;
        int x = (int)(mc.getWindow().getGuiScaledWidth() / scale) - w - 8, y = 8;
        boolean complete = tracking.phase() == JourneyTracker.Phase.COMPLETE || tracking.journey().legs().isEmpty();
        boolean riding = tracking.phase() == JourneyTracker.Phase.RIDING;
        var leg = complete ? null : tracking.journey().legs().get(Math.min(tracking.legIndex(), tracking.journey().legs().size() - 1));
        g.pose().pushPose();
        try {
            g.pose().scale(scale, scale, 1);
            g.fill(x, y, x + w, y + h, 0xD9282D2D);
            g.fill(x, y, x + 2, y + h, leg == null ? ACCENT : routeColor(leg.routeId()));
            String action = complete ? tr("mtrnav.complete") : tr(riding ? "mtrnav.hud_alight" : "mtrnav.hud_board") + " " + station(riding ? leg.toStationId() : leg.fromStationId());
            text(g, action, x + 8, y + 5, w - 61, TEXT);
            if (!complete) {
                text(g, remaining(riding ? leg.arrival() : leg.departure()), x + w - 47, y + 5, 40, ACCENT);
                var platform = catalog.platform(riding ? leg.toPlatformId() : leg.fromPlatformId());
                String platformName = platform == null ? tr("mtrnav.platform_pending") : tr("mtrnav.platform", displayName(platform.name()));
                text(g, routeCode(leg.routeId()) + " · " + platformName, x + 8, y + 18, w - 16, MUTED);
            }
            boolean stale = CLOCK.stale(System.nanoTime());
            String guidance = guidance();
            boolean warning = stale || !guidance.isEmpty() && !guidance.equals(tr("mtrnav.notice.manual_check")) && !guidance.equals(tr("mtrnav.notice.on_route"));
            String bottom = warning ? stale ? tr("mtrnav.stale") : guidance
                    : tr("mtrnav.hud_short_key", openKey.get()) + " · " + tr(usingNative ? "mtrnav.manual_label" : "mtrnav.automatic_label")
                    + " · " + Math.min(tracking.legIndex() + 1, tracking.journey().legs().size()) + "/" + tracking.journey().legs().size();
            text(g, bottom, x + 8, y + 31, w - 16, warning ? WARNING : MUTED);
        } finally { g.pose().popPose(); }
    }
}
