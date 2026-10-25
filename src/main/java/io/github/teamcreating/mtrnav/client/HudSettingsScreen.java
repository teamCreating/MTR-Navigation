package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Live placement editor: drag in the world view; position survives GUI and window changes. */
final class HudSettingsScreen extends Screen {
    private final Screen previous;
    private final List<Action> widgets = new ArrayList<>();
    private Action sizeLabel, fitButton;
    private boolean dragging, controlsAtTop;
    private int panelY;
    private double dragX, dragY;
    HudSettingsScreen(Screen previous) { super(Component.translatable("mtrnav.hud_settings")); this.previous = previous; }
    private Action button(String key, int x, int y, int w, Runnable action) {
        var button = new Action(x, y, w, 20, tr(key), action); addRenderableWidget(button); widgets.add(button); return button;
    }
    @Override protected void init() {
        widgets.clear();
        int x = Math.max(8, (width - 440) / 2), inner = Math.min(440, width - 16), y = height - 80;
        button("mtrnav.size_minus", x, y, 24, () -> NavigationPreferences.changeHudSize(-1));
        sizeLabel = button("mtrnav.hud_size", x + 28, y, 102, () -> {}); sizeLabel.active = false;
        button("mtrnav.size_plus", x + 134, y, 24, () -> NavigationPreferences.changeHudSize(1));
        fitButton = button("mtrnav.auto_fit", x + 166, y, inner - 166, NavigationPreferences::toggleAutoFit);
        fitButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.auto_fit_hint")));
        int cornerWidth = (inner - 12) / 4;
        button("mtrnav.corner_top_left", x, y + 24, cornerWidth, () -> NavigationPreferences.setHudPosition(0, 0, true));
        button("mtrnav.corner_top_right", x + cornerWidth + 4, y + 24, cornerWidth, () -> NavigationPreferences.setHudPosition(1, 0, true));
        button("mtrnav.corner_bottom_left", x + (cornerWidth + 4) * 2, y + 24, cornerWidth, () -> NavigationPreferences.setHudPosition(0, 1, true));
        button("mtrnav.corner_bottom_right", x + (cornerWidth + 4) * 3, y + 24, cornerWidth, () -> NavigationPreferences.setHudPosition(1, 1, true));
        button("mtrnav.reset_hud", x, y + 48, (inner - 6) / 2, NavigationPreferences::resetHud);
        button("mtrnav.done", x + (inner + 6) / 2, y + 48, (inner - 6) / 2, this::onClose);
        updateLabels();
        layoutControls();
    }
    private void layoutControls() {
        // Move the editor out of the selected area. Hide it entirely during a drag,
        // so its buttons cannot intercept placement anywhere in the world view.
        var p = NavigationHud.placement();
        if (p.y() + p.height() < height * .45) controlsAtTop = false;
        else if (p.y() > height * .55) controlsAtTop = true;
        panelY = controlsAtTop ? 42 : height - 108;
        int delta = panelY + 28 - widgets.getFirst().getY();
        for (var widget : widgets) { widget.setY(widget.getY() + delta); widget.visible = !dragging; }
        if (dragging) setFocused(null);
    }
    private void updateLabels() {
        sizeLabel.setMessage(Component.translatable("mtrnav.hud_size", NavigationPreferences.hudPercent()));
        fitButton.setMessage(Component.translatable("mtrnav.auto_fit", tr(NavigationPreferences.autoFit() ? "mtrnav.on" : "mtrnav.off")));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { NavigationPreferences.save(); minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x40101010);
        g.drawString(font, title, 10, 10, TEXT);
        text(g, tr("mtrnav.hud_drag_hint"), 10, 25, width - 20, TEXT);
        layoutControls();
        int x = Math.max(4, (width - 456) / 2), w = Math.min(456, width - 8);
        if (!dragging) {
            frame(g, x, panelY, w, 104);
            text(g, tr("mtrnav.hud_settings_hint"), x + 8, panelY + 8, w - 16, MUTED);
        }
        NavigationHud.preview(g);
        if (dragging) {
            var p = NavigationHud.placement();
            outline(g, (int)p.x() - 1, (int)p.y() - 1, (int)p.width() + 2, (int)p.height() + 2, ACCENT);
        }
        updateLabels(); for (var widget : widgets) if (widget.visible) widget.render(g, mouseX, mouseY, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        layoutControls();
        var position = NavigationHud.placement();
        if (button == 0 && position.contains(x, y)) { dragging = true; dragX = x - position.x(); dragY = y - position.y(); layoutControls(); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            var position = NavigationHud.placement();
            NavigationPreferences.setHudPosition(position.relativeX(x - dragX), position.relativeY(y - dragY), false); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging) { dragging = false; NavigationPreferences.save(); layoutControls(); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && NavigationHud.placement().contains(x, y)) { NavigationPreferences.changeHudSize(vertical > 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
