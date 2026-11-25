package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.TopologyProjection;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Smartphone-navigation-grade 2D transit map built on real Minecraft chunk terrain + dark cartography,
 * viewport-culled background corridors, smooth curved active routes with animated chevrons,
 * teardrop GPS pins, live player heading cone, floating ETA route card, North compass, and dynamic scale bar.
 * Engineered to stay buttery-smooth and clutter-free even on 200+ station mega-networks.
 */
public final class TopologyCanvasPane {
    private static final int CANVAS_BG = 0xFF0E1621;
    private static final int MAX_NON_ROUTE_LABELS = 18;

    private record Seg(float x1, float y1, float x2, float y2) {}
    private record ActiveLegPath(String routeId, String routeName, int color,
                                 List<TopologyProjection.Node> nodes, List<float[]> curve) {}

    private final WorkspaceHost host;
    private final TopologyProjection projection = new TopologyProjection();
    private final CartographicMapLayer mapLayer = new CartographicMapLayer();
    private UiRect bounds = UiRect.EMPTY;
    private UiRect compassRect = UiRect.EMPTY;
    private UiRect dockRect = UiRect.EMPTY;
    private TopologyProjection.Snapshot snapshot = TopologyProjection.Snapshot.EMPTY;
    private boolean panning;
    private long lastInteractNanos;
    private double lastMouseX, lastMouseY;
    private String popoverStationId;
    private UiRect popoverRect = UiRect.EMPTY;
    private String lastFocusKey = "";
    private Set<String> lastFocusStations = Set.of();
    private boolean userMovedCamera;
    private boolean showingFullOverview;

    private final UiButton zoomInBtn;
    private final UiButton zoomOutBtn;
    private final UiButton resetBtn;
    private final UiButton popOriginBtn;
    private final UiButton popDestBtn;
    private final UiButton popBoardBtn;
    private final UiButton popCloseBtn;

    public TopologyCanvasPane(WorkspaceHost host) {
        this.host = host;
        this.zoomInBtn = new UiButton("+", UiButton.Style.GHOST, () -> {
            userMovedCamera = true;
            lastInteractNanos = System.nanoTime();
            projection.zoomAt(1.35, bounds.centerX(), bounds.centerY(), bounds);
        });
        this.zoomOutBtn = new UiButton("−", UiButton.Style.GHOST, () -> {
            userMovedCamera = true;
            lastInteractNanos = System.nanoTime();
            projection.zoomAt(0.74, bounds.centerX(), bounds.centerY(), bounds);
        });
        this.resetBtn = new UiButton("", UiButton.Style.GHOST, this::toggleFocusOrOverview)
                .icon(UiIcons.LOCATE).tooltip(tr("mtrnav.reset_hud"));
        this.popOriginBtn = new UiButton(tr("mtrnav.use_as_origin"), UiButton.Style.SECONDARY, () -> {
            if (popoverStationId != null) {
                String id = popoverStationId;
                popoverStationId = null;
                host.openPlanWithStation(id, true);
            }
        });
        this.popDestBtn = new UiButton(tr("mtrnav.use_as_destination"), UiButton.Style.SECONDARY, () -> {
            if (popoverStationId != null) {
                String id = popoverStationId;
                popoverStationId = null;
                host.openPlanWithStation(id, false);
            }
        });
        this.popBoardBtn = new UiButton(tr("mtrnav.show_board"), UiButton.Style.PRIMARY, () -> {
            if (popoverStationId != null) {
                String id = popoverStationId;
                popoverStationId = null;
                host.openStationBoard(id);
            }
        });
        this.popCloseBtn = new UiButton("", UiButton.Style.GHOST, () -> popoverStationId = null).icon(UiIcons.CLOSE);
    }

    public void layout(UiRect rect) {
        this.bounds = rect;
        int dockW = 22;
        int rx = rect.right() - dockW - 8;
        int ry = rect.y() + 8;
        compassRect = new UiRect(rx, ry, dockW, 22);
        dockRect = new UiRect(rx, ry + 26, dockW, 60);
        zoomInBtn.bounds(new UiRect(rx + 2, dockRect.y() + 2, 18, 18));
        zoomOutBtn.bounds(new UiRect(rx + 2, dockRect.y() + 21, 18, 18));
        resetBtn.bounds(new UiRect(rx + 2, dockRect.y() + 40, 18, 18));
    }

    private void toggleFocusOrOverview() {
        if (catalog == null || bounds.isEmpty()) return;
        lastInteractNanos = 0L;
        if (userMovedCamera || showingFullOverview) {
            userMovedCamera = false;
            showingFullOverview = false;
            applyDefaultFocus(lastFocusStations);
        } else {
            userMovedCamera = false;
            showingFullOverview = true;
            projection.resetView();
        }
    }

    private void applyDefaultFocus(Set<String> focusStations) {
        if (focusStations.size() >= 2) {
            projection.fitStations(focusStations, catalog, bounds);
        } else if (catalog.stations().size() > 24 && origin != null && !origin.isEmpty() && catalog.station(origin) != null) {
            projection.fitAroundStation(origin, catalog, bounds, 12);
        } else {
            projection.fitCoreCluster(catalog, bounds);
        }
    }

    public void render(GuiGraphics g, UiContext ctx, Model.Journey highlightedJourney) {
        render(g, ctx, highlightedJourney, null);
    }

