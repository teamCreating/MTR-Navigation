package io.github.teamcreating.mtrnav.client.overlay;

import io.github.teamcreating.mtrmap.api.MtrMapApi;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrnav.client.OverlayBridge;
import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.Model;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Isolated optional adapter for MTR Map Overlay 1.6.0+ (MtrMapApi 1.0.0).
 * Never class-loaded unless {@link io.github.teamcreating.mtrnav.client.OverlayBridge}
 * confirms {@code MtrMapApi} is present on the classpath.
 * All methods must be invoked on the Minecraft client thread.
 */
public final class OverlayApiAdapter implements OverlayBridge.ApiRuntime {
    private static final String OWNER_PLANNER = "mtrnav:planner";
    private static final String OWNER_JOURNEY = "mtrnav:journey";
    private static final double COMPLETED_OPACITY = 0.35;
    private static final double UNUSED_OPACITY = 0.08;
    private static final double ACTIVE_WIDTH_SCALE = 2.2;
    private static final double COMPLETED_WIDTH_SCALE = 1.35;
    private static final int MAX_TRACK_QUERY = 4096;

    public interface Host {
        String currentDimension();
        Model.Catalog currentCatalog();
        String displayName();
        void openPlanTarget(String navDimension, String stationId);
        default void logInfo(String message) {}
        void logWarn(String message, Throwable error);
    }

    public record LegSpec(
            String routeId,
            String fromStationId,
            String toStationId,
            String fromPlatformId,
            String toPlatformId,
            int fromStopIndex,
            int toStopIndex
    ) {
        public LegSpec(String routeId, String fromPlatformId, String toPlatformId, int fromStopIndex, int toStopIndex) {
            this(routeId, "", "", fromPlatformId, toPlatformId, fromStopIndex, toStopIndex);
        }

        public boolean hasNativePlatforms() {
            return routeId != null && !routeId.isBlank()
                    && fromPlatformId != null && !fromPlatformId.isBlank() && !fromPlatformId.startsWith("map:")
                    && toPlatformId != null && !toPlatformId.isBlank() && !toPlatformId.startsWith("map:")
                    && fromStopIndex >= 0 && toStopIndex > fromStopIndex;
        }

        public boolean hasVerifiableEndpoints() {
            return routeId != null && !routeId.isBlank()
                    && fromStopIndex >= 0 && toStopIndex > fromStopIndex
                    && (hasNativePlatforms()
                    || (fromStationId != null && !fromStationId.isBlank() && toStationId != null && !toStationId.isBlank()));
        }
    }

    public record HighlightSpec(String id, String dimension, List<LegSpec> legs, int completedCount) {
        public HighlightSpec {
            legs = List.copyOf(legs);
            completedCount = Math.clamp(completedCount, 0, legs.size());
        }

        public String fingerprint() {
            StringBuilder sb = new StringBuilder();
            sb.append(id).append('|').append(dimension).append('|').append(completedCount);
            for (var l : legs) {
                sb.append('|').append(l.routeId()).append(':').append(l.fromStopIndex()).append('-').append(l.toStopIndex());
            }
            return sb.toString();
        }
    }

    private final Supplier<Optional<Client>> clientSupplier;
    private final Host host;

    private Client registeredClient;
    private Registration navRegistration;
    private boolean registrationFailed;

    private ViewSession focusSession;
    private WorldRef focusWorld;
    private String lastSyncFingerprint = "";
    private String lastDiagnostic = "";
    private boolean highlightActive;

    private Catalog lastOverlayCatalog;
    private Model.Catalog lastNavCatalog;

    private String lastCurveCacheKey = "";
    private List<List<double[]>> lastCurvePaths = List.of();

    public OverlayApiAdapter(Host host) {
        this(MtrMapApi::findClient, host);
    }

    public OverlayApiAdapter(OverlayBridge.HostBridge hostBridge) {
        this(MtrMapApi::findClient, new Host() {
            @Override public String currentDimension() { return hostBridge.currentDimension(); }
            @Override public Model.Catalog currentCatalog() { return hostBridge.currentCatalog(); }
            @Override public String displayName() { return hostBridge.displayName(); }
            @Override public void openPlanTarget(String navDimension, String stationId) { hostBridge.openPlanTarget(navDimension, stationId); }
            @Override public void logInfo(String message) { hostBridge.logInfo(message); }
            @Override public void logWarn(String message, Throwable error) { hostBridge.logWarn(message, error); }
        });
    }

