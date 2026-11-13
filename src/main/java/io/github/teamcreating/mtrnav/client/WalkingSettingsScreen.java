package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;
import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Query preferences are separate from connection records and moderation tools. */
final class WalkingSettingsScreen extends NavigationPanel {
    private final Screen previous;
    private int left, top, w, h, scroll, contentHeight, bodyTop, bottom;
    private final List<Action> choices = new ArrayList<>();
    private Action consent, done;
    private ScrollBarGeometry bar;
    private boolean dragging;
    private double grab;
    WalkingSettingsScreen(Screen previous) { super(Component.translatable("mtrnav.walk_layout.settings")); this.previous = previous; }
    @Override protected void init() {
        super.init();
        choices.clear(); dragging = false;
        w = Math.min(620, width - 20); h = Math.min(360, height - 16);
        left = (width - w) / 2; top = (height - h) / 2; bodyTop = top + 46; bottom = top + h - 38;
        consent = addRenderableWidget(new Action(left + 20, bodyTop + 64, w - 40, 20, "", () -> {
            if (WalkingClient.consent()) WalkingClient.consent(false);
            else WalkingManagementScreen.confirmConsent(this, () -> {});
        }));
        consent.toggle = true;
        int bw = (w - 52) / 4;
        for (int i = 0; i < 4; i++) {
            final int value = new int[]{15, 30, 60, -1}[i];
            choices.add(addRenderableWidget(new Action(left + 20 + i * (bw + 4), 0, bw, 20,
                    value < 0 ? tr("mtrnav.walk_unlimited") : tr("mtrnav.walk_layout.minutes", value), () -> WalkingClient.setWindow(value))));
        }
        for(var choice:choices)choice.segment=true;
        done = addRenderableWidget(new Action(left + w - 100, top + h - 28, 88, 20, tr("mtrnav.back"), this::onClose)); done.onLight = true;
    }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { WalkingClient.tick(true); }
    private int paragraph(GuiGraphics g, String key, int y) {
        for (var line : font.split(Component.translatable(key), w - 48)) {
            g.drawString(font, line, left + 24, y - scroll, MUTED, false); y += 12;
        }
        return y;
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        mx=OreReview.mouseX(mx);my=OreReview.mouseY(my);
        renderBackground(g, mx, my, delta); frame(g, left, top, w, h); icon(g, left + 12, top + 6, 16);
        text(g, title.getString(), left + 36, top + 10, w - 48, headerInk());
        railDivider(g, left + 12, top + 37, w - 24);
        g.enableScissor(left + 12, bodyTop, left + w - 12, bottom);
        int second = bodyTop + 80 + 12 * (lines("mtrnav.walk_layout.privacy") + lines("mtrnav.walk_layout.local_privacy"));
        int end = second + 64 + 12 * (lines("mtrnav.walk_layout.window_hint") + lines("mtrnav.walk_layout.window_changed"));
        card(g, bodyTop - scroll, second - 8 - scroll);
        card(g, second - 4 - scroll, end - scroll);
        int y = bodyTop + 8;
        text(g, tr("mtrnav.walk_layout.privacy_title"), left + 20, y - scroll, w - 40, ACCENT);
        y = paragraph(g, "mtrnav.walk_layout.privacy", y + 18) + 8;
        consent.selected=WalkingClient.consent(); consent.setY(y - scroll); consent.setMessage(Component.literal(tr("mtrnav.walk_consent", tr(WalkingClient.consent() ? "mtrnav.on" : "mtrnav.off"))));
        consent.visible = consent.getY() >= bodyTop && consent.getY() + 20 <= bottom;
        if (consent.visible) consent.render(g, mx, my, delta);
        y = paragraph(g, "mtrnav.walk_layout.local_privacy", y + 28) + 18;
        text(g, tr("mtrnav.walk_layout.window_title"), left + 20, y - scroll, w - 40, ACCENT);
        y = paragraph(g, "mtrnav.walk_layout.window_hint", y + 18) + 8;
        for (int i = 0; i < choices.size(); i++) {
            var b = choices.get(i); b.setY(y - scroll); b.selected = WalkingClient.options().extraMinutes() == new int[]{15, 30, 60, -1}[i];
            b.visible = b.getY() >= bodyTop && b.getY() + 20 <= bottom;
            if (b.visible) b.render(g, mx, my, delta);
        }
        y = paragraph(g, "mtrnav.walk_layout.window_changed", y + 30);
        contentHeight = y + 8 - bodyTop;
        g.disableScissor();
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - bodyTop)));
        bar = contentHeight > bottom - bodyTop ? ScrollBarGeometry.of(bodyTop, bottom - bodyTop, contentHeight, bottom - bodyTop, scroll, 12) : null;
        if (bar != null) {
            g.fill(left + w - 17, bodyTop, left + w - 13, bottom, BORDER);
            g.fill(left + w - 17, bar.thumbTop(), left + w - 13, bar.thumbTop() + bar.thumbHeight(), ACCENT);
        }
        text(g, tr("mtrnav.walk_message." + (WalkingClient.message.isEmpty() ? "ready" : WalkingClient.message)), left + 12, top + h - 22, w - 124, MUTED);
        done.render(g, mx, my, delta);
    }
    private int lines(String key) { return font.split(Component.translatable(key), w - 48).size(); }
    private void card(GuiGraphics g, int y, int end) {
        g.fill(left + 12, y, left + w - 24, end, RAISED);
        outline(g, left + 12, y, w - 36, end - y, BORDER);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x < left + w && y >= bodyTop && y < bottom) {
            scroll = Math.clamp(scroll - (int)(vertical * 24), 0, Math.max(0, contentHeight - (bottom - bodyTop))); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && bar != null && x >= left + w - 22 && x < left + w - 9 && y >= bodyTop && y < bottom) {
            dragging = true; grab = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0;
            scroll = bar.offsetAtThumbTop(y - grab); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0 && bar != null) { scroll = bar.offsetAtThumbTop(y - grab); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging && button == 0) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
}
