package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.client.ui.drawer.ConfirmDrawer;
import io.github.teamcreating.mtrnav.client.ui.drawer.StationPickerDrawer;
import io.github.teamcreating.mtrnav.client.ui.drawer.VehicleFilterDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiTextInput;
import io.github.teamcreating.mtrnav.client.ui.framework.UiView;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/**
 * Blended Journey Planner: left connected origin/destination + filter chips + 5 route cards;
 * right dual-mode pane switching cleanly between Itinerary Timeline and 2D Transit Topology.
 */
public final class JourneyPlanView implements UiView {
    private enum RightMode { TIMELINE, TOPOLOGY }

    private final WorkspaceHost host;
    private final TimelineDetailPane timeline;
    private final TopologyCanvasPane topology;
    private final ScrollState cardScroll = new ScrollState();

    private final UiTextInput originInput;
    private final UiTextInput destInput;
    private final UiButton locateBtn;
    private final UiButton pickOriginBtn;
    private final UiButton swapBtn;
    private final UiButton pickDestBtn;
    private final UiButton searchBtn;
    private final UiButton sortBtn;
    private final UiButton vehicleBtn;
    private final UiButton modeTimelineBtn;
    private final UiButton modeTopologyBtn;
    private final UiButton compactListBtn;
    private final UiButton compactDetailBtn;
    private final UiButton startBtn;

    private UiRect bounds = UiRect.EMPTY;
    private UiRect leftCol = UiRect.EMPTY;
    private UiRect rightCol = UiRect.EMPTY;
    private UiRect cardsArea = UiRect.EMPTY;
    private UiRect rightBody = UiRect.EMPTY;
    private UiRect footerRect = UiRect.EMPTY;
    private boolean wide = true;
    private boolean compactShowRight;

    private static RightMode rememberedRightMode = RightMode.TOPOLOGY;
    private static boolean rememberedUserToggled;

    private RightMode rightMode = rememberedRightMode;
    private boolean userToggledRightMode = rememberedUserToggled;

    public static void resetSessionView() {
        rememberedRightMode = RightMode.TOPOLOGY;
        rememberedUserToggled = false;
    }
    private int selectedJourney;
    private String locationMessage = "";
    private String pendingSelection = "";
    private List<Journey> lastJourneys = List.of();
    private List<ConnectionPlan> lastReferencePlans = List.of();
    private Catalog locationCatalog;
    private final List<UiRect> cardHitRects = new ArrayList<>();

    private List<ConnectionPlan> activeReferencePlans() {
        if (!referencePlans.isEmpty()) return referencePlans;
        return referencePlan == null ? List.of() : List.of(referencePlan);
    }

