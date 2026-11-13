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
class WalkingListScreen extends NavigationPanel {
    record Item(String id, String label, String detail, Runnable action) {}
    record Tool(Supplier<String> label, Runnable action) { Tool(String label, Runnable action) { this(() -> label, action); } }
    final Screen previous;
    final List<Tool> tools = new ArrayList<>();
    final List<Tool> tabs = new ArrayList<>(), footerTools = new ArrayList<>();
    int activeTab = -1;
    String context = "", emptyMessage = "";
    private final List<Action> tabButtons = new ArrayList<>();
    private Action openButton;
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
    void resetList() { filter = selected = lastId = ""; scroll = 0; lastClick = 0; }
    Item selected() { return filtered().stream().filter(i -> i.id().equals(selected)).findFirst().orElse(null); }
    private List<Item> filtered() { String needle = filter.toLowerCase(Locale.ROOT); return items.get().stream().filter(i -> (i.label() + " " + i.detail()).toLowerCase(Locale.ROOT).contains(needle)).toList(); }
    @Override protected void init() {
        super.init();
        buttons.clear(); tabButtons.clear(); dragging = false; openButton = null;
        w = Math.min(width - 20, 720); h = Math.min(height - 16, 430); left = (width - w) / 2; top = (height - h) / 2;
        int tabRows = tabs.isEmpty() ? 0 : 1;
        int tabColumns = Math.max(1, tabs.size());
        int tabWidth = (w - 24 - (tabColumns - 1) * 4) / tabColumns;
        for (int i = 0; i < tabs.size(); i++) {
            var t = tabs.get(i);
            var b = new Action(left + 12 + (i % tabColumns) * (tabWidth + 4), top + 39 + (i / tabColumns) * 23, tabWidth, 20, t.label().get(), t.action());
            b.tab = true; tabButtons.add(addRenderableWidget(b));
        }
        int toolTop = top + 39 + tabRows * 23 + (tabs.isEmpty() || h<300 ? 0 : 16);
        int rows = (tools.size() + 3) / 4, bw = (w - 36) / 4;
        for (int i = 0; i < tools.size(); i++) {
            var tool = tools.get(i); var b = new Action(left + 12 + (i % 4) * (bw + 4), toolTop + (i / 4) * 23, bw, 20, tool.label().get(), tool.action());
            if (tool.label().get().equals(NavigationClient.tr("mtrnav.walk_details"))) openButton = b;
            buttons.add(addRenderableWidget(b));
        }
        search = new TextField(font, left + 12, toolTop + 3 + rows * 23, w - 24, 20, Component.translatable("mtrnav.directory_search"));
        search.setHint(Component.translatable(tabs.isEmpty() ? "mtrnav.directory_search" : "mtrnav.walk_layout.search"));
        search.setMaxLength(128); search.setValue(filter); search.setResponder(s -> { filter = s; scroll = 0; }); addRenderableWidget(search);
        listTop = search.getY() + (tabs.isEmpty() || h<300 ? 26 : 40); bottom = top + h - (footerTools.isEmpty() ? 39 : 62);
        int fw = Math.min(116, (w - 112) / Math.max(1, footerTools.size()));
        for (int i = 0; i < footerTools.size(); i++) {
            var t = footerTools.get(i);
            buttons.add(addRenderableWidget(new Action(left + 12 + i * (fw + 4), top + h - 52, fw, 20, t.label().get(), t.action())));
        }
        buttons.add(addRenderableWidget(new Action(left + w - 88, top + h - 29, 76, 20, NavigationClient.tr("mtrnav.back"), this::onClose)));
        setInitialFocus(search);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void tick() { WalkingClient.tick(true); }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        mx=OreReview.mouseX(mx);my=OreReview.mouseY(my);
        renderBackground(g, mx, my, delta); frame(g, left, top, w, h); icon(g, left + 12, top + 6, 16);
        text(g, title.getString(), left + 36, top + 10, w - 50, headerInk()); railDivider(g, left + 12, top + 35, w - 24);
        var values = filtered(); int viewport = Math.max(0, bottom - listTop); scroll = Math.clamp(scroll, 0, Math.max(0, values.size() * 32 - viewport)); bar = null;
        if (!tabs.isEmpty()) {
            if(h>=300)text(g, context, left + 12, top + 65, w - 24, MUTED);
            if(h>=300)text(g, NavigationClient.tr("mtrnav.walk_layout.count", values.size()), left + 12, listTop - 12, w - 24, MUTED);
        }
        if (viewport > 0) {
            recess(g, left + 10, listTop - 2, w - 20, viewport + 4);
            g.enableScissor(left + 12, listTop, left + w - 12, bottom);
            for (int i = scroll / 32; i < values.size() && listTop + i * 32 - scroll < bottom; i++) {
                var item = values.get(i); int y = listTop + i * 32 - scroll;
                boolean hover = mx >= left + 12 && mx < left + w - 22 && my >= Math.max(y, listTop) && my < Math.min(bottom, y + 32);
                boolean chosen = item.id().equals(selected);
                listRow(g, left + 12, y, w - 32, 31, chosen, hover, true);
                text(g, item.label(), left + 32, y + 5, w - 60, TEXT); text(g, item.detail(), left + 32, y + 18, w - 60, chosen ? TEXT : MUTED);
            }
            if (values.isEmpty()) text(g, emptyMessage.isEmpty() ? NavigationClient.tr("mtrnav.directory_empty") : emptyMessage, left + 20, listTop + 8, w - 40, MUTED);
            g.disableScissor();
            if (values.size() * 32 > viewport) {
                bar = ScrollBarGeometry.of(listTop, viewport, values.size() * 32, viewport, scroll, 8);
                g.fill(left + w - 16, listTop, left + w - 12, bottom, BORDER); g.fill(left + w - 16, bar.thumbTop(), left + w - 12, bar.thumbTop() + bar.thumbHeight(), ACCENT);
            }
        }
        text(g, NavigationClient.tr("mtrnav.walk_message." + (WalkingClient.message.isEmpty() ? "ready" : WalkingClient.message)), left + 12, top + h - 22, w - 112, MUTED);
        for (int i = 0; i < tools.size(); i++) buttons.get(i).setMessage(Component.literal(tools.get(i).label().get()));
        for (int i = 0; i < footerTools.size(); i++) buttons.get(tools.size() + i).setMessage(Component.literal(footerTools.get(i).label().get()));
        for (int i = 0; i < tabs.size(); i++) { tabButtons.get(i).selected = i == activeTab; tabButtons.get(i).setMessage(Component.literal(tabs.get(i).label().get())); tabButtons.get(i).render(g, mx, my, delta); }
        if (openButton != null) openButton.active = selected() != null;
        search.render(g, mx, my, delta); for (var button : buttons) { button.onLight = button.getY() >= top + h - 38; button.render(g, mx, my, delta); }
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
