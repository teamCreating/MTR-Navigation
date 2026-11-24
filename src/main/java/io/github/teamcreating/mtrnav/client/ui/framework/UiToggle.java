package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiMotion;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Smooth pill toggle switch with 150ms cubic knob motion.
 */
public final class UiToggle {
    private UiRect bounds = UiRect.EMPTY;
    private String label;
    private String tooltip;
    private boolean enabled = true;
    private boolean checked;
    private final Runnable onToggle;
    private final UiMotion knob = new UiMotion(0.0, 150);

    public UiToggle(String label, Runnable onToggle) {
        this.label = label == null ? "" : label;
        this.onToggle = onToggle == null ? () -> {} : onToggle;
    }

    public UiToggle bounds(UiRect bounds) { this.bounds = bounds; return this; }
    public UiToggle label(String label) { this.label = label == null ? "" : label; return this; }
    public UiToggle enabled(boolean enabled) { this.enabled = enabled; return this; }
    public UiToggle tooltip(String tooltip) { this.tooltip = tooltip; return this; }

    public UiToggle checked(boolean value) {
        this.checked = value;
        knob.animateTo(value ? 1.0 : 0.0, System.nanoTime());
        return this;
    }

    public void render(GuiGraphics g, UiContext ctx) {
        if (bounds.isEmpty()) return;
        boolean hovered = enabled && bounds.contains(ctx.mouseX, ctx.mouseY);
        if (hovered && tooltip != null) ctx.tooltip(tooltip);
        if (hovered) UiDraw.roundedRect(g, bounds, UiTokens.RADIUS_SM, UiTokens.SURFACE_HOVER);

        UiDraw.text(g, label, bounds.x() + 6, bounds.centerY() - 4, Math.max(0, bounds.width() - 46),
                enabled ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_DISABLED);

        int trackW = 28, trackH = 14;
        int tx = bounds.right() - trackW - 6;
        int ty = bounds.centerY() - trackH / 2;
        double p = knob.value(ctx.nowNanos);
        int trackColor = !enabled ? UiTokens.INSET : checked ? UiTokens.PRIMARY : UiTokens.SURFACE_ELEVATED;
        UiDraw.roundedRect(g, tx, ty, trackW, trackH, 7f, trackColor);
        UiDraw.roundedOutline(g, tx, ty, trackW, trackH, 7f, 1f,
                checked && enabled ? UiTokens.PRIMARY_HOVER : UiTokens.BORDER_DEFAULT);

        float cx = (float) (tx + 7 + p * (trackW - 14));
        float cy = ty + trackH * 0.5f;
        UiDraw.circle(g, cx, cy, 4.8f, enabled ? 0xFFFFFFFF : UiTokens.TEXT_MUTED);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !enabled || !bounds.contains(x, y)) return false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        onToggle.run();
        return true;
    }
}
