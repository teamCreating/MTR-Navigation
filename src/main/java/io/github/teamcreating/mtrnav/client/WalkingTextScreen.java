package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

final class WalkingTextScreen extends Screen {
    record Field(String label, String value, int max) {}
    private final Screen previous; private final List<Field> fields; private final Consumer<List<String>> apply;
    private final List<EditBox> boxes = new ArrayList<>(); private final List<Action> buttons = new ArrayList<>();
    private int left, top, w, h;
    private String error = "";
    WalkingTextScreen(Screen previous, String title, List<Field> fields, Consumer<List<String>> apply) { super(Component.literal(title)); this.previous = previous; this.fields = new ArrayList<>(fields); this.apply = apply; }
    @Override protected void init() {
        for (int i = 0; i < boxes.size(); i++) fields.set(i, new Field(fields.get(i).label(), boxes.get(i).getValue(), fields.get(i).max()));
        boxes.clear(); buttons.clear(); w = Math.min(580, width - 20); h = Math.min(height - 16, 80 + fields.size() * 43); left = (width - w) / 2; top = (height - h) / 2;
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i); var box = new EditBox(font, left + 12, top + 40 + i * 43, w - 24, 20, Component.literal(f.label()));
            box.setMaxLength(f.max()); box.setValue(f.value()); boxes.add(addRenderableWidget(box));
        }
        buttons.add(addRenderableWidget(new Action(left + 12, top + h - 28, (w - 28) / 2, 20, NavigationClient.tr("mtrnav.walk_choose"), () -> {
            try { apply.accept(boxes.stream().map(EditBox::getValue).toList()); } catch (RuntimeException e) { WalkingClient.message = error = "invalid_connection"; }
        })));
        buttons.add(addRenderableWidget(new Action(left + 16 + (w - 28) / 2, top + h - 28, (w - 28) / 2, 20, NavigationClient.tr("mtrnav.back"), this::onClose)));
        if (!boxes.isEmpty()) setInitialFocus(boxes.getFirst());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        renderBackground(g, x, y, delta); frame(g, left, top, w, h); text(g, title.getString(), left + 12, top + 10, w - 24, TEXT);
        for (int i = 0; i < boxes.size(); i++) { text(g, fields.get(i).label(), left + 12, boxes.get(i).getY() - 11, w - 24, MUTED); boxes.get(i).render(g, x, y, delta); }
        if (!error.isEmpty()) text(g, NavigationClient.tr("mtrnav.walk_message." + error), left + 12, top + h - 42, w - 24, WARNING);
        for (var b : buttons) b.render(g, x, y, delta);
    }
}