    public OverlayApiAdapter(Supplier<Optional<Client>> clientSupplier, Host host) {
        this.clientSupplier = Objects.requireNonNull(clientSupplier);
        this.host = Objects.requireNonNull(host);
    }

    public static String toOverlayDimension(String navDimension) {
        if (navDimension == null || navDimension.isBlank()) return "";
        return navDimension.contains(":") ? navDimension : navDimension.replaceFirst("/", ":");
    }

    public static String toNavDimension(String overlayDimension) {
        if (overlayDimension == null || overlayDimension.isBlank()) return "";
        return overlayDimension.contains("/") ? overlayDimension : overlayDimension.replaceFirst(":", "/");
    }

    public static HighlightSpec fromTracking(JourneyTracker.State state) {
        if (state == null || state.journey() == null) return null;
        var journey = state.journey();
        var specs = new ArrayList<LegSpec>(journey.legs().size());
        for (var leg : journey.legs()) {
            specs.add(new LegSpec(leg.routeId(), leg.fromStationId(), leg.toStationId(),
                    leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex()));
        }
        int completed = state.phase() == JourneyTracker.Phase.COMPLETE
                ? specs.size()
                : Math.clamp(state.legIndex(), 0, specs.size());
        return new HighlightSpec("trip:" + journey.id() + ":" + state.phase().name(), journey.dimension(), specs, completed);
    }

    public static HighlightSpec fromJourneyPreview(Model.Journey journey) {
        if (journey == null) return null;
        var specs = new ArrayList<LegSpec>(journey.legs().size());
        for (var leg : journey.legs()) {
            specs.add(new LegSpec(leg.routeId(), leg.fromStationId(), leg.toStationId(),
                    leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex()));
        }
        return new HighlightSpec("preview:" + journey.id(), journey.dimension(), specs, 0);
    }

    public static HighlightSpec fromReferencePreview(String dimension, ConnectionPlan plan) {
        if (plan == null || dimension == null || dimension.isBlank()) return null;
        var specs = new ArrayList<LegSpec>(plan.legs().size());
        for (var leg : plan.legs()) {
            specs.add(new LegSpec(leg.routeId(), leg.fromStationId(), leg.toStationId(),
                    leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex()));
        }
        return new HighlightSpec("ref:" + plan.signature(), dimension, specs, 0);
    }

    @Override
    public boolean isClientPresent() {
        return clientSupplier.get().isPresent();
    }