    public void render(GuiGraphics g, UiContext ctx, Model.Journey highlightedJourney, ConnectionPlan highlightedReference) {
        if (bounds.isEmpty()) return;
        UiDraw.card(g, bounds, UiTokens.RADIUS_MD, CANVAS_BG, UiTokens.BORDER_SUBTLE);

        if (catalog == null || catalog.stations().isEmpty()) {
            String msg = tr(catalog == null ? status : "mtrnav.no_stations");
            UiDraw.text(g, msg, bounds.centerX() - Math.min(bounds.width() - 20, UiDraw.font().width(msg)) / 2,
                    bounds.centerY() - 4, bounds.width() - 20, UiTokens.TEXT_MUTED);
            return;
        }

        // Auto-focus camera onto the active route (or local neighborhood / core cluster) when selection changes
        Set<String> focusStations = collectFocusStationIds(highlightedJourney, highlightedReference);
        String focusKey = catalog.dimension() + "|" + catalog.revision() + "|" + bounds.width() + "x" + bounds.height() + "|"
                + (highlightedJourney != null ? highlightedJourney.id()
                : highlightedReference != null ? ("ref:" + highlightedReference.signature())
                : ("idle:" + (origin == null ? "" : origin)));
        if (!focusKey.equals(lastFocusKey)) {
            lastFocusKey = focusKey;
            lastFocusStations = focusStations;
            userMovedCamera = false;
            showingFullOverview = false;
            applyDefaultFocus(focusStations);
        }

        snapshot = projection.project(catalog, bounds);
        Map<String, TopologyProjection.Node> nodeById = new HashMap<>(snapshot.nodes().size() * 2);
        for (var n : snapshot.nodes()) nodeById.put(n.stationId(), n);

        Map<String, Model.Route> routeById = new HashMap<>(catalog.routes().size() * 2);
        for (var r : catalog.routes()) routeById.put(r.id(), r);

        // Classify highlighted journey (or reference plan) legs, transfer points, and walk times
        List<ActiveLegPath> activeLegs = new ArrayList<>();
        Set<String> activeStations = new HashSet<>();
        Set<String> transferStations = new HashSet<>();
        Map<String, Integer> transferWalkSeconds = new HashMap<>();
        Set<String> activeRouteEdgeKeys = new HashSet<>();
        Map<String, Integer> activeStationColor = new HashMap<>();

        String effectiveOrigin = highlightedJourney != null ? highlightedJourney.originStationId()
                : highlightedReference != null ? highlightedReference.origin() : origin;
        String effectiveDest = highlightedJourney != null ? highlightedJourney.destinationStationId()
                : highlightedReference != null ? highlightedReference.destination() : destination;
        double totalRouteBlocks = 0.0;
        List<Model.WalkLeg> activeWalks = highlightedJourney != null ? highlightedJourney.walks()
                : highlightedReference != null ? highlightedReference.walks() : List.of();

        if (highlightedJourney != null) {
            if (effectiveOrigin != null) activeStations.add(effectiveOrigin);
            if (effectiveDest != null) activeStations.add(effectiveDest);

            List<List<double[]>> overlayCurves = NewUiFixtures.enabled()
                    ? List.of()
                    : UiBridge.overlayJourneyCurves(catalog.dimension(), highlightedJourney);
            List<Model.RideLeg> legs = highlightedJourney.legs();
            for (int i = 0; i < legs.size(); i++) {
                var leg = legs.get(i);
                List<double[]> worldCurve = i < overlayCurves.size() ? overlayCurves.get(i) : List.of();
                totalRouteBlocks += collectActiveLeg(leg.routeId(), leg.fromStationId(), leg.viaStations(), leg.toStationId(),
                        worldCurve, nodeById, routeById, activeLegs, activeStations, activeStationColor, activeRouteEdgeKeys);
                if (i + 1 < legs.size() && leg.toStationId().equals(legs.get(i + 1).fromStationId())) {
                    transferStations.add(leg.toStationId());
                }
            }
        } else if (highlightedReference != null) {
            if (effectiveOrigin != null) activeStations.add(effectiveOrigin);
            if (effectiveDest != null) activeStations.add(effectiveDest);

            List<List<double[]>> overlayCurves = NewUiFixtures.enabled()
                    ? List.of()
                    : UiBridge.overlayReferenceCurves(catalog.dimension(), highlightedReference);
            List<ConnectionPlan.Leg> legs = highlightedReference.legs();
            for (int i = 0; i < legs.size(); i++) {
                var leg = legs.get(i);
                List<double[]> worldCurve = i < overlayCurves.size() ? overlayCurves.get(i) : List.of();
                totalRouteBlocks += collectActiveLeg(leg.routeId(), leg.fromStationId(), leg.viaStations(), leg.toStationId(),
                        worldCurve, nodeById, routeById, activeLegs, activeStations, activeStationColor, activeRouteEdgeKeys);
                if (i + 1 < legs.size() && leg.toStationId().equals(legs.get(i + 1).fromStationId())) {
                    transferStations.add(leg.toStationId());
                }
            }
        }
        for (var walk : activeWalks) {
            activeStations.add(walk.fromStationId());
            activeStations.add(walk.toStationId());
            transferStations.add(walk.fromStationId());
            transferStations.add(walk.toStationId());
            transferWalkSeconds.merge(walk.fromStationId(), walk.seconds(), Integer::sum);
        }
        transferStations.remove(effectiveOrigin);
        transferStations.remove(effectiveDest);
        boolean hasHighlight = !activeLegs.isEmpty();

        UiDraw.enableScissor(g, bounds.inset(1));

        // 1. O(1)/pixel cartographic dark-mode terrain base map (throttled during rapid drag/zoom)
        boolean interacting = panning || (ctx.nowNanos - lastInteractNanos) < 160_000_000L;
        mapLayer.render(g, bounds, projection, catalog, interacting);

        g.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, 0x44000000);
        g.fill(bounds.x() + 1, bounds.bottom() - 2, bounds.right() - 1, bounds.bottom() - 1, 0x44000000);

        // 2. Fast viewport-culled, deduplicated background railway corridors
        renderBackgroundEdgesFast(g, hasHighlight, activeRouteEdgeKeys);

        // 3. Highlighted active route (drop shadow + dark casing + vivid track + animated gliding nav chevrons)
        List<Seg> activeSegs = new ArrayList<>();
        if (hasHighlight) {
            double animSec = ctx.nowNanos / 1_000_000_000.0;
            for (var leg : activeLegs) {
                var pts = leg.curve();
                if (pts.size() < 2) continue;

                for (int i = 0; i + 1 < pts.size(); i++) {
                    float[] a = pts.get(i), b = pts.get(i + 1);
                    if (!segmentIntersectsBounds(a[0], a[1], b[0], b[1], 14f)) continue;
                    UiDraw.line(g, a[0], a[1] + 2.0f, b[0], b[1] + 2.0f, 9.5f, 0x4E04070D);
                    UiDraw.line(g, a[0], a[1], b[0], b[1], 10.0f, UiTokens.withAlpha(leg.color(), 42));
                    activeSegs.add(new Seg(a[0], a[1], b[0], b[1]));
                }
                for (int i = 0; i + 1 < pts.size(); i++) {
                    float[] a = pts.get(i), b = pts.get(i + 1);
                    if (!segmentIntersectsBounds(a[0], a[1], b[0], b[1], 10f)) continue;
                    UiDraw.line(g, a[0], a[1], b[0], b[1], 6.6f, 0xF20A101B);
                }
                for (int i = 0; i + 1 < pts.size(); i++) {
                    float[] a = pts.get(i), b = pts.get(i + 1);
                    if (!segmentIntersectsBounds(a[0], a[1], b[0], b[1], 8f)) continue;
                    UiDraw.line(g, a[0], a[1], b[0], b[1], 4.2f, leg.color());
                }
                for (var p : pts) {
                    if (pointInBounds(p[0], p[1], 6f)) UiDraw.circle(g, p[0], p[1], 2.1f, leg.color());
                }
                renderAnimatedChevrons(g, pts, leg.color(), animSec);
            }
        }

        // 4. Cross-station walking stepping stones
        for (var w : activeWalks) {
            var a = nodeById.get(w.fromStationId());
            var b = nodeById.get(w.toStationId());
            if (a != null && b != null && !a.stationId().equals(b.stationId())
                    && segmentIntersectsBounds(a.screenX(), a.screenY(), b.screenX(), b.screenY(), 8f)) {
                UiDraw.dottedLine(g, a.screenX(), a.screenY(), b.screenX(), b.screenY(), 2.9f, 6.5f, 0xDF0A101B);
                UiDraw.dottedLine(g, a.screenX(), a.screenY(), b.screenX(), b.screenY(), 1.75f, 6.5f, UiTokens.STATUS_WARN);
                activeSegs.add(new Seg(a.screenX(), a.screenY(), b.screenX(), b.screenY()));
            }
        }

        // 5. Live Player GPS Blue Dot & Rotating Heading Cone
        renderPlayerLocation(g, ctx.nowNanos);

        // 6. Viewport-culled station nodes & teardrop GPS pins
        var hoveredNode = popoverRect.contains(ctx.mouseX, ctx.mouseY) ? null
                : TopologyProjection.hitNode(snapshot, ctx.mouseX, ctx.mouseY, 12.0);

        List<TopologyProjection.Node> visibleNodes = new ArrayList<>();
        for (var n : snapshot.nodes()) {
            boolean isPin = n.stationId().equals(effectiveOrigin) || n.stationId().equals(effectiveDest);
            if (pointInBounds(n.screenX(), n.screenY(), isPin ? 28f : 10f)) {
                visibleNodes.add(n);
            }
        }
        visibleNodes.sort(Comparator.comparingInt(n -> nodeDrawPriority(n, effectiveOrigin, effectiveDest, transferStations, activeStations, hasHighlight, hoveredNode)));
        boolean denseView = visibleNodes.size() > 36;

