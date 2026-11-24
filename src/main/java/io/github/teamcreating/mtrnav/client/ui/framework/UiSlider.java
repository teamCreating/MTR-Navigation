package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import java.util.function.IntConsumer;

/**
 * Clean horizontal snapped slider for HUD scale (75%..200%, 25% steps).
 */
public final class UiSlider {
    private UiRect bounds = UiRect.EMPTY;
    private int percent = 150;
    private boolean dragging;
    private final IntConsumer onChange;

    public UiSlider(IntConsumer onChange) {
        this.onChange = onChange == null ? ignored -> {} : onChange;
    }

    public UiSlider bounds(UiRect bounds) { this.bounds = bounds; return this; }
    public UiSlider percent(int percent) { this.percent = snap(percent); return this; }
    public int percent() { return percent; }

    private static int snap(int value) {
        int clamped = Math.clamp(value, 75, 200);
        return 75 + Math.round((clamped - 75) / 25f) * 25;
    }

    private void updateFromX(double mouseX) {
        if (bounds.width() <= 12) return;
        double ratio = Math.clamp((mouseX - (bounds.x() + 6)) / (double) (bounds.width() - 12), 0.0, 1.0);
        int next = snap(75 + (int) Math.round(ratio * 125.0));
        if (next != percent) {
            percent = next;
            onChange.accept(percent);
        }
    }

    public void render(GuiGraphics g, UiContext ctx) {
        if (bounds.isEmpty()) return;
        int trackY = bounds.centerY() - 2;
        int trackX = bounds.x() + 6;
        int trackW = Math.max(10, bounds.width() - 12);
        double ratio = (percent - 75) / 125.0;
        int fillW = (int) Math.round(ratio * trackW);

        UiDraw.roundedRect(g, trackX, trackY, trackW, 4, 2f, UiTokens.INSET);
        if (fillW > 0) UiDraw.roundedRect(g, trackX, trackY, fillW, 4, 2f, UiTokens.PRIMARY);

        float knobX = trackX + fillW;
        float knobY = bounds.centerY();
        boolean hovered = bounds.contains(ctx.mouseX, ctx.mouseY) || dragging;
        UiDraw.circle(g, knobX, knobY, hovered ? 6f : 5f, 0xFFFFFFFF);
        UiDraw.ring(g, knobX, knobY, hovered ? 4.5f : 3.8f, hovered ? 6f : 5f,
                hovered ? UiTokens.PRIMARY_HOVER : UiTokens.BORDER_DEFAULT);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !bounds.contains(x, y)) return false;
        dragging = true;
        updateFromX(x);
        return true;
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (!dragging || button != 0) return false;
        updateFromX(x);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!dragging || button != 0) return false;
        dragging = false;
        return true;
    }
}
