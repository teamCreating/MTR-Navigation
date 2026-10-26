package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;
import io.github.teamcreating.mtrnav.core.StationSearch;
import io.github.teamcreating.mtrnav.core.StationLocator;
import io.github.teamcreating.mtrnav.core.StationDirectoryOrder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** A separate, fully scrollable directory; it never covers the journey screen with suggestions. */
final class StationDirectoryScreen extends Screen {
    enum Target { ORIGIN, DESTINATION, BOARD }
    private static final int ROW_HEIGHT = 32;
    private final NavigationScreen previous;
    private final Target defaultTarget;
    private final List<Action> buttons = new ArrayList<>(), selectionButtons = new ArrayList<>();
    private EditBox searchBox;
    private Action clearButton;
    private Action sortButton, indexButton, relocateButton;
    private final Map<String, Action> indexButtons = new LinkedHashMap<>();
    private Map<String, Long> groupCounts = Map.of();
    private StationDirectoryOrder.Mode order = StationDirectoryOrder.Mode.AZ;
    private boolean inlineIndex;
    private final boolean nearby;
    private StationLocator.Position location;
    private Object locationConnection;
    private Map<String, Double> distances = Map.of();
    private String locationNotice = "";
    private Catalog source;
    private List<Station> matches = List.of();
    private String searchText = "", selectedId = "", lastClickedId = "";
    private int left, top, panelWidth, panelHeight, listTop, listBottom, scroll;
    private long lastClick;
    private boolean dragging;
    private double grabOffset;
    private ScrollBarGeometry bar;

