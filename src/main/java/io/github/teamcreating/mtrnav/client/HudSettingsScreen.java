package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.HudPlacement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** One editor for two independent HUDs, previewing their actual collision-free arrangement. */
final class HudSettingsScreen extends Screen {
    private final Screen previous;
    private final List<Action> widgets = new ArrayList<>();
    private Action sizeLabel, fitButton, navigationButton, reminderButton, displayButton;
    private boolean dragging, editingReminder;
    private int panelY;
    private double dragX, dragY;
    private static final int PANEL_HEIGHT = 152;
    HudSettingsScreen(Screen previous) { super(Component.translatable("mtrnav.hud_settings")); this.previous = previous; }
    private Action button(String key, int x, int y, int w, Runnable action) {
        var button = new Action(x, y, w, 20, tr(key), action); addRenderableWidget(button); widgets.add(button); return button;
    }
    @Override protected void init() {
        widgets.clear();
        int x = Math.max(8, (width - 440) / 2), inner = Math.min(440, width - 16), y = height - 128;
        navigationButton = button("mtrnav.hud_edit_navigation", x, y, (inner - 4) / 2, () -> editingReminder = false);
        reminderButton = button("mtrnav.hud_edit_reminder", x + (inner + 4) / 2, y, (inner - 4) / 2, () -> editingReminder = true);
        button("mtrnav.size_minus", x, y + 24, 24, () -> changeSize(-1));
        sizeLabel = button("mtrnav.hud_size", x + 28, y + 24, 102, () -> {}); sizeLabel.active = false;
        button("mtrnav.size_plus", x + 134, y + 24, 24, () -> changeSize(1));
        fitButton = button("mtrnav.auto_fit", x + 166, y + 24, inner - 166, NavigationPreferences::toggleAutoFit);
        fitButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.hud_both_fit_hint")));
        int cornerWidth = (inner - 12) / 4;
        button("mtrnav.corner_top_left", x, y + 48, cornerWidth, () -> position(0, 0, true));
        button("mtrnav.corner_top_right", x + cornerWidth + 4, y + 48, cornerWidth, () -> position(1, 0, true));
        button("mtrnav.corner_bottom_left", x + (cornerWidth + 4) * 2, y + 48, cornerWidth, () -> position(0, 1, true));
        button("mtrnav.corner_bottom_right", x + (cornerWidth + 4) * 3, y + 48, cornerWidth, () -> position(1, 1, true));
        displayButton = button("mtrnav.reminder_display_mode", x, y + 72, inner, NavigationPreferences::toggleReminderExpanded);
        displayButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.reminder_display_hint")));
        button("mtrnav.hud_reset_selected", x, y + 96, (inner - 6) / 2, () -> { if (editingReminder) NavigationPreferences.resetReminder(); else NavigationPreferences.resetHud(); });
        button("mtrnav.done", x + (inner + 6) / 2, y + 96, (inner - 6) / 2, this::onClose);
        updateLabels(); layoutControls();
    }
    private HudPlacement selectedPlacement() { var pair = HudPositions.layout(true); return editingReminder ? pair.reminder() : pair.navigation(); }
    private void changeSize(int direction) { if (editingReminder) NavigationPreferences.changeReminderSize(direction); else NavigationPreferences.changeHudSize(direction); }
    private void position(double x, double y, boolean persist) { if (editingReminder) NavigationPreferences.setReminderPosition(x, y, persist); else NavigationPreferences.setHudPosition(x, y, persist); }
    private void layoutControls() {
        var pair = HudPositions.layout(true);
        int x = Math.max(4, (width - 456) / 2), w = Math.min(456, width - 8);
        double best = Double.POSITIVE_INFINITY;
        for (int proposed : new int[]{height - PANEL_HEIGHT - 4, 42, (height - PANEL_HEIGHT) / 2}) {
            int y = Math.clamp(proposed, 4, Math.max(4, height - PANEL_HEIGHT - 4));
            double area = overlapArea(x, y, w, PANEL_HEIGHT, pair.navigation()) + overlapArea(x, y, w, PANEL_HEIGHT, pair.reminder());
            if (area < best) { best = area; panelY = y; }
        }
        int delta = panelY + 28 - widgets.getFirst().getY();
        for (var widget : widgets) { widget.setY(widget.getY() + delta); widget.visible = !dragging; }
        if (dragging) setFocused(null);
    }
    private static double overlapArea(int x, int y, int w, int h, HudPlacement p) {
        return Math.max(0, Math.min(x + w, p.x() + p.width()) - Math.max(x, p.x()))
                * Math.max(0, Math.min(y + h, p.y() + p.height()) - Math.max(y, p.y()));
    }
    private void updateLabels() {
        sizeLabel.setMessage(Component.translatable("mtrnav.hud_size", editingReminder ? NavigationPreferences.reminderPercent() : NavigationPreferences.hudPercent()));
        fitButton.setMessage(Component.translatable("mtrnav.auto_fit", tr(NavigationPreferences.autoFit() ? "mtrnav.on" : "mtrnav.off")));
        navigationButton.selected = !editingReminder; reminderButton.selected = editingReminder;
        displayButton.setMessage(Component.translatable("mtrnav.reminder_display_mode", tr(NavigationPreferences.reminderExpanded() ? "mtrnav.reminder_display_expanded" : "mtrnav.reminder_display_auto")));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { NavigationPreferences.save(); minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x40101010);
        text(g, tr("mtrnav.hud_editing", tr(editingReminder ? "mtrnav.hud_edit_reminder" : "mtrnav.hud_edit_navigation")), 10, 10, width - 20, TEXT);
        text(g, tr("mtrnav.hud_both_drag_hint"), 10, 25, width - 20, TEXT);
        layoutControls();
        int x = Math.max(4, (width - 456) / 2), w = Math.min(456, width - 8);
        if (!dragging) {
            frame(g, x, panelY, w, PANEL_HEIGHT);
            text(g, tr("mtrnav.hud_both_settings_hint"), x + 8, panelY + 8, w - 16, MUTED);
        }
        NavigationHud.preview(g); ReminderHud.preview(g);
        var p = selectedPlacement();
        outline(g, (int)Math.floor(p.x()) - 1, (int)Math.floor(p.y()) - 1, (int)Math.ceil(p.width()) + 2, (int)Math.ceil(p.height()) + 2, ACCENT);
        updateLabels(); for (var widget : widgets) if (widget.visible) widget.render(g, mouseX, mouseY, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        layoutControls();
        // Visible controls take precedence if the small viewport leaves no empty panel area.
        for (var widget : widgets) if (widget.visible && widget.isMouseOver(x, y)) return super.mouseClicked(x, y, button);
        var pair = HudPositions.layout(true);
        if (button == 0 && (pair.reminder().contains(x, y) || pair.navigation().contains(x, y))) {
            editingReminder = pair.reminder().contains(x, y); var p = selectedPlacement();
            dragging = true; dragX = x - p.x(); dragY = y - p.y(); layoutControls(); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { var p = selectedPlacement(); position(p.relativeX(x - dragX), p.relativeY(y - dragY), false); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging) { dragging = false; NavigationPreferences.save(); layoutControls(); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        var pair = HudPositions.layout(true);
        if (vertical != 0 && (pair.reminder().contains(x, y) || pair.navigation().contains(x, y))) {
            editingReminder = pair.reminder().contains(x, y); changeSize(vertical > 0 ? 1 : -1); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
