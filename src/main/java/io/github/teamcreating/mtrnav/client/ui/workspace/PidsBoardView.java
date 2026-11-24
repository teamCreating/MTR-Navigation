package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.drawer.ReminderDrawer;
import io.github.teamcreating.mtrnav.client.ui.drawer.StationPickerDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.ConsistBar;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiView;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ArrivalReminder;
import io.github.teamcreating.mtrnav.core.DepartureBoard;
import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Railway concourse PIDS departure display: live clock, line filter pills, giant tabular countdowns,
 * visual car consist blocks, platform badges, and one-click slide-over reminder drawer.
 */
public final class PidsBoardView implements UiView {
    private record RowAction(UiRect rect, Model.StopEvent event) {}

    private final WorkspaceHost host;
    private final ScrollState scroll = new ScrollState();
    private final List<UiButton> filterButtons = new ArrayList<>();
    private final List<RowAction> bellHits = new ArrayList<>();
    private UiRect bounds = UiRect.EMPTY;
    private UiRect headerRect = UiRect.EMPTY;
    private UiRect tableHeaderRect = UiRect.EMPTY;
    private UiRect listRect = UiRect.EMPTY;

    private final UiButton pickStationBtn;
    private final UiButton refreshBtn;

    private String selectedStationId = "";
    private String selectedRouteFilter = ""; // empty = all lines
    private List<DepartureBoard.Group> groups = List.of();
    private List<Model.StopEvent> flatEvents = List.of();
    private List<Model.StopEvent> lastArrivals;
    private Model.Catalog lastCatalog;

    public PidsBoardView(WorkspaceHost host) {
        this.host = host;
        this.pickStationBtn = new UiButton(tr("mtrnav.all_stations"), UiButton.Style.SECONDARY, this::openStationPicker)
                .icon(UiIcons.STATIONS);
        this.refreshBtn = new UiButton(tr("mtrnav.refresh_board"), UiButton.Style.SECONDARY, () -> {
            if (!selectedStationId.isEmpty()) requestBoard(selectedStationId);
        });
    }

    public void showStation(String stationId) {
        if (stationId == null || stationId.isEmpty()) return;
        this.selectedStationId = stationId;
        this.selectedRouteFilter = "";
        this.scroll.reset();
        requestBoard(stationId);
    }

    private void requestBoard(String stationId) {
        if (NewUiFixtures.enabled()) NewUiFixtures.populateBoard(stationId);
        else board(stationId);
    }

    private void openStationPicker() {
        host.openDrawer(new StationPickerDrawer(
                tr("mtrnav.board_station"),
                selectedStationId,
                false,
                null,
                station -> {
                    showStation(station.id());
                    host.closeDrawer();
                }));
    }

    @Override
    public void onActivate() {
        if (selectedStationId.isEmpty()) {
            if (!boardStation.isEmpty()) selectedStationId = boardStation;
            else if (!origin.isEmpty()) selectedStationId = origin;
            else if (catalog != null && !catalog.stations().isEmpty()) selectedStationId = catalog.stations().getFirst().id();
        }
        if (!selectedStationId.isEmpty() && !selectedStationId.equals(boardStation)) {
            requestBoard(selectedStationId);
        }
    }

    @Override
    public void onDeactivate() {
        if (!NewUiFixtures.enabled()) stopBoard();
    }

    @Override
    public void layout(UiRect bounds) {
        this.bounds = bounds;
        UiRect inner = bounds.inset(12, 10, 12, 10);
        this.headerRect = inner.sliceTop(54);
        UiRect body = inner.trimTop(60);
        this.tableHeaderRect = body.sliceTop(18);
        this.listRect = body.trimTop(20);

        refreshBtn.bounds(new UiRect(headerRect.right() - 72, headerRect.y() + 2, 72, 20));
        pickStationBtn.bounds(new UiRect(headerRect.right() - 156, headerRect.y() + 2, 80, 20));
    }