    StationDirectoryScreen(NavigationScreen previous, Target defaultTarget) {
        super(Component.translatable("mtrnav.all_stations")); this.previous = previous; this.defaultTarget = defaultTarget; nearby = false;
    }
    StationDirectoryScreen(NavigationScreen previous, StationLocator.Position location) {
        super(Component.translatable("mtrnav.nearby_stations")); this.previous = previous; defaultTarget = Target.ORIGIN;
        nearby = true; this.location = location; locationConnection = net.minecraft.client.Minecraft.getInstance().getConnection();
        order = StationDirectoryOrder.Mode.DISTANCE;
    }
    private Action button(String key, int x, int y, int width, int height, Runnable action) {
        var button = new Action(x, y, width, height, tr(key), action);
        addRenderableWidget(button); buttons.add(button); return button;
    }
    @Override protected void init() {
        buttons.clear(); selectionButtons.clear(); indexButtons.clear(); dragging = false; bar = null;
        panelWidth = Math.min(width - 20, Math.min(720, Math.max(360, (int)(width * .82))));
        panelHeight = Math.min(height - 16, Math.min(460, Math.max(220, (int)(height * .88))));
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int x = left + 12, inner = panelWidth - 24;
        button("mtrnav.close", left + panelWidth - 26, top + 7, 16, 16, this::onClose);
        searchBox = new EditBox(font, x, top + 38, inner - 26, 20, Component.translatable("mtrnav.directory_search"));
        searchBox.setMaxLength(128); searchBox.setHint(Component.translatable("mtrnav.station_hint")); searchBox.setTextColor(TEXT);
        addRenderableWidget(searchBox); searchBox.setValue(searchText);
        searchBox.setResponder(value -> { searchText = value; filter(); });
        clearButton = button("mtrnav.clear_symbol", x + inner - 20, top + 38, 20, 20, () -> { searchBox.setValue(""); setFocused(searchBox); });
        clearButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.clear_input")));
        inlineIndex = panelWidth >= 500 && panelHeight >= 260;
        listTop = top + (inlineIndex ? 102 : 80); listBottom = top + panelHeight - 48;
        int sortX = x + inner - (nearby ? 182 : 90);
        sortButton = button("mtrnav.directory_sort.az", sortX, top + 61, 90, 16, this::cycleOrder);
        sortButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.directory_sort_hint")));
        if (nearby) relocateButton = button("mtrnav.relocate", x + inner - 88, top + 61, 88, 16, this::relocate);
        if (inlineIndex) {
            int count = StationDirectoryOrder.GROUPS.size();
            for (int i = 0; i < count; i++) {
                String group = StationDirectoryOrder.GROUPS.get(i);
                int start = x + i * inner / count, end = x + (i + 1) * inner / count;
                indexButtons.put(group, button(group, start, top + 81, end - start - 1, 16, () -> jump(group)));
            }
        } else {
            indexButton = button("mtrnav.directory_index", sortX - 48, top + 61, 44, 16, this::openIndex);
            indexButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.directory_index_hint")));
        }
        if (!nearby) relocateButton = button("mtrnav.relocate", sortX - 68 - (inlineIndex ? 0 : 48), top + 61, 64, 16, this::relocate);
        int buttonWidth = nearby ? (inner - 4) / 2 : (inner - 8) / 3;
        for (var target : nearby ? List.of(Target.ORIGIN) : List.of(Target.values())) {
            var action = button(switch (target) {
                case ORIGIN -> "mtrnav.use_as_origin";
                case DESTINATION -> "mtrnav.use_as_destination";
                case BOARD -> "mtrnav.show_board";
            }, x + target.ordinal() * (buttonWidth + 4), listBottom + 12, buttonWidth, 22, () -> apply(target));
            action.primary = target == defaultTarget; selectionButtons.add(action);
        }
        if (nearby) button("mtrnav.back", x + buttonWidth + 4, listBottom + 12, buttonWidth, 22, this::onClose);
        source = NavigationClient.catalog;
        if (!nearby && order == StationDirectoryOrder.Mode.DISTANCE) sampleLocation();
        filter(); setInitialFocus(searchBox);
    }
    private void filter() {
        matches = source == null ? List.of() : StationSearch.matches(source.stations(), searchText);
        if (nearby || order == StationDirectoryOrder.Mode.DISTANCE) {
            var measured = new HashMap<String, Double>();
            if (sameLocationContext()) for (var candidate : StationLocator.nearby(source, NavigationClient.locatedStations(), location))
                measured.put(candidate.station().id(), candidate.distance());
            distances = Map.copyOf(measured);
            if (nearby) matches = matches.stream().filter(s -> distances.containsKey(s.id())).toList();
        }
        matches = StationDirectoryOrder.sort(matches, order, distances);
        var counts = new LinkedHashMap<String, Long>(); for (var s : matches) counts.merge(StationDirectoryOrder.group(s), 1L, Long::sum); groupCounts = Map.copyOf(counts);
        if (matches.stream().noneMatch(s -> s.id().equals(selectedId))) selectedId = matches.size() == 1 ? matches.getFirst().id() : "";
        for (var e : indexButtons.entrySet()) {
            e.getValue().active = groupCounts.containsKey(e.getKey());
            long count = groupCounts.getOrDefault(e.getKey(), 0L);
            e.getValue().setTooltip(Tooltip.create(Component.translatable("mtrnav.directory_index_count", groupLabel(e.getKey()), count)));
        }
        scroll = 0; bar = null; dragging = false; updateButtons();
    }
    private Station selected() { return matches.stream().filter(s -> s.id().equals(selectedId)).findFirst().orElse(null); }
    private void updateButtons() {
        boolean selected = selected() != null;
        for (var button : selectionButtons) button.active = selected;
        if (clearButton != null) clearButton.active = !searchText.isEmpty();
        if (sortButton != null) sortButton.setMessage(Component.translatable("mtrnav.directory_sort." + order.name().toLowerCase(Locale.ROOT)));
        if (relocateButton != null) relocateButton.visible = nearby || order == StationDirectoryOrder.Mode.DISTANCE;

    }
    private void apply(Target target) {
        var station = selected(); if (station == null) return;
        var current = NavigationClient.catalog;
        if (current == null || source == null || !Objects.equals(source.dimension(), current.dimension()) || current.station(station.id()) == null
                || nearby && (!sameLocationContext() || StationLocator.nearby(current, NavigationClient.locatedStations(), location).stream().noneMatch(c -> c.station().id().equals(station.id())))) {
            if (nearby) locationNotice = tr(sameLocationContext() ? "mtrnav.location_catalog_changed" : "mtrnav.location_changed");
            source = current; selectedId = ""; filter(); return;
        }
        if (nearby) previous.useLocatedStation(current.station(station.id()), location);
        else previous.useStation(current.station(station.id()), target);
        onClose();
    }
    private boolean sameLocationContext() {
        var player = NavigationClient.playerPosition();
        return location != null && player != null && player.dimension().equals(location.dimension()) && locationConnection == minecraft.getConnection();
    }
    private void sampleLocation() { location = NavigationClient.playerPosition(); locationConnection = minecraft.getConnection(); }
    private void cycleOrder() {
        order = StationDirectoryOrder.Mode.values()[(order.ordinal() + 1) % StationDirectoryOrder.Mode.values().length];
        if (order == StationDirectoryOrder.Mode.DISTANCE) sampleLocation(); filter();
    }
    private static String groupLabel(String group) { return group.equals("#") ? tr("mtrnav.directory_digits") : group.equals("…") ? tr("mtrnav.directory_other") : group; }
    private void jump(String group) {
        if (order == StationDirectoryOrder.Mode.DISTANCE) { order = StationDirectoryOrder.Mode.AZ; filter(); }
        int index = StationDirectoryOrder.first(matches, group);
        if (index >= 0) { selectedId = matches.get(index).id(); scroll = index * ROW_HEIGHT; clampScroll(); setFocused(null); updateButtons(); }
    }
    private void openIndex() {
        WalkingListScreen.choice(this, tr("mtrnav.directory_index"), StationDirectoryOrder.GROUPS.stream().filter(groupCounts::containsKey).map(group ->
                new WalkingListScreen.Item(group, groupLabel(group), tr("mtrnav.directory_index_count", groupLabel(group), groupCounts.get(group)), () -> {
                    minecraft.setScreen(this); jump(group);
                })).toList());
    }
    private void relocate() {
        var position = NavigationClient.playerPosition();
        if (position == null) { locationNotice = tr("mtrnav.location_no_player"); return; }
        location = position; locationConnection = minecraft.getConnection(); source = NavigationClient.catalog;
        locationNotice = ""; selectedId = ""; filter();
    }
    @Override public void tick() {
        if (nearby && !sameLocationContext()) { locationNotice = tr("mtrnav.location_changed"); if (!matches.isEmpty()) { source = NavigationClient.catalog; filter(); } }
        else if (source != NavigationClient.catalog) {
            source = NavigationClient.catalog;
            if (nearby && !selectedId.isEmpty() && (source == null || source.station(selectedId) == null)) locationNotice = tr("mtrnav.location_catalog_changed");
            filter();
        }
        updateButtons();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(previous); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, matches.size() * ROW_HEIGHT - Math.max(0, listBottom - listTop))); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        frame(g, left, top, panelWidth, panelHeight); icon(g, left + 10, top + 7, 16);
        text(g, title.getString(), left + 31, top + 11, panelWidth - 68, TEXT);
        railDivider(g, left + 12, top + 28, panelWidth - 24);
        text(g, nearby ? locationNotice.isEmpty() ? tr("mtrnav.nearby_count", matches.size()) : locationNotice
                : tr("mtrnav.directory_count", matches.size(), source == null ? 0 : source.stations().size()), left + 12, top + 65, panelWidth - (nearby ? 210 : order == StationDirectoryOrder.Mode.DISTANCE ? 188 : 120) - (inlineIndex ? 0 : 48), MUTED);
        int x = left + 12, right = left + panelWidth - 12, viewport = Math.max(0, listBottom - listTop);
        g.fill(x, listTop, right, listBottom, 0xA0181D1C); clampScroll(); bar = null;
        Station hovered = null;
        if (viewport > 0) {
            g.enableScissor(x, listTop, right, listBottom);
            for (int i = scroll / ROW_HEIGHT; i < matches.size() && listTop + i * ROW_HEIGHT - scroll < listBottom; i++) {
                var station = matches.get(i); int y = listTop + i * ROW_HEIGHT - scroll;
                boolean over = mouseX >= x && mouseX < right - 10 && mouseY >= Math.max(listTop, y) && mouseY < Math.min(listBottom, y + ROW_HEIGHT);
                boolean chosen = station.id().equals(selectedId);
                listRow(g, x, y, right - x - 8, ROW_HEIGHT - 1, chosen, over, (i & 1) == 0);
                if (over) hovered = station;
                text(g, primary(station.name()), x + 20, y + 5, right - x - 144, chosen ? 0xFFFFEBC0 : TEXT);
                String english = Arrays.stream(station.name().split("\\|+")).skip(1).map(String::strip).filter(s -> !s.isEmpty()).reduce((a, b) -> a + " · " + b).orElse("");
                text(g, english, x + 20, y + 19, right - x - 144, chosen ? TEXT : MUTED);
                boolean distance = nearby || order == StationDirectoryOrder.Mode.DISTANCE;
                text(g, station.x() + ", " + station.y() + ", " + station.z(), right - 116, y + (distance ? 18 : 12), 104, chosen ? TEXT : MUTED);
                if (distance) text(g, distances.containsKey(station.id()) ? tr("mtrnav.location_distance", Math.round(distances.get(station.id()))) : tr("mtrnav.directory_unknown_distance"), right - 116, y + 5, 104, ACCENT);
            }
            if (matches.isEmpty()) text(g, tr(source == null ? "mtrnav.connecting" : nearby && searchText.isBlank() ? "mtrnav.location_no_coordinates" : "mtrnav.directory_empty"), x + 8, listTop + 12, right - x - 20, MUTED);
            g.disableScissor();
            if (matches.size() * ROW_HEIGHT > viewport) {
                bar = ScrollBarGeometry.of(listTop, viewport, matches.size() * ROW_HEIGHT, viewport, scroll, 8);
                g.fill(right - 4, listTop, right, listBottom, BORDER); g.fill(right - 4, bar.thumbTop(), right, bar.thumbTop() + bar.thumbHeight(), ACCENT);
            }
        }
        railDivider(g, x, listBottom + 4, right - x);
        text(g, selected() == null ? tr(nearby ? "mtrnav.nearby_hint" : "mtrnav.directory_hint") : tr("mtrnav.directory_selected", displayName(selected().name())), x, top + panelHeight - 10, panelWidth - 24, selected() == null ? MUTED : ACCENT);
        updateButtons(); searchBox.render(g, mouseX, mouseY, delta);
        for (var button : buttons) button.render(g, mouseX, mouseY, delta);
        if (hovered != null && font.width(displayName(hovered.name())) > panelWidth - 156)
            g.renderTooltip(font, Component.literal(displayName(hovered.name()) + " (" + hovered.x() + ", " + hovered.y() + ", " + hovered.z() + ")"), mouseX, mouseY);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && bar != null && x >= left + panelWidth - 20 && x <= left + panelWidth - 10 && y >= listTop && y < listBottom) {
            dragging = true; grabOffset = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0;
            if (!bar.onThumb(y)) scroll = bar.offsetAtThumbTop(y - grabOffset); return true;
        }
        if (button == 0 && x >= left + 12 && x < left + panelWidth - 22 && y >= listTop && y < listBottom) {
            int index = (int)((y - listTop + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < matches.size()) {
                var station = matches.get(index); selectedId = station.id(); setFocused(null); updateButtons();
                long now = System.nanoTime(); boolean twice = lastClickedId.equals(selectedId) && now - lastClick < 300_000_000L;
                lastClick = now; lastClickedId = selectedId;
                if (twice) apply(defaultTarget); return true;
            }
        }
        if (clearButton.isMouseOver(x, y)) return clearButton.mouseClicked(x, y, button);
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { if (bar != null) scroll = bar.offsetAtThumbTop(y - grabOffset); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left + 12 && x < left + panelWidth - 12 && y >= listTop && y < listBottom) { scroll -= (int)(vertical * ROW_HEIGHT); clampScroll(); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!(getFocused() instanceof EditBox) && key >= 65 && key <= 90 && !hasControlDown() && !hasAltDown()) { jump(Character.toString((char)key)); return true; }
        if (!(getFocused() instanceof EditBox) && (key == 266 || key == 267 || key == 268 || key == 269)) {
            scroll = key == 268 ? 0 : key == 269 ? matches.size() * ROW_HEIGHT : scroll + (key == 266 ? -1 : 1) * Math.max(ROW_HEIGHT, listBottom - listTop);
            clampScroll(); return true;
        }
        if ((key == 257 || key == 335) && !(getFocused() instanceof Action)) { apply(defaultTarget); return true; }
        if ((key == 264 || key == 265) && !matches.isEmpty()) {
            int current = -1; for (int i = 0; i < matches.size(); i++) if (matches.get(i).id().equals(selectedId)) { current = i; break; }
            int index = Math.clamp(current < 0 ? key == 264 ? 0 : matches.size() - 1 : current + (key == 264 ? 1 : -1), 0, matches.size() - 1);
            selectedId = matches.get(index).id();
            if (index * ROW_HEIGHT < scroll) scroll = index * ROW_HEIGHT;
            else if ((index + 1) * ROW_HEIGHT > scroll + listBottom - listTop) scroll = (index + 1) * ROW_HEIGHT - (listBottom - listTop);
            clampScroll(); updateButtons(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