    @Override
    public boolean isWorldReady(String navDimension) {
        if (navDimension == null || navDimension.isBlank()) return false;
        var clientOpt = clientSupplier.get();
        if (clientOpt.isEmpty()) return false;
        try {
            var worldOpt = clientOpt.get().world(toOverlayDimension(navDimension));
            if (worldOpt.isEmpty()) return false;
            Catalog cat = clientOpt.get().catalog(worldOpt.get());
            return cat != null && cat.status() == Status.READY;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean isHighlightActive() {
        return highlightActive;
    }

    @Override
    public void syncState(String currentNavDimension, JourneyTracker.State tracking, Model.Journey previewJourney, ConnectionPlan previewReference) {
        HighlightSpec spec = null;
        if (tracking != null) spec = fromTracking(tracking);
        else if (previewJourney != null) spec = fromJourneyPreview(previewJourney);
        else if (previewReference != null) spec = fromReferencePreview(currentNavDimension, previewReference);
        syncHighlight(currentNavDimension, spec);
    }

    @Override
    public List<List<double[]>> resolveJourneyCurves(String navDimension, Model.Journey journey) {
        if (journey == null || journey.legs().isEmpty()) return List.of();
        var specs = new ArrayList<LegSpec>(journey.legs().size());
        for (var leg : journey.legs()) {
            specs.add(new LegSpec(leg.routeId(), leg.fromStationId(), leg.toStationId(),
                    leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex()));
        }
        return resolveLegCurves(navDimension, specs);
    }

    @Override
    public List<List<double[]>> resolveReferenceCurves(String navDimension, ConnectionPlan plan) {
        if (plan == null || plan.legs().isEmpty()) return List.of();
        var specs = new ArrayList<LegSpec>(plan.legs().size());
        for (var leg : plan.legs()) {
            specs.add(new LegSpec(leg.routeId(), leg.fromStationId(), leg.toStationId(),
                    leg.fromPlatformId(), leg.toPlatformId(), leg.fromStopIndex(), leg.toStopIndex()));
        }
        return resolveLegCurves(navDimension, specs);
    }

    /**
     * Registers MTR Navigation once as an official navigation provider in Xaero/JourneyMap tooltips.
     * Supports both 4-argument per-target availability (Overlay 1.6.0+) and 3-argument fallback (Overlay 1.5.3).
     */
    @Override
    public void ensureNavigationRegistered() {
        var clientOpt = clientSupplier.get();
        if (clientOpt.isEmpty()) return;
        Client client = clientOpt.get();
        if (navRegistration != null && registeredClient == client) return;
        if (registrationFailed && registeredClient == client) return;

        closeRegistrationQuietly();
        registeredClient = client;
        String displayName = host.displayName();
        if (displayName == null || displayName.isBlank()) displayName = "MTR Navigation";
        if (displayName.length() > 80) displayName = displayName.substring(0, 80);

        try {
            try {
                navRegistration = client.registerNavigation(OWNER_PLANNER, displayName, this::isTargetNavigable, this::handleNavigateTarget);
                diag("registered navigation provider (4-arg)");
            } catch (UnsupportedOperationException | NoSuchMethodError legacyOverlay) {
                navRegistration = client.registerNavigation(OWNER_PLANNER, displayName, this::handleNavigateTarget);
                diag("registered navigation provider (3-arg legacy)");
            }
            registrationFailed = false;
        } catch (RuntimeException | LinkageError e) {
            registrationFailed = true;
            host.logWarn("[MTR Navigation] Failed to register navigation provider with MTR Map Overlay", e);
        }
    }

    boolean isTargetNavigable(NavigationTarget target) {
        if (target == null || target.world() == null || target.stationId() == null || target.stationId().isBlank()) {
            return false;
        }
        String targetDim = toNavDimension(target.world().dimensionId());
        String currentDim = host.currentDimension();
        if (currentDim == null || currentDim.isBlank() || !currentDim.equals(targetDim)) {
            return false;
        }
        Model.Catalog navCatalog = host.currentCatalog();
        if (navCatalog != null && navCatalog.dimension().equals(targetDim) && !navCatalog.stations().isEmpty()) {
            return navCatalog.station(target.stationId()) != null;
        }
        return true;
    }

    void handleNavigateTarget(NavigationTarget target) {
        if (target == null || target.world() == null || target.stationId() == null || target.stationId().isBlank()) return;
        host.openPlanTarget(toNavDimension(target.world().dimensionId()), target.stationId());
    }

    /**
     * Synchronizes active journey (or optional map preview) onto Overlay's map focus view.
     * Deduplicated by state fingerprint; zero work when state is unchanged.
     */
    public void syncHighlight(String currentNavDimension, HighlightSpec spec) {
        var clientOpt = clientSupplier.get();
        if (clientOpt.isEmpty()) {
            clearFocusSession();
            return;
        }
        if (spec == null || spec.legs().isEmpty() || currentNavDimension == null || currentNavDimension.isBlank()) {
            clearFocusSession();
            return;
        }
        if (!currentNavDimension.equals(spec.dimension())) {
            clearFocusSession();
            return;
        }

        Client client = clientOpt.get();
        String overlayDim = toOverlayDimension(currentNavDimension);
        Optional<WorldRef> worldOpt;
        try {
            worldOpt = client.world(overlayDim);
        } catch (RuntimeException | LinkageError e) {
            clearFocusSession();
            return;
        }
        if (worldOpt.isEmpty()) {
            highlightActive = false;
            diag("world(" + overlayDim + ") is empty (server may lack Overlay 1.6.0 overlay-1 channel or catalog still syncing)");
            return;
        }
        WorldRef world = worldOpt.get();
        Catalog overlayCat;
        try {
            overlayCat = client.catalog(world);
        } catch (RuntimeException | LinkageError e) {
            clearFocusSession();
            return;
        }
        if (overlayCat == null || overlayCat.status() != Status.READY) {
            highlightActive = false;
            diag("catalog status=" + (overlayCat == null ? "null" : overlayCat.status()));
            return;
        }

        String fp = world.serverEpoch() + "|" + world.dimensionId() + "|" + overlayCat.revision() + "|" + spec.fingerprint();
        if (fp.equals(lastSyncFingerprint)) return;

        Map<String, Route> routeMap = new HashMap<>(overlayCat.routes().size() * 2);
        for (var r : overlayCat.routes()) routeMap.put(r.id(), r);

        var completedSpans = new ArrayList<SelectedSpan>();
        var remainingSpans = new ArrayList<SelectedSpan>();
        var fallbackWholeRoutes = new LinkedHashSet<String>();
        var fallbackCompletedRoutes = new LinkedHashSet<String>();
        int noPathLegs = 0, resolveFailLegs = 0, unverifiedLegs = 0;

        for (int i = 0; i < spec.legs().size(); i++) {
            var leg = spec.legs().get(i);
            boolean completed = i < spec.completedCount();
            Route route = routeMap.get(leg.routeId());
            if (!isVerifiedLeg(route, leg)) {
                unverifiedLegs++;
                continue;
            }
            RouteSection section = toVerifiedSection(route, leg);
            if (section == null) {
                noPathLegs++;
                fallbackWholeRoutes.add(route.id());
                if (completed) fallbackCompletedRoutes.add(route.id());
                continue;
            }
            // Resolve each leg individually so one unresolvable leg never drops exact spans for the other legs.
            Resolution res = resolveImmediate(client, world, overlayCat.revision(), List.of(section));
            if (res == null) {
                highlightActive = false;
                diag("resolve returned pending/null for route " + route.id());
                return;
            }
            if (res.status() == Status.STALE) {
                highlightActive = false;
                lastSyncFingerprint = "";
                diag("resolve STALE; retrying next tick");
                return;
            }
            if (res.status() == Status.READY && !res.spans().isEmpty()) {
                if (completed) completedSpans.addAll(res.spans());
                else remainingSpans.addAll(res.spans());
            } else {
                resolveFailLegs++;
                fallbackWholeRoutes.add(route.id());
                if (completed) fallbackCompletedRoutes.add(route.id());
            }
        }
        if (completedSpans.isEmpty() && remainingSpans.isEmpty() && fallbackWholeRoutes.isEmpty()) {
            clearFocusSession();
            lastSyncFingerprint = fp;
            diag("no legs verified (unverified=" + unverifiedLegs + ", overlayRoutes=" + overlayCat.routes().size() + ")");
            return;
        }

        // Filter out any rail missing from MapDataCache or with curve == null (nativeLength = NaN),
        // because Overlay's ViewSession.replace rejects the entire request with UNAVAILABLE if even 1 rail lacks a curve.
        int droppedSpans = 0;
        if (!completedSpans.isEmpty() || !remainingSpans.isEmpty()) {
            Set<String> validRails = validCurvedRails(client, world, completedSpans, remainingSpans);
            if (validRails != null) {
                int before = completedSpans.size() + remainingSpans.size();
                completedSpans = new ArrayList<>(completedSpans.stream().filter(s -> validRails.contains(s.rail().railId())).toList());
                remainingSpans = new ArrayList<>(remainingSpans.stream().filter(s -> validRails.contains(s.rail().railId())).toList());
                droppedSpans = before - (completedSpans.size() + remainingSpans.size());
            }
        }

        if (completedSpans.isEmpty() && remainingSpans.isEmpty() && fallbackWholeRoutes.isEmpty()) {
            clearFocusSession();
            lastSyncFingerprint = fp;
            diag("resolved 0 curved spans (dropped=" + droppedSpans + ")");
            return;
        }

        try {
            if (focusSession == null || !world.equals(focusWorld)) {
                closeFocusSessionOnly();
                String name = host.displayName();
                if (name == null || name.isBlank()) name = "MTR Navigation";
                if (name.length() > 80) name = name.substring(0, 80);
                focusSession = client.openView(OWNER_JOURNEY, name, Purpose.FOCUS, 0);
                focusWorld = world;
            }
            ViewRequest request = buildViewRequest(world, overlayCat.revision(), remainingSpans, completedSpans,
                    fallbackWholeRoutes, fallbackCompletedRoutes);
            Status status = awaitImmediate(focusSession.replace(request));
            if (status == Status.READY) {
                highlightActive = true;
                lastSyncFingerprint = fp;
                diag("highlight READY (remSpans=" + remainingSpans.size() + ", compSpans=" + completedSpans.size()
                        + ", wholeRoutes=" + fallbackWholeRoutes.size()
                        + ", noPath=" + noPathLegs + ", resFail=" + resolveFailLegs
                        + ", unverified=" + unverifiedLegs + ", droppedRails=" + droppedSpans + ")");
            } else if (status == Status.STALE) {
                highlightActive = false;
                lastSyncFingerprint = "";
                diag("replace STALE; retrying");
            } else if (status == Status.CLOSED) {
                closeFocusSessionOnly();
                highlightActive = false;
                lastSyncFingerprint = "";
                diag("replace CLOSED; reopening");
            } else {
                highlightActive = false;
                lastSyncFingerprint = fp;
                diag("replace returned " + status);
            }
        } catch (RuntimeException | LinkageError e) {
            closeFocusSessionOnly();
            highlightActive = false;
            lastSyncFingerprint = fp;
            host.logWarn("[MTR Navigation] Failed to update MTR Map Overlay focus view", e);
        }
    }

    private static ViewRequest buildViewRequest(
            WorldRef world,
            long revision,
            List<SelectedSpan> remainingSpans,
            List<SelectedSpan> completedSpans,
            Set<String> fallbackWholeRoutes,
            Set<String> fallbackCompletedRoutes
    ) {
        Style activeStyle = new Style(Optional.empty(), 1.0, ACTIVE_WIDTH_SCALE, Pattern.INHERIT);
        Style completedStyle = new Style(Optional.empty(), COMPLETED_OPACITY, COMPLETED_WIDTH_SCALE, Pattern.INHERIT);

        var allSpans = new ArrayList<SelectedSpan>(completedSpans.size() + remainingSpans.size());
        allSpans.addAll(completedSpans);
        allSpans.addAll(remainingSpans);

        var spanStyles = new ArrayList<SpanStyle>(allSpans.size());
        for (var span : completedSpans) spanStyles.add(new SpanStyle(span, completedStyle));
        // Remaining wins when a repeated/circular journey traverses a completed physical interval again.
        for (var span : remainingSpans) spanStyles.add(new SpanStyle(span, activeStyle));

        var routeStyles = new LinkedHashMap<String, Style>();
        for (String rId : fallbackWholeRoutes) {
            routeStyles.put(rId, fallbackCompletedRoutes.contains(rId) ? completedStyle : activeStyle);
        }
        return new ViewRequest(world, revision, allSpans, fallbackWholeRoutes, Unmatched.DIM,
                routeStyles, true, Set.of(), spanStyles, UNUSED_OPACITY);
    }

    private static Set<String> validCurvedRails(Client client, WorldRef world, List<SelectedSpan> a, List<SelectedSpan> b) {
        var ids = new LinkedHashSet<String>();
        for (var s : a) if (ids.size() < MAX_TRACK_QUERY) ids.add(s.rail().railId());
        for (var s : b) if (ids.size() < MAX_TRACK_QUERY) ids.add(s.rail().railId());
        if (ids.isEmpty()) return Set.of();
        try {
            List<Track> tracks = client.tracks(world, ids);
            var valid = new HashSet<String>(tracks.size() * 2);
            for (var t : tracks) {
                if (Double.isFinite(t.nativeLength()) && t.nativeLength() > 0.0) {
                    valid.add(t.id());
                }
            }
            return valid;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Reads Overlay's official Catalog snapshot and converts it into a Navigation {@link Model.Catalog}.
     * Returns {@code null} when Overlay catalog is not READY so callers may fall back cleanly.
     */
    @Override
    public Model.Catalog catalog(String navDimension) {
        var clientOpt = clientSupplier.get();
        if (clientOpt.isEmpty() || navDimension == null || navDimension.isBlank()) return null;
        Client client = clientOpt.get();
        try {
            var worldOpt = client.world(toOverlayDimension(navDimension));
            if (worldOpt.isEmpty()) return null;
            Catalog overlayCat = client.catalog(worldOpt.get());
            if (overlayCat == null || overlayCat.status() != Status.READY || overlayCat.stations().isEmpty()) return null;
            if (overlayCat == lastOverlayCatalog && lastNavCatalog != null && lastNavCatalog.dimension().equals(navDimension)) {
                return lastNavCatalog;
            }
            var stations = new ArrayList<Model.Station>(overlayCat.stations().size());
            for (var s : overlayCat.stations()) {
                stations.add(new Model.Station(
                        s.id(), s.name(),
                        Math.round(s.position().x()), Math.round(s.position().y()), Math.round(s.position().z()),
                        List.of()));
            }
            stations.sort(Comparator.comparing(Model.Station::id));

            var platforms = new ArrayList<Model.Platform>(overlayCat.platforms().size());
            for (var p : overlayCat.platforms()) {
                platforms.add(new Model.Platform(
                        p.id(), p.stationId(), p.name(),
                        Math.round(p.position().x()), Math.round(p.position().y()), Math.round(p.position().z())));
            }

            var routes = new ArrayList<Model.Route>(overlayCat.routes().size());
            for (var r : overlayCat.routes()) {
                var stopPlatforms = new ArrayList<String>(r.stops().size());
                for (var stop : r.stops()) {
                    if (stop.platformId() != null && !stop.platformId().isEmpty()) {
                        stopPlatforms.add(stop.platformId());
                    }
                }
                boolean circular = stopPlatforms.size() >= 2 && stopPlatforms.getFirst().equals(stopPlatforms.getLast());
                routes.add(new Model.Route(r.id(), r.name(), r.color(), stopPlatforms, circular, parseRouteType(r.type())));
            }

            lastOverlayCatalog = overlayCat;
            lastNavCatalog = new Model.Catalog(navDimension, overlayCat.revision(), stations, platforms, routes);
            return lastNavCatalog;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Resolves real curved railway geometry (world X/Z polylines) for highlighted legs via Overlay's
     * {@link Client#tracks(WorldRef, Set)} API, cached per (world, revision, leg list).
     */
    public List<List<double[]>> resolveLegCurves(String navDimension, List<LegSpec> legs) {
        if (navDimension == null || navDimension.isBlank() || legs == null || legs.isEmpty()) return List.of();
        var clientOpt = clientSupplier.get();
        if (clientOpt.isEmpty()) return List.of();
        Client client = clientOpt.get();
        try {
            var worldOpt = client.world(toOverlayDimension(navDimension));
            if (worldOpt.isEmpty()) return List.of();
            WorldRef world = worldOpt.get();
            Catalog overlayCat = client.catalog(world);
            if (overlayCat == null || overlayCat.status() != Status.READY) return List.of();

            StringBuilder keyBuilder = new StringBuilder();
            keyBuilder.append(world.serverEpoch()).append('|').append(overlayCat.revision());
            for (var l : legs) {
                keyBuilder.append('|').append(l.routeId()).append(':').append(l.fromStopIndex()).append('-').append(l.toStopIndex());
            }
            String cacheKey = keyBuilder.toString();
            if (cacheKey.equals(lastCurveCacheKey)) return lastCurvePaths;

            Map<String, Route> routeMap = new HashMap<>(overlayCat.routes().size() * 2);
            for (var r : overlayCat.routes()) routeMap.put(r.id(), r);

            List<List<SelectedSpan>> spansPerLeg = new ArrayList<>(legs.size());
            LinkedHashSet<String> neededRailIds = new LinkedHashSet<>();
            for (var leg : legs) {
                Route route = routeMap.get(leg.routeId());
                if (!isVerifiedLeg(route, leg)) {
                    spansPerLeg.add(List.of());
                    continue;
                }
                RouteSection section = toVerifiedSection(route, leg);
                if (section == null) {
                    spansPerLeg.add(List.of());
                    continue;
                }
                Resolution res = resolveImmediate(client, world, overlayCat.revision(), List.of(section));
                if (res != null && res.status() == Status.READY && !res.spans().isEmpty()) {
                    spansPerLeg.add(res.spans());
                    for (var span : res.spans()) {
                        if (neededRailIds.size() < MAX_TRACK_QUERY) neededRailIds.add(span.rail().railId());
                    }
                } else {
                    spansPerLeg.add(List.of());
                }
            }

            if (neededRailIds.isEmpty()) {
                lastCurveCacheKey = cacheKey;
                lastCurvePaths = List.of();
                return List.of();
            }

            List<Track> fetchedTracks = client.tracks(world, neededRailIds);
            Map<String, Track> trackById = new HashMap<>(fetchedTracks.size() * 2);
            for (var t : fetchedTracks) trackById.put(t.id(), t);

            List<List<double[]>> curves = new ArrayList<>(legs.size());
            boolean anyCurve = false;
            for (var spans : spansPerLeg) {
                List<double[]> polyline = stitchSpansToWorldXZ(spans, trackById);
                if (polyline.size() >= 2) anyCurve = true;
                curves.add(polyline);
            }
            lastCurveCacheKey = cacheKey;
            lastCurvePaths = anyCurve ? List.copyOf(curves) : List.of();
            return lastCurvePaths;
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    @Override
    public void resetSession() {
        clearFocusSession();
        lastOverlayCatalog = null;
        lastNavCatalog = null;
        lastCurveCacheKey = "";
        lastCurvePaths = List.of();
        lastDiagnostic = "";
    }

    public void closeAll() {
        resetSession();
        closeRegistrationQuietly();
        registeredClient = null;
        registrationFailed = false;
    }

    private void diag(String msg) {
        if (!msg.equals(lastDiagnostic)) {
            lastDiagnostic = msg;
            host.logInfo("[MTR Navigation] Overlay API: " + msg);
        }
    }

    private void clearFocusSession() {
        closeFocusSessionOnly();
        highlightActive = false;
        lastSyncFingerprint = "";
    }

    private void closeFocusSessionOnly() {
        if (focusSession != null) {
            try {
                focusSession.close();
            } catch (RuntimeException | LinkageError ignored) { }
            focusSession = null;
            focusWorld = null;
        }
    }

    private void closeRegistrationQuietly() {
        if (navRegistration != null) {
            try {
                navRegistration.close();
            } catch (RuntimeException | LinkageError ignored) { }
            navRegistration = null;
        }
    }

    static boolean isVerifiedLeg(Route route, LegSpec leg) {
        if (route == null || leg == null || !leg.hasVerifiableEndpoints()) return false;
        List<Stop> stops = route.stops();
        if (leg.fromStopIndex() < 0 || leg.toStopIndex() >= stops.size() || leg.fromStopIndex() >= leg.toStopIndex()) {
            return false;
        }
        Stop fromStop = stops.get(leg.fromStopIndex());
        Stop toStop = stops.get(leg.toStopIndex());
        if (leg.hasNativePlatforms()) {
            return leg.fromPlatformId().equals(fromStop.platformId())
                    && leg.toPlatformId().equals(toStop.platformId());
        }
        // Fallback when Navigation's native mode still holds map: placeholder platform IDs:
        // verify exact routeId + stop ordinals + station IDs against Overlay's authoritative server Catalog.
        return !fromStop.platformId().isEmpty() && !toStop.platformId().isEmpty()
                && leg.fromStationId().equals(fromStop.stationId())
                && leg.toStationId().equals(toStop.stationId());
    }

    static RouteSection toVerifiedSection(Route route, LegSpec leg) {
        if (!isVerifiedLeg(route, leg)) return null;
        List<PathVariant> paths = route.paths();
        if (paths.isEmpty()) return null;
        if (paths.size() == 1) {
            return coversRange(paths.getFirst(), leg.fromStopIndex(), leg.toStopIndex())
                    ? new RouteSection(route.id(), leg.fromStopIndex(), leg.toStopIndex(), Optional.empty())
                    : null;
        }
        for (var variant : paths) {
            if (coversRange(variant, leg.fromStopIndex(), leg.toStopIndex())) {
                return new RouteSection(route.id(), leg.fromStopIndex(), leg.toStopIndex(), Optional.of(variant.id()));
            }
        }
        return null;
    }

    private static boolean coversRange(PathVariant variant, int fromStop, int toStop) {
        int matched = 0;
        for (var leg : variant.legs()) {
            if (leg.fromStop() >= fromStop && leg.toStop() <= toStop) matched++;
        }
        return matched == (toStop - fromStop);
    }

    private static Resolution resolveImmediate(Client client, WorldRef world, long revision, List<RouteSection> sections) {
        if (sections.isEmpty()) return new Resolution(Status.READY, revision, List.of(), List.of());
        return awaitImmediate(client.resolve(world, revision, sections));
    }

    private static <T> T awaitImmediate(CompletionStage<T> stage) {
        if (stage == null) return null;
        var future = stage.toCompletableFuture();
        if (!future.isDone() || future.isCompletedExceptionally() || future.isCancelled()) return null;
        return future.getNow(null);
    }

    static List<double[]> stitchSpansToWorldXZ(List<SelectedSpan> spans, Map<String, Track> trackById) {
        if (spans.isEmpty()) return List.of();
        var out = new ArrayList<double[]>();
        for (var span : spans) {
            Track track = trackById.get(span.rail().railId());
            if (track == null || track.points().size() < 2) continue;
            List<double[]> slice = sliceTrackXZ(track.points(), span.rail().from(), span.rail().to());
            for (var pt : slice) {
                if (out.isEmpty()) {
                    out.add(pt);
                } else {
                    double[] prev = out.get(out.size() - 1);
                    if (Math.hypot(pt[0] - prev[0], pt[1] - prev[1]) > 0.35) {
                        out.add(pt);
                    }
                }
            }
        }
        return out;
    }

    static List<double[]> sliceTrackXZ(List<Point> points, double from, double to) {
        int n = points.size();
        if (n < 2) return List.of();
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            Point a = points.get(i - 1), b = points.get(i);
            double dy = (Double.isFinite(a.y()) && Double.isFinite(b.y())) ? (b.y() - a.y()) : 0.0;
            cum[i] = cum[i - 1] + Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + dy * dy + (b.z() - a.z()) * (b.z() - a.z()));
        }
        double total = cum[n - 1];
        if (total <= 1e-6) return List.of();

        boolean reverse = from > to;
        double lo = Math.min(from, to) * total;
        double hi = Math.max(from, to) * total;

        var slice = new ArrayList<double[]>();
        slice.add(sampleAtDistance(points, cum, lo));
        for (int i = 1; i < n - 1; i++) {
            if (cum[i] > lo + 1e-4 && cum[i] < hi - 1e-4) {
                slice.add(new double[]{points.get(i).x(), points.get(i).z()});
            }
        }
        slice.add(sampleAtDistance(points, cum, hi));
        if (reverse) Collections.reverse(slice);
        return slice;
    }

    private static double[] sampleAtDistance(List<Point> points, double[] cum, double dist) {
        if (dist <= 0.0) return new double[]{points.getFirst().x(), points.getFirst().z()};
        int last = points.size() - 1;
        if (dist >= cum[last]) return new double[]{points.get(last).x(), points.get(last).z()};
        for (int i = 1; i <= last; i++) {
            if (cum[i] >= dist) {
                double seg = Math.max(1e-9, cum[i] - cum[i - 1]);
                double t = Math.clamp((dist - cum[i - 1]) / seg, 0.0, 1.0);
                Point a = points.get(i - 1), b = points.get(i);
                return new double[]{a.x() + (b.x() - a.x()) * t, a.z() + (b.z() - a.z()) * t};
            }
        }
        return new double[]{points.get(last).x(), points.get(last).z()};
    }

    private static Model.RouteType parseRouteType(String raw) {
        if (raw == null || raw.isBlank()) return Model.RouteType.UNKNOWN;
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> Model.RouteType.NORMAL;
            case "LIGHT_RAIL" -> Model.RouteType.LIGHT_RAIL;
            case "HIGH_SPEED" -> Model.RouteType.HIGH_SPEED;
            default -> Model.RouteType.UNKNOWN;
        };
    }
}