    public JourneyPlanView(WorkspaceHost host) {
        this.host = host;
        this.timeline = new TimelineDetailPane(host);
        this.topology = new TopologyCanvasPane(host);

        this.originInput = new UiTextInput(tr("mtrnav.origin"))
                .placeholder(tr("mtrnav.origin") + " · " + tr("mtrnav.station_hint"))
                .selectAllOnFocus(true)
                .responder(v -> {
                    UiBridge.originLocation().edited();
                    locationMessage = "";
                    applyTypedStation(true, v);
                });
        this.destInput = new UiTextInput(tr("mtrnav.destination"))
                .placeholder(tr("mtrnav.destination") + " · " + tr("mtrnav.station_hint"))
                .selectAllOnFocus(true)
                .responder(v -> applyTypedStation(false, v));

        this.locateBtn = new UiButton("", UiButton.Style.SECONDARY, this::locateOrigin)
                .icon(UiIcons.LOCATE).tooltip(tr("mtrnav.locate_hint"));
        this.pickOriginBtn = new UiButton("", UiButton.Style.SECONDARY, () -> openStationPicker(true))
                .icon(UiIcons.STATIONS).tooltip(tr("mtrnav.all_stations_hint"));
        this.swapBtn = new UiButton("", UiButton.Style.SECONDARY, this::swapEndpoints)
                .icon(UiIcons.SWAP).tooltip(tr("mtrnav.swap"));
        this.pickDestBtn = new UiButton("", UiButton.Style.SECONDARY, () -> openStationPicker(false))
                .icon(UiIcons.STATIONS).tooltip(tr("mtrnav.all_stations_hint"));

        this.searchBtn = new UiButton(tr("mtrnav.search"), UiButton.Style.PRIMARY, this::triggerSearch)
                .icon(UiIcons.SEARCH);
        this.sortBtn = new UiButton(tr("mtrnav.fastest"), UiButton.Style.CHIP, () -> {
            sort = sort == Sort.FASTEST ? Sort.FEWEST_TRANSFERS : Sort.FASTEST;
            if (!origin.isEmpty() && !destination.isEmpty()) triggerSearch();
        }).tooltip(tr("mtrnav.sort_hint"));
        this.vehicleBtn = new UiButton(tr("mtrnav.vehicle_filter"), UiButton.Style.CHIP,
                () -> host.openDrawer(new VehicleFilterDrawer()))
                .icon(UiIcons.FILTER).tooltip(tr("mtrnav.vehicle_filter"));

        this.modeTimelineBtn = new UiButton(tr("mtrnav.ui.mode_timeline"), UiButton.Style.CHIP, () -> {
            rightMode = RightMode.TIMELINE;
            userToggledRightMode = true;
            rememberedRightMode = rightMode;
            rememberedUserToggled = true;
        }).icon(UiIcons.ROUTE);
        this.modeTopologyBtn = new UiButton(tr("mtrnav.ui.mode_topology"), UiButton.Style.CHIP, () -> {
            rightMode = RightMode.TOPOLOGY;
            userToggledRightMode = true;
            rememberedRightMode = rightMode;
            rememberedUserToggled = true;
        }).icon(UiIcons.MAP);

        this.compactListBtn = new UiButton(tr("mtrnav.ui.list"), UiButton.Style.CHIP, () -> compactShowRight = false);
        this.compactDetailBtn = new UiButton(tr("mtrnav.ui.details"), UiButton.Style.CHIP, () -> compactShowRight = true);

        this.startBtn = new UiButton(tr("mtrnav.start"), UiButton.Style.PRIMARY, this::startSelectedJourney)
                .icon(UiIcons.CHECK);
    }

    public void setRightModeTopology(boolean topologyMode) {
        this.rightMode = topologyMode ? RightMode.TOPOLOGY : RightMode.TIMELINE;
        this.userToggledRightMode = true;
        rememberedRightMode = this.rightMode;
        rememberedUserToggled = true;
    }

    public void setStation(String stationId, boolean asOrigin) {
        if (catalog == null || catalog.station(stationId) == null) return;
        if (asOrigin) {
            UiBridge.originLocation().edited();
            locationMessage = "";
            origin = stationId;
            originInput.setValue(catalog.stationName(stationId));
        } else {
            destination = stationId;
            destInput.setValue(catalog.stationName(stationId));
        }
        clearSearchResults();
        selectedJourney = 0;
        cardScroll.reset();
        timeline.resetScroll();
    }

    private void syncInputsFromState() {
        if (catalog == null) return;
        if (!origin.isEmpty() && originInput.value().isEmpty()) originInput.setValue(catalog.stationName(origin));
        if (!destination.isEmpty() && destInput.value().isEmpty()) destInput.setValue(catalog.stationName(destination));
    }

    private void applyTypedStation(boolean isOrigin, String text) {
        String id = catalog == null ? "" : StationSearch.exact(catalog.stations(), text);
        String prev = isOrigin ? origin : destination;
        if (isOrigin) origin = id;
        else destination = id;
        if (!prev.equals(id)) clearSearchResults();
        if (Set.of("mtrnav.search_failed", "mtrnav.search_budget", "mtrnav.station_refreshed", "mtrnav.no_route").contains(status)) {
            status = "mtrnav.ready";
        }
    }

    private void swapEndpoints() {
        UiBridge.originLocation().edited();
        locationMessage = "";
        String oId = origin, dId = destination;
        String oTxt = originInput.value(), dTxt = destInput.value();
        origin = dId;
        destination = oId;
        originInput.setValue(dTxt);
        destInput.setValue(oTxt);
        clearSearchResults();
    }

    private void triggerSearch() {
        if (usingNative && status.equals("mtrnav.native_unavailable")) {
            UiBridge.notifyNativeOpened();
            return;
        }
        if (NewUiFixtures.enabled()) {
            selectedJourney = 0;
            cardScroll.reset();
            timeline.resetScroll();
            NewUiFixtures.query();
            return;
        }
        if (!usingNative && WalkingClient.supportsQuery() && WalkingClient.needsConsent()) {
            host.openDrawer(new ConfirmDrawer(
                    tr("mtrnav.walk_consent_title"),
                    tr("mtrnav.walk_consent_explanation"),
                    tr("mtrnav.walk_consent_agree"),
                    tr("mtrnav.walk_consent_decline"),
                    () -> { WalkingClient.consent(true); if (WalkingClient.consent()) triggerSearch(); },
                    () -> { WalkingClient.consent(false); triggerSearch(); }));
            return;
        }
        selectedJourney = 0;
        cardScroll.reset();
        timeline.resetScroll();
        query();
    }

