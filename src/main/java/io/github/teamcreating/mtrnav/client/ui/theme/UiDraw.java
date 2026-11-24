package io.github.teamcreating.mtrnav.client.ui.theme;

import io.github.teamcreating.mtrnav.compat.MinecraftGui;
import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * High-craft native vector drawing primitives built on MinecraftGui.quad (zero external dependencies).
 */
public final class UiDraw {
    private static final int CORNER_STEPS = 5;

    private UiDraw() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        g.fill(x, y, x + w, y + h, argb);
    }

    public static void roundedRect(GuiGraphics g, float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f || (argb >>> 24) == 0) return;
        float r = Math.min(radius, Math.min(w * 0.5f, h * 0.5f));
        if (r <= 0.5f) {
            MinecraftGui.quad(g, x, y, x, y + h, x + w, y + h, x + w, y, argb);
            return;
        }
        // Center body
        MinecraftGui.quad(g, x + r, y, x + r, y + h, x + w - r, y + h, x + w - r, y, argb);
        // Left & right side strips
        MinecraftGui.quad(g, x, y + r, x, y + h - r, x + r, y + h - r, x + r, y + r, argb);
        MinecraftGui.quad(g, x + w - r, y + r, x + w - r, y + h - r, x + w, y + h - r, x + w, y + r, argb);
        // 4 smooth corner fans
        cornerFill(g, x + r, y + r, r, Math.PI, Math.PI * 1.5, argb);
        cornerFill(g, x + w - r, y + r, r, Math.PI * 1.5, Math.PI * 2.0, argb);
        cornerFill(g, x + w - r, y + h - r, r, 0.0, Math.PI * 0.5, argb);
        cornerFill(g, x + r, y + h - r, r, Math.PI * 0.5, Math.PI, argb);
    }

    public static void roundedRect(GuiGraphics g, UiRect rect, float radius, int argb) {
        roundedRect(g, rect.x(), rect.y(), rect.width(), rect.height(), radius, argb);
    }

    public static void roundedOutline(GuiGraphics g, float x, float y, float w, float h, float radius, float thickness, int argb) {
        if (w <= 0f || h <= 0f || thickness <= 0f || (argb >>> 24) == 0) return;
        float r = Math.min(radius, Math.min(w * 0.5f, h * 0.5f));
        float t = Math.min(thickness, Math.min(w * 0.5f, h * 0.5f));
        if (r <= 0.5f) {
            MinecraftGui.quad(g, x, y, x, y + t, x + w, y + t, x + w, y, argb);
            MinecraftGui.quad(g, x, y + h - t, x, y + h, x + w, y + h, x + w, y + h - t, argb);
            MinecraftGui.quad(g, x, y + t, x, y + h - t, x + t, y + h - t, x + t, y + t, argb);
            MinecraftGui.quad(g, x + w - t, y + t, x + w - t, y + h - t, x + w, y + h - t, x + w, y + t, argb);
            return;
        }
        // Straight edges
        MinecraftGui.quad(g, x + r, y, x + r, y + t, x + w - r, y + t, x + w - r, y, argb);
        MinecraftGui.quad(g, x + r, y + h - t, x + r, y + h, x + w - r, y + h, x + w - r, y + h - t, argb);
        MinecraftGui.quad(g, x, y + r, x, y + h - r, x + t, y + h - r, x + t, y + r, argb);
        MinecraftGui.quad(g, x + w - t, y + r, x + w - t, y + h - r, x + w, y + h - r, x + w, y + r, argb);
        // 4 curved corner arcs
        float innerR = Math.max(0f, r - t);
        arcRing(g, x + r, y + r, innerR, r, Math.PI, Math.PI * 1.5, CORNER_STEPS, argb);
        arcRing(g, x + w - r, y + r, innerR, r, Math.PI * 1.5, Math.PI * 2.0, CORNER_STEPS, argb);
        arcRing(g, x + w - r, y + h - r, innerR, r, 0.0, Math.PI * 0.5, CORNER_STEPS, argb);
        arcRing(g, x + r, y + h - r, innerR, r, Math.PI * 0.5, Math.PI, CORNER_STEPS, argb);
    }

    public static void roundedOutline(GuiGraphics g, UiRect rect, float radius, float thickness, int argb) {
        roundedOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), radius, thickness, argb);
    }

    public static void card(GuiGraphics g, UiRect rect, float radius, int bg, int border) {
        roundedRect(g, rect, radius, bg);
        if ((border >>> 24) != 0) roundedOutline(g, rect, radius, 1f, border);
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int argb) {
        if (radius <= 0f || (argb >>> 24) == 0) return;
        int segs = radius >= 8 ? 20 : 14;
        double step = Math.PI * 2.0 / segs;
        for (int i = 0; i < segs; i++) {
            double a0 = i * step, a1 = (i + 1) * step;
            float x0 = cx + (float) (Math.cos(a0) * radius);
            float y0 = cy + (float) (Math.sin(a0) * radius);
            float x1 = cx + (float) (Math.cos(a1) * radius);
            float y1 = cy + (float) (Math.sin(a1) * radius);
            MinecraftGui.quad(g, cx, cy, x0, y0, x1, y1, cx, cy, argb);
        }
    }

    public static void ring(GuiGraphics g, float cx, float cy, float innerRadius, float outerRadius, int argb) {
        if (outerRadius <= innerRadius || (argb >>> 24) == 0) return;
        int segs = outerRadius >= 8 ? 20 : 14;
        arcRing(g, cx, cy, Math.max(0f, innerRadius), outerRadius, 0.0, Math.PI * 2.0, segs, argb);
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int argb) {
        if (thickness <= 0f || (argb >>> 24) == 0) return;
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f) return;
        float nx = -dy / len * (thickness * 0.5f);
        float ny = dx / len * (thickness * 0.5f);
        MinecraftGui.quad(g, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, argb);
    }

    public static void dashedLine(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, float dash, float gap, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f) return;
        float ux = dx / len, uy = dy / len;
        for (float pos = 0f; pos < len; pos += dash + gap) {
            float end = Math.min(len, pos + dash);
            line(g, x1 + ux * pos, y1 + uy * pos, x1 + ux * end, y1 + uy * end, thickness, argb);
        }
    }

    public static void dottedLine(GuiGraphics g, float x1, float y1, float x2, float y2, float dotRadius, float step, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f || step <= 0.5f) return;
        float ux = dx / len, uy = dy / len;
        for (float pos = step * 0.6f; pos <= len - step * 0.6f; pos += step) {
            circle(g, x1 + ux * pos, y1 + uy * pos, dotRadius, argb);
        }
    }

    /**
     * Draws a crisp directional chevron pointing along unit vector (ux, uy) centered at (cx, cy).
     */
    public static void chevron(GuiGraphics g, float cx, float cy, float ux, float uy, float size, float thickness, int argb) {
        float nx = -uy, ny = ux;
        float tipX = cx + ux * (size * 0.45f);
        float tipY = cy + uy * (size * 0.45f);
        float backX = cx - ux * (size * 0.45f);
        float backY = cy - uy * (size * 0.45f);
        line(g, backX + nx * size * 0.65f, backY + ny * size * 0.65f, tipX, tipY, thickness, argb);
        line(g, backX - nx * size * 0.65f, backY - ny * size * 0.65f, tipX, tipY, thickness, argb);
    }

    public static void triangle(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        if ((argb >>> 24) == 0) return;
        MinecraftGui.quad(g, x1, y1, x2, y2, x3, y3, x3, y3, argb);
    }

    public static void text(GuiGraphics g, String value, int x, int y, int maxWidth, int color) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return;
        Font f = font();
        String clipped = f.width(value) <= maxWidth
                ? value
                : f.plainSubstrByWidth(value, Math.max(0, maxWidth - f.width("…"))) + "…";
        g.drawString(f, clipped, x, y, color, false);
    }

    public static void bold(GuiGraphics g, String value, int x, int y, int maxWidth, int color) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return;
        Font f = font();
        String shown = value;
        while (!shown.isEmpty() && f.width(Component.literal(shown.equals(value) ? shown : shown + "…").withStyle(net.minecraft.ChatFormatting.BOLD)) > maxWidth) {
            shown = shown.substring(0, shown.offsetByCodePoints(shown.length(), -1));
        }
        if (!shown.equals(value)) shown += "…";
        g.drawString(f, Component.literal(shown).withStyle(net.minecraft.ChatFormatting.BOLD), x, y, color, false);
    }

    public static void scaledText(GuiGraphics g, String value, int x, int y, int maxWidth, int color, float scale) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        text(g, value, 0, 0, (int) (maxWidth / scale), color);
        g.pose().popPose();
    }

    public static int wrapped(GuiGraphics g, String value, int x, int y, int maxWidth, int lineStep, int color) {
        if (value == null || value.isEmpty() || maxWidth <= 10) return y;
        Font f = font();
        for (var line : f.split(Component.literal(value), maxWidth)) {
            g.drawString(f, line, x, y, color, false);
            y += lineStep;
        }
        return y;
    }

    public static int wrappedHeight(String value, int maxWidth, int lineStep) {
        if (value == null || value.isEmpty() || maxWidth <= 10) return 0;
        return font().split(Component.literal(value), maxWidth).size() * lineStep;
    }

    public static int pill(GuiGraphics g, String label, int x, int y, int maxWidth, int bgColor, int inkColor) {
        if (label == null || label.isEmpty() || maxWidth < 12) return 0;
        int w = Math.min(maxWidth, font().width(label) + 10);
        roundedRect(g, x, y, w, 13, 3.5f, bgColor);
        text(g, label, x + 5, y + 3, w - 10, inkColor);
        return w;
    }

    public static int lineBadge(GuiGraphics g, String label, int x, int y, int maxWidth, int routeColor) {
        return pill(g, label, x, y, maxWidth, routeColor, UiTokens.contrastInk(routeColor));
    }

    public static void transportIcon(GuiGraphics g, Model.RouteType type, int x, int y, int size) {
        String name = switch (type) {
            case LIGHT_RAIL -> "light_rail_1";
            case HIGH_SPEED -> "xrl_2";
            default -> "train";
        };
        MinecraftGui.icon(g, ResourceLocation.fromNamespaceAndPath("mtr", "textures/block/sign/" + name + ".png"), x, y, size);
    }

    public static void scrollbar(GuiGraphics g, ScrollState scroll, int trackX, int trackWidth) {
        if (!scroll.scrollable() || scroll.geometry() == null) return;
        var geom = scroll.geometry();
        roundedRect(g, trackX, geom.top(), trackWidth, geom.height(), trackWidth * 0.5f, UiTokens.withAlpha(UiTokens.BORDER_SUBTLE, 140));
        roundedRect(g, trackX, geom.thumbTop(), trackWidth, geom.thumbHeight(), trackWidth * 0.5f,
                scroll.dragging() ? UiTokens.ACCENT_SKY : UiTokens.BORDER_DEFAULT);
    }

    public static void enableScissor(GuiGraphics g, UiRect rect) {
        g.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    private static void cornerFill(GuiGraphics g, float cx, float cy, float r, double startAngle, double endAngle, int argb) {
        double step = (endAngle - startAngle) / CORNER_STEPS;
        for (int i = 0; i < CORNER_STEPS; i++) {
            double a0 = startAngle + i * step;
            double a1 = startAngle + (i + 1) * step;
            float x0 = cx + (float) (Math.cos(a0) * r);
            float y0 = cy + (float) (Math.sin(a0) * r);
            float x1 = cx + (float) (Math.cos(a1) * r);
            float y1 = cy + (float) (Math.sin(a1) * r);
            MinecraftGui.quad(g, cx, cy, x0, y0, x1, y1, cx, cy, argb);
        }
    }

    private static void arcRing(GuiGraphics g, float cx, float cy, float innerR, float outerR, double startAngle, double endAngle, int steps, int argb) {
        double step = (endAngle - startAngle) / steps;
        for (int i = 0; i < steps; i++) {
            double a0 = startAngle + i * step;
            double a1 = startAngle + (i + 1) * step;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            MinecraftGui.quad(g,
                    cx + c0 * innerR, cy + s0 * innerR,
                    cx + c0 * outerR, cy + s0 * outerR,
                    cx + c1 * outerR, cy + s1 * outerR,
                    cx + c1 * innerR, cy + s1 * innerR,
                    argb);
        }
    }
}
