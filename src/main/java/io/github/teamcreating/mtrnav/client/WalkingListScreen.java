package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Independently scrollable book pages, only visible rows are drawn or hit-tested. */
class WalkingListScreen extends Screen {
    record Item(String id, String label, String detail, Runnable action) {}
    record Tool(Supplier<String> label, Runnable action) { Tool(String label, Runnable action) { this(() -> label, action); } }
    final Screen previous;
    final List<Tool> tools = new ArrayList<>();
    Supplier<List<Item>> items;
    private final List<Action> buttons = new ArrayList<>();
    private EditBox search;
    private String filter = "", selected = "", lastId = "";
    private long lastClick;
    private int left, top, w, h, listTop, bottom, scroll;
    private boolean dragging;
    private double grab;
    private ScrollBarGeometry bar;
    WalkingListScreen(Screen previous, String title, Supplier<List<Item>> items) { super(Component.literal(title)); this.previous = previous; this.items = items; }
    Item selected() { return filtered().stream().filter(i -> i.id().equals(selected)).findFirst().orElse(null); }
    private List<Item> filtered() { String needle = filter.toLowerCase(Locale.ROOT); return items.get().stream().filter(i -> (i.label() + " " + i.detail()).toLowerCase(Locale.ROOT).contains(needle)).toList(); }
    @Override protected void init() {
        buttons.clear(); dragging = false;
        w = Math.min(width - 20, 720); h = Math.min(height - 16, 430); left = (width - w) / 2; top = (height - h) / 2;
        int rows = (tools.size() + 3) / 4, bw = (w - 36) / 4;
        for (int i = 0; i < tools.size(); i++) {
            var tool = tools.get(i); var b = new Action(left + 12 + (i % 4) * (bw + 4), top + 31 + (i / 4) * 23, bw, 20, tool.label().get(), tool.action());
            buttons.add(addRenderableWidget(b));
        }
        search = new EditBox(font, left + 12, top + 34 + rows * 23, w - 24, 20, Component.translatable("mtrnav.directory_search"));
        search.setMaxLength(128); search.setValue(filter); search.setResponder(s -> { filter = s; scroll = 0; }); addRenderableWidget(search);
        listTop = search.getY() + 26; bottom = top + h - 39;
        buttons.add(addRenderableWidget(new Action(left + w - 88, top + h - 29, 76, 20, NavigationClient.tr("mtrnav.back"), this::onClose)));
        setInitialFocus(search);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void tick() { WalkingClient.tick(true); }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta); frame(g, left, top, w, h); icon(g, left + 10, top + 7, 16);
        text(g, title.getString(), left + 31, top + 11, w - 50, TEXT); railDivider(g, left + 12, top + 27, w - 24);
        var values = filtered(); int viewport = Math.max(0, bottom - listTop); scroll = Math.clamp(scroll, 0, Math.max(0, values.size() * 32 - viewport)); bar = null;
        if (viewport > 0) {
            g.enableScissor(left + 12, listTop, left + w - 12, bottom);
            for (int i = scroll / 32; i < values.size() && listTop + i * 32 - scroll < bottom; i++) {
                var item = values.get(i); int y = listTop + i * 32 - scroll;
                boolean hover = mx >= left + 12 && mx < left + w - 22 && my >= Math.max(y, listTop) && my < Math.min(bottom, y + 32);
                g.fill(left + 12, y, left + w - 20, y + 31, item.id().equals(selected) ? 0xFF4A493A : hover ? BORDER : RAISED);
                text(g, item.label(), left + 20, y + 4, w - 48, TEXT); text(g, item.detail(), left + 20, y + 18, w - 48, MUTED);
            }
            if (values.isEmpty()) text(g, NavigationClient.tr("mtrnav.directory_empty"), left + 20, listTop + 8, w - 40, MUTED);
            g.disableScissor();
            if (values.size() * 32 > viewport) {
                bar = ScrollBarGeometry.of(listTop, viewport, values.size() * 32, viewport, scroll, 8);
                g.fill(left + w - 16, listTop, left + w - 12, bottom, BORDER); g.fill(left + w - 16, bar.thumbTop(), left + w - 12, bar.thumbTop() + bar.thumbHeight(), ACCENT);
            }
        }
        text(g, NavigationClient.tr("mtrnav.walk_message." + (WalkingClient.message.isEmpty() ? "ready" : WalkingClient.message)), left + 12, top + h - 22, w - 112, MUTED);
        for (int i = 0; i < tools.size(); i++) buttons.get(i).setMessage(Component.literal(tools.get(i).label().get()));
        search.render(g, mx, my, delta); for (var button : buttons) button.render(g, mx, my, delta);
        if (mx >= left + 12 && mx < left + w - 22 && my >= listTop && my < bottom) {
            int index = (my - listTop + scroll) / 32;
            if (index >= 0 && index < values.size()) g.renderTooltip(font, font.split(Component.literal(values.get(index).label() + "\n" + values.get(index).detail()), Math.min(320, width - 30)), mx, my);
        }
    }
    private void activate() { var item = selected(); if (item != null && item.action() != null) item.action().run(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && bar != null && x >= left + w - 22 && x <= left + w - 10 && y >= listTop && y < bottom) {
            dragging = true; grab = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0; scroll = bar.offsetAtThumbTop(y - grab); return true;
        }
        if (button == 0 && x >= left + 12 && x < left + w - 22 && y >= listTop && y < bottom) {
            var values = filtered(); int i = (int)((y - listTop + scroll) / 32);
            if (i < values.size()) {
                selected = values.get(i).id(); long now = System.nanoTime(); boolean twice = selected.equals(lastId) && now - lastClick < 300_000_000L;
                lastId = selected; lastClick = now; setFocused(null); if (twice) activate(); return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { if (dragging && b == 0) { if (bar != null) scroll = bar.offsetAtThumbTop(y - grab); return true; } return super.mouseDragged(x, y, b, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int b) { if (b == 0 && dragging) { dragging = false; return true; } return super.mouseReleased(x, y, b); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { if (x >= left && x < left + w && y >= listTop && y < bottom) { scroll -= (int)(vertical * 32); return true; } return super.mouseScrolled(x, y, horizontal, vertical); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { if (!(getFocused() instanceof Action)) { activate(); return true; } }
        if (key == 264 || key == 265) {
            var values = filtered(); if (values.isEmpty()) return true;
            int index = -1; for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(selected)) index = i;
            index = Math.clamp(index + (key == 264 ? 1 : -1), 0, values.size() - 1); selected = values.get(index).id();
            if (index * 32 < scroll) scroll = index * 32; else if (index * 32 + 32 > scroll + bottom - listTop) scroll = index * 32 + 32 - (bottom - listTop); return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    static void choice(Screen previous, String title, List<Item> items) {
        var screen = new WalkingListScreen(previous, title, () -> items);
        screen.tools.add(new Tool(NavigationClient.tr("mtrnav.walk_choose"), screen::activate)); MinecraftAccess.open(screen);
    }
    private static final class MinecraftAccess { static void open(Screen screen) { net.minecraft.client.Minecraft.getInstance().setScreen(screen); } }
}