    private void startSelectedJourney() {
        if (journeys.isEmpty()) return;
        var chosen = journeys.get(Math.clamp(selectedJourney, 0, journeys.size() - 1));
        if (NewUiFixtures.enabled()) {
            tracking = new JourneyTracker.State(chosen, 0, JourneyTracker.Phase.WAITING, "", false);
            host.switchTab(WorkspaceHost.Tab.ACTIVE);
            return;
        }
        pendingSelection = chosen.id();
        select(chosen);
    }

    private void openStationPicker(boolean forOrigin) {
        host.openDrawer(new StationPickerDrawer(
                tr(forOrigin ? "mtrnav.origin" : "mtrnav.destination"),
                forOrigin ? origin : destination,
                false,
                null,
                station -> {
                    setStation(station.id(), forOrigin);
                    host.closeDrawer();
                }));
    }

    private void locateOrigin() {
        UiBridge.originLocation().attempted();
        var position = UiBridge.playerPosition();
        if (position == null) { locationMessage = tr("mtrnav.location_no_player"); return; }
        if (catalog == null || !catalog.dimension().equals(position.dimension()) || catalog.stations().isEmpty()) {
            locationMessage = tr("mtrnav.location_loading"); return;
        }
        var candidates = StationLocator.nearby(catalog, UiBridge.locatedStations(), position);
        if (candidates.isEmpty()) { locationMessage = tr("mtrnav.location_no_coordinates"); return; }
        if (StationLocator.unambiguous(candidates)) {
            var st = candidates.getFirst().station();
            setStation(st.id(), true);
            UiBridge.originLocation().selected(st.id(), position);
            locationMessage = "";
        } else {
            host.openDrawer(new StationPickerDrawer(
                    tr("mtrnav.nearby_stations"),
                    origin,
                    true,
                    position,
                    station -> {
                        setStation(station.id(), true);
                        UiBridge.originLocation().selected(station.id(), position);
                        host.closeDrawer();
                    }));
        }
    }

    @Override
    public void onActivate() {
        syncInputsFromState();
        if (!userToggledRightMode) {
            rightMode = (!journeys.isEmpty() || referencePlan != null) ? RightMode.TIMELINE : RightMode.TOPOLOGY;
        }
    }

    @Override
    public void tick() {
        syncInputsFromState();
        if (tracking == null && !NewUiFixtures.enabled()
                && UiBridge.originLocation().ready(!origin.isEmpty(), originInput.value().isBlank(),
                        UiBridge.playerPosition() != null, catalog != null && !catalog.stations().isEmpty())) {
            locateOrigin();
        }
        if (locationCatalog != catalog) {
            locationCatalog = catalog;
            var pos = UiBridge.playerPosition();
            if (pos != null && UiBridge.originLocation().positionFor(origin, pos.dimension()) != null
                    && (catalog == null || !catalog.dimension().equals(pos.dimension()) || catalog.station(origin) == null
                    || UiBridge.locatedStations() != null && !UiBridge.locatedStations().contains(origin))) {
                UiBridge.originLocation().edited();
            }
        }
        if (origin.isEmpty() && !originInput.value().isBlank()) applyTypedStation(true, originInput.value());
        if (destination.isEmpty() && !destInput.value().isBlank()) applyTypedStation(false, destInput.value());

        if (!pendingSelection.isEmpty() && tracking != null && tracking.journey().id().equals(pendingSelection)) {
            pendingSelection = "";
            host.switchTab(WorkspaceHost.Tab.ACTIVE);
        }
        if (lastJourneys != journeys || lastReferencePlans != referencePlans) {
            lastJourneys = journeys;
            lastReferencePlans = referencePlans;
            selectedJourney = 0;
            cardScroll.reset();
            timeline.resetScroll();
            if (!userToggledRightMode) {
                rightMode = (!journeys.isEmpty() || !activeReferencePlans().isEmpty()) ? RightMode.TIMELINE : RightMode.TOPOLOGY;
            }
        }
    }

