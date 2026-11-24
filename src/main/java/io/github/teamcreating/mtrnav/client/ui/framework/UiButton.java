package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.UiPress;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Clean, single-purpose rounded button supporting primary/secondary/ghost/chip/danger variants and release-inside activation.
 */
public final class UiButton {
    public enum Style { PRIMARY, SECONDARY, GHOST, CHIP, DANGER }

    private UiRect bounds = UiRect.EMPTY;
    private String label;
    private UiIcons icon;
    private Style style;
    private boolean enabled = true;
    private boolean selected;
    private String tooltip;
    private final Runnable action;
    private final UiPress press = new UiPress();

    public UiButton(String label, Style style, Runnable action) {
        this.label = label == null ? "" : label;
        this.style = style;
        this.action = action == null ? () -> {} : action;
    }

    public UiButton icon(UiIcons icon) { this.icon = icon; return this; }
    public UiButton label(String label) { this.label = label == null ? "" : label; return this; }
    public UiButton style(Style style) { this.style = style; return this; }
    public UiButton enabled(boolean enabled) { this.enabled = enabled; return this; }
    public UiButton selected(boolean selected) { this.selected = selected; return this; }
    public UiButton tooltip(String tooltip) { this.tooltip = tooltip; return this; }
    public UiButton bounds(UiRect bounds) { this.bounds = bounds; return this; }
    public UiRect bounds() { return bounds; }
    public boolean enabled() { return enabled; }
    public boolean selected() { return selected; }

    public void render(GuiGraphics g, UiContext ctx) {
        if (bounds.isEmpty()) return;
        boolean hovered = enabled && bounds.contains(ctx.mouseX, ctx.mouseY);
        if (hovered && tooltip != null) ctx.tooltip(tooltip);
        double depth = press.depth(ctx.nowNanos);
        boolean pressed = enabled && depth > 0.35;

        int bg;
        int border = 0;
        int ink;
        float radius = style == Style.CHIP ? Math.min(8f, bounds.height() * 0.5f) : UiTokens.RADIUS_SM;

        switch (style) {
            case PRIMARY -> {
                bg = !enabled ? UiTokens.SURFACE_ELEVATED
                        : pressed ? UiTokens.PRIMARY_PRESSED
                        : hovered ? UiTokens.PRIMARY_HOVER : UiTokens.PRIMARY;
                ink = enabled ? UiTokens.PRIMARY_INK : UiTokens.TEXT_DISABLED;
            }
            case DANGER -> {
                bg = !enabled ? UiTokens.SURFACE_ELEVATED
                        : pressed ? 0xFFB91C1C : hovered ? 0xFFEF4444 : 0xFFDC2626;
                ink = enabled ? 0xFFFFFFFF : UiTokens.TEXT_DISABLED;
            }
            case CHIP -> {
                bg = selected ? UiTokens.withAlpha(UiTokens.ACCENT_SKY, 48)
                        : hovered ? UiTokens.SURFACE_ELEVATED : UiTokens.SURFACE;
                border = selected ? UiTokens.ACCENT_SKY : UiTokens.BORDER_SUBTLE;
                ink = !enabled ? UiTokens.TEXT_DISABLED : selected ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_SECONDARY;
            }
            case GHOST -> {
                bg = selected ? UiTokens.SURFACE_SELECTED
                        : hovered ? UiTokens.SURFACE_ELEVATED : 0;
                border = selected ? UiTokens.BORDER_SELECTED : 0;
                ink = !enabled ? UiTokens.TEXT_DISABLED : (selected || hovered) ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_SECONDARY;
            }
            default -> {
                bg = !enabled ? UiTokens.INSET
                        : selected ? UiTokens.SURFACE_SELECTED
                        : pressed ? UiTokens.INSET
                        : hovered ? UiTokens.SURFACE_ELEVATED : UiTokens.SURFACE;
                border = selected ? UiTokens.BORDER_SELECTED
                        : hovered ? UiTokens.BORDER_DEFAULT : UiTokens.BORDER_SUBTLE;
                ink = !enabled ? UiTokens.TEXT_DISABLED : UiTokens.TEXT_PRIMARY;
            }
        }

        if ((bg >>> 24) != 0) UiDraw.roundedRect(g, bounds, radius, bg);
        if ((border >>> 24) != 0) UiDraw.roundedOutline(g, bounds, radius, 1f, border);

        int iconSize = bounds.height() >= 20 ? 11 : 10;
        boolean hasIcon = icon != null;
        boolean hasText = !label.isEmpty();
        var font = UiDraw.font();
        int pad = hasText ? 6 : 0;
        int textW = hasText ? Math.min(font.width(label), Math.max(0, bounds.width() - pad * 2 - (hasIcon ? iconSize + 4 : 0))) : 0;
        int contentW = (hasIcon ? iconSize : 0) + (hasIcon && hasText ? 4 : 0) + textW;
        int cx = bounds.x() + Math.max(4, (bounds.width() - contentW) / 2);
        int cy = bounds.centerY();

        if (hasIcon) {
            icon.draw(g, cx, cy - iconSize / 2, iconSize, ink);
            cx += iconSize + 4;
        }
        if (hasText && textW > 0) {
            UiDraw.text(g, label, cx, cy - 4, textW, ink);
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !enabled || !bounds.contains(x, y)) return false;
        press.press(System.nanoTime());
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !press.down()) return false;
        boolean activate = press.release(enabled && bounds.contains(x, y), System.nanoTime());
        if (activate) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            action.run();
        }
        return true;
    }

    public void cancelPress() {
        press.cancel(System.nanoTime());
    }
}
