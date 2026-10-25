package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.StationSearch;
import io.github.teamcreating.mtrnav.core.DepartureBoard;
import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Railway journey-book layout with a separate, independently searchable station board. */
public final class NavigationScreen extends Screen {
    private enum View { JOURNEYS, DEPARTURES, ACTIVE }
    private record Row(String text, int color, int height, int kind, String stationId) {
        Row(String text, int color, int height, int kind) { this(text, color, height, kind, ""); }
    }
    private record StationTarget(String id, int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record ScrollTarget(int input, int x, ScrollBarGeometry geometry) {
        boolean contains(double px, double py) { return px >= x - 4 && px <= x + 6 && py >= geometry.top() && py < geometry.top() + geometry.height(); }
    }
    private record BoardTarget(String key, int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private EditBox fromBox, toBox, boardBox;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Action> cards = new ArrayList<>();
    private final List<StationTarget> stationTargets = new ArrayList<>();
    private final List<ScrollTarget> scrollTargets = new ArrayList<>();
    private final List<BoardTarget> boardTargets = new ArrayList<>();
    private final List<Action> clearButtons = new ArrayList<>();
    private final Set<String> expandedLines = new HashSet<>();
    private int draggingScroll = -2;
    private double scrollGrab;
    private Action queryButton, sortButton, routesTab, departuresTab, activeTab, startButton, progressButton, backButton, requeryButton, cancelButton, allStationsButton;
    private int selectedJourney, scroll, contentHeight, left, top, panelWidth, panelHeight, bodyTop, bodyBottom, detailX, detailTop, footerY;
    private boolean wide, collapsed, showAllStops;
    private boolean searchCollapsed = tracking != null || !journeys.isEmpty();
    private View view = tracking == null ? View.JOURNEYS : View.ACTIVE;
    private String fromText, toText, boardText, selectedBoard = "", pendingSelection = "";
    private List<Journey> lastJourneys = journeys;
    private String lastBoardStation = "";
    private Catalog groupedCatalog;
    private List<StopEvent> groupedArrivals;
    private List<DepartureBoard.Group> boardGroups = List.of();

    public NavigationScreen() { super(Component.translatable("mtrnav.title")); }
    boolean wantsBoard() { return view == View.DEPARTURES; }
    private Action button(String key, int x, int y, int w, int h, Runnable action) {
        var b = new Action(x, y, w, h, tr(key), action);
        addRenderableWidget(b); widgets.add(b); return b;
    }
    private void rebuild() { clearWidgets(); init(); setFocused(null); }
    private void changeView(View next) {
        if (view == next) return;
        if (view == View.DEPARTURES) stopBoard();
        view = next; scroll = 0; draggingScroll = -2;
        if (next == View.DEPARTURES && selectedBoard.isEmpty() && !origin.isEmpty()) selectedBoard = origin;
        if (next == View.DEPARTURES && catalog != null && !selectedBoard.isEmpty()) {
            boardText = catalog.stationName(selectedBoard); board(selectedBoard);
        }
        rebuild();
    }
    private void openBoard(String stationId) {
        if (catalog == null || catalog.station(stationId) == null) return;
        selectedBoard = stationId; boardText = catalog.stationName(stationId);
        if (view != View.DEPARTURES) changeView(View.DEPARTURES);
        else { boardBox.setValue(boardText); board(stationId); scroll = 0; }
    }
    private void search() {
        if (usingNative && status.equals("mtrnav.native_unavailable")) { NativeMtrClient.opened(); return; }
        if (view == View.DEPARTURES) { if (!selectedBoard.isEmpty()) { board(selectedBoard); scroll = 0; } return; }
        if (collapsed) { searchCollapsed = false; rebuild(); setInitialFocus(fromBox); return; }
        selectedJourney = 0; changeView(View.JOURNEYS); scroll = 0; query();
    }
    @Override protected void init() {
        widgets.clear(); cards.clear(); stationTargets.clear();
        clearButtons.clear(); scrollTargets.clear(); boardTargets.clear(); draggingScroll = -2;
        panelWidth = Math.min(width - 20, Math.min(760, Math.max(360, (int)(width * .82))));
        panelHeight = Math.min(height - 16, Math.min(460, Math.max(220, (int)(height * .88))));
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        wide = panelWidth >= 380;
        collapsed = view == View.ACTIVE || view == View.JOURNEYS && searchCollapsed && panelHeight < 310;
        int x = left + 12, inner = panelWidth - 24;
        int tabWidth = panelWidth < 500 ? 63 : 76;
        int tabsX = left + panelWidth - 66 - (tracking == null ? 2 : 3) * (tabWidth + 3);
        routesTab = button("mtrnav.query_tab", tabsX, top + 6, tabWidth, 19, () -> changeView(View.JOURNEYS)); routesTab.tab = true;
        departuresTab = button("mtrnav.departures", tabsX + tabWidth + 3, top + 6, tabWidth, 19, () -> changeView(View.DEPARTURES)); departuresTab.tab = true;
        activeTab = button("mtrnav.active_trip", tabsX + (tabWidth + 3) * 2, top + 6, tabWidth, 19, () -> changeView(View.ACTIVE)); activeTab.tab = true;
        button("mtrnav.close", left + panelWidth - 26, top + 7, 16, 16, this::onClose);
        button("mtrnav.hud_settings_short", left + panelWidth - 58, top + 7, 28, 16, () -> minecraft.setScreen(new HudSettingsScreen(this)))
                .setTooltip(Tooltip.create(Component.translatable("mtrnav.hud_settings")));
        int queryWidth = 68, fieldWidth = (inner - queryWidth - 40) / 2, right = x + fieldWidth + 28;
        fromBox = new EditBox(font, x, top + 44, fieldWidth - 22, 20, Component.translatable("mtrnav.origin"));
        toBox = new EditBox(font, right, top + 44, fieldWidth - 22, 20, Component.translatable("mtrnav.destination"));
        boardBox = new EditBox(font, x, top + 44, inner - queryWidth - 30, 20, Component.translatable("mtrnav.board_station"));
        for (var box : List.of(fromBox, toBox, boardBox)) {
            box.setMaxLength(128); box.setTextColor(TEXT); box.setTextColorUneditable(MUTED);
            box.setHint(Component.translatable("mtrnav.station_hint")); addRenderableWidget(box); widgets.add(box);
        }
        fromBox.setValue(fromText != null ? fromText : catalog == null ? "" : catalog.stationName(origin));
        toBox.setValue(toText != null ? toText : catalog == null ? "" : catalog.stationName(destination));
        boardBox.setValue(boardText != null ? boardText : catalog == null ? "" : catalog.stationName(selectedBoard));
        fromBox.setResponder(value -> { fromText = value; edited(true, value); updateControls(); });
        toBox.setResponder(value -> { toText = value; edited(false, value); updateControls(); });
        boardBox.setResponder(value -> { boardText = value; selectedBoard = resolve(value); updateControls(); });
        fromBox.visible = toBox.visible = !collapsed && view == View.JOURNEYS;
        boardBox.visible = view == View.DEPARTURES;
        for (int i = 0; i < 3; i++) {
            final int input = i; var box = inputBox(i);
            var clear = button("mtrnav.clear_symbol", box.getX() + box.getWidth() + 2, box.getY(), 20, 20, () -> clearInput(input));
            clear.setTooltip(Tooltip.create(Component.translatable("mtrnav.clear_input"))); clearButtons.add(clear);
        }
        var swap = button("mtrnav.swap_short", x + fieldWidth + 4, top + 44, 20, 20, () -> {
            String a = origin, b = destination, value = fromBox.getValue();
            fromBox.setValue(toBox.getValue()); toBox.setValue(value); origin = b; destination = a; updateControls();
        }); swap.visible = fromBox.visible; swap.setTooltip(Tooltip.create(Component.translatable("mtrnav.swap")));
        queryButton = button("mtrnav.search", collapsed ? x : x + inner - queryWidth, top + (collapsed ? 33 : 44), queryWidth, 20, this::search); queryButton.primary = !collapsed;
        sortButton = button("mtrnav.fastest", x + inner - 100, top + (collapsed ? 33 : 70), 100, 16, () -> {
            sort = sort == Sort.FASTEST ? Sort.FEWEST_TRANSFERS : Sort.FASTEST;
            if (!origin.isEmpty() && !destination.isEmpty()) { selectedJourney = 0; changeView(View.JOURNEYS); scroll = 0; query(); }
        });
        allStationsButton = button("mtrnav.all_stations", x, top + 70, 84, 16, () -> {
            var target = view == View.DEPARTURES ? StationDirectoryScreen.Target.BOARD
                    : fromBox.isFocused() ? StationDirectoryScreen.Target.ORIGIN
                    : toBox.isFocused() || !origin.isEmpty() ? StationDirectoryScreen.Target.DESTINATION : StationDirectoryScreen.Target.ORIGIN;
            minecraft.setScreen(new StationDirectoryScreen(this, target));
        });
        allStationsButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.all_stations_hint")));
        bodyTop = top + (collapsed ? 61 : 92);
        footerY = top + panelHeight - 43; bodyBottom = footerY - 7;
        detailX = wide ? x + Math.min(220, inner / 3) + 12 : x;
        int cardHeight = wide ? Math.min(56, Math.max(18, (bodyBottom - bodyTop - 12) / 3)) : 36;
        for (int i = 0; i < 3; i++) {
            final int index = i;
            int cw = wide ? detailX - x - 12 : (inner - 10) / 3;
            var card = button("", wide ? x : x + i * (cw + 5), bodyTop + (wide ? i * (cardHeight + 5) : 0), cw, cardHeight, () -> { selectedJourney = index; scroll = 0; setFocused(null); });
            cards.add(card);
        }
        int primaryWidth = Math.min(140, inner / 3 + 12);
        startButton = button("mtrnav.start", x + inner - primaryWidth, footerY + 6, primaryWidth, 22, () -> {
            if (view == View.DEPARTURES) { if (!boardStation.isEmpty()) board(boardStation); }
            else if (!journeys.isEmpty()) { var selected = journeys.get(selectedJourney); pendingSelection = selected.id(); select(selected); }
        }); startButton.primary = true;
        progressButton = button("mtrnav.boarded", startButton.getX(), footerY + 6, primaryWidth, 22, () -> {
            if (tracking != null) { scroll = 0; correct(tracking.phase() == JourneyTracker.Phase.RIDING ? "next" : "board"); }
        }); progressButton.primary = true;
        int secondaryWidth = (inner - primaryWidth - 15) / 3;
        backButton = button("mtrnav.previous_step", x, footerY + 6, secondaryWidth, 22, () -> { scroll = 0; correct("previous"); });
        requeryButton = button("mtrnav.requery", x + secondaryWidth + 4, footerY + 6, secondaryWidth, 22, () -> {
            if (tracking != null && tracking.legIndex() < tracking.journey().legs().size()) {
                var leg = tracking.journey().legs().get(tracking.legIndex());
                origin = leg.fromStationId(); destination = tracking.journey().destinationStationId();
                fromText = catalog.stationName(origin); toText = catalog.stationName(destination);
            }
            changeView(View.JOURNEYS); scroll = 0; query();
        });
        cancelButton = button("mtrnav.end_trip", x + (secondaryWidth + 4) * 2, footerY + 6, secondaryWidth, 22, () -> { cancel(); changeView(View.JOURNEYS); });
        if (view == View.DEPARTURES && !selectedBoard.isEmpty() && boardStation.isEmpty()) board(selectedBoard);
        updateControls();
    }
    private EditBox inputBox(int input) { return input == 0 ? fromBox : input == 1 ? toBox : boardBox; }
    private void clearInput(int input) {
        var box = inputBox(input); box.setValue("");
        if (input == 2) { selectedBoard = ""; stopBoard(); expandedLines.clear(); scroll = 0; }
        else clearSearchResults();
        setFocused(box); updateControls();
    }
    private String resolve(String value) { return catalog == null ? "" : StationSearch.exact(catalog.stations(), value); }
    private void edited(boolean from, String value) {
        String id = resolve(value), previous = from ? origin : destination;
        if (from) origin = id; else destination = id;
        if (!previous.equals(id)) clearSearchResults();
        if (Set.of("mtrnav.search_failed", "mtrnav.search_budget", "mtrnav.station_refreshed", "mtrnav.no_route").contains(status)) status = "mtrnav.ready";
    }
    void useStation(Station station, StationDirectoryScreen.Target target) {
        scroll = 0;
        if (target == StationDirectoryScreen.Target.BOARD) {
            selectedBoard = station.id(); boardText = displayName(station.name()); view = View.DEPARTURES;
        } else {
            if (target == StationDirectoryScreen.Target.ORIGIN) { origin = station.id(); fromText = displayName(station.name()); }
            else { destination = station.id(); toText = displayName(station.name()); }
            clearSearchResults(); view = View.JOURNEYS; searchCollapsed = false;
        }
    }
    @Override public void tick() {
        if (origin.isEmpty()) edited(true, fromBox.getValue());
        if (destination.isEmpty()) edited(false, toBox.getValue());
        if (selectedBoard.isEmpty()) selectedBoard = resolve(boardBox.getValue());
        if (!pendingSelection.isEmpty() && tracking != null && tracking.journey().id().equals(pendingSelection)) { pendingSelection = ""; changeView(View.ACTIVE); }
        if (activeTab.visible != (tracking != null)) rebuild();
        if (lastJourneys != journeys) {
            lastJourneys = journeys; selectedJourney = 0; scroll = 0;
            if (view == View.JOURNEYS && !journeys.isEmpty() && !searchCollapsed && panelHeight < 310) { searchCollapsed = true; rebuild(); }
        }
        if (view == View.ACTIVE && tracking == null) changeView(View.JOURNEYS);
        updateControls();
    }
    private void updateControls() {
        boolean active = view == View.ACTIVE && tracking != null;
        boolean retry = usingNative && status.equals("mtrnav.native_unavailable");
        allStationsButton.visible = !collapsed && !active;
        allStationsButton.active = catalog != null && !catalog.stations().isEmpty();
        for (int i = 0; i < clearButtons.size(); i++) {
            var box = inputBox(i); clearButtons.get(i).visible = box.visible;
            clearButtons.get(i).active = !box.getValue().isEmpty();
        }
        routesTab.selected = view == View.JOURNEYS; departuresTab.selected = view == View.DEPARTURES;
        activeTab.visible = tracking != null; activeTab.selected = active;
        queryButton.visible = !active;
        queryButton.active = retry || (view == View.DEPARTURES ? !selectedBoard.isEmpty() : collapsed || catalog != null && !origin.isEmpty() && !destination.isEmpty() && !status.equals("mtrnav.searching"));
        queryButton.setMessage(Component.translatable(retry ? "mtrnav.retry_service" : view == View.DEPARTURES ? "mtrnav.show_board" : collapsed ? "mtrnav.edit_stations" : status.equals("mtrnav.searching") ? "mtrnav.searching_short" : "mtrnav.search"));
        sortButton.visible = view == View.JOURNEYS; sortButton.setMessage(Component.literal(tr(sort == Sort.FASTEST ? "mtrnav.fastest" : "mtrnav.fewest") + " ▾"));
        sortButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.sort_hint")));
        startButton.visible = !active;
        startButton.setMessage(Component.translatable(view == View.DEPARTURES ? "mtrnav.refresh_board" : "mtrnav.start"));
        startButton.active = view == View.DEPARTURES ? !boardStation.isEmpty() && !boardLoading : !journeys.isEmpty() && !CLOCK.stale(System.nanoTime());
        progressButton.visible = backButton.visible = requeryButton.visible = cancelButton.visible = active;
        if (active) {
            var phase = tracking.phase();
            progressButton.setMessage(Component.translatable(phase == JourneyTracker.Phase.COMPLETE ? "mtrnav.complete" : phase == JourneyTracker.Phase.RIDING ? "mtrnav.alighted" : "mtrnav.boarded"));
            progressButton.active = phase != JourneyTracker.Phase.COMPLETE;
            progressButton.setTooltip(Tooltip.create(Component.translatable(phase == JourneyTracker.Phase.RIDING ? "mtrnav.confirm_alighted" : "mtrnav.confirm_boarded")));
            backButton.active = tracking.legIndex() > 0 || phase == JourneyTracker.Phase.RIDING; requeryButton.active = phase != JourneyTracker.Phase.COMPLETE;
        }
        selectedJourney = Math.max(0, Math.min(selectedJourney, journeys.size() - 1));
        for (int i = 0; i < cards.size(); i++) {
            var b = cards.get(i); b.visible = view == View.JOURNEYS && (i < journeys.size() || i == 0 && journeys.isEmpty() && referencePlan != null);
            if (!b.visible) continue;
            if (journeys.isEmpty()) {
                b.selected = true; b.setMessage(Component.literal(tr("mtrnav.reference_route")));
                b.detail = tr("mtrnav.transfers", referencePlan.transfers());
                b.extra = referencePlan.legs().stream().map(l -> routeCode(l.routeId())).reduce((a, c) -> a + " → " + c).orElse(tr("mtrnav.same_station"));
                b.tint = ACCENT; b.setTooltip(Tooltip.create(Component.translatable(referenceReason))); continue;
            }
            var journey = journeys.get(i); b.selected = selectedJourney == i;
            b.setMessage(Component.literal(tr("mtrnav.option", i + 1) + "  " + duration(journey.arrival() - journey.queryTime())));
            b.detail = tr("mtrnav.transfers", journey.transfers()) + " · " + remaining(journey.arrival());
            b.extra = journey.legs().stream().map(l -> routeCode(l.routeId())).reduce((a, c) -> a + " → " + c).orElse(tr("mtrnav.same_station"));
            b.tint = journey.legs().isEmpty() ? ACCENT : routeColor(journey.legs().getFirst().routeId());
            b.setTooltip(Tooltip.create(Component.literal(journey.legs().stream().map(l -> route(l.routeId())).reduce((a, c) -> a + " → " + c).orElse(tr("mtrnav.same_station")))));
        }
        detailTop = bodyTop + (!wide && view == View.JOURNEYS && (!journeys.isEmpty() || referencePlan != null) ? 43 : 0);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && queryButton.visible && queryButton.active && !collapsed && !(getFocused() instanceof Action)) { search(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) for (var target : scrollTargets) if (target.contains(x, y)) {
            var bar = target.geometry(); draggingScroll = target.input();
            scrollGrab = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0;
            dragScroll(target, y); return true;
        }
        // Handle these directly so the container cannot focus the button after
        // the action has returned focus to the newly cleared input field.
        for (var b : clearButtons) if (b.visible && b.isMouseOver(x, y)) return b.mouseClicked(x, y, button);
        if (button == 0) for (var target : boardTargets) if (target.contains(x, y)) {
            if (!expandedLines.add(target.key())) expandedLines.remove(target.key()); return true;
        }
        if (button == 0) for (var target : stationTargets) if (target.contains(x, y)) { openBoard(target.id()); return true; }
        int contentX = wide && view == View.JOURNEYS ? detailX : left + 12;
        if (button == 0 && scroll == 0 && x >= contentX && x < left + panelWidth - 12 && y >= detailTop && y < detailTop + 22 && view != View.DEPARTURES && (tracking != null || !journeys.isEmpty() || referencePlan != null)) { showAllStops = !showAllStops; return true; }
        return super.mouseClicked(x, y, button);
    }
    private void dragScroll(ScrollTarget target, double mouseY) {
        int offset = target.geometry().offsetAtThumbTop(mouseY - scrollGrab);
        scroll = offset;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll != -2 && button == 0) {
            for (var target : scrollTargets) if (target.input() == draggingScroll) { dragScroll(target, y); break; }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScroll != -2) { draggingScroll = -2; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { if (view == View.DEPARTURES) stopBoard(); super.removed(); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        frame(g, left, top, panelWidth, panelHeight);
        icon(g, left + 10, top + 7, 16);
        text(g, title.getString(), left + 31, top + 11, Math.max(0, routesTab.getX() - left - 36), TEXT);
        railDivider(g, left + 12, top + 28, panelWidth - 24);
        if (!collapsed) {
            if (view == View.DEPARTURES) text(g, tr("mtrnav.board_station"), boardBox.getX(), top + 32, boardBox.getWidth(), MUTED);
            else {
                text(g, tr("mtrnav.origin") + (origin.isEmpty() ? "" : " ✓"), fromBox.getX(), top + 32, fromBox.getWidth(), MUTED);
                text(g, tr("mtrnav.destination") + (destination.isEmpty() ? "" : " ✓"), toBox.getX(), top + 32, toBox.getWidth(), MUTED);
            }
            String message = view == View.DEPARTURES ? tr("mtrnav.board_search_hint") : journeys.isEmpty() ? tr(status) : tr("mtrnav.options_found", journeys.size());
            text(g, message, left + 104, top + 74, panelWidth - (view == View.JOURNEYS ? 228 : 116), MUTED);
        } else {
            String label = view == View.ACTIVE && tracking != null ? station(tracking.journey().originStationId()) + " → " + station(tracking.journey().destinationStationId()) : station(origin) + " → " + station(destination);
            text(g, label, left + 12 + (view == View.ACTIVE ? 0 : 76), top + 39, panelWidth - 36 - (view == View.ACTIVE ? 0 : 180), MUTED);
        }
        g.fill(left + 12, bodyTop - 4, left + panelWidth - 12, bodyTop - 3, BORDER);
        stationTargets.clear(); boardTargets.clear(); scrollTargets.clear();
        if (view == View.DEPARTURES) renderBoard(g, mouseX, mouseY);
        else renderJourney(g, mouseX, mouseY);
        railDivider(g, left + 12, footerY - 1, panelWidth - 24);
        if (view != View.ACTIVE) {
            String summary = view == View.DEPARTURES ? tr("mtrnav.board_summary", arrivals.size()) : journeys.isEmpty() ? tr(referencePlan == null ? "mtrnav.search_help" : "mtrnav.reference_only") : tr("mtrnav.option_summary", selectedJourney + 1, journeys.size());
            text(g, summary, left + 12, footerY + 12, panelWidth - 40 - startButton.getWidth(), TEXT);
        }
        boolean stale = view == View.DEPARTURES ? boardStale() : CLOCK.stale(System.nanoTime());
        String footer = stale && catalog != null ? tr("mtrnav.stale") : view == View.ACTIVE ? tr(usingNative ? "mtrnav.manual_short" : "mtrnav.progress_hint") : tr(view == View.DEPARTURES ? "mtrnav.board_detail_hint" : "mtrnav.station_click_hint");
        text(g, footer, left + 12, top + panelHeight - 10, panelWidth - 24, stale ? WARNING : MUTED);
        for (var widget : widgets) if (widget.visible) widget.render(g, mouseX, mouseY, delta);
        for (var target : stationTargets) if (target.contains(mouseX, mouseY)) { g.renderTooltip(font, Component.translatable("mtrnav.station_board_hint", station(target.id())), mouseX, mouseY); break; }
    }
    private void renderJourney(GuiGraphics g, int mouseX, int mouseY) {
        int contentX = wide && view == View.JOURNEYS ? detailX : left + 12;
        int contentWidth = left + panelWidth - 12 - contentX;
        if (wide && view == View.JOURNEYS) {
            g.fill(detailX - 7, bodyTop, detailX - 6, bodyBottom, BORDER);
            if (journeys.isEmpty() && referencePlan == null) { int y = bodyTop + 10; for (var line : font.split(Component.translatable("mtrnav.search_help"), detailX - left - 28)) { g.drawString(font, line, left + 16, y, MUTED, false); y += 13; } }
        }
        var rows = rows(contentWidth - 26); contentHeight = rows.stream().mapToInt(Row::height).sum();
        int viewport = Math.max(0, bodyBottom - detailTop); clampScroll(viewport);
        if (viewport <= 0) return;
        g.enableScissor(contentX, detailTop, contentX + contentWidth, bodyBottom);
        int y = detailTop - scroll;
        for (var row : rows) {
            if (row.kind == 1) { g.fill(contentX, y, contentX + 3, y + row.height, row.color); g.fill(contentX + 3, y, contentX + contentWidth, y + row.height, RAISED); }
            if (row.kind == 2) { g.fill(contentX + 9, y, contentX + 11, y + row.height, row.color); outline(g, contentX + 7, y + 6, 6, 6, row.color); }
            if (!row.stationId.isEmpty() && y + row.height > detailTop && y < bodyBottom) {
                var target = new StationTarget(row.stationId, contentX + 16, Math.max(y, detailTop), contentWidth - 20, Math.min(bodyBottom, y + row.height) - Math.max(y, detailTop));
                stationTargets.add(target); if (target.contains(mouseX, mouseY)) g.fill(contentX + 16, y, contentX + contentWidth - 4, y + row.height, RAISED);
            }
            if (!row.text.isEmpty()) text(g, row.text, contentX + (row.kind == 2 ? 20 : 8), y + (row.kind == 1 || row.kind == 2 ? 5 : 2), contentWidth - (row.kind == 2 ? 30 : 18), row.kind == 1 || row.kind == 2 ? TEXT : row.color);
            y += row.height;
        }
        g.disableScissor(); scrollbar(g, contentX + contentWidth - 3, detailTop, viewport);
    }
    private boolean boardStale() { return boardUpdatedNanos == 0 || System.nanoTime() - boardUpdatedNanos >= 5_000_000_000L || CLOCK.stale(System.nanoTime()); }
    private String boardTime(long time) { return boardStale() ? "—" : remaining(time); }
    private void renderBoard(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12, w = panelWidth - 24, right = x + w;
        if (catalog == null || boardStation.isEmpty()) { wrapped(g, tr(catalog == null ? status : "mtrnav.choose_board_station"), x + 8, bodyTop + 10, w - 16, MUTED); return; }
        text(g, station(boardStation), x + 5, bodyTop + 6, w - 12, TEXT);
        int labelY = bodyTop + 24, rowTop = bodyTop + 39;
        int stateX = right - 34, departureX = stateX - 48, arrivalX = departureX - 48, platformX = arrivalX - 58;
        text(g, tr("mtrnav.line_direction"), x + 6, labelY, platformX - x - 12, MUTED);
        text(g, tr("mtrnav.platform_label"), platformX, labelY, 54, MUTED);
        text(g, tr("mtrnav.arrival"), arrivalX, labelY, 44, MUTED);
        text(g, tr("mtrnav.departure"), departureX, labelY, 44, MUTED);
        text(g, tr("mtrnav.time_source"), stateX, labelY, 34, MUTED);
        g.fill(x, rowTop - 5, right, rowTop - 4, BORDER);
        if (!lastBoardStation.equals(boardStation)) { lastBoardStation = boardStation; groupedArrivals = null; expandedLines.clear(); scroll = 0; }
        if (groupedCatalog != catalog || groupedArrivals != arrivals) {
            groupedCatalog = catalog; groupedArrivals = arrivals;
            boardGroups = DepartureBoard.groups(catalog, boardStation, arrivals);
        }
        var groups = boardGroups;
        int viewport = Math.max(0, bodyBottom - rowTop);
        contentHeight = groups.stream().mapToInt(group -> 30 + (expandedLines.contains(group.key()) ? Math.max(1, group.events().size()) * 28 : 0)).sum(); clampScroll(viewport);
        if (groups.isEmpty()) { wrapped(g, tr(boardLoading ? "mtrnav.board_loading" : status.equals("mtrnav.native_unavailable") ? status : "mtrnav.no_departures"), x + 6, rowTop + 8, w - 12, MUTED); return; }
        if (viewport <= 0) return;
        g.enableScissor(x, rowTop, right, bodyBottom);
        int y = rowTop - scroll;
        for (var group : groups) {
            boolean expanded = expandedLines.contains(group.key());
            int groupHeight = 30 + (expanded ? Math.max(1, group.events().size()) * 28 : 0);
            if (y + groupHeight <= rowTop || y >= bodyBottom) { y += groupHeight; continue; }
            int tint = 0xFF000000 | group.color() & 0xFFFFFF;
            g.fill(x, y, right - 8, y + 29, RAISED); g.fill(x, y + 2, x + 3, y + 27, tint);
            if (y + 30 > rowTop && y < bodyBottom) {
                var target = new BoardTarget(group.key(), x, Math.max(y, rowTop), w - 10, Math.min(y + 30, bodyBottom) - Math.max(y, rowTop));
                boardTargets.add(target);
                if (target.contains(mouseX, mouseY)) outline(g, x + 1, y + 1, w - 10, 28, ACCENT);
            }
            text(g, expanded ? "▾" : "▸", x + 7, y + 5, 12, ACCENT);
            text(g, group.name().isEmpty() ? tr("mtrnav.route_pending") : primary(group.name()), x + 22, y + 5, w - 40, TEXT);
            String next = group.events().isEmpty() ? tr(boardLoading ? "mtrnav.board_loading" : "mtrnav.no_departures")
                    : tr("mtrnav.line_departures", group.events().size(), boardTime(group.events().getFirst().departure()));
            text(g, next, x + 22, y + 18, w - 40, MUTED); y += 30;
            if (!expanded) continue;
            if (group.events().isEmpty()) { text(g, tr(boardLoading ? "mtrnav.board_loading" : "mtrnav.no_departures"), x + 14, y + 8, w - 30, MUTED); y += 28; }
            int index = 0;
            for (var event : group.events()) {
                if (y + 28 <= rowTop || y >= bodyBottom) { y += 28; index++; continue; }
                if ((index++ & 1) == 0) g.fill(x + 8, y, right - 8, y + 27, RAISED);
                g.fill(x + 8, y + 4, x + 10, y + 24, tint);
                text(g, "→ " + primary(event.destination()), x + 17, y + 10, platformX - x - 23, TEXT);
                text(g, platform(event.platformId()), platformX, y + 10, 54, TEXT);
                text(g, boardTime(event.arrival()), arrivalX, y + 10, 44, TEXT);
                text(g, boardTime(event.departure()), departureX, y + 10, 44, ACCENT);
                text(g, tr(event.realtime() ? "mtrnav.live_short" : "mtrnav.schedule_short"), stateX, y + 10, 27, event.realtime() ? ACCENT : MUTED);
                y += 28;
            }
        }
        g.disableScissor(); scrollbar(g, right - 4, rowTop, viewport);
    }
    private void clampScroll(int viewport) { scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport))); }
    private void scrollbar(GuiGraphics g, int x, int y, int viewport) {
        if (contentHeight <= viewport || viewport <= 0) return;
        drawScrollbar(g, new ScrollTarget(-1, x, ScrollBarGeometry.of(y, viewport, contentHeight, viewport, scroll, 8)));
    }
    private void drawScrollbar(GuiGraphics g, ScrollTarget target) {
        scrollTargets.add(target); var geometry = target.geometry(); int x = target.x();
        g.fill(x, geometry.top(), x + 4, geometry.top() + geometry.height(), BORDER);
        g.fill(x, geometry.thumbTop(), x + 4, geometry.thumbTop() + geometry.thumbHeight(), ACCENT);
    }
    private List<Row> rows(int width) {
        var rows = new ArrayList<Row>();
        if (catalog == null) { wrap(rows, tr(status), width, MUTED); return rows; }
        Journey journey;
        if (view == View.ACTIVE && tracking != null) {
            journey = tracking.journey();
            rows.add(new Row(tr("mtrnav.phase." + tracking.phase().name().toLowerCase(Locale.ROOT)), ACCENT, 21, 1));
            if (tracking.phase() == JourneyTracker.Phase.COMPLETE) wrap(rows, tr("mtrnav.arrived_at", station(journey.destinationStationId())), width, ACCENT);
            else if (tracking.legIndex() < journey.legs().size()) {
                var leg = journey.legs().get(tracking.legIndex()); boolean riding = tracking.phase() == JourneyTracker.Phase.RIDING;
                wrap(rows, tr(riding ? "mtrnav.next_alight" : "mtrnav.next_board", station(riding ? leg.toStationId() : leg.fromStationId())), width, TEXT);
                wrap(rows, tr("mtrnav.platform", platform(riding ? leg.toPlatformId() : leg.fromPlatformId())) + " · " + tr(riding ? "mtrnav.arrival" : "mtrnav.departure") + " " + remaining(riding ? leg.arrival() : leg.departure()), width, ACCENT);
                if (!guidance().isEmpty()) wrap(rows, guidance(), width, WARNING);
            }
        } else {
            if (journeys.isEmpty()) {
                if (referencePlan != null) return referenceRows(width);
                wrap(rows, tr(status), width, TEXT); wrap(rows, tr("mtrnav.empty_help"), width, MUTED); return rows;
            }
            journey = journeys.get(selectedJourney);
            rows.add(new Row(duration(journey.arrival() - journey.queryTime()) + " · " + tr("mtrnav.transfers", journey.transfers()), ACCENT, 21, 1));
            wrap(rows, station(journey.originStationId()) + " → " + station(journey.destinationStationId()) + " · " + tr("mtrnav.arrival") + " " + remaining(journey.arrival()), width, TEXT);
        }
        if (journey.legs().isEmpty()) wrap(rows, tr("mtrnav.same_station"), width, TEXT);
        for (int i = 0; i < journey.legs().size(); i++) {
            var leg = journey.legs().get(i); int tint = routeColor(leg.routeId());
            if (i > 0) { rows.add(new Row("", TEXT, 5, 0)); wrap(rows, tr("mtrnav.transfer_gap", duration(leg.departure() - journey.legs().get(i - 1).arrival())), width, WARNING); }
            rows.add(new Row((i + 1) + "  " + route(leg.routeId()), tint, 20, 1));
            wrap(rows, tr("mtrnav.direction", primary(leg.direction())), width, MUTED);
            rows.add(new Row(station(leg.fromStationId()) + " · " + tr("mtrnav.platform", platform(leg.fromPlatformId())), tint, 18, 2, leg.fromStationId()));
            wrap(rows, tr("mtrnav.departure") + " " + remaining(leg.departure()) + " · " + tr(leg.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled"), width, MUTED);
            if (showAllStops) for (var via : leg.viaStations()) rows.add(new Row(station(via), MUTED, 17, 2, via));
            else if (!leg.viaStations().isEmpty()) wrap(rows, tr("mtrnav.stops_count", leg.viaStations().size()), width, MUTED);
            rows.add(new Row(station(leg.toStationId()) + " · " + tr("mtrnav.platform", platform(leg.toPlatformId())), tint, 18, 2, leg.toStationId()));
            wrap(rows, tr("mtrnav.arrival") + " " + remaining(leg.arrival()), width, MUTED); rows.add(new Row("", TEXT, 6, 0));
        }
        return rows;
    }
    private List<Row> referenceRows(int width) {
        var rows = new ArrayList<Row>();
        rows.add(new Row(tr("mtrnav.reference_route") + " · " + tr("mtrnav.transfers", referencePlan.transfers()), ACCENT, 21, 1));
        wrap(rows, tr(referenceReason), width, WARNING);
        wrap(rows, tr("mtrnav.reference_only"), width, MUTED);
        for (int i = 0; i < referencePlan.legs().size(); i++) {
            var leg = referencePlan.legs().get(i); int tint = routeColor(leg.routeId());
            if (i > 0) wrap(rows, tr("mtrnav.reference_transfer"), width, WARNING);
            rows.add(new Row((i + 1) + "  " + route(leg.routeId()), tint, 20, 1));
            wrap(rows, tr("mtrnav.direction", primary(leg.direction())), width, MUTED);
            rows.add(new Row(station(leg.fromStationId()) + " · " + tr("mtrnav.platform", platform(leg.fromPlatformId())), tint, 18, 2, leg.fromStationId()));
            if (showAllStops) for (var via : leg.viaStations()) rows.add(new Row(station(via), MUTED, 17, 2, via));
            else if (!leg.viaStations().isEmpty()) wrap(rows, tr("mtrnav.stops_count", leg.viaStations().size()), width, MUTED);
            rows.add(new Row(station(leg.toStationId()) + " · " + tr("mtrnav.platform", platform(leg.toPlatformId())), tint, 18, 2, leg.toStationId()));
            rows.add(new Row("", TEXT, 6, 0));
        }
        return rows;
    }
    private void wrap(List<Row> rows, String text, int width, int color) {
        for (var line : font.split(Component.literal(text), Math.max(30, width))) {
            var plain = new StringBuilder(); line.accept((index, style, point) -> { plain.appendCodePoint(point); return true; }); rows.add(new Row(plain.toString(), color, 12, 0));
        }
    }
    private void wrapped(GuiGraphics g, String text, int x, int y, int width, int color) { for (var line : font.split(Component.literal(text), width)) { g.drawString(font, line, x, y, color, false); y += 13; } }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x <= left + panelWidth && y >= bodyTop && y <= bodyBottom) { scroll = Math.max(0, scroll - (int)(vertical * 26)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
