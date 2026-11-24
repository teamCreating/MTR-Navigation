package io.github.teamcreating.mtrnav.client.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import java.util.List;

/**
 * Per-frame UI render context with deferred tooltip queue so tooltips always draw above drawers and scissors.
 */
public final class UiContext {
    public final int mouseX;
    public final int mouseY;
    public final float delta;
    public final long nowNanos;
    private String tooltipText;
    private int tooltipX;
    private int tooltipY;

    public UiContext(int mouseX, int mouseY, float delta, long nowNanos) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.delta = delta;
        this.nowNanos = nowNanos;
    }

    public void tooltip(String text) {
        if (text != null && !text.isBlank()) {
            this.tooltipText = text;
            this.tooltipX = mouseX;
            this.tooltipY = mouseY;
        }
    }

    public void flushTooltip(GuiGraphics g, int maxWidth) {
        if (tooltipText == null) return;
        var font = UiDraw.font();
        var lines = font.split(Component.literal(tooltipText), Math.max(120, Math.min(280, maxWidth - 20)));
        g.renderTooltip(font, lines, tooltipX, tooltipY);
    }
}