    private void rebuildGroupsIfNeeded() {
        if (lastCatalog == catalog && lastArrivals == arrivals) return;
        lastCatalog = catalog;
        lastArrivals = arrivals;
        groups = (catalog == null || selectedStationId.isEmpty())
                ? List.of()
                : DepartureBoard.groups(catalog, selectedStationId, arrivals);

        filterButtons.clear();
        filterButtons.add(new UiButton(tr("mtrnav.ui.all_lines"), UiButton.Style.CHIP, () -> {
            selectedRouteFilter = "";
            rebuildFilteredEvents();
            scroll.reset();
        }));
        for (var group : groups) {
            String key = group.key();
            filterButtons.add(new UiButton(NavFormat.primary(group.name()), UiButton.Style.CHIP, () -> {
                selectedRouteFilter = key;
                rebuildFilteredEvents();
                scroll.reset();
            }));
        }
        rebuildFilteredEvents();
    }

    private void rebuildFilteredEvents() {
        var list = new ArrayList<Model.StopEvent>();
        for (var group : groups) {
            if (selectedRouteFilter.isEmpty() || selectedRouteFilter.equals(group.key())) {
                list.addAll(group.events());
            }
        }
        list.sort(Comparator.comparingLong(Model.StopEvent::arrival).thenComparingLong(Model.StopEvent::departure));
        flatEvents = List.copyOf(list);
    }

