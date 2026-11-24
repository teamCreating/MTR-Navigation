package io.github.teamcreating.mtrnav.core.ui;

import io.github.teamcreating.mtrnav.core.Model;
import java.util.*;

/**
 * Pure-Java 2D schematic projection of a MTR Catalog's stations (x, z) and route connections.
 * Supports pan, zoom, hit-testing, and graceful handling of degenerate or sparse networks.
 */
public final class TopologyProjection {
    public record Node(String stationId, String name, float screenX, float screenY, List<Integer> lineColors, boolean hub) {}
    public record Edge(String routeId, String fromStationId, String toStationId, int color, float x1, float y1, float x2, float y2) {}
    public record Snapshot(List<Node> nodes, List<Edge> edges, double minX, double maxX, double minZ, double maxZ) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), 0, 0, 0, 0);
    }

    private double panX;
    private double panY;
    private double zoom = 1.0;
    private double lastMidX;
    private double lastMidZ;
    private double lastCenterX;
    private double lastCenterY;
    private double lastBaseScale = 1.0;
    private double lastScale = 1.0;

    public double panX() { return panX; }
    public double panY() { return panY; }
    public double zoom() { return zoom; }
    public double pixelsPerBlock() { return lastScale; }
    public double maxZoom() { return Math.clamp(1.5 / Math.max(1e-5, lastBaseScale), 12.0, 256.0); }

    public float worldToScreenX(double worldX) {
        return (float) (lastCenterX + (worldX - lastMidX) * lastScale);
    }

    public float worldToScreenY(double worldZ) {
        return (float) (lastCenterY + (worldZ - lastMidZ) * lastScale);
    }

    public double screenToWorldX(double screenX) {
        return lastMidX + (screenX - lastCenterX) / Math.max(1e-6, lastScale);
    }

    public double screenToWorldZ(double screenY) {
        return lastMidZ + (screenY - lastCenterY) / Math.max(1e-6, lastScale);
    }

    public void centerOnWorld(double worldX, double worldZ, UiRect viewport) {
        panX = -(worldX - lastMidX) * lastScale;
        panY = -(worldZ - lastMidZ) * lastScale;
    }

    public void resetView() {
        panX = 0;
        panY = 0;
        zoom = 1.0;
    }

    public void pan(double dx, double dy) {
        panX += dx;
        panY += dy;
    }

    public void zoomAt(double factor, double anchorX, double anchorY, UiRect viewport) {
        double next = Math.clamp(zoom * factor, 0.5, maxZoom());
        if (Math.abs(next - zoom) < 1e-6) return;
        double ratio = next / zoom;
        double cx = viewport.centerX();
        double cy = viewport.centerY();
        panX = (anchorX - cx) - ((anchorX - cx) - panX) * ratio;
        panY = (anchorY - cy) - ((anchorY - cy) - panY) * ratio;
        zoom = next;
    }

    /**
     * Fits the camera smoothly onto a subset of stations (e.g. the active highlighted journey),
     * ensuring readability even on huge worlds with far-away outlier stations.
     */
    public void fitStations(Collection<String> stationIds, Model.Catalog catalog, UiRect viewport) {
        if (catalog == null || catalog.stations().isEmpty() || viewport.isEmpty()) return;
        double[] full = fullBounds(catalog, viewport);
        double fullMidX = full[0], fullMidZ = full[1], baseScale = full[2];
        this.lastBaseScale = baseScale;

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        int matched = 0;
        Set<String> idSet = stationIds instanceof Set<String> s ? s : new HashSet<>(stationIds);
        for (var st : catalog.stations()) {
            if (idSet.contains(st.id())) {
                minX = Math.min(minX, st.x());
                maxX = Math.max(maxX, st.x());
                minZ = Math.min(minZ, st.z());
                maxZ = Math.max(maxZ, st.z());
                matched++;
            }
        }
        if (matched == 0) {
            resetView();
            return;
        }
        applySubBounds(minX, maxX, minZ, maxZ, fullMidX, fullMidZ, baseScale, viewport, true);
    }

    /**
     * Fits the dense urban core when extreme far-away outlier stations (e.g. distant airports/test stations)
     * would otherwise squash downtown stations into an unreadable clump.
     */
    public void fitCoreCluster(Model.Catalog catalog, UiRect viewport) {
        if (catalog == null || catalog.stations().isEmpty() || viewport.isEmpty()) return;
        var stations = catalog.stations();
        if (stations.size() < 5) {
            resetView();
            return;
        }
        double[] full = fullBounds(catalog, viewport);
        double fullMidX = full[0], fullMidZ = full[1], baseScale = full[2];
        double fullSpanX = Math.max(1.0, full[3]);
        double fullSpanZ = Math.max(1.0, full[4]);
        this.lastBaseScale = baseScale;

        double[] xs = stations.stream().mapToDouble(Model.Station::x).sorted().toArray();
        double[] zs = stations.stream().mapToDouble(Model.Station::z).sorted().toArray();
        double medX = xs[xs.length / 2];
        double medZ = zs[zs.length / 2];
        double q1X = xs[xs.length / 4], q3X = xs[(xs.length * 3) / 4];
        double q1Z = zs[zs.length / 4], q3Z = zs[(zs.length * 3) / 4];
        double spread = Math.max(250.0, Math.hypot(q3X - q1X, q3Z - q1Z));
        double limit = spread * 2.2;

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        int kept = 0;
        for (var st : stations) {
            if (Math.hypot(st.x() - medX, st.z() - medZ) <= limit) {
                minX = Math.min(minX, st.x());
                maxX = Math.max(maxX, st.x());
                minZ = Math.min(minZ, st.z());
                maxZ = Math.max(maxZ, st.z());
                kept++;
            }
        }
        if (kept < 2 || kept == stations.size()) {
            resetView();
            return;
        }
        double coreSpan = Math.max(1.0, Math.max(maxX - minX, maxZ - minZ));
        double fullSpan = Math.max(fullSpanX, fullSpanZ);
        if (fullSpan / coreSpan < 1.8) {
            resetView();
            return;
        }
        applySubBounds(minX, maxX, minZ, maxZ, fullMidX, fullMidZ, baseScale, viewport, false);
    }

    private void applySubBounds(double minX, double maxX, double minZ, double maxZ,
                                double fullMidX, double fullMidZ, double baseScale,
                                UiRect viewport, boolean routeMode) {
        double subSpanX = Math.max(80.0, maxX - minX);
        double subSpanZ = Math.max(80.0, maxZ - minZ);
        double padX = Math.min(116.0, Math.max(64.0, viewport.width() * 0.30));
        double padY = Math.min(132.0, Math.max(76.0, viewport.height() * 0.38));
        double usableW = Math.max(30.0, viewport.width() - padX);
        double usableH = Math.max(30.0, viewport.height() - padY);
        double desiredScale = Math.min(usableW / subSpanX, usableH / subSpanZ);
        // Prevent over-zooming on very short hops
        desiredScale = Math.min(desiredScale, 0.45);
        double nextZoom = Math.clamp(desiredScale / Math.max(1e-6, baseScale), 0.75, maxZoom());
        double scale = baseScale * nextZoom;
        double subMidX = (minX + maxX) * 0.5;
        double subMidZ = (minZ + maxZ) * 0.5;
        this.zoom = nextZoom;
        this.panX = -(subMidX - fullMidX) * scale;
        // Shift slightly downward when route card occupies top-left of viewport
        this.panY = -(subMidZ - fullMidZ) * scale + (routeMode ? 10.0 : 0.0);
    }

    private static double[] fullBounds(Model.Catalog catalog, UiRect viewport) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (var st : catalog.stations()) {
            minX = Math.min(minX, st.x());
            maxX = Math.max(maxX, st.x());
            minZ = Math.min(minZ, st.z());
            maxZ = Math.max(maxZ, st.z());
        }
        double spanX = Math.max(1.0, maxX - minX);
        double spanZ = Math.max(1.0, maxZ - minZ);
        double padX = Math.min(96.0, Math.max(52.0, viewport.width() * 0.24));
        double padY = Math.min(104.0, Math.max(60.0, viewport.height() * 0.28));
        double usableW = Math.max(24.0, viewport.width() - padX);
        double usableH = Math.max(24.0, viewport.height() - padY);
        double baseScale = (maxX == minX && maxZ == minZ)
                ? 1.0
                : Math.min(usableW / spanX, usableH / spanZ);
        return new double[]{(minX + maxX) * 0.5, (minZ + maxZ) * 0.5, baseScale, spanX, spanZ};
    }

    public Snapshot project(Model.Catalog catalog, UiRect viewport) {
        if (catalog == null || catalog.stations().isEmpty() || viewport.isEmpty()) return Snapshot.EMPTY;

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (var st : catalog.stations()) {
            minX = Math.min(minX, st.x());
            maxX = Math.max(maxX, st.x());
            minZ = Math.min(minZ, st.z());
            maxZ = Math.max(maxZ, st.z());
        }

        double spanX = Math.max(1.0, maxX - minX);
        double spanZ = Math.max(1.0, maxZ - minZ);
        double padX = Math.min(96.0, Math.max(52.0, viewport.width() * 0.24));
        double padY = Math.min(104.0, Math.max(60.0, viewport.height() * 0.28));
        double usableW = Math.max(24.0, viewport.width() - padX);
        double usableH = Math.max(24.0, viewport.height() - padY);
        double baseScale = (maxX == minX && maxZ == minZ)
                ? 1.0
                : Math.min(usableW / spanX, usableH / spanZ);
        double midX = (minX + maxX) * 0.5;
        double midZ = (minZ + maxZ) * 0.5;
        double centerX = viewport.centerX() + panX;
        double centerY = viewport.centerY() + panY;
        double scale = baseScale * zoom;
        this.lastBaseScale = baseScale;
        this.lastMidX = midX;
        this.lastMidZ = midZ;
        this.lastCenterX = centerX;
        this.lastCenterY = centerY;
        this.lastScale = scale;

        Map<String, float[]> coords = new LinkedHashMap<>();
        for (var st : catalog.stations()) {
            float sx = (float) (centerX + (st.x() - midX) * scale);
            float sy = (float) (centerY + (st.z() - midZ) * scale);
            coords.put(st.id(), new float[]{sx, sy});
        }

        // Map platformId -> stationId
        Map<String, String> platformStation = new HashMap<>();
        for (var p : catalog.platforms()) platformStation.put(p.id(), p.stationId());

        // Collect deduplicated station-to-station edges per route and line colors per station
        Map<String, LinkedHashSet<Integer>> stationColors = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        Set<String> seenEdges = new HashSet<>();

        for (var route : catalog.routes()) {
            int color = 0xFF000000 | (route.color() & 0xFFFFFF);
            String prevStation = null;
            for (String platId : route.platforms()) {
                String stId = platformStation.get(platId);
                if (stId == null || !coords.containsKey(stId)) continue;
                stationColors.computeIfAbsent(stId, k -> new LinkedHashSet<>()).add(color);
                if (prevStation != null && !prevStation.equals(stId)) {
                    String a = prevStation.compareTo(stId) <= 0 ? prevStation : stId;
                    String b = prevStation.compareTo(stId) <= 0 ? stId : prevStation;
                    String key = route.id() + "|" + a + "|" + b;
                    if (seenEdges.add(key)) {
                        float[] p1 = coords.get(prevStation);
                        float[] p2 = coords.get(stId);
                        edges.add(new Edge(route.id(), prevStation, stId, color, p1[0], p1[1], p2[0], p2[1]));
                    }
                }
                prevStation = stId;
            }
        }

        List<Node> nodes = new ArrayList<>(catalog.stations().size());
        for (var st : catalog.stations()) {
            float[] pt = coords.get(st.id());
            var colors = new ArrayList<>(stationColors.getOrDefault(st.id(), new LinkedHashSet<>()));
            nodes.add(new Node(st.id(), st.name(), pt[0], pt[1], List.copyOf(colors), colors.size() >= 2));
        }

        return new Snapshot(List.copyOf(nodes), List.copyOf(edges), minX, maxX, minZ, maxZ);
    }

    public static Node hitNode(Snapshot snapshot, double mouseX, double mouseY, double hitRadius) {
        Node best = null;
        double bestDistSq = hitRadius * hitRadius;
        for (var node : snapshot.nodes()) {
            double dx = mouseX - node.screenX();
            double dy = mouseY - node.screenY();
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                best = node;
            }
        }
        return best;
    }
}
