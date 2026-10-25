package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.StationSearch;
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
    private EditBox fromBox, toBox, boardBox;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Action> fromMatches = new ArrayList<>(), toMatches = new ArrayList<>(), boardMatches = new ArrayList<>(), cards = new ArrayList<>();
    private final List<StationTarget> stationTargets = new ArrayList<>();
    private List<Station> fromStations = List.of(), toStations = List.of(), boardStations = List.of();
    private final int[] matchOffsets = new int[3];
    private Action queryButton, sortButton, routesTab, departuresTab, activeTab, startButton, progressButton, backButton, requeryButton, cancelButton;
    private int selectedJourney, scroll, contentHeight, left, top, panelWidth, panelHeight, bodyTop, bodyBottom, detailX, detailTop, footerY;
    private boolean wide, collapsed, showAllStops;
    private boolean searchCollapsed = tracking != null || !journeys.isEmpty();
    private View view = tracking == null ? View.JOURNEYS : View.ACTIVE;
    private String fromText, toText, boardText, selectedBoard = "", pendingSelection = "";
    private List<Journey> lastJourneys = journeys;

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
        view = next; scroll = 0;
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
        widgets.clear(); fromMatches.clear(); toMatches.clear(); boardMatches.clear(); cards.clear(); stationTargets.clear();
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
        fromBox = new EditBox(font, x, top + 44, fieldWidth, 20, Component.translatable("mtrnav.origin"));
        toBox = new EditBox(font, right, top + 44, fieldWidth, 20, Component.translatable("mtrnav.destination"));
        boardBox = new EditBox(font, x, top + 44, inner - queryWidth - 8, 20, Component.translatable("mtrnav.board_station"));
        for (var box : List.of(fromBox, toBox, boardBox)) {
            box.setMaxLength(128); box.setTextColor(TEXT); box.setTextColorUneditable(MUTED);
            box.setHint(Component.translatable("mtrnav.station_hint")); addRenderableWidget(box); widgets.add(box);
        }
        fromBox.setValue(fromText != null ? fromText : catalog == null ? "" : catalog.stationName(origin));
        toBox.setValue(toText != null ? toText : catalog == null ? "" : catalog.stationName(destination));
        boardBox.setValue(boardText != null ? boardText : catalog == null ? "" : catalog.stationName(selectedBoard));
        fromBox.setResponder(value -> { fromText = value; matchOffsets[0] = 0; edited(true, value); refreshMatches(); });
        toBox.setResponder(value -> { toText = value; matchOffsets[1] = 0; edited(false, value); refreshMatches(); });
        boardBox.setResponder(value -> { boardText = value; matchOffsets[2] = 0; selectedBoard = resolve(value); refreshMatches(); });
        fromBox.visible = toBox.visible = !collapsed && view == View.JOURNEYS;
        boardBox.visible = view == View.DEPARTURES;
        var swap = button("mtrnav.swap_short", x + fieldWidth + 4, top + 44, 20, 20, () -> {
            String a = origin, b = destination, value = fromBox.getValue();
            fromBox.setValue(toBox.getValue()); toBox.setValue(value); origin = b; destination = a; refreshMatches();
        }); swap.visible = fromBox.visible; swap.setTooltip(Tooltip.create(Component.translatable("mtrnav.swap")));
        queryButton = button("mtrnav.search", collapsed ? x : x + inner - queryWidth, top + (collapsed ? 33 : 44), queryWidth, 20, this::search); queryButton.primary = !collapsed;
        sortButton = button("mtrnav.fastest", x + inner - 100, top + (collapsed ? 33 : 70), 100, 16, () -> {
            sort = sort == Sort.FASTEST ? Sort.FEWEST_TRANSFERS : Sort.FASTEST;
            if (!origin.isEmpty() && !destination.isEmpty()) { selectedJourney = 0; changeView(View.JOURNEYS); scroll = 0; query(); }
        });
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
        for (int i = 0; i < 3; i++) {
            final int index = i;
            fromMatches.add(button("", x, top + 66 + i * 22, fieldWidth, 22, () -> choose(0, index)));
            toMatches.add(button("", right, top + 66 + i * 22, fieldWidth, 22, () -> choose(1, index)));
            boardMatches.add(button("", x, top + 66 + i * 22, boardBox.getWidth(), 22, () -> choose(2, index)));
        }
        if (view == View.DEPARTURES && !selectedBoard.isEmpty() && boardStation.isEmpty()) board(selectedBoard);
        refreshMatches(); updateControls();
    }
    private String resolve(String value) { return catalog == null ? "" : StationSearch.exact(catalog.stations(), value); }
    private void edited(boolean from, String value) {
        String id = resolve(value); if (from) origin = id; else destination = id;
        if (Set.of("mtrnav.search_failed", "mtrnav.search_budget", "mtrnav.station_refreshed", "mtrnav.no_route").contains(status)) status = "mtrnav.ready";
    }
    private void choose(int input, int index) {
        var matches = input == 0 ? fromStations : input == 1 ? toStations : boardStations;
        index += matchOffsets[input];
        if (index >= matches.size()) return;
        var s = matches.get(index);
        if (input == 0) { fromBox.setValue(displayName(s.name())); origin = s.id(); setFocused(toBox); }
        else if (input == 1) { toBox.setValue(displayName(s.name())); destination = s.id(); setFocused(null); }
        else { selectedBoard = s.id(); boardText = displayName(s.name()); boardBox.setValue(boardText); board(s.id()); scroll = 0; setFocused(null); }
        refreshMatches();
    }
    private List<Station> matches(String value) {
        if (catalog == null) return List.of();
        String needle = value.strip().toLowerCase(Locale.ROOT);
        return catalog.stations().stream().filter(s -> displayName(s.name()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Station::name).thenComparing(Station::id)).toList();
    }
    private void refreshMatches() {
        if (fromBox == null) return;
        fromStations = matches(fromBox.getValue()); toStations = matches(toBox.getValue()); boardStations = matches(boardBox.getValue());
        updateMatches(0, fromMatches, fromStations, fromBox.visible && fromBox.isFocused() && origin.isEmpty());
        updateMatches(1, toMatches, toStations, toBox.visible && toBox.isFocused() && destination.isEmpty());
        updateMatches(2, boardMatches, boardStations, boardBox.visible && boardBox.isFocused() && selectedBoard.isEmpty());
    }
    private void updateMatches(int input, List<Action> buttons, List<Station> stations, boolean show) {
        matchOffsets[input] = Math.max(0, Math.min(matchOffsets[input], stations.size() - buttons.size()));
        for (int i = 0; i < buttons.size(); i++) {
            int index = i + matchOffsets[input];
            var b = buttons.get(i); b.visible = show && index < stations.size();
            if (b.visible) { var s = stations.get(index); String label = displayName(s.name()) + " (" + s.x() + ", " + s.z() + ")";
                b.setMessage(Component.literal(label)); b.setTooltip(Tooltip.create(Component.literal(label + (stations.size() > 3 ? " · " + (index + 1) + "/" + stations.size() + " · " + tr("mtrnav.scroll_matches") : "")))); }
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
        refreshMatches(); updateControls();
    }
    private void updateControls() {
        boolean active = view == View.ACTIVE && tracking != null;
        boolean retry = usingNative && status.equals("mtrnav.native_unavailable");
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
        for (var list : List.of(fromMatches, toMatches, boardMatches)) for (var b : list) if (b.visible && b.isMouseOver(x, y)) return b.mouseClicked(x, y, button);
        if (button == 0) for (var target : stationTargets) if (target.contains(x, y)) { openBoard(target.id()); return true; }
        int contentX = wide && view == View.JOURNEYS ? detailX : left + 12;
        if (button == 0 && scroll == 0 && x >= contentX && x < left + panelWidth - 12 && y >= detailTop && y < detailTop + 22 && view != View.DEPARTURES && (tracking != null || !journeys.isEmpty() || referencePlan != null)) { showAllStops = !showAllStops; return true; }
        return super.mouseClicked(x, y, button);
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
            text(g, message, left + 12, top + 74, panelWidth - (view == View.JOURNEYS ? 136 : 24), MUTED);
        } else {
            String label = view == View.ACTIVE && tracking != null ? station(tracking.journey().originStationId()) + " → " + station(tracking.journey().destinationStationId()) : station(origin) + " → " + station(destination);
            text(g, label, left + 12 + (view == View.ACTIVE ? 0 : 76), top + 39, panelWidth - 36 - (view == View.ACTIVE ? 0 : 180), MUTED);
        }
        g.fill(left + 12, bodyTop - 4, left + panelWidth - 12, bodyTop - 3, BORDER);
        stationTargets.clear();
        if (view == View.DEPARTURES) renderBoard(g);
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
        for (int i = 0; i < 3; i++) {
            var list = i == 0 ? fromMatches : i == 1 ? toMatches : boardMatches;
            var stations = i == 0 ? fromStations : i == 1 ? toStations : boardStations;
            if (!list.getFirst().visible || stations.size() <= 3) continue;
            var first = list.getFirst(); int h = list.size() * 22;
            int thumb = Math.max(6, h * 3 / stations.size());
            int barX = first.getX() + first.getWidth() - 3;
            g.fill(barX, first.getY(), barX + 2, first.getY() + h, BORDER);
            int barY = first.getY() + (h - thumb) * matchOffsets[i] / Math.max(1, stations.size() - 3);
            g.fill(barX, barY, barX + 2, barY + thumb, ACCENT);
        }
        boolean popup = fromMatches.stream().anyMatch(b -> b.visible) || toMatches.stream().anyMatch(b -> b.visible) || boardMatches.stream().anyMatch(b -> b.visible);
        if (!popup) for (var target : stationTargets) if (target.contains(mouseX, mouseY)) { g.renderTooltip(font, Component.translatable("mtrnav.station_board_hint", station(target.id())), mouseX, mouseY); break; }
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
    private void renderBoard(GuiGraphics g) {
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
        int viewport = Math.max(0, bodyBottom - rowTop); contentHeight = arrivals.size() * 34; clampScroll(viewport);
        if (arrivals.isEmpty()) { wrapped(g, tr(boardLoading ? "mtrnav.board_loading" : status.equals("mtrnav.native_unavailable") ? status : "mtrnav.no_departures"), x + 6, rowTop + 8, w - 12, MUTED); return; }
        if (viewport <= 0) return;
        g.enableScissor(x, rowTop, right, bodyBottom);
        int index = 0;
        for (var event : arrivals) {
            int y = rowTop + index * 34 - scroll;
            if ((index++ & 1) == 0) g.fill(x, y, right, y + 33, RAISED);
            g.fill(x, y + 4, x + 2, y + 27, routeColor(event.routeId()));
            text(g, route(event.routeId()), x + 6, y + 4, platformX - x - 12, TEXT);
            text(g, "→ " + primary(event.destination()), x + 6, y + 18, platformX - x - 12, MUTED);
            text(g, platform(event.platformId()), platformX, y + 11, 54, TEXT);
            text(g, boardTime(event.arrival()), arrivalX, y + 11, 44, TEXT);
            text(g, boardTime(event.departure()), departureX, y + 11, 44, ACCENT);
            text(g, tr(event.realtime() ? "mtrnav.live_short" : "mtrnav.schedule_short"), stateX, y + 11, 33, event.realtime() ? ACCENT : MUTED);
        }
        g.disableScissor(); scrollbar(g, right - 3, rowTop, viewport);
    }
    private void clampScroll(int viewport) { scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport))); }
    private void scrollbar(GuiGraphics g, int x, int y, int viewport) {
        if (contentHeight <= viewport || viewport <= 0) return;
        int thumb = Math.min(viewport, Math.max(8, viewport * viewport / contentHeight));
        int offset = (viewport - thumb) * scroll / Math.max(1, contentHeight - viewport);
        g.fill(x, y, x + 2, y + viewport, BORDER); g.fill(x, y + offset, x + 2, y + offset + thumb, ACCENT);
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
        var lists = List.of(fromMatches, toMatches, boardMatches);
        for (int i = 0; i < lists.size(); i++) {
            var list = lists.get(i); var first = list.getFirst();
            if (vertical != 0 && first.visible && x >= first.getX() && x <= first.getX() + first.getWidth() && y >= first.getY() && y <= first.getY() + list.size() * 22) {
                matchOffsets[i] += vertical < 0 ? 1 : -1; refreshMatches(); return true;
            }
        }
        if (x >= left && x <= left + panelWidth && y >= bodyTop && y <= bodyBottom) { scroll = Math.max(0, scroll - (int)(vertical * 26)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
