package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/** Shared native screen/HUD styling. Coordinates are Minecraft GUI units. */
final class NavigationTheme {
    static final int BACKGROUND = 0xF5282D2D, PANEL = 0xFF282D2D, RAISED = 0xFF323837;
    static final int BORDER = 0xFF51594F, TEXT = 0xFFF4EFDF, MUTED = 0xFFADB1A5;
    static final int ACCENT = 0xFFC6BB8D, WARNING = 0xFFFFCC80;
    private NavigationTheme() {}
    static String station(String id) {
        var station = catalog == null ? null : catalog.station(id);
        return station == null ? tr("mtrnav.station_pending") : primary(station.name());
    }
    static String route(String id) {
        if (catalog == null) return tr("mtrnav.route_pending");
        return catalog.routes().stream().filter(r -> r.id().equals(id)).findFirst().map(r -> primary(r.name())).orElse(tr("mtrnav.route_pending"));
    }
    static String platform(String id) {
        var platform = catalog == null ? null : catalog.platform(id);
        return platform == null ? tr("mtrnav.platform_pending") : displayName(platform.name());
    }
    static String routeCode(String id) {
        String label = route(id);
        var code = java.util.regex.Pattern.compile("[A-Z]{1,3}[0-9]{1,2}[A-Z]?|[0-9]{1,2}号线").matcher(label);
        return code.find() ? code.group() : label;
    }
    static String primary(String name) { return name == null ? "" : name.split("\\|", 2)[0].trim(); }
    static int routeColor(String id) {
        return catalog == null ? ACCENT : catalog.routes().stream().filter(r -> r.id().equals(id))
                .map(r -> 0xFF000000 | r.color() & 0xFFFFFF).findFirst().orElse(ACCENT);
    }
    static void text(GuiGraphics g, String text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        if (width <= 0) return;
        String visible = font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
        g.drawString(font, visible, x, y, color, false);
    }
    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
    static String duration(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        return tr("mtrnav.minutes", minutes);
    }
    static String remaining(long timestamp) { return CLOCK.stale(System.nanoTime()) ? "—" : countdown(timestamp); }
    static String guidance() {
        if (tracking == null) return "";
        if (!tracking.notice().isEmpty() && !tracking.notice().equals("manual_check")) return tr("mtrnav.notice." + tracking.notice());
        if (usingNative && !CLOCK.stale(System.nanoTime()) && tracking.legIndex() < tracking.journey().legs().size()) {
            var leg = tracking.journey().legs().get(tracking.legIndex());
            long now = CLOCK.now(System.nanoTime());
            if (tracking.phase() == io.github.teamcreating.mtrnav.core.JourneyTracker.Phase.RIDING && leg.arrival() - now <= 30_000)
                return tr(now < leg.arrival() ? "mtrnav.estimated_alight_soon" : "mtrnav.estimated_alight_check");
            if (tracking.phase() != io.github.teamcreating.mtrnav.core.JourneyTracker.Phase.RIDING && now > leg.departure() + 10_000)
                return tr("mtrnav.notice.missed_train");
        }
        return tracking.notice().isEmpty() ? "" : tr("mtrnav.notice." + tracking.notice());
    }

    /** Button keeps vanilla focus, keyboard activation and narration, with a flat painted surface. */
    static final class Action extends Button {
        boolean selected, primary, tab;
        String detail, extra;
        int tint = ACCENT;
        Action(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            boolean highlighted = isHoveredOrFocused();
            int surface = !active ? 0xFF252A28 : primary ? (highlighted ? 0xFFDFD3A0 : ACCENT)
                    : selected ? RAISED : highlighted ? 0xFF3C443D : PANEL;
            g.fill(getX(), getY(), getX() + width, getY() + height, surface);
            if (tab) g.fill(getX(), getY() + height - 2, getX() + width, getY() + height, selected ? ACCENT : BORDER);
            else outline(g, getX(), getY(), width, height, selected || highlighted ? ACCENT : BORDER);
            if (selected) g.fill(getX(), getY(), getX() + 3, getY() + height, tint);
            int color = active ? primary ? 0xFF161E19 : TEXT : 0xFF778176;
            if (detail != null && height >= 34) {
                text(g, getMessage().getString(), getX() + 9, getY() + 8, width - 18, color);
                text(g, detail, getX() + 9, getY() + 23, width - 18, active ? MUTED : 0xFF637488);
                if (extra != null && height >= 48) text(g, extra, getX() + 9, getY() + 38, width - 18, ACCENT);
            } else {
                var font = Minecraft.getInstance().font;
                String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 10));
                g.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 8) / 2, color, false);
            }
        }
    }
}