    @Override
    public void render(GuiGraphics g, UiContext ctx) {
        rebuildGroupsIfNeeded();
        bellHits.clear();

        // Header card
        UiDraw.card(g, headerRect, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        String stPrimary = selectedStationId.isEmpty() ? tr("mtrnav.board_station") : NavFormat.station(selectedStationId);
        var stObj = catalog == null ? null : catalog.station(selectedStationId);
        String stEn = stObj == null ? "" : NavFormat.secondary(stObj.name());

        int titleX = headerRect.x() + 10;
        UiDraw.scaledText(g, stPrimary, titleX, headerRect.y() + 6, Math.max(40, headerRect.width() - 245), UiTokens.TEXT_PRIMARY, 1.25f);
        if (!stEn.isEmpty()) {
            int pw = (int) (UiDraw.font().width(stPrimary) * 1.25f);
            UiDraw.text(g, stEn, titleX + pw + 8, headerRect.y() + 9, Math.max(0, headerRect.width() - pw - 260), UiTokens.TEXT_MUTED);
        }

        // Live clock
        String clock = NavFormat.liveClock();
        int clockW = UiDraw.font().width(clock);
        UiDraw.circle(g, headerRect.right() - 168 - clockW - 8, headerRect.y() + 12, 3f,
                boardFresh() ? UiTokens.STATUS_LIVE : UiTokens.STATUS_WARN);
        UiDraw.bold(g, clock, headerRect.right() - 168 - clockW, headerRect.y() + 8, clockW + 4, UiTokens.TEXT_PRIMARY);

        pickStationBtn.render(g, ctx);
        refreshBtn.enabled(!selectedStationId.isEmpty() && !boardLoading);
        refreshBtn.render(g, ctx);

        // Filter pills row
        int fx = headerRect.x() + 10;
        int fy = headerRect.y() + 30;
        for (int i = 0; i < filterButtons.size(); i++) {
            var btn = filterButtons.get(i);
            String key = i == 0 ? "" : groups.get(i - 1).key();
            btn.selected(selectedRouteFilter.equals(key));
            String lbl = i == 0 ? tr("mtrnav.ui.all_lines") : NavFormat.primary(groups.get(i - 1).name());
            int bw = Math.min(92, UiDraw.font().width(lbl) + 14);
            if (fx + bw > headerRect.right() - 8) break;
            btn.bounds(new UiRect(fx, fy, bw, 17));
            btn.render(g, ctx);
            fx += bw + 4;
        }

        // Table Header
        UiDraw.roundedRect(g, tableHeaderRect, UiTokens.RADIUS_SM, UiTokens.INSET);
        int x = tableHeaderRect.x();
        int w = tableHeaderRect.width();
        boolean wide = w >= 420;
        int colTime = x + 15;
        int colLine = x + 82;
        int colConsist = wide ? x + w / 2 - 10 : -1;
        int colPlat = x + w - 122;
        int colBell = x + w - 76;

        UiDraw.text(g, tr("mtrnav.arrival"), colTime, tableHeaderRect.y() + 5, 62, UiTokens.TEXT_MUTED);
        UiDraw.text(g, tr("mtrnav.line_direction"), colLine, tableHeaderRect.y() + 5, 110, UiTokens.TEXT_MUTED);
        if (wide) UiDraw.text(g, tr("mtrnav.ui.consist_col"), colConsist, tableHeaderRect.y() + 5, 110, UiTokens.TEXT_MUTED);
        UiDraw.text(g, tr("mtrnav.platform_label"), colPlat, tableHeaderRect.y() + 5, 42, UiTokens.TEXT_MUTED);
        UiDraw.text(g, tr("mtrnav.reminder_short"), colBell, tableHeaderRect.y() + 5, 68, UiTokens.TEXT_MUTED);

        // Departure list container
        UiDraw.card(g, listRect, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        if (catalog == null || selectedStationId.isEmpty()) {
            UiDraw.text(g, tr(catalog == null ? status : "mtrnav.choose_board_station"),
                    listRect.x() + 12, listRect.y() + 14, listRect.width() - 24, UiTokens.TEXT_MUTED);
            return;
        }
        if (flatEvents.isEmpty()) {
            UiDraw.text(g, tr(boardLoading ? "mtrnav.board_loading" : "mtrnav.no_departures"),
                    listRect.x() + 12, listRect.y() + 14, listRect.width() - 24, UiTokens.TEXT_MUTED);
            return;
        }

        int rowH = 44;
        scroll.update(listRect.y() + 4, listRect.height() - 8, flatEvents.size() * rowH, 16);
        UiDraw.enableScissor(g, listRect.inset(1));
        int startY = listRect.y() + 4 - scroll.offset();
        long nowNanos = ctx.nowNanos;
        long serverNow = CLOCK.now(nowNanos);
        boolean stale = !boardFresh();

        for (int i = 0; i < flatEvents.size(); i++) {
            int ry = startY + i * rowH;
            if (ry + rowH < listRect.y() || ry > listRect.bottom()) continue;
            var event = flatEvents.get(i);
            boolean armed = reminder != null && reminder.stationId().equals(selectedStationId)
                    && ArrivalReminder.sameEvent(reminder.event(), event);
            UiRect rowRect = new UiRect(listRect.x() + 4, ry, listRect.width() - 14, rowH - 3);
            boolean rowHover = rowRect.contains(ctx.mouseX, ctx.mouseY) && listRect.contains(ctx.mouseX, ctx.mouseY);

            int bg = armed ? UiTokens.withAlpha(UiTokens.PRIMARY, 28) : rowHover ? UiTokens.SURFACE_HOVER : UiTokens.SURFACE;
            int border = armed ? UiTokens.PRIMARY : UiTokens.BORDER_SUBTLE;
            UiDraw.card(g, rowRect, UiTokens.RADIUS_SM, bg, border);

            int routeColor = NavFormat.routeColor(event.routeId());
            // Left vertical route strip
            UiDraw.roundedRect(g, rowRect.x() + 3, rowRect.y() + 5, 3, rowRect.height() - 10, 1.5f, routeColor);

            // Countdown + clock + live/plan
            long leftMs = event.arrival() - serverNow;
            String cd = stale ? "—" : NavFormat.remaining(event.arrival());
            int cdColor = !stale && leftMs <= 180_000L ? UiTokens.STATUS_LIVE : UiTokens.TEXT_PRIMARY;
            UiDraw.scaledText(g, cd, colTime, ry + 7, 60, cdColor, 1.35f);
            UiDraw.text(g, NavFormat.clockTime(event.departure()) + " · " + tr(event.realtime() ? "mtrnav.live_short" : "mtrnav.schedule_short"),
                    colTime, ry + 26, 66, event.realtime() ? UiTokens.STATUS_LIVE : UiTokens.TEXT_MUTED);

            // Line + destination
            int lineMax = Math.max(40, (wide ? colConsist : colPlat) - colLine - 8);
            UiDraw.bold(g, NavFormat.primary(event.destination()), colLine, ry + 8, lineMax, UiTokens.TEXT_PRIMARY);
            UiDraw.text(g, NavFormat.route(event.routeId()) + " · " + UiBridge.routeCategory(NavFormat.routeType(event.routeId())),
                    colLine, ry + 23, lineMax, UiTokens.TEXT_SECONDARY);

            // Consist visualization (wide)
            if (wide) {
                int cw = Math.max(40, colPlat - colConsist - 8);
                ConsistBar.render(g, event.consist(), routeColor, colConsist, ry + 10, cw);
                UiDraw.text(g, UiBridge.stockCompact(event.consist(), cw), colConsist, ry + 25, cw, UiTokens.TEXT_MUTED);
            }

            // Platform badge
            String plat = NavFormat.platform(event.platformId());
            int pw = Math.min(38, Math.max(22, UiDraw.font().width(plat) + 10));
            UiDraw.card(g, new UiRect(colPlat, ry + 11, pw, 18), 4f, UiTokens.SURFACE_ELEVATED, UiTokens.BORDER_DEFAULT);
            UiDraw.bold(g, plat, colPlat + (pw - Math.min(pw - 6, UiDraw.font().width(plat))) / 2, ry + 16, pw - 4, UiTokens.TEXT_PRIMARY);

            // Bell reminder button
            UiRect bellRect = new UiRect(colBell, ry + 10, 66, 20);
            boolean bellHover = bellRect.contains(ctx.mouseX, ctx.mouseY) && listRect.contains(ctx.mouseX, ctx.mouseY);
            int bellBg = armed ? UiTokens.PRIMARY : bellHover ? UiTokens.SURFACE_ELEVATED : UiTokens.INSET;
            int bellInk = armed ? UiTokens.PRIMARY_INK : UiTokens.TEXT_PRIMARY;
            UiDraw.card(g, bellRect, 4f, bellBg, armed ? UiTokens.PRIMARY_HOVER : UiTokens.BORDER_DEFAULT);
            UiIcons.BELL.draw(g, bellRect.x() + 5, bellRect.y() + 5, 10, bellInk);
            String bellText = armed ? tr("mtrnav.ui.rem_armed_short", reminder.minutes()) : tr("mtrnav.reminder_short");
            UiDraw.text(g, bellText, bellRect.x() + 18, bellRect.y() + 6, bellRect.width() - 22, bellInk);
            UiRect clippedBell = bellRect.intersect(listRect);
            if (!clippedBell.isEmpty()) bellHits.add(new RowAction(clippedBell, event));
        }

        g.disableScissor();
        UiDraw.scrollbar(g, scroll, listRect.right() - 7, 4);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (pickStationBtn.mouseClicked(x, y, button) || refreshBtn.mouseClicked(x, y, button)) return true;
        for (var btn : filterButtons) if (btn.mouseClicked(x, y, button)) return true;
        if (button == 0 && scroll.beginDrag(x, y, listRect.right() - 7, 4)) return true;
        if (button == 0) {
            for (var hit : bellHits) {
                if (hit.rect().contains(x, y)) {
                    int lead = reminder != null && reminder.stationId().equals(selectedStationId)
                            && ArrivalReminder.sameEvent(reminder.event(), hit.event())
                            ? reminder.minutes() : ArrivalReminder.DEFAULT_MINUTES;
                    host.openDrawer(new ReminderDrawer(selectedStationId, hit.event(), lead));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && scroll.drag(y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = pickStationBtn.mouseReleased(x, y, button) | refreshBtn.mouseReleased(x, y, button);
        for (var btn : filterButtons) handled |= btn.mouseReleased(x, y, button);
        if (button == 0 && scroll.endDrag()) return true;
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return listRect.contains(x, y) && scroll.scrollBy(vertical, 34);
    }
}
