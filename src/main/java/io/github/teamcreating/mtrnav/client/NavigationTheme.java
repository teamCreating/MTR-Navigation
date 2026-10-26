package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/** Shared native screen/HUD styling. Coordinates are Minecraft GUI units. */
final class NavigationTheme {
    static final int BACKGROUND = 0xF5282D2D, PANEL = 0xFF282D2D, RAISED = 0xFF323837;
    static final int BORDER = 0xFF51594F, TEXT = 0xFFF4EFDF, MUTED = 0xFFADB1A5;
    static final int ACCENT = 0xFFD4B478, WARNING = 0xFFFFCC80;
    private static final int BRASS_DARK = 0xFF68502F, BRASS_LIGHT = 0xFFE3C68A;
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("mtrnav", "icon.png");
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
    static void icon(GuiGraphics g, int x, int y, int size) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0); g.pose().scale(size / 1254f, size / 1254f, 1);
        g.blit(ICON, 0, 0, 0f, 0f, 1254, 1254, 1254, 1254); g.pose().popPose();
    }
    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        for (int tx = 0; tx < w; tx += 16) for (int ty = 0; ty < h; ty += 16)
            g.blit(STONE, x + tx, y + ty, 0f, 0f, Math.min(16, w - tx), Math.min(16, h - ty), 16, 16);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xE8252928);
        outline(g, x, y, w, h, 0xFF111210); outline(g, x + 1, y + 1, w - 2, h - 2, 0xFF85877D);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, 0xFF171916);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 2, 0xFF171916);
        // Brass casing corners around an otherwise vanilla stone-and-iron panel.
        for (int side : new int[]{0, 1}) {
            int cx = side == 0 ? x + 2 : x + w - 26;
            g.fill(cx, y + 2, cx + 24, y + 4, BRASS_LIGHT);
            g.fill(cx, y + h - 5, cx + 24, y + h - 3, BRASS_DARK);
            int edge = side == 0 ? x + 2 : x + w - 5;
            g.fill(edge, y + 2, edge + 3, y + 24, ACCENT);
            g.fill(edge, y + h - 24, edge + 3, y + h - 3, ACCENT);
        }
        for (int rx : new int[]{x + 5, x + w - 8}) for (int ry : new int[]{y + 5, y + h - 8}) {
            g.fill(rx, ry, rx + 3, ry + 3, 0xFF53422B); g.fill(rx, ry, rx + 2, ry + 2, ACCENT);
        }
    }
    static void railDivider(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, BRASS_DARK);
        g.fill(x, y + 2, x + width, y + 3, BORDER);
        for (int tick = 0; tick < width; tick += 12) g.fill(x + tick, y, x + tick + 2, y + 3, ACCENT);
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

    /** Vanilla stone controls inside a brass railway instrument casing. */
    static final class Action extends Button {
        boolean selected, primary, tab, locateIcon;
        String detail, extra;
        int tint = ACCENT;
        Action(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
        }
        @Override public void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int color) {
            if (locateIcon) {
                if (width > 24) g.drawCenteredString(font, getMessage(), getX() + (width + 14) / 2, getY() + (height - 8) / 2, color);
            } else super.renderString(g, font, color);
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            boolean highlighted = isHoveredOrFocused();
            if (detail == null) {
                super.renderWidget(g, mouseX, mouseY, delta);
                if (locateIcon) {
                    int cx = getX() + (width <= 24 ? width / 2 : 10), cy = getY() + height / 2;
                    int color = active ? ACCENT : MUTED;
                    outline(g, cx - 3, cy - 3, 7, 7, color);
                    g.fill(cx, cy - 5, cx + 1, cy + 6, color); g.fill(cx - 5, cy, cx + 6, cy + 1, color);
                    g.fill(cx - 1, cy - 1, cx + 2, cy + 2, PANEL);
                }
                if (primary && active) {
                    outline(g, getX() + 1, getY() + 1, width - 2, height - 2, ACCENT);
                    for (int edge : new int[]{getX() + 3, getX() + width - 5}) g.fill(edge, getY() + 3, edge + 2, getY() + 5, BRASS_LIGHT);
                }
                if (tab && selected) g.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 1, ACCENT);
                return;
            }
            g.blitSprite(BUTTON, getX(), getY(), width, height);
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0xD0252A27);
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
