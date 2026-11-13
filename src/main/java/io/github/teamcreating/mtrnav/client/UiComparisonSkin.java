package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.compat.MinecraftGui;
import io.github.teamcreating.mtrnav.core.UiComparison.Variant;
import io.github.teamcreating.mtrnav.core.UiLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Per-screen colors and original pixel primitives; never changes NavigationTheme's global palette. */
final class UiComparisonSkin {
    record Palette(int frame, int surface, int inset, int text, int muted, int edge, int accent,
                   int selected, int button, int buttonText, int header, int headerText) {}
    final Variant variant;
    final Palette p;
    UiComparisonSkin(Variant variant) {
        this.variant = variant;
        p = switch (variant) {
            case AE2 -> new Palette(0xFFCBCCD4, 0xFFCBCCD4, 0xFFADB0C4, 0xFF20202A, 0xFF413F54,
                    0xFF413F54, 0xFF343D70, 0xFFBDC7E5, 0xFF9A9FB4, 0xFF20202A, 0xFFCBCCD4, 0xFF20202A);
            case BEDROCK -> new Palette(0xFF313233, 0xFF48494A, 0xFF313233, 0xFFFFFFFF, 0xFFD0D1D4,
                    0xFF1E1E1F, 0xFF6CC349, 0xFF58585A, 0xFFD0D1D4, 0xFF1E1E1F, 0xFFE6E8EB, 0xFF1E1E1F);
            case JAVA -> new Palette(0xFF353535, 0xFF303030, 0xFF191919, 0xFFFFFFFF, 0xFFC7C7C7,
                    0xFF080808, 0xFFFFFFFF, 0xFF191919, 0xFF6B6B6B, 0xFFFFFFFF, 0xFF353535, 0xFFFFFFFF);
            case HYBRID -> new Palette(0xFFC6C6C6, 0xFF34383A, 0xFF222628, 0xFFF4F4F4, 0xFFC1CBCF,
                    0xFF121719, 0xFF9BCBD9, 0xFF31515E, 0xFF6B6B6B, 0xFFFFFFFF, 0xFFC6C6C6, 0xFF202020);
        };
    }
    static String label(Variant v) { return switch (v) {
        case AE2 -> "AE2 终端"; case BEDROCK -> "BE / Ore UI";
        case JAVA -> "Java 原生"; case HYBRID -> "融合方案";
    }; }
    static void fill(GuiGraphics g, Rect r, int color) {
        if (r.width() > 0 && r.height() > 0) g.fill(r.x(), r.y(), r.right(), r.bottom(), color);
    }
    static void edge(GuiGraphics g, Rect r, int color) {
        if (r.width() < 1 || r.height() < 1) return;
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }
    void panel(GuiGraphics g, Rect r) {
        fill(g, r, p.frame); edge(g, r, p.edge);
        if (variant == Variant.AE2 || variant == Variant.HYBRID) {
            g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, 0xFFFFFFFF);
            g.fill(r.x() + 1, r.y() + 1, r.x() + 2, r.bottom() - 1, 0xFFFFFFFF);
        }
        fill(g, r.inset(variant == Variant.BEDROCK ? 1 : 4), p.surface);
        int inset = variant == Variant.BEDROCK ? 1 : 4;
        fill(g, new Rect(r.x() + inset, r.y() + inset, Math.max(0, r.width() - 2 * inset), 28), p.header);
        if (variant == Variant.BEDROCK) {
            g.fill(r.x() + 1, r.y() + 28, r.right() - 1, r.y() + 30, 0xFFB1B2B5);
            g.fill(r.x() + 1, r.y() + 30, r.right() - 1, r.y() + 31, 0xFF1E1E1F);
        }
    }
    void recess(GuiGraphics g, Rect r) { fill(g, r, p.inset); edge(g, r, p.edge); }
    void row(GuiGraphics g, Rect r, boolean selected, boolean hovered) {
        fill(g, r, selected ? p.selected : p.surface);
        if (selected) {
            if (variant == Variant.BEDROCK) {
                edge(g, r, 0xFFD0D1D4);
                g.fill(r.right() - 6, r.y() + 4, r.right() - 3, r.y() + 7, p.accent);
            } else if (variant == Variant.HYBRID)
                g.fill(r.x(), r.y(), r.x() + 3, r.bottom(), p.accent);
            else edge(g, r, p.accent);
        }
        if (hovered) edge(g, r.inset(1), p.accent);
    }
    void button(GuiGraphics g, Rect r, boolean primary, boolean selected, boolean enabled, boolean focused, boolean pressed) {
        if (variant == Variant.JAVA || variant == Variant.HYBRID) {
            String name = !enabled ? "button_disabled" : focused ? "button_highlighted" : "button";
            MinecraftGui.sprite(g, ResourceLocation.withDefaultNamespace("widget/" + name), r.x(), r.y(), r.width(), r.height());
            if (variant == Variant.HYBRID && primary && enabled) g.fill(r.x() + 2, r.bottom() - 3, r.right() - 2, r.bottom() - 1, p.accent);
        } else {
            if (variant == Variant.BEDROCK) {
                // Ore's secondary controls have a neutral shadow, not a green bevel.
                int shadow = primary ? 0xFF1D4D13 : 0xFF58585A;
                int face = !enabled ? 0xFFB1B2B5 : primary ? pressed ? 0xFF1D4D13 : focused ? 0xFF2A641C : 0xFF3C8527
                        : pressed || focused ? 0xFFB1B2B5 : 0xFFD0D1D4;
                fill(g, r, p.edge); fill(g, r.inset(1), !enabled ? 0xFF8C8D90 : shadow);
                var front = new Rect(r.x() + 1, r.y() + (pressed ? 3 : 1), Math.max(0, r.width() - 2), Math.max(0, r.height() - (pressed ? 4 : 5)));
                fill(g, front, face);
                if (enabled && !pressed) g.fill(front.x(), front.y(), front.right(), front.y() + 1, primary ? 0xFF639D52 : 0xFFECEDEF);
            } else {
                fill(g, r, !enabled ? p.inset : selected || pressed ? p.selected : p.button); edge(g, r, p.edge);
                if (enabled && r.height() >= 4) {
                    g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, 0xFFF2F2F2);
                    g.fill(r.x() + 1, r.bottom() - 2, r.right() - 1, r.bottom() - 1, 0xFF696D88);
                }
            }
            if (selected && variant == Variant.AE2) edge(g, r, p.accent);
        }
        if (focused && enabled) edge(g, r, variant == Variant.AE2 ? p.accent : 0xFFFFFFFF);
    }
    void tab(GuiGraphics g, Rect r, boolean selected, boolean enabled, boolean focused) {
        if (variant == Variant.JAVA || variant == Variant.HYBRID) {
            String name = selected ? focused ? "tab_selected_highlighted" : "tab_selected" : focused ? "tab_highlighted" : "tab";
            MinecraftGui.sprite(g, ResourceLocation.withDefaultNamespace("widget/" + name), r.x(), r.y(), r.width(), r.height());
        } else if (variant == Variant.BEDROCK) {
            fill(g, r, selected ? 0xFF48494A : 0xFF313233);
            edge(g, r, 0xFF1E1E1F);
            if (selected) g.fill(r.x() + 1, r.bottom() - 3, r.right() - 1, r.bottom() - 1, 0xFFD0D1D4);
            if (focused) edge(g, r.inset(1), 0xFFFFFFFF);
        } else button(g, r, false, selected, enabled, focused, false);
    }
    void navigation(GuiGraphics g, Rect r, boolean selected, boolean focused) {
        fill(g, r, selected ? 0xFF58585A : focused ? 0xFF48494A : 0xFF313233);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), 0xFF242425);
        if (selected) g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), 0xFFFFFFFF);
        if (focused) edge(g, r, 0xFFFFFFFF);
    }
    void toggle(GuiGraphics g, Rect r, boolean on, boolean focused) {
        fill(g, r, p.surface);
        if (focused) edge(g, r, 0xFFFFFFFF);
        var track = new Rect(r.right() - 34, r.y() + 4, 30, 12);
        fill(g, track, 0xFF1E1E1F);
        fill(g, track.inset(1), on ? 0xFF3C8527 : 0xFF58585A);
        var thumb = new Rect(on ? track.right() - 12 : track.x(), track.y() - 2, 12, 16);
        fill(g, thumb, 0xFF1E1E1F); fill(g, thumb.inset(1), 0xFFD0D1D4);
        g.fill(thumb.x() + 1, thumb.y() + 1, thumb.right() - 1, thumb.y() + 2, 0xFFFFFFFF);
    }
    void field(GuiGraphics g, Rect r, boolean focused) {
        fill(g, r, variant == Variant.AE2 ? 0xFF4D4D67 : 0xFF313233);
        edge(g, r, variant == Variant.AE2 ? 0xFF696D88 : 0xFF1E1E1F);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 3, variant == Variant.AE2 ? 0xFF413F54 : 0xFF242425);
        if (focused) edge(g, r, variant == Variant.AE2 ? 0xFFF2F2F2 : 0xFFFFFFFF);
    }
    int buttonInk(boolean primary, boolean selected, boolean enabled) {
        if (!enabled) return variant == Variant.BEDROCK ? 0xFF58585A : variant == Variant.AE2 ? 0xFF696D88 : 0xFFA0A0A0;
        return variant == Variant.BEDROCK && primary ? 0xFFFFFFFF : p.buttonText;
    }
}