    @Override
    public void layout(UiRect bounds) {
        this.bounds = bounds;
        UiRect inner = bounds.inset(10, 8, 10, 8);
        this.wide = inner.width() >= 450;

        int leftW = wide ? Math.clamp(inner.width() * 41 / 100, 196, 244) : inner.width();
        this.leftCol = wide ? inner.sliceLeft(leftW) : inner;
        this.rightCol = wide ? inner.trimLeft(leftW + 10) : inner;

        int topOffset = wide ? 0 : 24;
        if (!wide) {
            int hw = (inner.width() - 6) / 2;
            compactListBtn.bounds(new UiRect(inner.x(), inner.y(), hw, 18));
            compactDetailBtn.bounds(new UiRect(inner.x() + hw + 6, inner.y(), inner.width() - hw - 6, 18));
        }

        // Left search card (origin + destination + action/filter bar)
        int sx = leftCol.x();
        int sy = leftCol.y() + topOffset;
        int sw = leftCol.width();
        int inputW = Math.max(60, sw - 54);
        originInput.bounds(new UiRect(sx + 14, sy + 6, inputW, 20));
        locateBtn.bounds(new UiRect(sx + sw - 38, sy + 7, 17, 18));
        pickOriginBtn.bounds(new UiRect(sx + sw - 19, sy + 7, 17, 18));

        destInput.bounds(new UiRect(sx + 14, sy + 30, inputW, 20));
        swapBtn.bounds(new UiRect(sx + sw - 38, sy + 31, 17, 18));
        pickDestBtn.bounds(new UiRect(sx + sw - 19, sy + 31, 17, 18));

        int barY = sy + 58;
        int searchW = Math.min(64, sw / 3);
        searchBtn.bounds(new UiRect(sx + sw - searchW, barY, searchW, 20));
        int chipAvail = sw - searchW - 8;
        int sortW = Math.min(72, Math.max(48, chipAvail / 2));
        sortBtn.bounds(new UiRect(sx, barY + 1, sortW, 18));
        vehicleBtn.bounds(new UiRect(sx + sortW + 4, barY + 1, Math.max(44, chipAvail - sortW - 4), 18));

        int cardsTop = barY + 26;
        this.cardsArea = new UiRect(sx, cardsTop, sw, Math.max(0, leftCol.bottom() - cardsTop));

        // Right column: mode switcher + body + footer
        int rx = rightCol.x();
        int ry = rightCol.y() + topOffset;
        int rw = rightCol.width();
        int tabW = Math.min(98, (rw - 6) / 2);
        modeTimelineBtn.bounds(new UiRect(rx, ry, tabW, 19));
        modeTopologyBtn.bounds(new UiRect(rx + tabW + 4, ry, tabW, 19));

        this.footerRect = new UiRect(rx, rightCol.bottom() - 28, rw, 28);
        this.rightBody = new UiRect(rx, ry + 24, rw, Math.max(0, footerRect.y() - (ry + 24) - 6));
        timeline.layout(rightBody);
        topology.layout(rightBody);

        int startW = Math.min(116, Math.max(82, rw / 3));
        startBtn.bounds(new UiRect(footerRect.right() - startW, footerRect.y() + 4, startW, 22));
    }

    @Override
    public void render(GuiGraphics g, UiContext ctx) {
        if (!wide) {
            compactListBtn.selected(!compactShowRight);
            compactDetailBtn.selected(compactShowRight);
            compactListBtn.render(g, ctx);
            compactDetailBtn.render(g, ctx);
        }

        if (wide || !compactShowRight) {
            renderLeftColumn(g, ctx);
        }
        if (wide || compactShowRight) {
            renderRightColumn(g, ctx);
        }
    }

