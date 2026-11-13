package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.ArrivalReminder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Configure or cancel one exact board-row reminder; keeps this station subscribed while open. */
final class ReminderSettingsScreen extends NavigationPanel {
    private final Screen previous;
    private final String stationId;
    private final StopEvent selected;
    private final List<Action> buttons = new ArrayList<>();
    private Action leadLabel, armButton, cancelButton, displayButton;
    private int minutes, left, top, panelWidth, panelHeight, infoBottom, infoScroll;
    private StopEvent live;
    ReminderSettingsScreen(Screen previous, String stationId, StopEvent selected, int minutes) {
        super(Component.translatable("mtrnav.reminder_title")); this.previous = previous; this.stationId = stationId; this.selected = selected; this.minutes = minutes;
    }
    private Action button(String key, int x, int y, int width, Runnable action) {
        var button = new Action(x, y, width, 20, tr(key), action); button.onLight = y >= top + panelHeight - 38; addRenderableWidget(button); buttons.add(button); return button;
    }
    @Override protected void init() {
        super.init();
        buttons.clear();
        panelWidth = Math.min(width - 20, 440); panelHeight = Math.min(height - 16, 278);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int x = left + 12, inner = panelWidth - 24, controlsY = top + panelHeight - 84;
        infoBottom = controlsY - 21;
        button("mtrnav.close", left + panelWidth - 30, top + 4, 18, this::onClose);
        button("mtrnav.size_minus", x, controlsY, 24, () -> minutes = Math.max(1, minutes - 1));
        leadLabel = button("mtrnav.reminder_lead", x + 28, controlsY, inner - 56, () -> {}); leadLabel.active = false; leadLabel.labelOnly = true;
        button("mtrnav.size_plus", x + inner - 24, controlsY, 24, () -> minutes = Math.min(60, minutes + 1));
        displayButton = button("mtrnav.reminder_display_mode", x, controlsY + 24, inner, NavigationPreferences::toggleReminderExpanded);
        displayButton.toggle=true;
        displayButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mtrnav.reminder_display_hint")));
        cancelButton = button("mtrnav.back", x, top + panelHeight - 32, (inner - 6) / 2, () -> { if (isCurrent()) cancelReminder(); onClose(); });
        armButton = button("mtrnav.reminder_arm", x + (inner + 6) / 2, top + panelHeight - 32, (inner - 6) / 2, () -> {
            if (live != null && armReminder(stationId, live, minutes)) onClose();
        }); armButton.primary = true;
        board(stationId); refresh();
    }
    private boolean isCurrent() { return reminder != null && reminder.stationId().equals(stationId) && ArrivalReminder.sameEvent(selected, reminder.event()); }
    private void refresh() {
        live = boardStation.equals(stationId) && boardFresh() ? ArrivalReminder.unique(selected, arrivals).orElse(null) : null;
        armButton.active = live != null && live.departure() >= CLOCK.now(System.nanoTime());
        armButton.setMessage(Component.translatable(reminder == null ? "mtrnav.reminder_arm" : isCurrent() ? "mtrnav.reminder_update" : "mtrnav.reminder_replace"));
        cancelButton.setMessage(Component.translatable(isCurrent() ? "mtrnav.reminder_cancel" : "mtrnav.back"));
        leadLabel.setMessage(Component.translatable("mtrnav.reminder_lead", minutes));
        displayButton.selected=NavigationPreferences.reminderExpanded();
        displayButton.setMessage(Component.translatable("mtrnav.reminder_display_mode", tr(NavigationPreferences.reminderExpanded() ? "mtrnav.reminder_display_expanded" : "mtrnav.reminder_display_auto")));
    }
    @Override public void tick() { refresh(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void removed() { stopBoard(); super.removed(); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        mouseX=OreReview.mouseX(mouseX);mouseY=OreReview.mouseY(mouseY);
        renderBackground(g, mouseX, mouseY, delta); refresh(); frame(g, left, top, panelWidth, panelHeight);
        icon(g, left + 12, top + 6, 16); text(g, title.getString(), left + 36, top + 10, panelWidth - 70, headerInk());
        railDivider(g, left + 12, top + 38, panelWidth - 24);
        var event = live == null ? selected : live; int x = left + 12, w = panelWidth - 24;
        g.enableScissor(x, top + 44, x + w, Math.max(top + 45, infoBottom));
        text(g, station(stationId) + " · " + route(event.routeId()), x, top + 49 - infoScroll, w, TEXT);
        text(g, "→ " + primary(event.destination()) + " · " + tr("mtrnav.platform", platform(event.platformId())), x, top + 65 - infoScroll, w, MUTED);
        text(g, tr("mtrnav.arrival") + " " + (live == null ? "—" : countdown(event.arrival())) + " · " + tr("mtrnav.departure") + " " + (live == null ? "—" : countdown(event.departure())), x, top + 83 - infoScroll, w, ACCENT);
        text(g, tr(event.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled"), x, top + 99 - infoScroll, w, MUTED);
        text(g, tr("mtrnav.reminder_policy"), x, top + 120 - infoScroll, w, MUTED);
        g.disableScissor();
        String state = live == null ? tr("mtrnav.reminder_unconfirmed") : event.departure() < CLOCK.now(System.nanoTime()) ? tr("mtrnav.reminder_stage.passed")
                : event.arrival() - CLOCK.now(System.nanoTime()) <= minutes * 60_000L ? tr("mtrnav.reminder_immediate") : tr("mtrnav.reminder_keeps_running");
        text(g, state, x, top + panelHeight - 98, w, live == null || event.departure() < CLOCK.now(System.nanoTime()) ? WARNING : MUTED);
        for (var button : buttons) button.render(g, mouseX, mouseY, delta);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left + 12 && x < left + panelWidth - 12 && y >= top + 44 && y < infoBottom) {
            infoScroll = Math.clamp(infoScroll - (int)(vertical * 18), 0, Math.max(0, top + 134 - infoBottom)); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
