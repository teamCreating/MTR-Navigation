package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Secondary navigation tools; return to the same query without clearing its inputs. */
final class NavigationMenuScreen extends NavigationPanel {
    private final Screen previous;
    private final Runnable showDepartures;
    private final List<Action> buttons = new ArrayList<>();
    private int left, top, w, h;
    NavigationMenuScreen(Screen previous, Runnable showDepartures) {
        super(Component.translatable("mtrnav.more")); this.previous = previous; this.showDepartures = showDepartures;
    }
    @Override protected void init() {
        super.init();
        buttons.clear(); w = Math.min(width - 20, 360); h = Math.min(height - 16, 274);
        left = (width - w) / 2; top = (height - h) / 2;
        var reminders = new Action(left + 14, top + 47, w - 28, 22, tr("mtrnav.reminder_short"), () -> {
            if (reminder == null) { onClose(); showDepartures.run(); }
            else minecraft.setScreen(new ReminderSettingsScreen(this, reminder.stationId(), reminder.event(), reminder.minutes()));
        });
        reminders.primary = reminder != null; buttons.add(addRenderableWidget(reminders));
        buttons.add(addRenderableWidget(new Action(left + 14, top + 108, w - 28, 22, tr("mtrnav.hud_settings"),
                () -> minecraft.setScreen(new HudSettingsScreen(this)))));
        buttons.add(addRenderableWidget(new Action(left + w - 94, top + h - 30, 80, 20, tr("mtrnav.back"), this::onClose)));
        buttons.add(addRenderableWidget(new Action(left + 14, top + 164, w - 28, 22, tr("mtrnav.vehicle_filter"),
                () -> minecraft.setScreen(new VehicleSelectionScreen(this)))));
        setInitialFocus(reminders);
    }
    private void rebuildMenu(){clearWidgets();init();}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        mx=OreReview.mouseX(mx);my=OreReview.mouseY(my);
        renderBackground(g, mx, my, delta); frame(g, left, top, w, h); icon(g, left + 12, top + 6, 16);
        text(g, title.getString(), left + 36, top + 10, w - 48, headerInk()); railDivider(g, left + 12, top + 37, w - 24);
        paragraph(g, tr(reminder == null ? "mtrnav.more_reminder_empty" : "mtrnav.more_reminder_active"), top + 75);
        paragraph(g, tr("mtrnav.more_hud_hint"), top + 136);
        paragraph(g, tr("mtrnav.ore_appearance"), top + 202);
        for (var b : buttons) { b.onLight = b.getY() >= top + h - 38; b.render(g, mx, my, delta); }
    }
    private void paragraph(GuiGraphics g, String value, int y) {
        for (var line : font.split(Component.literal(value), w - 32)) {
            g.drawString(font, line, left + 16, y, MUTED, false); y += 11;
        }
    }
}