    private void renderLeftColumn(GuiGraphics g, UiContext ctx) {
        int sx = leftCol.x();
        int sy = leftCol.y() + (wide ? 0 : 24);
        int sw = leftCol.width();

        // Connected origin-destination surface card
        UiRect formCard = new UiRect(sx, sy, sw, 54);
        UiDraw.card(g, formCard, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        // Connected origin (green) -> destination (sky) guide line
        UiDraw.circle(g, sx + 7, sy + 16, 2.7f, UiTokens.PRIMARY);
        UiDraw.line(g, sx + 7, sy + 20, sx + 7, sy + 36, 1.4f, UiTokens.BORDER_DEFAULT);
        UiDraw.circle(g, sx + 7, sy + 40, 2.7f, UiTokens.ACCENT_SKY);

        originInput.render(g, ctx);
        destInput.render(g, ctx);
        locateBtn.enabled(UiBridge.playerPosition() != null).render(g, ctx);
        pickOriginBtn.enabled(catalog != null && !catalog.stations().isEmpty()).render(g, ctx);
        swapBtn.render(g, ctx);
        pickDestBtn.enabled(catalog != null && !catalog.stations().isEmpty()).render(g, ctx);

        // Action & filter row
        boolean retry = usingNative && status.equals("mtrnav.native_unavailable");
        searchBtn.label(tr(retry ? "mtrnav.retry_service"
                : status.equals("mtrnav.searching") ? "mtrnav.searching_short" : "mtrnav.search"));
        searchBtn.enabled(retry || (catalog != null && !origin.isEmpty() && !destination.isEmpty() && !status.equals("mtrnav.searching")));
        searchBtn.render(g, ctx);

        sortBtn.label(tr(sort == Sort.FASTEST ? "mtrnav.fastest" : "mtrnav.fewest"));
        sortBtn.selected(sort == Sort.FEWEST_TRANSFERS);
        sortBtn.render(g, ctx);

        vehicleBtn.selected(vehicleCondition.active());
        vehicleBtn.label(vehicleCondition.active()
                ? tr("mtrnav.vehicle_filter") + " (" + vehicleCondition.models().size() + ")"
                : tr("mtrnav.vehicle_filter"));
        vehicleBtn.render(g, ctx);

        // Journey / Reference Cards list
        cardHitRects.clear();
        UiDraw.card(g, cardsArea, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);

        List<ConnectionPlan> refs = journeys.isEmpty() ? activeReferencePlans() : List.of();
        int count = journeys.isEmpty() ? refs.size() : journeys.size();
        if (count == 0) {
            String msg = !locationMessage.isEmpty() ? locationMessage : tr(status);
            UiDraw.wrapped(g, msg, cardsArea.x() + 10, cardsArea.y() + 12, cardsArea.width() - 20, 12, UiTokens.TEXT_SECONDARY);
            UiDraw.wrapped(g, tr("mtrnav.empty_help"), cardsArea.x() + 10, cardsArea.y() + 36, cardsArea.width() - 20, 12, UiTokens.TEXT_MUTED);
            return;
        }

        // Fit 5 cards smoothly on standard 1080p/2x without scroll; scroll gracefully on small screens
        int availH = Math.max(0, cardsArea.height() - 10);
        int cardH = availH >= 285 ? 54 : Math.max(44, (availH - 16) / 5);
        int step = cardH + 4;
        cardScroll.update(cardsArea.y() + 5, availH, count * step - 4, 14);

        UiDraw.enableScissor(g, cardsArea.inset(1));
        int cw = cardsArea.width() - (cardScroll.scrollable() ? 16 : 10);
        for (int i = 0; i < count; i++) {
            int cy = cardsArea.y() + 5 + i * step - cardScroll.offset();
            UiRect rect = new UiRect(cardsArea.x() + 5, cy, cw, cardH);
            cardHitRects.add(rect.intersect(cardsArea));
            if (cy + cardH < cardsArea.y() || cy > cardsArea.bottom()) continue;

            if (journeys.isEmpty()) {
                renderReferenceCard(g, ctx, i, refs.get(i), refs, rect);
            } else {
                renderJourneyCard(g, ctx, i, journeys.get(i), rect);
            }
        }
        g.disableScissor();
        UiDraw.scrollbar(g, cardScroll, cardsArea.right() - 7, 4);
    }

    private void renderReferenceCard(GuiGraphics g, UiContext ctx, int index, ConnectionPlan plan, List<ConnectionPlan> allRefs, UiRect r) {
        boolean sel = index == selectedJourney;
        boolean hov = r.contains(ctx.mouseX, ctx.mouseY) && cardsArea.contains(ctx.mouseX, ctx.mouseY);
        int bg = sel ? UiTokens.SURFACE_SELECTED : hov ? UiTokens.SURFACE_HOVER : UiTokens.SURFACE;
        int border = sel ? UiTokens.STATUS_WARN : UiTokens.BORDER_SUBTLE;
        UiDraw.card(g, r, UiTokens.RADIUS_SM, bg, border);
        UiDraw.roundedRect(g, r.x() + 3, r.y() + 5, 3, r.height() - 10, 1.5f,
                sel ? UiTokens.STATUS_WARN : UiTokens.withAlpha(UiTokens.STATUS_WARN, 130));

        double distBlocks = plan.estimatedBlocks(catalog);
        String metric = distBlocks >= 1000.0
                ? String.format(Locale.ROOT, "%.1f km", distBlocks / 1000.0)
                : distBlocks >= 10.0
                ? Math.round(distBlocks) + " m"
                : tr("mtrnav.ui.ref_stops", plan.stopCount());
        int metricW = (int) (UiDraw.font().width(metric) * 1.12f) + 6;
        UiDraw.scaledText(g, metric, r.right() - metricW - 4, r.y() + 6, metricW + 4,
                sel ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_SECONDARY, 1.12f);

        String title = allRefs.size() > 1 ? tr("mtrnav.ui.ref_option", index + 1) : tr("mtrnav.reference_route");
        int titleX = r.x() + 11;
        UiDraw.bold(g, title, titleX, r.y() + 6, Math.max(20, r.width() - metricW - 22), UiTokens.TEXT_PRIMARY);

        if (allRefs.size() > 1 && index == 0) {
            String badge = tr(sort == Sort.FEWEST_TRANSFERS ? "mtrnav.fewest" : "mtrnav.ui.ref_shortest");
            int tx = titleX + UiDraw.font().width(title) + 8;
            int maxB = r.right() - metricW - 8 - tx;
            if (maxB >= 24) {
                UiDraw.pill(g, badge, tx, r.y() + 4, maxB,
                        sel ? UiTokens.STATUS_WARN : UiTokens.withAlpha(UiTokens.STATUS_WARN, 48),
                        sel ? UiTokens.PRIMARY_INK : UiTokens.STATUS_WARN);
            }
        }

        String subtitle = tr("mtrnav.transfers", plan.transfers()) + " · " + tr("mtrnav.ui.ref_stops", plan.stopCount());
        UiDraw.text(g, subtitle, titleX, r.y() + 20, r.width() - 18, sel ? UiTokens.TEXT_SECONDARY : UiTokens.TEXT_MUTED);
        renderChain(g, JourneyChain.of(plan), titleX, r.bottom() - 17, r.width() - 18);
    }

    private void renderJourneyCard(GuiGraphics g, UiContext ctx, int index, Journey journey, UiRect r) {
        boolean sel = index == selectedJourney;
        boolean hov = r.contains(ctx.mouseX, ctx.mouseY) && cardsArea.contains(ctx.mouseX, ctx.mouseY);
        int bg = sel ? UiTokens.SURFACE_SELECTED : hov ? UiTokens.SURFACE_HOVER : UiTokens.SURFACE;
        int border = sel ? UiTokens.BORDER_SELECTED : UiTokens.BORDER_SUBTLE;
        UiDraw.card(g, r, UiTokens.RADIUS_SM, bg, border);

        int tint = journey.legs().isEmpty() ? UiTokens.ACCENT_SKY : NavFormat.routeColor(journey.legs().getFirst().routeId());
        UiDraw.roundedRect(g, r.x() + 3, r.y() + 5, 3, r.height() - 10, 1.5f, tint);

        String dur = NavFormat.shortDuration(journey.arrival() - journey.queryTime());
        int durW = (int) (UiDraw.font().width(dur) * 1.25f) + 6;
        UiDraw.scaledText(g, dur, r.right() - durW - 4, r.y() + 6, durW + 4, UiTokens.TEXT_PRIMARY, 1.25f);

        String title = tr("mtrnav.option", index + 1);
        int titleX = r.x() + 11;
        UiDraw.bold(g, title, titleX, r.y() + 6, Math.max(20, r.width() - durW - 22), UiTokens.TEXT_PRIMARY);

        String badge = fastestArrival > 0 && journey.arrival() == fastestArrival ? tr("mtrnav.walk_fastest")
                : index == 0 && sort == Sort.FEWEST_TRANSFERS ? tr("mtrnav.fewest") : null;
        if (badge != null) {
            int tx = titleX + UiDraw.font().width(title) + 8;
            int maxB = r.right() - durW - 8 - tx;
            if (maxB >= 24) {
                UiDraw.pill(g, badge, tx, r.y() + 4, maxB,
                        sel ? UiTokens.PRIMARY : UiTokens.withAlpha(UiTokens.PRIMARY, 45),
                        sel ? UiTokens.PRIMARY_INK : UiTokens.STATUS_LIVE);
            }
        }

        String timeRange = (journey.legs().isEmpty() ? "—" : NavFormat.clockTime(journey.legs().getFirst().departure()))
                + " → " + NavFormat.clockTime(journey.arrival())
                + " · " + tr("mtrnav.transfers", journey.transfers());
        UiDraw.text(g, timeRange, titleX, r.y() + 20, r.width() - 18, sel ? UiTokens.TEXT_SECONDARY : UiTokens.TEXT_MUTED);

        renderChain(g, JourneyChain.of(journey), titleX, r.bottom() - 17, r.width() - 18);
    }

    private void renderChain(GuiGraphics g, List<JourneyChain.Item> source, int x, int y, int width) {
        if (source.isEmpty() || width < 24) return;
        int cursor = x;
        for (int i = 0; i < source.size(); i++) {
            var item = source.get(i);
            if (i > 0) {
                if (cursor + 10 > x + width) break;
                UiDraw.text(g, "›", cursor, y + 3, 6, UiTokens.TEXT_MUTED);
                cursor += 8;
            }
            if (item.walking()) {
                String mins = Long.toString((item.seconds() + 59L) / 60L);
                int need = 14 + UiDraw.font().width(mins);
                if (cursor + need > x + width) {
                    UiDraw.text(g, "+" + (source.size() - i), cursor, y + 3, Math.max(0, x + width - cursor), UiTokens.TEXT_MUTED);
                    break;
                }
                UiIcons.WALK.draw(g, cursor, y + 2, 10, UiTokens.STATUS_WARN);
                UiDraw.text(g, mins, cursor + 11, y + 3, need - 11, UiTokens.TEXT_SECONDARY);
                cursor += need + 2;
            } else {
                String code = NavFormat.routeCode(item.routeId());
                int bw = Math.min(62, UiDraw.font().width(code) + 10);
                if (cursor + bw > x + width) {
                    if (x + width - cursor >= 16) {
                        UiDraw.text(g, "+" + (source.size() - i), cursor, y + 3, x + width - cursor, UiTokens.TEXT_MUTED);
                    }
                    break;
                }
                int drawn = UiDraw.lineBadge(g, code, cursor, y, bw, NavFormat.routeColor(item.routeId()));
                cursor += drawn + 3;
                if (cursor + 13 <= x + width) {
                    UiDraw.transportIcon(g, NavFormat.routeType(item.routeId()), cursor, y, 12);
                    cursor += 14;
                }
            }
        }
    }

    private void renderRightColumn(GuiGraphics g, UiContext ctx) {
        modeTimelineBtn.selected(rightMode == RightMode.TIMELINE).render(g, ctx);
        modeTopologyBtn.selected(rightMode == RightMode.TOPOLOGY).render(g, ctx);

        List<ConnectionPlan> refs = journeys.isEmpty() ? activeReferencePlans() : List.of();
        int optionCount = journeys.isEmpty() ? refs.size() : journeys.size();
        selectedJourney = Math.clamp(selectedJourney, 0, Math.max(0, optionCount - 1));
        Journey current = journeys.isEmpty() ? null : journeys.get(selectedJourney);
        ConnectionPlan currentRef = journeys.isEmpty() && !refs.isEmpty() ? refs.get(selectedJourney) : null;

        if (rightMode == RightMode.TOPOLOGY) {
            topology.render(g, ctx, current, currentRef);
        } else if (current != null) {
            timeline.renderJourney(g, ctx, current, -1);
            timeline.updateTooltips(ctx);
        } else if (currentRef != null) {
            timeline.renderReference(g, ctx, currentRef, referenceReason);
            timeline.updateTooltips(ctx);
        } else {
            UiDraw.card(g, rightBody, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
            String title = tr("mtrnav.ui.empty_title");
            UiDraw.bold(g, title, rightBody.centerX() - UiDraw.font().width(title) / 2, rightBody.centerY() - 14, rightBody.width() - 24, UiTokens.TEXT_PRIMARY);
            String hint = tr("mtrnav.ui.empty_hint");
            UiDraw.text(g, hint, rightBody.centerX() - UiDraw.font().width(hint) / 2, rightBody.centerY() + 2, rightBody.width() - 24, UiTokens.TEXT_MUTED);
        }

        // Bottom footer
        String summary = !journeys.isEmpty()
                ? tr("mtrnav.option_summary", selectedJourney + 1, journeys.size())
                : refs.size() > 1
                ? tr("mtrnav.ui.ref_summary", selectedJourney + 1, refs.size())
                : tr(currentRef == null ? "mtrnav.search_help" : "mtrnav.reference_only");
        UiDraw.text(g, summary, footerRect.x() + 2, footerRect.y() + 11, Math.max(0, footerRect.width() - startBtn.bounds().width() - 10), UiTokens.TEXT_SECONDARY);
        startBtn.enabled(!journeys.isEmpty() && !CLOCK.stale(ctx.nowNanos));
        startBtn.render(g, ctx);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!wide) {
            if (compactListBtn.mouseClicked(x, y, button) || compactDetailBtn.mouseClicked(x, y, button)) return true;
        }
        if (wide || !compactShowRight) {
            if (originInput.mouseClicked(x, y, button)) { destInput.setFocused(false); return true; }
            if (destInput.mouseClicked(x, y, button)) { originInput.setFocused(false); return true; }
            if (locateBtn.mouseClicked(x, y, button) || pickOriginBtn.mouseClicked(x, y, button)
                    || swapBtn.mouseClicked(x, y, button) || pickDestBtn.mouseClicked(x, y, button)
                    || searchBtn.mouseClicked(x, y, button) || sortBtn.mouseClicked(x, y, button)
                    || vehicleBtn.mouseClicked(x, y, button)) return true;
            if (button == 0 && cardScroll.beginDrag(x, y, cardsArea.right() - 7, 4)) return true;
            if (button == 0 && cardsArea.contains(x, y)) {
                int optionCount = journeys.isEmpty() ? activeReferencePlans().size() : journeys.size();
                for (int i = 0; i < cardHitRects.size(); i++) {
                    if (cardHitRects.get(i).contains(x, y) && i < optionCount) {
                        selectedJourney = i;
                        timeline.resetScroll();
                        if (!wide) compactShowRight = true;
                        return true;
                    }
                }
            }
        }
        if (wide || compactShowRight) {
            if (modeTimelineBtn.mouseClicked(x, y, button) || modeTopologyBtn.mouseClicked(x, y, button)
                    || startBtn.mouseClicked(x, y, button)) return true;
            if (rightMode == RightMode.TOPOLOGY && topology.mouseClicked(x, y, button)) return true;
            if (rightMode == RightMode.TIMELINE && timeline.mouseClicked(x, y, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && cardScroll.drag(y)) return true;
        if (rightMode == RightMode.TOPOLOGY && topology.mouseDragged(x, y, button)) return true;
        return rightMode == RightMode.TIMELINE && timeline.mouseDragged(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = compactListBtn.mouseReleased(x, y, button)
                | compactDetailBtn.mouseReleased(x, y, button)
                | locateBtn.mouseReleased(x, y, button)
                | pickOriginBtn.mouseReleased(x, y, button)
                | swapBtn.mouseReleased(x, y, button)
                | pickDestBtn.mouseReleased(x, y, button)
                | searchBtn.mouseReleased(x, y, button)
                | sortBtn.mouseReleased(x, y, button)
                | vehicleBtn.mouseReleased(x, y, button)
                | modeTimelineBtn.mouseReleased(x, y, button)
                | modeTopologyBtn.mouseReleased(x, y, button)
                | startBtn.mouseReleased(x, y, button)
                | cardScroll.endDrag()
                | topology.mouseReleased(x, y, button)
                | timeline.mouseReleased(button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if ((wide || !compactShowRight) && cardsArea.contains(x, y)) return cardScroll.scrollBy(vertical, 32);
        if ((wide || compactShowRight) && rightBody.contains(x, y)) {
            return rightMode == RightMode.TOPOLOGY
                    ? topology.mouseScrolled(x, y, vertical)
                    : timeline.mouseScrolled(x, y, vertical);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) {
            if (originInput.focused()) { originInput.setFocused(false); destInput.setFocused(true); return true; }
            if (destInput.focused()) { destInput.setFocused(false); originInput.setFocused(true); return true; }
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && searchBtn.enabled()) {
            triggerSearch();
            return true;
        }
        int optionCount = journeys.isEmpty() ? activeReferencePlans().size() : journeys.size();
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && optionCount > 1 && !originInput.focused() && !destInput.focused()) {
            selectedJourney = Math.clamp(selectedJourney + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, optionCount - 1);
            timeline.resetScroll();
            return true;
        }
        return originInput.keyPressed(key, scan, modifiers) || destInput.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return originInput.charTyped(ch, modifiers) || destInput.charTyped(ch, modifiers);
    }

    @Override
    public boolean isTyping() {
        return originInput.focused() || destInput.focused();
    }
}