        for (var node : visibleNodes) {
            boolean isOrigin = node.stationId().equals(effectiveOrigin);
            boolean isDest = node.stationId().equals(effectiveDest);
            boolean isTransfer = transferStations.contains(node.stationId());
            boolean isRouteNode = hasHighlight && activeStations.contains(node.stationId());
            boolean hovered = hoveredNode != null && hoveredNode.stationId().equals(node.stationId());
            boolean selected = node.stationId().equals(popoverStationId);

            renderStationMarker(g, node, isOrigin, isDest, isTransfer, isRouteNode, hasHighlight, denseView,
                    activeStationColor.getOrDefault(node.stationId(), UiTokens.TEXT_PRIMARY), hovered, selected);
        }

        // 7. Strict viewport-culled, non-overlapping label engine (O(visible) — zero edge-clamping hell!)
        UiRect topRouteCard = hasHighlight
                ? new UiRect(bounds.x() + 8, bounds.y() + 8, Math.min(238, Math.max(150, bounds.width() - 48)), 36)
                : new UiRect(bounds.x() + 8, bounds.y() + 8, Math.min(180, bounds.width() - 48), 20);
        UiRect bottomChrome = new UiRect(bounds.x() + 6, bounds.bottom() - 24, bounds.width() - 12, 20);
        UiRect rightDockZone = new UiRect(compassRect.x() - 4, compassRect.y() - 2, compassRect.width() + 8, 108);
        UiRect safeArea = new UiRect(bounds.x() + 6, bounds.y() + (hasHighlight ? 46 : 26),
                Math.max(20, bounds.width() - 12), Math.max(20, bounds.height() - (hasHighlight ? 72 : 52)));

        List<UiRect> occupied = new ArrayList<>(48);
        occupied.add(topRouteCard);
        occupied.add(rightDockZone);
        occupied.add(bottomChrome);
        // Only add exclusion zones for visible important markers (pins, transfers, route stops, hovered)
        for (var n : visibleNodes) {
            boolean isPin = n.stationId().equals(effectiveOrigin) || n.stationId().equals(effectiveDest);
            boolean isKey = isPin || transferStations.contains(n.stationId()) || (hasHighlight && activeStations.contains(n.stationId()));
            if (!isKey) continue;
            if (isPin) {
                occupied.add(new UiRect(Math.round(n.screenX() - 11), Math.round(n.screenY() - 25), 22, 29));
            } else {
                occupied.add(new UiRect(Math.round(n.screenX() - 8), Math.round(n.screenY() - 8), 16, 16));
            }
        }

        // Sort visible nodes by priority descending (hovered -> pins -> transfers -> active route -> major hubs -> regular)
        List<TopologyProjection.Node> labelOrder = new ArrayList<>(visibleNodes);
        labelOrder.sort((a, b) -> {
            int pa = nodeDrawPriority(a, effectiveOrigin, effectiveDest, transferStations, activeStations, hasHighlight, hoveredNode);
            int pb = nodeDrawPriority(b, effectiveOrigin, effectiveDest, transferStations, activeStations, hasHighlight, hoveredNode);
            if (pa != pb) return Integer.compare(pb, pa);
            return Integer.compare(b.lineColors().size(), a.lineColors().size());
        });

        int nonRouteLabelsDrawn = 0;
        for (var node : labelOrder) {
            // Strictly skip stations whose anchor dot is outside the visible map area (never clamp onto edges!)
            if (!pointInBounds(node.screenX(), node.screenY(), -2f)) continue;

            boolean isOrigin = node.stationId().equals(effectiveOrigin);
            boolean isDest = node.stationId().equals(effectiveDest);
            boolean isTransfer = transferStations.contains(node.stationId());
            boolean isRouteStop = hasHighlight && activeStations.contains(node.stationId());
            boolean hovered = hoveredNode != null && hoveredNode.stationId().equals(node.stationId());
            boolean selected = node.stationId().equals(popoverStationId);

            boolean mustShow = hovered || selected || isOrigin || isDest || isTransfer;
            boolean routePriority = mustShow || isRouteStop;
            if (!routePriority) {
                // When a route is active on a big network, keep background labels super restrained
                int cap = hasHighlight ? (snapshot.nodes().size() > 24 ? 0 : 8) : MAX_NON_ROUTE_LABELS;
                if (nonRouteLabelsDrawn >= cap) continue;
            }

            boolean drawn = renderSmartStationLabel(g, node, isOrigin, isDest, isTransfer,
                    transferWalkSeconds.getOrDefault(node.stationId(), 0),
                    isRouteStop, mustShow, hovered, selected, safeArea, occupied, activeSegs);
            if (drawn && !routePriority) nonRouteLabelsDrawn++;
        }

        // Place floating route-name pills along each active leg
        if (hasHighlight) {
            for (var leg : activeLegs) {
                renderLegRouteBadge(g, leg, safeArea, occupied, activeSegs);
            }
        }

        // 8. Floating smartphone navigation chrome
        renderTopLeftNavCard(g, topRouteCard, activeLegs, highlightedJourney, highlightedReference, totalRouteBlocks);
        renderBottomScaleAndStatus(g);

        // 9. Contextual station popover
        renderPopover(g, ctx);

        g.disableScissor();

