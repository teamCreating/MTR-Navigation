package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.ConsistBar;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * High-clarity vertical itinerary timeline with anti-aliased route rails, visual consist bars,
 * expandable intermediate stops / car order, and click-station-to-board navigation.
 */
public final class TimelineDetailPane {
    private record Hit(UiRect rect, Runnable action, String tooltip) {}

    private final WorkspaceHost host;
    private final ScrollState scroll = new ScrollState();
    private final Set<String> expandedKeys = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private UiRect bounds = UiRect.EMPTY;

    public TimelineDetailPane(WorkspaceHost host) {
        this.host = host;
    }

    public void resetScroll() { scroll.reset(); }
    public void layout(UiRect rect) { this.bounds = rect; }

    public void renderJourney(GuiGraphics g, UiContext ctx, Model.Journey journey, int highlightLegIndex) {
        hits.clear();
        if (bounds.isEmpty()) return;
        UiDraw.card(g, bounds, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        if (journey == null) return;

        UiRect inner = bounds.inset(10, 8, 14, 8);
        int contentW = inner.width();

        // First measure total height, then render inside scissor
        int totalH = measureJourney(journey, contentW);
        scroll.update(inner.y(), inner.height(), totalH, 16);

        UiDraw.enableScissor(g, bounds.inset(1));
        int y = inner.y() - scroll.offset();
        int x = inner.x();

        // Summary header block
        boolean untimed = ConnectionPlan.isGuidedJourney(journey);
        if (untimed) {
            int stops = journey.legs().stream().mapToInt(l -> l.viaStations().size() + 1).sum();
            UiDraw.scaledText(g, tr("mtrnav.ui.ref_nav_tag"), x, y, Math.max(40, contentW - 110), UiTokens.STATUS_WARN, 1.35f);
            String meta = tr("mtrnav.transfers", journey.transfers()) + " · " + tr("mtrnav.ui.ref_stops", stops);
            UiDraw.text(g, meta, x, y + 16, contentW, UiTokens.TEXT_SECONDARY);
        } else {
            UiDraw.scaledText(g, NavFormat.duration(journey.arrival() - journey.queryTime()), x, y, Math.max(40, contentW - 110), UiTokens.TEXT_PRIMARY, 1.35f);
            String meta = tr("mtrnav.transfers", journey.transfers()) + " · " + NavFormat.clockTime(journey.arrival()) + " " + tr("mtrnav.arrival");
            UiDraw.text(g, meta, x, y + 16, contentW, UiTokens.TEXT_SECONDARY);
        }
        if (UiBridge.overlayWorldReady(journey.dimension())) {
            String sync = tr("mtrnav.ui.overlay_sync_hint");
            int sw = Math.min(contentW, UiDraw.font().width(sync) + 10);
            UiDraw.pill(g, sync, x + contentW - sw, y + 1, sw, UiTokens.withAlpha(UiTokens.PRIMARY, 42), UiTokens.STATUS_LIVE);
        }
        y += 30;
        if (partialResults && !untimed) {
            y = UiDraw.wrapped(g, tr("mtrnav.partial_results"), x, y, contentW, 11, UiTokens.STATUS_WARN) + 3;
        }
        if (!untimed && fastestArrival > 0 && journey.arrival() > fastestArrival) {
            UiDraw.text(g, tr("mtrnav.walk_slower", (journey.arrival() - fastestArrival + 59_999L) / 60_000L), x, y, contentW, UiTokens.TEXT_MUTED);
            y += 12;
        }
        UiDraw.rect(g, x, y, contentW, 1, UiTokens.BORDER_SUBTLE);
        y += 8;

        if (journey.legs().isEmpty()) {
            UiDraw.text(g, tr("mtrnav.same_station"), x, y + 4, contentW, UiTokens.TEXT_PRIMARY);
        }

        for (int i = 0; i <= journey.legs().size(); i++) {
            for (var walk : journey.walks()) {
                if (walk.beforeRideIndex() == i) {
                    y = renderWalkCard(g, walk, x, y, contentW);
                }
            }
            if (i == journey.legs().size()) break;
            var leg = journey.legs().get(i);
            boolean activeLeg = i == highlightLegIndex;
            if (!untimed && (i > 0 || journey.walks().stream().anyMatch(w -> w.beforeRideIndex() == 0))) {
                final int before = i;
                long walked = journey.walks().stream().filter(w -> w.beforeRideIndex() == before).mapToLong(w -> w.seconds() * 1000L).sum();
                long gap = leg.departure() - (i == 0 ? journey.queryTime() : journey.legs().get(i - 1).arrival()) - walked;
                long buffer = Math.min(Math.max(0, gap), journey.transferBufferSeconds() * 1000L);
                UiDraw.text(g, tr("mtrnav.walk_buffer_wait", buffer / 1000, (Math.max(0, gap - buffer) + 999) / 1000),
                        x + 4, y, contentW - 8, UiTokens.STATUS_WARN);
                y += 13;
            }
            y = renderRideLeg(g, ctx, journey.id(), i, leg, x, y, contentW, activeLeg);
        }

        g.disableScissor();
        UiDraw.scrollbar(g, scroll, bounds.right() - 7, 4);
    }

    public void renderReference(GuiGraphics g, UiContext ctx, ConnectionPlan plan, String reasonKey) {
        hits.clear();
        if (bounds.isEmpty() || plan == null) return;
        UiDraw.card(g, bounds, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        UiRect inner = bounds.inset(10, 8, 14, 8);
        int x = inner.x(), contentW = inner.width();
        int y = inner.y() - scroll.offset();

        UiDraw.enableScissor(g, bounds.inset(1));
        int startY = y;
        UiDraw.pill(g, tr("mtrnav.reference_route"), x, y, 90, UiTokens.withAlpha(UiTokens.STATUS_WARN, 50), UiTokens.STATUS_WARN);
        UiDraw.text(g, tr("mtrnav.transfers", plan.transfers()), x + 94, y + 3, contentW - 94, UiTokens.TEXT_SECONDARY);
        y += 18;
        y = UiDraw.wrapped(g, tr(reasonKey), x, y, contentW, 12, UiTokens.STATUS_WARN) + 4;
        y = UiDraw.wrapped(g, tr("mtrnav.reference_only"), x, y, contentW, 12, UiTokens.TEXT_MUTED) + 8;

        for (int i = 0; i <= plan.legs().size(); i++) {
            for (var walk : plan.walks()) if (walk.beforeRideIndex() == i) y = renderWalkCard(g, walk, x, y, contentW);
            if (i == plan.legs().size()) break;
            var leg = plan.legs().get(i);
            int tint = NavFormat.routeColor(leg.routeId());
            UiRect head = new UiRect(x, y, contentW, 20);
            UiDraw.roundedRect(g, head, UiTokens.RADIUS_SM, UiTokens.SURFACE);
            int bw = UiDraw.lineBadge(g, NavFormat.routeCode(leg.routeId()), x + 6, y + 3, 92, tint);
            UiDraw.text(g, UiBridge.routeCategory(NavFormat.routeType(leg.routeId())) + " · " + tr("mtrnav.direction", NavFormat.primary(leg.direction())),
                    x + bw + 12, y + 6, contentW - bw - 18, UiTokens.TEXT_SECONDARY);
            y += 24;

            int railTop = y + 8;
            renderStationRow(g, ctx, leg.fromStationId(), leg.fromPlatformId(), "", tint, true, x, y, contentW);
            y += 22;
            String viaKey = "ref/" + leg.routeId() + "/" + i;
            if (!leg.viaStations().isEmpty()) {
                y = renderViaToggle(g, ctx, viaKey, leg.viaStations(), tint, x, y, contentW);
            }
            int railBottom = y + 8;
            UiDraw.line(g, x + 9, railTop, x + 9, railBottom, 2.2f, tint);
            renderStationRow(g, ctx, leg.toStationId(), leg.toPlatformId(), "", tint, false, x, y, contentW);
            y += 26;
        }
        g.disableScissor();
        scroll.update(inner.y(), inner.height(), y - startY, 16);
        UiDraw.scrollbar(g, scroll, bounds.right() - 7, 4);
    }

    private int renderRideLeg(GuiGraphics g, UiContext ctx, String journeyId, int index, Model.RideLeg leg,
                              int x, int y, int w, boolean active) {
        int tint = NavFormat.routeColor(leg.routeId());
        // Leg header card
        UiRect head = new UiRect(x, y, w, 22);
        UiDraw.card(g, head, UiTokens.RADIUS_SM, active ? UiTokens.SURFACE_SELECTED : UiTokens.SURFACE,
                active ? UiTokens.BORDER_SELECTED : UiTokens.BORDER_SUBTLE);
        int bw = UiDraw.lineBadge(g, NavFormat.routeCode(leg.routeId()), x + 6, y + 4, Math.min(96, w / 3), tint);
        UiDraw.transportIcon(g, NavFormat.routeType(leg.routeId()), x + bw + 9, y + 4, 13);
        UiDraw.text(g, tr("mtrnav.direction", NavFormat.primary(leg.direction())),
                x + bw + 26, y + 7, w - bw - 32, UiTokens.TEXT_PRIMARY);
        y += 25;

        // Consist bar + optional car details toggle (skip for untimed reference guide)
        boolean untimed = leg.departure() <= 0 && leg.arrival() <= 0;
        if (!untimed) {
            ConsistBar.render(g, leg.consist(), tint, x + 18, y, w - 22);
            y += 12;
        }
        if (!leg.consist().cars().isEmpty()) {
            String stockKey = journeyId + "/stock/" + index;
            boolean open = expandedKeys.contains(stockKey);
            UiRect stockRow = new UiRect(x + 18, y, w - 22, 16);
            boolean hov = stockRow.contains(ctx.mouseX, ctx.mouseY) && bounds.contains(ctx.mouseX, ctx.mouseY);
            if (hov) UiDraw.roundedRect(g, stockRow, 3f, UiTokens.SURFACE_HOVER);
            (open ? UiIcons.CHEVRON_DOWN : UiIcons.CHEVRON_RIGHT).draw(g, x + 20, y + 3, 9, UiTokens.TEXT_MUTED);
            UiDraw.text(g, tr(open ? "mtrnav.vehicle_details_collapse" : "mtrnav.vehicle_details"),
                    x + 32, y + 4, w - 40, UiTokens.TEXT_MUTED);
            addHit(stockRow, () -> toggleKey(stockKey), null);
            y += 17;
            if (open) {
                y = UiDraw.wrapped(g, UiBridge.stockOrdered(leg.consist()), x + 22, y + 1, w - 28, 11, UiTokens.TEXT_MUTED) + 4;
            }
        }

        // Compute vertical rail span
        int railStartY = y + 9;
        // Origin station row
        renderStationRow(g, ctx, leg.fromStationId(), leg.fromPlatformId(), untimed ? "" : NavFormat.clockTime(leg.departure()), tint, true, x, y, w);
        y += untimed ? 22 : 18;
        if (!untimed) {
            // Departure countdown & live/plan status
            String depLine = tr("mtrnav.departure") + " " + NavFormat.remaining(leg.departure())
                    + " · " + tr(leg.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled");
            UiDraw.text(g, depLine, x + 20, y, w - 26, leg.realtime() ? UiTokens.STATUS_LIVE : UiTokens.TEXT_SECONDARY);
            y += 14;
        }

        // Expandable intermediate stops
        String viaKey = journeyId + "/via/" + index;
        if (!leg.viaStations().isEmpty()) {
            y = renderViaToggle(g, ctx, viaKey, leg.viaStations(), tint, x, y, w);
        }

        // Destination station row
        int railEndY = y + 9;
        UiDraw.line(g, x + 9, railStartY, x + 9, railEndY, 2.4f, tint);
        // Re-draw origin & destination circles cleanly above rail
        UiDraw.circle(g, x + 9, railStartY, 4.0f, UiTokens.INSET);
        UiDraw.ring(g, x + 9, railStartY, 2.2f, 4.0f, tint);
        UiDraw.circle(g, x + 9, railEndY, 4.0f, tint);

        renderStationRow(g, ctx, leg.toStationId(), leg.toPlatformId(), untimed ? "" : NavFormat.clockTime(leg.arrival()), tint, false, x, y, w);
        y += untimed ? 24 : 18;
        if (!untimed) {
            UiDraw.text(g, tr("mtrnav.arrival") + " " + NavFormat.remaining(leg.arrival()), x + 20, y, w - 26, UiTokens.TEXT_MUTED);
            y += 16;
        }
        return y;
    }

    private int renderViaToggle(GuiGraphics g, UiContext ctx, String key, List<String> viaStations, int tint, int x, int y, int w) {
        boolean open = expandedKeys.contains(key);
        UiRect row = new UiRect(x + 18, y, w - 22, 18);
        boolean hov = row.contains(ctx.mouseX, ctx.mouseY) && bounds.contains(ctx.mouseX, ctx.mouseY);
        UiDraw.roundedRect(g, row, 4f, hov ? UiTokens.SURFACE_ELEVATED : UiTokens.SURFACE);
        (open ? UiIcons.CHEVRON_DOWN : UiIcons.CHEVRON_RIGHT).draw(g, x + 22, y + 4, 10, UiTokens.TEXT_SECONDARY);
        UiDraw.text(g, tr(open ? "mtrnav.stops_collapse" : "mtrnav.stops_count", viaStations.size()),
                x + 35, y + 5, w - 44, UiTokens.TEXT_SECONDARY);
        addHit(row, () -> toggleKey(key), null);
        y += 21;
        if (open) {
            for (String stId : viaStations) {
                UiRect stRow = new UiRect(x + 16, y, w - 20, 17);
                boolean sh = stRow.contains(ctx.mouseX, ctx.mouseY) && bounds.contains(ctx.mouseX, ctx.mouseY);
                if (sh) UiDraw.roundedRect(g, stRow, 3f, UiTokens.SURFACE_HOVER);
                UiDraw.circle(g, x + 9, y + 8, 2.8f, UiTokens.INSET);
                UiDraw.ring(g, x + 9, y + 8, 1.5f, 2.8f, tint);
                UiDraw.text(g, NavFormat.station(stId), x + 22, y + 4, w - 30, sh ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_SECONDARY);
                addHit(stRow, () -> host.openStationBoard(stId), tr("mtrnav.station_board_hint", NavFormat.station(stId)));
                y += 18;
            }
        }
        return y;
    }

    private void renderStationRow(GuiGraphics g, UiContext ctx, String stationId, String platformId, String clock,
                                  int tint, boolean hollow, int x, int y, int w) {
        UiRect row = new UiRect(x + 16, y, w - 18, 18);
        boolean hov = row.contains(ctx.mouseX, ctx.mouseY) && bounds.contains(ctx.mouseX, ctx.mouseY);
        if (hov) UiDraw.roundedRect(g, row, 4f, UiTokens.SURFACE_HOVER);

        if (hollow) {
            UiDraw.circle(g, x + 9, y + 9, 4.0f, UiTokens.INSET);
            UiDraw.ring(g, x + 9, y + 9, 2.2f, 4.0f, tint);
        } else {
            UiDraw.circle(g, x + 9, y + 9, 4.0f, tint);
        }

        int clockW = clock.isEmpty() ? 0 : UiDraw.font().width(clock) + 6;
        String platText = tr("mtrnav.platform", NavFormat.platform(platformId));
        int platW = Math.min(92, UiDraw.font().width(platText) + 10);
        int nameMax = Math.max(24, w - 24 - clockW - platW - 10);
        String name = NavFormat.station(stationId);
        UiDraw.bold(g, name, x + 20, y + 5, nameMax, UiTokens.TEXT_PRIMARY);
        int drawnNameW = Math.min(nameMax, UiDraw.font().width(name) + 6);
        UiDraw.pill(g, platText, x + 20 + drawnNameW + 4, y + 2, platW, UiTokens.SURFACE_ELEVATED, UiTokens.TEXT_SECONDARY);
        if (!clock.isEmpty()) {
            UiDraw.text(g, clock, x + w - clockW, y + 5, clockW, UiTokens.TEXT_PRIMARY);
        }
        addHit(row, () -> host.openStationBoard(stationId), tr("mtrnav.station_board_hint", name));
    }

    private int renderWalkCard(GuiGraphics g, Model.WalkLeg walk, int x, int y, int w) {
        UiRect card = new UiRect(x + 14, y, w - 18, 36);
        UiDraw.roundedRect(g, card, UiTokens.RADIUS_SM, UiTokens.withAlpha(UiTokens.STATUS_WARN, 22));
        UiDraw.roundedOutline(g, card, UiTokens.RADIUS_SM, 1f, UiTokens.withAlpha(UiTokens.STATUS_WARN, 110));
        UiDraw.dashedLine(g, x + 9, y + 2, x + 9, y + 34, 2f, 3f, 3f, UiTokens.STATUS_WARN);
        UiIcons.WALK.draw(g, x + 20, y + 6, 11, UiTokens.STATUS_WARN);
        UiDraw.text(g, tr("mtrnav.walk_to", NavFormat.station(walk.toStationId())) + " · " + tr("mtrnav.walk_estimated", walk.seconds()),
                x + 35, y + 7, w - 46, UiTokens.TEXT_PRIMARY);
        String detail = NavFormat.station(walk.fromStationId())
                + (walk.fromPlatformId().isEmpty() ? "" : " / " + NavFormat.platform(walk.fromPlatformId()))
                + " → " + NavFormat.station(walk.toStationId())
                + (walk.toPlatformId().isEmpty() ? "" : " / " + NavFormat.platform(walk.toPlatformId()))
                + " · " + tr("mtrnav.walk_source." + walk.source());
        UiDraw.text(g, detail, x + 20, y + 21, w - 30, UiTokens.TEXT_SECONDARY);
        return y + 42;
    }

    private int measureJourney(Model.Journey journey, int w) {
        int h = 42;
        if (partialResults) h += 16;
        if (fastestArrival > 0 && journey.arrival() > fastestArrival) h += 12;
        for (int i = 0; i <= journey.legs().size(); i++) {
            for (var walk : journey.walks()) if (walk.beforeRideIndex() == i) h += 42;
            if (i == journey.legs().size()) break;
            var leg = journey.legs().get(i);
            boolean untimed = leg.departure() <= 0 && leg.arrival() <= 0;
            if (!untimed && (i > 0 || journey.walks().stream().anyMatch(wk -> wk.beforeRideIndex() == 0))) h += 13;
            h += untimed ? (25 + 22 + 24) : (25 + 12 + 18 + 14 + 18 + 16);
            if (!leg.consist().cars().isEmpty()) {
                h += 17;
                if (expandedKeys.contains(journey.id() + "/stock/" + i)) {
                    h += UiDraw.wrappedHeight(UiBridge.stockOrdered(leg.consist()), w - 28, 11) + 5;
                }
            }
            if (!leg.viaStations().isEmpty()) {
                h += 21;
                if (expandedKeys.contains(journey.id() + "/via/" + i)) {
                    h += leg.viaStations().size() * 18;
                }
            }
        }
        return h + 8;
    }

    private void addHit(UiRect rect, Runnable action, String tooltip) {
        UiRect clipped = rect.intersect(bounds);
        if (!clipped.isEmpty()) hits.add(new Hit(clipped, action, tooltip));
    }

    private void toggleKey(String key) {
        if (!expandedKeys.add(key)) expandedKeys.remove(key);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        if (button == 0 && scroll.beginDrag(x, y, bounds.right() - 7, 4)) return true;
        if (button == 0) {
            for (var hit : hits) {
                if (hit.rect().contains(x, y)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double x, double y, int button) {
        return button == 0 && scroll.drag(y);
    }

    public boolean mouseReleased(int button) {
        return button == 0 && scroll.endDrag();
    }

    public boolean mouseScrolled(double x, double y, double vertical) {
        return bounds.contains(x, y) && scroll.scrollBy(vertical, 28);
    }

    public void updateTooltips(UiContext ctx) {
        for (var hit : hits) {
            if (hit.tooltip() != null && hit.rect().contains(ctx.mouseX, ctx.mouseY)) {
                ctx.tooltip(hit.tooltip());
                return;
            }
        }
    }
}
