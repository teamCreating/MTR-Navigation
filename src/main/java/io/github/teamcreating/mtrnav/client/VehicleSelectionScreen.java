package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.Model.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/** Connection-local selection. No resource-pack names or settings are uploaded. */
final class VehicleSelectionScreen extends NavigationPanel {
    private final Screen previous;
    private final Set<String> selected = new LinkedHashSet<>();
    private VehicleMode mode;
    private String search = "";
    private int left, top, w, h, bodyTop, bottom, scroll, total;
    private io.github.teamcreating.mtrnav.core.ScrollBarGeometry bar;
    private boolean dragging;
    private double grab;
    private final Map<String, Action> rows = new LinkedHashMap<>();
    private final List<Action> modes = new ArrayList<>();
    private TextField field;
    private Action apply, clear, back;
    VehicleSelectionScreen(Screen previous) {
        super(Component.translatable("mtrnav.vehicle_filter")); this.previous = previous;
        selected.addAll(vehicleCondition.models()); mode = vehicleCondition.mode();
    }
    @Override protected void init() {
        super.init(); rows.clear(); modes.clear(); VehicleNames.refresh();
        w = Math.min(600, width - 20); h = Math.min(420, height - 16);
        left = (width - w) / 2; top = (height - h) / 2; bodyTop = top + 106; bottom = top + h - 58;
        int bw = (w - 32) / 3;
        for (var value : VehicleMode.values()) {
            var b = addRenderableWidget(new Action(left + 12 + value.ordinal() * (bw + 4), top + 40, bw, 20,
                    tr("mtrnav.vehicle_mode." + value.name().toLowerCase(Locale.ROOT)), () -> mode = value));
            b.segment = true; modes.add(b);
        }
        field = addRenderableWidget(new TextField(font, left + 12, top + 70, w - 24, 20, Component.translatable("mtrnav.search")));
        field.setMaxLength(256); field.setValue(search); field.setHint(Component.translatable("mtrnav.vehicle_search"));
        field.setTooltip(Tooltip.create(Component.translatable("mtrnav.vehicle_search")));
        field.setResponder(value -> { search = value; scroll = 0; });
        var ids = new TreeSet<>(fleet); ids.addAll(selected);
        for (String id : ids) {
            var b = addRenderableWidget(new Action(left + 12, bodyTop, w - 32, 30, "", () -> {
                if (!selected.remove(id) && selected.size() < 16) selected.add(id);
                if (!selected.isEmpty() && mode == VehicleMode.NONE) mode = VehicleMode.PREFER;
            })); b.checkbox = true; b.extra = id;
            var info = VehicleNames.info(id);
            b.setTooltip(Tooltip.create(Component.literal(info.name() + "\n" + id + "\n" + info.description() + "\n" + info.tags())));
            rows.put(id, b);
        }
        int fw = (w - 32) / 3;
        clear = addRenderableWidget(new Action(left + 12, top + h - 30, fw, 20, tr("mtrnav.vehicle_clear"), () -> { selected.clear(); mode = VehicleMode.NONE; }));
        back = addRenderableWidget(new Action(left + 16 + fw, top + h - 30, fw, 20, tr("mtrnav.back"), this::onClose));
        apply = addRenderableWidget(new Action(left + 20 + fw * 2, top + h - 30, fw, 20, tr("mtrnav.vehicle_apply"), () -> {
            vehicleCondition(new VehicleCondition(mode, List.copyOf(selected))); onClose();
        })); apply.primary = true; setInitialFocus(field);
    }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        mx = OreReview.mouseX(mx); my = OreReview.mouseY(my);
        renderBackground(g, mx, my, delta); frame(g, left, top, w, h);
        text(g, title.getString(), left + 12, top + 10, w - 24, headerInk());
        for (int i = 0; i < modes.size(); i++) { var b = modes.get(i); b.selected = mode.ordinal() == i; b.active = supportsVehicles() || i == 0; b.render(g, mx, my, delta); }
        field.render(g, mx, my, delta);
        if (search.isEmpty() && field.isFocused()) text(g, tr("mtrnav.vehicle_search"), left + 18, top + 76, w - 36, FAINT);
        String query = search.toLowerCase(Locale.ROOT);
        var visible = rows.keySet().stream().filter(id -> {
            var info = VehicleNames.info(id); return (id + " " + info.name() + " " + info.description() + " " + info.tags()).toLowerCase(Locale.ROOT).contains(query);
        }).toList(); total = visible.size() * 34;
        scroll = Math.clamp(scroll, 0, Math.max(0, total - Math.max(0, bottom - bodyTop)));
        rows.values().forEach(b -> b.visible = false);
        lining(g, left + 10, bodyTop - 4, w - 20, Math.max(0, bottom - bodyTop + 4));
        if (bottom > bodyTop) {
            g.enableScissor(left + 10, bodyTop, left + w - 10, bottom);
            for (int i = 0; i < visible.size(); i++) {
                String id = visible.get(i); var b = rows.get(id); int y = bodyTop + i * 34 - scroll;
                b.setY(y); b.visible = y >= bodyTop && y + 30 <= bottom; b.selected = selected.contains(id);
                b.active = supportsVehicles() && (b.selected || selected.size() < 16);
                b.setMessage(Component.literal(VehicleNames.info(id).name())); if (b.visible) b.render(g, mx, my, delta);
            }
            g.disableScissor();
            bar = null;
            if (total > bottom - bodyTop) {
                bar = io.github.teamcreating.mtrnav.core.ScrollBarGeometry.of(bodyTop, bottom - bodyTop, total, bottom - bodyTop, scroll, 10);
                g.fill(left + w - 16, bodyTop, left + w - 12, bottom, BORDER);
                g.fill(left + w - 16, bar.thumbTop(), left + w - 12, bar.thumbTop() + bar.thumbHeight(), ACCENT);
            }
        }
        if (visible.isEmpty()) text(g, tr("mtrnav.vehicle_empty"), left + 18, bodyTop + 8, w - 36, MUTED);
        text(g, supportsVehicles() ? tr("mtrnav.vehicle_rule", selected.size()) : tr("mtrnav.vehicle_unsupported"), left + 12, bottom + 8, w - 24, MUTED);
        apply.setTooltip(Tooltip.create(Component.literal(tr("mtrnav.vehicle_rule", selected.size()) + "\n" + tr("mtrnav.vehicle_prefer_hint"))));
        apply.active = supportsVehicles() || mode == VehicleMode.NONE;
        clear.render(g, mx, my, delta); back.render(g, mx, my, delta); apply.render(g, mx, my, delta);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= bodyTop && y < bottom) { scroll = Math.clamp(scroll - (int)(vertical * 34), 0, Math.max(0, total - (bottom - bodyTop))); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && bar != null && x >= left + w - 20 && x < left + w - 8 && y >= bodyTop && y < bottom) {
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
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Math.clamp(scroll + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(34, bottom - bodyTop), 0, Math.max(0, total - (bottom - bodyTop))); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

}