        // 10. Top-right North Compass & Zoom Dock
        renderRightMapDock(g, ctx);
    }

    /**
     * Viewport-culled, corridor-deduplicated background railway rendering.
     * Avoids drawing 10 overlapping identical trunk lines and skips off-screen segments completely.
     */
    private void renderBackgroundEdgesFast(GuiGraphics g, boolean hasHighlight, Set<String> activeRouteEdgeKeys) {
        var edges = snapshot.edges();
        boolean megaNetwork = edges.size() > 60;
        Map<String, Integer> pairCount = megaNetwork ? new HashMap<>(edges.size()) : null;

        float casingW = hasHighlight ? 3.2f : (megaNetwork ? 3.8f : 4.8f);
        float coreW = hasHighlight ? 1.8f : (megaNetwork ? 2.2f : 2.9f);
        int casingCol = hasHighlight ? 0x800A101B : 0xBD0A101B;
        int alpha = hasHighlight ? 92 : 210;

        for (var e : edges) {
            if (hasHighlight && activeRouteEdgeKeys.contains(edgeKey(e.routeId(), e.fromStationId(), e.toStationId()))) {
                continue;
            }
            if (!segmentIntersectsBounds(e.x1(), e.y1(), e.x2(), e.y2(), 6f)) continue;

            float x1 = e.x1(), y1 = e.y1(), x2 = e.x2(), y2 = e.y2();
            if (pairCount != null) {
                String pk = pairKey(e.fromStationId(), e.toStationId());
                int seen = pairCount.getOrDefault(pk, 0);
                // On huge trunk networks, draw at most 2 parallel colors per station pair to keep GPU lean & clean
                if (seen >= 2) continue;
                pairCount.put(pk, seen + 1);
                if (seen == 1) {
                    float dx = x2 - x1, dy = y2 - y1;
                    float len = (float) Math.hypot(dx, dy);
                    if (len > 1f) {
                        float ox = -dy / len * 2.2f;
                        float oy = dx / len * 2.2f;
                        x1 += ox; y1 += oy; x2 += ox; y2 += oy;
                    }
                }
            }
            if (!megaNetwork || !hasHighlight) {
                UiDraw.line(g, x1, y1, x2, y2, casingW, casingCol);
            }
            UiDraw.line(g, x1, y1, x2, y2, coreW, UiTokens.withAlpha(e.color(), alpha));
        }
    }

    private void renderAnimatedChevrons(GuiGraphics g, List<float[]> pts, int routeColor, double animSec) {
        int ink = UiTokens.withAlpha(UiTokens.contrastInk(routeColor), 225);
        float spacing = 28.0f;
        float offset = (float) ((animSec * 18.0) % spacing);
        float accum = 0f;
        float nextDist = offset + 12f;

        for (int i = 0; i + 1 < pts.size(); i++) {
            float x1 = pts.get(i)[0], y1 = pts.get(i)[1];
            float x2 = pts.get(i + 1)[0], y2 = pts.get(i + 1)[1];
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.5f) continue;
            if (!segmentIntersectsBounds(x1, y1, x2, y2, 8f)) {
                accum += len;
                while (nextDist <= accum) nextDist += spacing;
                continue;
            }
            float ux = dx / len, uy = dy / len;
            while (nextDist <= accum + len - 6f) {
                float local = nextDist - accum;
                if (local >= 6f) {
                    float cx = x1 + ux * local;
                    float cy = y1 + uy * local;
                    if (pointInBounds(cx, cy, 4f)) {
                        UiDraw.chevron(g, cx, cy, ux, uy, 4.0f, 1.35f, ink);
                    }
                }
                nextDist += spacing;
            }
            accum += len;
        }
    }

    private void renderPlayerLocation(GuiGraphics g, long nowNanos) {
        if (NewUiFixtures.enabled()) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || catalog == null) return;
        String playerDim = mc.level == null ? "" : mc.level.dimension().location().toString().replace(':', '/');
        if (!catalog.dimension().isEmpty() && !catalog.dimension().equals(playerDim)) return;

        float px = projection.worldToScreenX(player.getX());
        float py = projection.worldToScreenY(player.getZ());
        if (!bounds.contains(px, py)) return;

        double rad = Math.toRadians(player.getYRot());
        double dirX = -Math.sin(rad);
        double dirY = Math.cos(rad);
        double spread = Math.toRadians(28.0);
        float coneLen = 22.0f;
        float lx = px + (float) ((dirX * Math.cos(spread) - dirY * Math.sin(spread)) * coneLen);
        float ly = py + (float) ((dirX * Math.sin(spread) + dirY * Math.cos(spread)) * coneLen);
        float rx = px + (float) ((dirX * Math.cos(-spread) - dirY * Math.sin(-spread)) * coneLen);
        float ry = py + (float) ((dirX * Math.sin(-spread) + dirY * Math.cos(-spread)) * coneLen);
        UiDraw.triangle(g, px, py, lx, ly, rx, ry, 0x4A2589FF);

        float pulse = (float) (0.5 + 0.5 * Math.sin(nowNanos / 350_000_000.0));
        UiDraw.circle(g, px, py, 6.5f + pulse * 3.2f, UiTokens.withAlpha(0xFF2589FF, (int) (75 - pulse * 45)));
        UiDraw.circle(g, px, py, 4.6f, 0xFFF8FAFC);
        UiDraw.circle(g, px, py, 3.2f, 0xFF2589FF);
    }

    private void renderStationMarker(GuiGraphics g, TopologyProjection.Node node,
                                     boolean isOrigin, boolean isDest, boolean isTransfer,
                                     boolean isRouteNode, boolean hasHighlight, boolean denseView,
                                     int routeColor, boolean hovered, boolean selected) {
        float cx = node.screenX();
        float cy = node.screenY();

        if (isOrigin || isDest) {
            int col = isOrigin ? UiTokens.PRIMARY : UiTokens.ACCENT_SKY;
            String tag = isOrigin ? tr("mtrnav.ui.tag_origin") : tr("mtrnav.ui.tag_dest");
            UiDraw.circle(g, cx, cy + 1.0f, 5.0f, 0x66000000);
            UiDraw.circle(g, cx, cy, 3.3f, 0xFFF8FAFC);
            UiDraw.circle(g, cx, cy, 2.0f, col);

            if (hovered || selected) {
                UiDraw.circle(g, cx, cy - 14.5f, 11.0f, UiTokens.withAlpha(col, 68));
            }
            float headY = cy - 14.5f;
            UiDraw.triangle(g, cx, cy - 1.5f, cx - 6.8f, headY + 4.2f, cx + 6.8f, headY + 4.2f, 0xFFF8FAFC);
            UiDraw.circle(g, cx, headY, 8.6f, 0xFFF8FAFC);
            UiDraw.triangle(g, cx, cy - 3.6f, cx - 5.2f, headY + 3.2f, cx + 5.2f, headY + 3.2f, col);
            UiDraw.circle(g, cx, headY, 7.2f, col);
            int tw = UiDraw.font().width(tag);
            UiDraw.text(g, tag, Math.round(cx - tw * 0.5f), Math.round(headY - 4f), 14, UiTokens.contrastInk(col));
            return;
        }

        if (hovered || selected) {
            float hr = isTransfer ? 6.6f : (isRouteNode || node.hub()) ? 4.6f : 3.4f;
            UiDraw.circle(g, cx, cy, hr + 4.2f, UiTokens.withAlpha(UiTokens.ACCENT_SKY, 68));
            UiDraw.ring(g, cx, cy, hr + 2.2f, hr + 3.4f, UiTokens.withAlpha(UiTokens.ACCENT_SKY, 230));
        }

        if (isTransfer) {
            UiDraw.circle(g, cx, cy + 1.0f, 8.0f, 0x55000000);
            UiDraw.circle(g, cx, cy, 9.6f, UiTokens.withAlpha(UiTokens.STATUS_WARN, 46));
            UiDraw.circle(g, cx, cy, 7.0f, 0xF00A101B);
            UiDraw.circle(g, cx, cy, 6.0f, 0xFFF8FAFC);
            UiDraw.circle(g, cx, cy, 4.2f, UiTokens.STATUS_WARN);
            UiDraw.circle(g, cx, cy, 2.2f, 0xFF0E1621);
        } else if (isRouteNode) {
            UiDraw.circle(g, cx, cy, 5.2f, 0xEE0A101B);
            UiDraw.circle(g, cx, cy, 4.1f, routeColor);
            UiDraw.circle(g, cx, cy, 2.3f, 0xFFF8FAFC);
        } else if (!hasHighlight && node.hub()) {
            float r = denseView ? 3.5f : 4.4f;
            UiDraw.circle(g, cx, cy, r + 1.0f, 0xDD0A101B);
            UiDraw.circle(g, cx, cy, r, 0xFFF8FAFC);
            UiDraw.circle(g, cx, cy, r * 0.52f, CANVAS_BG);
        } else {
            // Lightweight crisp station dot (fast even when 150 stations are visible)
            float r = denseView ? 2.4f : 3.0f;
            int col = hovered ? UiTokens.TEXT_PRIMARY : hasHighlight ? 0xFF53657E : 0xFF8396B0;
            UiDraw.circle(g, cx, cy, r + 0.9f, 0xCC0A101B);
            UiDraw.circle(g, cx, cy, r, col);
            if (!denseView) UiDraw.circle(g, cx, cy, 1.3f, 0xFF131C28);
        }
    }

    private boolean renderSmartStationLabel(GuiGraphics g, TopologyProjection.Node node,
                                            boolean isOrigin, boolean isDest, boolean isTransfer, int walkSecs,
                                            boolean isRouteStop, boolean mustShow, boolean hovered, boolean selected,
                                            UiRect safeArea, List<UiRect> occupied, List<Seg> activeSegs) {
        String name = NavFormat.primary(node.name());
        var font = UiDraw.font();
        int maxTextW = 92;
        int nameW = Math.min(maxTextW, font.width(name));

        String walkMeta = (isTransfer && walkSecs > 0) ? tr("mtrnav.ui.map_walk_min", Math.max(1, (walkSecs + 30) / 60)) : null;
        String transferTag = isTransfer ? tr("mtrnav.ui.tag_transfer") : null;
        int tagW = transferTag != null ? font.width(transferTag) + 6 : 0;
        int walkW = walkMeta != null ? font.width(walkMeta) + 6 : 0;

        int padX = 5;
        int boxW = padX + (tagW > 0 ? tagW + 4 : 0) + nameW + (walkW > 0 ? walkW + 2 : 0) + padX;
        int boxH = isTransfer ? 16 : (isOrigin || isDest || isRouteStop || hovered || selected ? 14 : 13);

        float cx = node.screenX();
        float cy = node.screenY();
        float r = isTransfer ? 6.6f : (isRouteStop || node.hub()) ? 4.6f : 3.2f;

        int[][] candidates;
        if (isOrigin || isDest) {
            candidates = new int[][]{
                    {Math.round(cx - boxW * 0.5f), Math.round(cy + 8), 0},
                    {Math.round(cx + 12), Math.round(cy - 10), 8},
                    {Math.round(cx - 12 - boxW), Math.round(cy - 10), 8},
                    {Math.round(cx - boxW * 0.5f), Math.round(cy - 28 - boxH), 14}
            };
        } else {
            float gap = isTransfer ? r + 7.5f : r + 5.0f;
            candidates = new int[][]{
                    {Math.round(cx - boxW * 0.5f), Math.round(cy - gap - boxH), 0},
                    {Math.round(cx - boxW * 0.5f), Math.round(cy + gap), 6},
                    {Math.round(cx + gap), Math.round(cy - boxH * 0.5f), 12},
                    {Math.round(cx - gap - boxW), Math.round(cy - boxH * 0.5f), 12}
            };
        }

        UiRect bestRect = null;
        int bestIdx = 0;
        int bestOverlap = Integer.MAX_VALUE;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < candidates.length; i++) {
            int[] c = candidates[i];
            int lx = c[0];
            int ly = c[1];
            boolean outOfBounds = lx < safeArea.x() || ly < safeArea.y()
                    || lx + boxW > safeArea.right() || ly + boxH > safeArea.bottom();
            if (outOfBounds) {
                if (!mustShow) continue; // Non-critical labels NEVER clamp onto map edges!
                lx = Math.clamp(lx, safeArea.x(), Math.max(safeArea.x(), safeArea.right() - boxW));
                ly = Math.clamp(ly, safeArea.y(), Math.max(safeArea.y(), safeArea.bottom() - boxH));
            }
            UiRect rect = new UiRect(lx, ly, boxW, boxH);
            int overlap = 0;
            for (var occ : occupied) {
                overlap += overlapArea(rect, occ);
                if (!mustShow && overlap > 0) break;
            }
            if (!mustShow && overlap > 0) continue;

            double score = c[2] + overlap * 32.0;
            if (outOfBounds) score += (Math.abs(lx - c[0]) + Math.abs(ly - c[1])) * 14.0;
            if (!activeSegs.isEmpty()) {
                score += segmentCollisionPenalty(rect, activeSegs, 10.0, 36.0);
            }
            if (score < bestScore) {
                bestScore = score;
                bestRect = rect;
                bestIdx = i;
                bestOverlap = overlap;
            }
        }
        if (bestRect == null) return false;
        if (!mustShow && bestOverlap > 0) return false;

        // Reserve padded breathing room around every placed label so adjacent stations never touch
        occupied.add(new UiRect(bestRect.x() - 3, bestRect.y() - 2, bestRect.width() + 6, bestRect.height() + 4));

        if (mustShow || isRouteStop) {
            UiDraw.roundedRect(g, bestRect.x(), bestRect.y() + 1.5f, bestRect.width(), bestRect.height(), 4.5f, 0x55000000);
        }
        int bg = (hovered || selected) ? 0xEE131D2D
                : isTransfer ? 0xE8101723
                : (isOrigin || isDest) ? 0xE60E1520
                : isRouteStop ? 0xDC0F1622
                : 0xBA0C131D;
        int border = (hovered || selected) ? UiTokens.ACCENT_SKY
                : isOrigin ? UiTokens.withAlpha(UiTokens.PRIMARY, 165)
                : isDest ? UiTokens.withAlpha(UiTokens.ACCENT_SKY, 165)
                : isTransfer ? UiTokens.withAlpha(UiTokens.STATUS_WARN, 185)
                : isRouteStop ? 0x773B4E68
                : 0x442C3A4E;

        UiDraw.roundedRect(g, bestRect, 4.5f, bg);
        UiDraw.roundedOutline(g, bestRect, 4.5f, 1f, border);

        if (isTransfer && (bestIdx == 0 || bestIdx == 1)) {
            float px = Math.clamp(cx, bestRect.x() + 8, bestRect.right() - 8);
            if (bestIdx == 0) {
                UiDraw.triangle(g, px - 3.5f, bestRect.bottom() - 0.5f, px + 3.5f, bestRect.bottom() - 0.5f, px, bestRect.bottom() + 3.5f, bg);
            } else {
                UiDraw.triangle(g, px - 3.5f, bestRect.y() + 0.5f, px + 3.5f, bestRect.y() + 0.5f, px, bestRect.y() - 3.5f, bg);
            }
        }

        int tx = bestRect.x() + padX;
        int ty = bestRect.y() + (bestRect.height() - 8) / 2;
        if (transferTag != null) {
            UiDraw.roundedRect(g, tx, bestRect.y() + 2.5f, tagW, bestRect.height() - 5f, 2.5f, UiTokens.STATUS_WARN);
            UiDraw.text(g, transferTag, tx + 3, ty, tagW - 4, UiTokens.contrastInk(UiTokens.STATUS_WARN));
            tx += tagW + 4;
        }
        int textColor = (hovered || selected || isOrigin || isDest || isTransfer) ? UiTokens.TEXT_PRIMARY
                : isRouteStop ? 0xFFE6EDF5
                : node.hub() ? 0xFFD0DCEB
                : 0xFFA3B3C7;
        UiDraw.text(g, name, tx, ty, nameW + 2, textColor);
        if (walkMeta != null) {
            tx += nameW + 5;
            UiDraw.text(g, walkMeta, tx, ty, walkW, UiTokens.STATUS_WARN);
        }
        return true;
    }

    private void renderLegRouteBadge(GuiGraphics g, ActiveLegPath leg, UiRect safeArea,
                                     List<UiRect> occupied, List<Seg> activeSegs) {
        var pts = leg.nodes();
        if (pts.size() < 2 || leg.routeName() == null || leg.routeName().isBlank()) return;

        float bestLen = 0f;
        TopologyProjection.Node bestA = null, bestB = null;
        for (int i = 0; i + 1 < pts.size(); i++) {
            var a = pts.get(i);
            var b = pts.get(i + 1);
            if (!segmentIntersectsBounds(a.screenX(), a.screenY(), b.screenX(), b.screenY(), 0f)) continue;
            float len = (float) Math.hypot(b.screenX() - a.screenX(), b.screenY() - a.screenY());
            if (len > bestLen) {
                bestLen = len;
                bestA = a;
                bestB = b;
            }
        }
        if (bestA == null || bestLen < 42f) return;

        String label = leg.routeName();
        int maxW = Math.min(88, Math.max(40, safeArea.width() / 3));
        int badgeW = Math.min(maxW, UiDraw.font().width(label) + 10);
        int badgeH = 13;

        float dx = bestB.screenX() - bestA.screenX();
        float dy = bestB.screenY() - bestA.screenY();
        float nx = -dy / bestLen;
        float ny = dx / bestLen;
        if (ny > 0) { nx = -nx; ny = -ny; }

        float[] alongs = {0.5f, 0.38f, 0.62f};
        float[] sides = {15.5f, -15.5f, 23.0f, -23.0f};
        UiRect bestRect = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (float t : alongs) {
            float mx = bestA.screenX() + dx * t;
            float my = bestA.screenY() + dy * t;
            for (float s : sides) {
                int bx = Math.round(mx + nx * s - badgeW * 0.5f);
                int by = Math.round(my + ny * s - badgeH * 0.5f);
                if (bx < safeArea.x() || by < safeArea.y() || bx + badgeW > safeArea.right() || by + badgeH > safeArea.bottom()) {
                    continue;
                }
                UiRect rect = new UiRect(bx, by, badgeW, badgeH);
                int overlap = 0;
                for (var occ : occupied) overlap += overlapArea(rect, occ);
                if (overlap > 0) continue;
                double score = Math.abs(t - 0.5) * 20.0 + (Math.abs(s) > 18 ? 14.0 : 0.0);
                score += segmentCollisionPenalty(rect, activeSegs, 8.5, 28.0);
                if (score < bestScore) {
                    bestScore = score;
                    bestRect = rect;
                }
            }
        }
        if (bestRect == null) return;
        occupied.add(new UiRect(bestRect.x() - 2, bestRect.y() - 2, bestRect.width() + 4, bestRect.height() + 4));

        UiDraw.roundedRect(g, bestRect.x() - 1f, bestRect.y() + 0.5f, bestRect.width() + 2f, bestRect.height() + 2f, 4.5f, 0x66000000);
        UiDraw.roundedRect(g, bestRect.x() - 1f, bestRect.y() - 1f, bestRect.width() + 2f, bestRect.height() + 2f, 4.5f, 0xEB0A101B);
        UiDraw.roundedRect(g, bestRect, 3.5f, leg.color());
        UiDraw.text(g, label, bestRect.x() + 5, bestRect.y() + 3, bestRect.width() - 10, UiTokens.contrastInk(leg.color()));
    }

    private double collectActiveLeg(String routeId, String fromStationId, List<String> viaStations, String toStationId,
                                    List<double[]> overlayWorldCurve,
                                    Map<String, TopologyProjection.Node> nodeById, Map<String, Model.Route> routeById,
                                    List<ActiveLegPath> activeLegs, Set<String> activeStations,
                                    Map<String, Integer> activeStationColor, Set<String> activeRouteEdgeKeys) {
        var route = routeById.get(routeId);
        int color = route != null ? (0xFF000000 | (route.color() & 0xFFFFFF)) : UiTokens.ACCENT_SKY;
        String routeName = route != null ? NavFormat.primary(route.name()) : routeId;

        List<TopologyProjection.Node> chain = new ArrayList<>();
        appendNode(chain, nodeById.get(fromStationId));
        for (String via : viaStations) appendNode(chain, nodeById.get(via));
        appendNode(chain, nodeById.get(toStationId));
        if (chain.isEmpty()) return 0.0;

        List<float[]> screenCurve;
        if (overlayWorldCurve != null && overlayWorldCurve.size() >= 2) {
            screenCurve = projectWorldCurve(overlayWorldCurve);
        } else {
            List<float[]> raw = new ArrayList<>(chain.size());
            for (var cn : chain) raw.add(new float[]{cn.screenX(), cn.screenY()});
            screenCurve = smoothPolyline(raw, false);
        }
        activeLegs.add(new ActiveLegPath(routeId, routeName, color, chain, screenCurve));

        double blocks = 0.0;
        for (int k = 0; k < chain.size(); k++) {
            String stId = chain.get(k).stationId();
            activeStations.add(stId);
            activeStationColor.putIfAbsent(stId, color);
            if (k + 1 < chain.size()) {
                String nextId = chain.get(k + 1).stationId();
                activeRouteEdgeKeys.add(edgeKey(routeId, stId, nextId));
                var stA = catalog.station(stId);
                var stB = catalog.station(nextId);
                if (stA != null && stB != null) {
                    blocks += Math.hypot(stB.x() - stA.x(), stB.z() - stA.z());
                }
            }
        }
        return blocks;
    }

    private List<float[]> projectWorldCurve(List<double[]> worldPts) {
        List<float[]> out = new ArrayList<>(Math.min(worldPts.size(), 128));
        float lastX = Float.NaN, lastY = Float.NaN;
        int lastIdx = worldPts.size() - 1;
        for (int i = 0; i <= lastIdx; i++) {
            double[] wp = worldPts.get(i);
            float sx = projection.worldToScreenX(wp[0]);
            float sy = projection.worldToScreenY(wp[1]);
            if (i == 0 || i == lastIdx || Math.hypot(sx - lastX, sy - lastY) >= 2.0f) {
                out.add(new float[]{sx, sy});
                lastX = sx;
                lastY = sy;
            }
        }
        return out;
    }

    private void renderTopLeftNavCard(GuiGraphics g, UiRect card, List<ActiveLegPath> activeLegs,
                                      Model.Journey journey, ConnectionPlan refPlan, double totalBlocks) {
        if ((journey == null && refPlan == null) || activeLegs.isEmpty()) {
            String title = (mapLayer.hasLiveTerrain() ? tr("mtrnav.ui.map_live_terrain") : tr("mtrnav.ui.map_base_layer"))
                    + " · " + snapshot.nodes().size();
            int bw = Math.min(card.width(), UiDraw.font().width(title) + 20);
            UiDraw.roundedRect(g, card.x(), card.y(), bw, 16, 4.5f, 0xDE0E1521);
            UiDraw.roundedOutline(g, card.x(), card.y(), bw, 16, 4.5f, 1f, 0x7736475E);
            UiDraw.circle(g, card.x() + 7.5f, card.y() + 8f, 2.3f, mapLayer.hasLiveTerrain() ? UiTokens.STATUS_LIVE : UiTokens.ACCENT_SKY);
            UiDraw.text(g, title, card.x() + 13, card.y() + 4, bw - 16, UiTokens.TEXT_PRIMARY);
            return;
        }

        UiDraw.roundedRect(g, card.x(), card.y() + 1.5f, card.width(), card.height(), 6f, 0x55000000);
        UiDraw.roundedRect(g, card, 6f, 0xE20E1521);
        UiDraw.roundedOutline(g, card, 6f, 1f, journey != null ? 0x80384A63 : UiTokens.withAlpha(UiTokens.STATUS_WARN, 140));

        int rx = card.x() + 8;
        int ry = card.y() + 5;
        if (journey != null) {
            int mins = Math.max(1, (int) Math.round((journey.arrival() - journey.queryTime()) / 60_000.0));
            String dur = mins + tr("mtrnav.ui.minute_unit");
            UiDraw.bold(g, dur, rx, ry, 46, UiTokens.STATUS_LIVE);
            rx += UiDraw.font().width(dur) + 8;

            long dep = journey.legs().get(0).departure();
            String span = NavFormat.clockTime(dep) + " → " + NavFormat.clockTime(journey.arrival());
            String meta = journey.transfers() > 0 ? span + " · " + tr("mtrnav.transfers", journey.transfers()) : span;
            UiDraw.text(g, meta, rx, ry, card.right() - rx - 8, UiTokens.TEXT_SECONDARY);
        } else {
            String refTitle = tr("mtrnav.reference_route");
            UiDraw.bold(g, refTitle, rx, ry, 72, UiTokens.STATUS_WARN);
            rx += UiDraw.font().width(refTitle) + 8;
            String meta = refPlan.transfers() > 0 ? tr("mtrnav.transfers", refPlan.transfers()) : tr("mtrnav.route_type.normal");
            UiDraw.text(g, meta, rx, ry, card.right() - rx - 8, UiTokens.TEXT_SECONDARY);
        }

        int lx = card.x() + 8;
        int ly = card.y() + 19;
        String distText = totalBlocks >= 1000.0
                ? String.format(Locale.ROOT, "%.1f km", totalBlocks / 1000.0)
                : Math.max(10, Math.round(totalBlocks / 10.0) * 10) + " m";
        int distW = UiDraw.font().width(distText);
        int maxPillsRight = card.right() - distW - 14;

        for (int i = 0; i < activeLegs.size(); i++) {
            var leg = activeLegs.get(i);
            int avail = maxPillsRight - lx;
            if (avail < 26) break;
            if (i > 0) {
                UiDraw.text(g, "›", lx, ly + 2, 8, UiTokens.TEXT_MUTED);
                lx += 7;
                avail -= 7;
            }
            int w = UiDraw.lineBadge(g, leg.routeName(), lx, ly, Math.min(avail, 74), leg.color());
            lx += w + 4;
        }
        UiDraw.text(g, distText, card.right() - distW - 8, ly + 2, distW + 2, UiTokens.TEXT_MUTED);
    }

    private void renderBottomScaleAndStatus(GuiGraphics g) {
        double ppb = Math.max(1e-4, projection.pixelsPerBlock());
        int[] steps = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
        int chosenMeters = 100;
        int barPx = 56;
        for (int m : steps) {
            int px = (int) Math.round(m * ppb);
            if (px >= 38 && px <= 84) {
                chosenMeters = m;
                barPx = px;
                break;
            }
            if (px < 38) {
                chosenMeters = m;
                barPx = Math.clamp(px, 28, 84);
            }
        }
        String scaleLabel = chosenMeters >= 1000 ? (chosenMeters / 1000) + " km" : chosenMeters + " m";
        int labelW = UiDraw.font().width(scaleLabel);
        int pillW = barPx + labelW + 18;
        int sx = bounds.x() + 8;
        int sy = bounds.bottom() - 22;

        UiDraw.roundedRect(g, sx, sy, pillW, 15, 4f, 0xD60D141F);
        UiDraw.roundedOutline(g, sx, sy, pillW, 15, 4f, 1f, 0x5A334155);
        int bx = sx + 6;
        int by = sy + 10;
        g.fill(bx, by - 3, bx + 1, by + 1, UiTokens.TEXT_SECONDARY);
        g.fill(bx, by, bx + barPx, by + 1, UiTokens.TEXT_SECONDARY);
        g.fill(bx + barPx - 1, by - 3, bx + barPx, by + 1, UiTokens.TEXT_SECONDARY);
        UiDraw.text(g, scaleLabel, bx + barPx + 5, sy + 4, labelW + 2, UiTokens.TEXT_SECONDARY);

        String layerTag = (mapLayer.hasLiveTerrain() ? tr("mtrnav.ui.map_live_terrain") : tr("mtrnav.ui.map_base_layer"))
                + " · " + tr("mtrnav.ui.topology_hint_short");
        int rw = Math.min(bounds.width() - pillW - 24, UiDraw.font().width(layerTag) + 14);
        if (rw >= 60) {
            int rx = bounds.right() - rw - 8;
            UiDraw.roundedRect(g, rx, sy, rw, 15, 4f, 0xCE0D141F);
            UiDraw.roundedOutline(g, rx, sy, rw, 15, 4f, 1f, 0x50334155);
            UiDraw.text(g, layerTag, rx + 7, sy + 4, rw - 12, UiTokens.TEXT_MUTED);
        }
    }

    private void renderRightMapDock(GuiGraphics g, UiContext ctx) {
        UiDraw.roundedRect(g, compassRect.x(), compassRect.y() + 1f, compassRect.width(), compassRect.height(), 5f, 0x44000000);
        UiDraw.roundedRect(g, compassRect, 5f, 0xE2101723);
        UiDraw.roundedOutline(g, compassRect, 5f, 1f, 0x8036475E);
        float ccx = compassRect.centerX();
        UiDraw.triangle(g, ccx, compassRect.y() + 3.5f, ccx - 3.0f, compassRect.y() + 8.5f, ccx + 3.0f, compassRect.y() + 8.5f, 0xFFEF4444);
        UiDraw.text(g, "N", compassRect.centerX() - 3, compassRect.y() + 10, 10, UiTokens.TEXT_PRIMARY);

        UiDraw.roundedRect(g, dockRect.x(), dockRect.y() + 1f, dockRect.width(), dockRect.height(), 5f, 0x44000000);
        UiDraw.roundedRect(g, dockRect, 5f, 0xE2101723);
        UiDraw.roundedOutline(g, dockRect, 5f, 1f, 0x8036475E);
        g.fill(dockRect.x() + 4, dockRect.y() + 20, dockRect.right() - 4, dockRect.y() + 21, 0x5536475E);
        g.fill(dockRect.x() + 4, dockRect.y() + 39, dockRect.right() - 4, dockRect.y() + 40, 0x5536475E);

        zoomInBtn.render(g, ctx);
        zoomOutBtn.render(g, ctx);
        resetBtn.render(g, ctx);

        if (Math.abs(projection.zoom() - 1.0) > 0.02) {
            String zt = Math.round(projection.zoom() * 100) + "%";
            int zw = UiDraw.font().width(zt) + 6;
            int zx = dockRect.right() - zw;
            int zy = dockRect.bottom() + 4;
            UiDraw.roundedRect(g, zx, zy, zw, 12, 3f, 0xDE0E1521);
            UiDraw.text(g, zt, zx + 3, zy + 2, zw - 4, UiTokens.TEXT_MUTED);
        }
    }

    private void renderPopover(GuiGraphics g, UiContext ctx) {
        if (popoverStationId == null) {
            popoverRect = UiRect.EMPTY;
            return;
        }
        TopologyProjection.Node target = null;
        for (var n : snapshot.nodes()) {
            if (n.stationId().equals(popoverStationId)) { target = n; break; }
        }
        if (target == null) {
            popoverStationId = null;
            popoverRect = UiRect.EMPTY;
            return;
        }
        int pw = Math.min(168, Math.max(130, bounds.width() - 20));
        int ph = 78;
        int px = Math.clamp((int) target.screenX() - pw / 2, bounds.x() + 8, Math.max(bounds.x() + 8, bounds.right() - pw - 8));
        int py = (int) target.screenY() + 12;
        if (py + ph > bounds.bottom() - 28) py = Math.max(bounds.y() + 48, (int) target.screenY() - ph - 16);
        popoverRect = new UiRect(px, py, pw, ph);

        UiDraw.roundedRect(g, px, py + 2f, pw, ph, UiTokens.RADIUS_MD, 0x66000000);
        UiDraw.card(g, popoverRect, UiTokens.RADIUS_MD, UiTokens.DRAWER_BG, UiTokens.BORDER_SELECTED);
        UiDraw.bold(g, NavFormat.primary(target.name()), px + 8, py + 7, pw - 28, UiTokens.TEXT_PRIMARY);
        String en = NavFormat.secondary(target.name());
        if (!en.isEmpty()) UiDraw.text(g, en, px + 8, py + 18, pw - 16, UiTokens.TEXT_MUTED);

        int dx = px + 8;
        for (int col : target.lineColors()) {
            if (dx + 12 > px + pw - 8) break;
            UiDraw.roundedRect(g, dx, py + 29, 10, 3, 1.5f, col);
            dx += 13;
        }

        popCloseBtn.bounds(new UiRect(px + pw - 18, py + 4, 14, 14));
        int half = (pw - 20) / 2;
        popOriginBtn.bounds(new UiRect(px + 8, py + 36, half, 17));
        popDestBtn.bounds(new UiRect(px + 12 + half, py + 36, pw - 20 - half, 17));
        popBoardBtn.bounds(new UiRect(px + 8, py + 56, pw - 16, 17));

        popCloseBtn.render(g, ctx);
        popOriginBtn.render(g, ctx);
        popDestBtn.render(g, ctx);
        popBoardBtn.render(g, ctx);
    }

    private boolean pointInBounds(float x, float y, float margin) {
        return x >= bounds.x() - margin && x <= bounds.right() + margin
                && y >= bounds.y() - margin && y <= bounds.bottom() + margin;
    }

    private boolean segmentIntersectsBounds(float x1, float y1, float x2, float y2, float margin) {
        float minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        return maxX >= bounds.x() - margin && minX <= bounds.right() + margin
                && maxY >= bounds.y() - margin && minY <= bounds.bottom() + margin;
    }

    private static List<float[]> smoothPolyline(List<float[]> pts, boolean closed) {
        if (pts.size() < 3) return pts;
        List<float[]> out = new ArrayList<>(pts.size() * 4);
        out.add(pts.get(0));
        int last = pts.size() - 1;
        for (int i = 1; i < last; i++) {
            float[] prev = pts.get(i - 1);
            float[] cur = pts.get(i);
            float[] next = pts.get(i + 1);
            float d1 = (float) Math.hypot(cur[0] - prev[0], cur[1] - prev[1]);
            float d2 = (float) Math.hypot(next[0] - cur[0], next[1] - cur[1]);
            float r = Math.min(18f, Math.min(d1, d2) * 0.24f);
            if (r < 3f) {
                out.add(cur);
                continue;
            }
            float ax = cur[0] - (cur[0] - prev[0]) * (r / d1);
            float ay = cur[1] - (cur[1] - prev[1]) * (r / d1);
            float bx = cur[0] + (next[0] - cur[0]) * (r / d2);
            float by = cur[1] + (next[1] - cur[1]) * (r / d2);
            int steps = 5;
            for (int s = 0; s <= steps; s++) {
                float t = s / (float) steps;
                float u = 1f - t;
                float qx = u * u * ax + 2f * u * t * cur[0] + t * t * bx;
                float qy = u * u * ay + 2f * u * t * cur[1] + t * t * by;
                out.add(new float[]{qx, qy});
            }
        }
        out.add(pts.get(last));
        if (closed && out.size() > 2) out.add(pts.get(0));
        return out;
    }

    private static Set<String> collectFocusStationIds(Model.Journey journey, ConnectionPlan refPlan) {
        Set<String> ids = new LinkedHashSet<>();
        if (journey != null) {
            if (journey.originStationId() != null) ids.add(journey.originStationId());
            if (journey.destinationStationId() != null) ids.add(journey.destinationStationId());
            for (var leg : journey.legs()) {
                ids.add(leg.fromStationId());
                ids.add(leg.toStationId());
                ids.addAll(leg.viaStations());
            }
            for (var w : journey.walks()) {
                ids.add(w.fromStationId());
                ids.add(w.toStationId());
            }
        } else if (refPlan != null) {
            if (refPlan.origin() != null) ids.add(refPlan.origin());
            if (refPlan.destination() != null) ids.add(refPlan.destination());
            for (var leg : refPlan.legs()) {
                ids.add(leg.fromStationId());
                ids.add(leg.toStationId());
                ids.addAll(leg.viaStations());
            }
            for (var w : refPlan.walks()) {
                ids.add(w.fromStationId());
                ids.add(w.toStationId());
            }
        }
        return ids;
    }

    private static void appendNode(List<TopologyProjection.Node> chain, TopologyProjection.Node node) {
        if (node == null) return;
        if (chain.isEmpty() || !chain.get(chain.size() - 1).stationId().equals(node.stationId())) {
            chain.add(node);
        }
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String edgeKey(String routeId, String a, String b) {
        return routeId + "|" + pairKey(a, b);
    }

    private static int nodeDrawPriority(TopologyProjection.Node n, String effOrigin, String effDest,
                                        Set<String> transfers, Set<String> active, boolean hasHighlight,
                                        TopologyProjection.Node hovered) {
        if (hovered != null && hovered.stationId().equals(n.stationId())) return 50;
        if (n.stationId().equals(effOrigin) || n.stationId().equals(effDest)) return 40;
        if (transfers.contains(n.stationId())) return 30;
        if (hasHighlight && active.contains(n.stationId())) return 20;
        return n.hub() ? 10 : 0;
    }

    private static int overlapArea(UiRect a, UiRect b) {
        int x1 = Math.max(a.x(), b.x());
        int y1 = Math.max(a.y(), b.y());
        int x2 = Math.min(a.right(), b.right());
        int y2 = Math.min(a.bottom(), b.bottom());
        if (x2 <= x1 || y2 <= y1) return 0;
        return (x2 - x1) * (y2 - y1);
    }

    private static double segmentCollisionPenalty(UiRect rect, List<Seg> segs, double threshold, double weight) {
        if (segs.isEmpty()) return 0.0;
        float cx = rect.centerX();
        float cy = rect.centerY();
        float rx1 = rect.x() + 2, ry1 = rect.y() + 2;
        float rx2 = rect.right() - 2, ry2 = rect.bottom() - 2;
        double penalty = 0.0;
        for (var s : segs) {
            double d = Math.min(
                    distPointToSegment(cx, cy, s),
                    Math.min(
                            Math.min(distPointToSegment(rx1, ry1, s), distPointToSegment(rx2, ry1, s)),
                            Math.min(distPointToSegment(rx1, ry2, s), distPointToSegment(rx2, ry2, s))
                    ));
            if (d < threshold) {
                double ratio = (threshold - d) / threshold;
                penalty += ratio * ratio * weight * 10.0;
            }
        }
        return penalty;
    }

    private static double distPointToSegment(float px, float py, Seg s) {
        float dx = s.x2 - s.x1;
        float dy = s.y2 - s.y1;
        float lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-4f) return Math.hypot(px - s.x1, py - s.y1);
        float t = Math.clamp(((px - s.x1) * dx + (py - s.y1) * dy) / lenSq, 0f, 1f);
        float projX = s.x1 + t * dx;
        float projY = s.y1 + t * dy;
        return Math.hypot(px - projX, py - projY);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        if (compassRect.contains(x, y) && button == 0) {
            toggleFocusOrOverview();
            return true;
        }
        if (zoomInBtn.mouseClicked(x, y, button) || zoomOutBtn.mouseClicked(x, y, button) || resetBtn.mouseClicked(x, y, button)) return true;
        if (popoverStationId != null && popoverRect.contains(x, y)) {
            if (popCloseBtn.mouseClicked(x, y, button) || popOriginBtn.mouseClicked(x, y, button)
                    || popDestBtn.mouseClicked(x, y, button) || popBoardBtn.mouseClicked(x, y, button)) return true;
            return true;
        }
        if (button == 0) {
            var hit = TopologyProjection.hitNode(snapshot, x, y, 12.0);
            if (hit != null) {
                popoverStationId = hit.stationId().equals(popoverStationId) ? null : hit.stationId();
                return true;
            }
            popoverStationId = null;
            panning = true;
            lastMouseX = x;
            lastMouseY = y;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (!panning || button != 0) return false;
        userMovedCamera = true;
        lastInteractNanos = System.nanoTime();
        projection.pan(x - lastMouseX, y - lastMouseY);
        lastMouseX = x;
        lastMouseY = y;
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = zoomInBtn.mouseReleased(x, y, button)
                | zoomOutBtn.mouseReleased(x, y, button)
                | resetBtn.mouseReleased(x, y, button)
                | popCloseBtn.mouseReleased(x, y, button)
                | popOriginBtn.mouseReleased(x, y, button)
                | popDestBtn.mouseReleased(x, y, button)
                | popBoardBtn.mouseReleased(x, y, button);
        if (panning && button == 0) {
            panning = false;
            lastInteractNanos = 0L;
            return true;
        }
        return handled;
    }

    public boolean mouseScrolled(double x, double y, double vertical) {
        if (!bounds.contains(x, y)) return false;
        userMovedCamera = true;
        lastInteractNanos = System.nanoTime();
        projection.zoomAt(vertical > 0 ? 1.24 : 0.80, x, y, bounds);
        return true;
    }
}
