package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;
import io.github.teamcreating.mtrnav.client.WalkingListScreen.Item;

/** A direct form: select two endpoints, enter a duration, then choose where to save. */
final class WalkingEditorScreen extends Screen {
    private final Screen previous;
    private final WalkingLibrary.Proposal proposal;
    private String id, group, dimension, from = "", to = "", fromPlatform = "", toPlatform = "", name = "", description = "", minutes = "", seconds = "";
    private boolean twoWay, enabled = true, dragging;
    private String error = "";
    private int left, top, w, h, bodyTop, bottom, scroll;
    private double grab;
    private ScrollBarGeometry bar;
    private record Control(AbstractWidget widget, int offset) {}
    private final List<Control> controls = new ArrayList<>();
    private final List<Action> footer = new ArrayList<>();
    private Action fromButton, toButton, fromPlatformButton, toPlatformButton, directionButton, enabledButton, saveButton, shareButton;
    private static final int CONTENT_HEIGHT = 312;

    WalkingEditorScreen(Screen previous, WalkingConnection value, WalkingLibrary.Proposal proposal) {
        super(Component.translatable("mtrnav.walk_edit")); this.previous = previous; this.proposal = proposal;
        if (value == null) {
            id = UUID.randomUUID().toString(); group = UUID.randomUUID().toString(); var pos = playerPosition(); dimension = pos == null ? "" : pos.dimension(); twoWay = true;
        } else {
            id = value.id(); group = value.groupId(); dimension = value.dimension(); from = value.fromStation(); to = value.toStation();
            fromPlatform = value.fromPlatform(); toPlatform = value.toPlatform(); minutes = Integer.toString(value.seconds() / 60); seconds = Integer.toString(value.seconds() % 60);
            name = value.name(); description = value.description(); enabled = value.enabled();
        }
    }
    @Override protected void init() {
        controls.clear(); footer.clear(); dragging = false;
        w = Math.min(580, width - 16); h = Math.min(432, height - 16); left = (width - w) / 2; top = (height - h) / 2;
        bodyTop = top + 48; bottom = top + h - 58; int x = left + 14, full = w - 38;
        fromButton = button(x, 12, full, "", () -> stations(true));
        fromPlatformButton = button(x, 36, full, "", () -> platforms(true));
        toButton = button(x, 76, full, "", () -> stations(false));
        toPlatformButton = button(x, 100, full, "", () -> platforms(false));
        var mins = box(x, 140, 55, "mtrnav.walk_form.minutes", minutes, 2, s -> minutes = s, true);
        var secs = box(x + 86, 140, 55, "mtrnav.walk_form.seconds", seconds, 2, s -> seconds = s, true);
        mins.setHint(Component.literal("0")); secs.setHint(Component.literal("0"));
        box(x, 196, full, "mtrnav.walk_form.name", name, 48, s -> name = s, false);
        box(x, 236, full, "mtrnav.walk_form.description", description, 256, s -> description = s, false);
        directionButton = button(x, 264, full, "", () -> twoWay = !twoWay);
        enabledButton = button(x, 288, full, "", () -> enabled = !enabled);
        int y = top + h - 29, bw = (w - 36) / 3;
        saveButton = new Action(left + 12, y, bw, 20, tr(proposal == null ? "mtrnav.walk_form.save" : "mtrnav.walk_amend"), this::save);
        saveButton.primary = true; footer.add(addRenderableWidget(saveButton));
        shareButton = new Action(left + 16 + bw, y, bw, 20, tr("mtrnav.walk_submit"), this::share);
        shareButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.walk_form.share_hint"))); footer.add(addRenderableWidget(shareButton));
        footer.add(addRenderableWidget(new Action(left + 20 + bw * 2, y, bw, 20, tr("mtrnav.back"), this::onClose)));
        layout(); setInitialFocus(from.isEmpty() ? fromButton : to.isEmpty() ? toButton : mins);
    }
    private Action button(int x, int offset, int width, String label, Runnable action) {
        var b = new Action(x, bodyTop + offset, width, 20, label, action); controls.add(new Control(addRenderableWidget(b), offset)); return b;
    }
    private EditBox box(int x, int offset, int width, String label, String value, int max, Consumer<String> changed, boolean numeric) {
        var b = new EditBox(font, x, bodyTop + offset, width, 20, Component.translatable(label)); b.setMaxLength(max);
        if (numeric) b.setFilter(s -> s.matches("[0-9]*"));
        b.setValue(value); b.setResponder(s -> { changed.accept(s); error = ""; }); controls.add(new Control(addRenderableWidget(b), offset)); return b;
    }
    private int duration() {
        if (minutes.isBlank() && seconds.isBlank()) throw new IllegalArgumentException();
        int m = minutes.isBlank() ? 0 : Integer.parseInt(minutes), s = seconds.isBlank() ? 0 : Integer.parseInt(seconds);
        int total = m * 60 + s; if (s > 59 || total < 10 || total > 3600) throw new IllegalArgumentException(); return total;
    }
    private String problem() {
        if (catalog == null || !dimension.equals(catalog.dimension())) return "catalog";
        if (from.isEmpty()) return "from";
        if (to.isEmpty()) return "to";
        if (from.equals(to) && fromPlatform.equals(toPlatform)) return "same";
        try { duration(); } catch (RuntimeException e) { return "time"; }
        try { value(); } catch (RuntimeException e) { return "invalid"; }
        return "";
    }
    private WalkingConnection value() {
        var value = new WalkingConnection(id, group, dimension, from, fromPlatform, to, toPlatform, duration(), name, description, enabled);
        value.validate(catalog); return value;
    }
    private void save() {
        error = problem(); if (!error.isEmpty()) return;
        if (proposal != null) {
            WalkingClient.change(new WalkingLibrary.Command("amend", proposal.id(), proposal.revision(), value(), "")); minecraft.setScreen(previous); return;
        }
        try {
            var value = value(); var links = new ArrayList<WalkingConnection>(); links.add(value);
            if (twoWay) {
                var reverse = WalkingClient.local().stream().filter(c -> c.groupId().equals(group) && c.fromStation().equals(to) && c.toStation().equals(from)
                        && c.fromPlatform().equals(toPlatform) && c.toPlatform().equals(fromPlatform)).findFirst().orElse(null);
                links.add(value.reverse(reverse == null ? UUID.randomUUID().toString() : reverse.id()));
            }
            WalkingClient.putLocal(links);
            if (WalkingClient.message.equals("saved")) minecraft.setScreen(previous); else error = "save";
        } catch (RuntimeException e) { error = "invalid"; }
    }
    private void share() {
        error = problem(); if (!error.isEmpty()) return;
        WalkingManagementScreen.submitPreview(this, value(), WalkingClient.entryRevision(id));
    }
    private void layout() {
        scroll = Math.clamp(scroll, 0, Math.max(0, CONTENT_HEIGHT - (bottom - bodyTop)));
        for (var c : controls) { c.widget().setY(bodyTop + c.offset() - scroll); c.widget().visible = c.widget().getY() >= bodyTop && c.widget().getY() + c.widget().getHeight() <= bottom; }
        fromButton.setMessage(Component.literal(from.isEmpty() ? tr("mtrnav.walk_form.choose_from") : station(from) + "  ·  " + tr("mtrnav.walk_form.change")));
        toButton.setMessage(Component.literal(to.isEmpty() ? tr("mtrnav.walk_form.choose_to") : station(to) + "  ·  " + tr("mtrnav.walk_form.change")));
        fromButton.active = toButton.active = catalog != null && dimension.equals(catalog.dimension());
        fromPlatformButton.active = fromButton.active && !from.isEmpty(); toPlatformButton.active = toButton.active && !to.isEmpty();
        fromPlatformButton.setMessage(Component.literal(tr("mtrnav.walk_form.platform", fromPlatform.isEmpty() ? tr("mtrnav.walk_form.any_platform") : platform(fromPlatform))));
        toPlatformButton.setMessage(Component.literal(tr("mtrnav.walk_form.platform", toPlatform.isEmpty() ? tr("mtrnav.walk_form.any_platform") : platform(toPlatform))));
        directionButton.setMessage(Component.literal(tr(twoWay ? "mtrnav.walk_form.both" : "mtrnav.walk_form.one_way")));
        directionButton.active = proposal == null;
        if (proposal != null) directionButton.setMessage(Component.translatable("mtrnav.walk_form.review_direction"));
        enabledButton.setMessage(Component.literal(tr(enabled ? "mtrnav.walk_form.enabled" : "mtrnav.walk_form.disabled")));
        String issue = problem(); saveButton.active = issue.isEmpty() && (proposal == null || WalkingClient.supportsShared());
        shareButton.visible = proposal == null; shareButton.active = issue.isEmpty() && WalkingClient.supportsShared();
        saveButton.setTooltip(Tooltip.create(Component.literal(tr(issue.isEmpty() ? (proposal == null ? "mtrnav.walk_form.save_hint" : "mtrnav.walk_form.review_direction") : "mtrnav.walk_form.error." + issue))));
        shareButton.setTooltip(Tooltip.create(Component.translatable(WalkingClient.supportsShared() ? "mtrnav.walk_form.share_hint" : "mtrnav.walk_message.server_unavailable")));
        bar = CONTENT_HEIGHT > bottom - bodyTop ? ScrollBarGeometry.of(bodyTop, bottom - bodyTop, CONTENT_HEIGHT, bottom - bodyTop, scroll, 12) : null;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { WalkingClient.tick(true); }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        layout(); renderBackground(g, mx, my, delta); frame(g, left, top, w, h); icon(g, left + 12, top + 8, 16);
        text(g, title.getString(), left + 34, top + 12, w - 48, TEXT);
        text(g, tr("mtrnav.walk_form.intro"), left + 14, top + 32, w - 28, MUTED);
        g.enableScissor(left + 10, bodyTop, left + w - 10, bottom);
        label(g, "mtrnav.walk_form.from", 0); label(g, "mtrnav.walk_form.to", 64); label(g, "mtrnav.walk_form.duration", 128);
        label(g, "mtrnav.walk_form.name", 184); label(g, "mtrnav.walk_form.description", 224);
        text(g, tr("mtrnav.walk_form.minutes"), left + 72, bodyTop + 146 - scroll, 25, TEXT);
        text(g, tr("mtrnav.walk_form.seconds"), left + 158, bodyTop + 146 - scroll, 25, TEXT);
        text(g, tr("mtrnav.walk_form.time_hint"), left + 14, bodyTop + 166 - scroll, w - 38, MUTED);
        for (var c : controls) if (c.widget().visible) c.widget().render(g, mx, my, delta);
        g.disableScissor();
        if (bar != null) { g.fill(left + w - 17, bodyTop, left + w - 13, bottom, BORDER); g.fill(left + w - 17, bar.thumbTop(), left + w - 13, bar.thumbTop() + bar.thumbHeight(), ACCENT); }
        railDivider(g, left + 12, bottom + 5, w - 24);
        String issue = error.isEmpty() ? problem() : error;
        text(g, tr(issue.isEmpty() ? (proposal == null ? "mtrnav.walk_form.save_hint" : "mtrnav.walk_form.review_direction") : "mtrnav.walk_form.error." + issue), left + 14, bottom + 13, w - 28, error.isEmpty() ? MUTED : WARNING);
        for (var b : footer) if (b.visible) b.render(g, mx, my, delta);
    }
    private void label(GuiGraphics g, String key, int offset) { text(g, tr(key), left + 14, bodyTop + offset - scroll, w - 38, ACCENT); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x < left + w && y >= bodyTop && y < bottom) { scroll -= (int)(vertical * 28); layout(); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        layout(); if (button == 0 && bar != null && x >= left + w - 22 && x < left + w - 9 && y >= bodyTop && y < bottom) {
            dragging = true; grab = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0; scroll = bar.offsetAtThumbTop(y - grab); layout(); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { if (dragging && button == 0 && bar != null) { scroll = bar.offsetAtThumbTop(y - grab); layout(); return true; } return super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { if (button == 0 && dragging) { dragging = false; return true; } return super.mouseReleased(x, y, button); }
    private void stations(boolean source) {
        if (catalog == null) return;
        WalkingListScreen.choice(this, tr(source ? "mtrnav.walk_form.choose_from" : "mtrnav.walk_form.choose_to"), catalog.stations().stream().sorted(Comparator.comparing(Model.Station::name).thenComparing(Model.Station::id)).map(s -> new Item(s.id(), Model.displayName(s.name()), s.x() + ", " + s.y() + ", " + s.z(), () -> {
            if (source) { from = s.id(); fromPlatform = ""; } else { to = s.id(); toPlatform = ""; } error = ""; Minecraft.getInstance().setScreen(this);
        })).toList());
    }
    private void platforms(boolean source) {
        if (catalog == null) return; String stationId = source ? from : to;
        var choices = new ArrayList<Item>();
        choices.add(new Item("any", tr("mtrnav.walk_form.any_platform"), tr("mtrnav.walk_time_explanation"), () -> { if (source) fromPlatform = ""; else toPlatform = ""; error = ""; Minecraft.getInstance().setScreen(this); }));
        catalog.platforms().stream().filter(p -> p.stationId().equals(stationId) && p.id().matches("[0-9a-f]{1,16}")).sorted(Comparator.comparing(Model.Platform::name).thenComparing(Model.Platform::id)).forEach(p -> choices.add(new Item(p.id(), Model.displayName(p.name()), p.id(), () -> {
            if (source) fromPlatform = p.id(); else toPlatform = p.id(); error = ""; Minecraft.getInstance().setScreen(this);
        })));
        WalkingListScreen.choice(this, tr("mtrnav.platform_label"), choices);
    }
}
