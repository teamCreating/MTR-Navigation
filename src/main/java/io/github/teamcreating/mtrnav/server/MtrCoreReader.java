package io.github.teamcreating.mtrnav.server;

import io.github.teamcreating.mtrnav.core.Model;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Trip;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.TransportMode;
import org.mtr.core.simulation.Simulator;
import java.lang.reflect.*;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Version-pinned, read-only adapter. Call exclusively on the owning Simulator thread. */
public final class MtrCoreReader {
    private static final int MAX_EVENTS = 200_000;
    private final Class<?> consumerType;
    private final Method iterateArrivals;
    private final Field platformIndexInRoute;

    public MtrCoreReader() {
        try {
            consumerType = Class.forName("org.mtr.core.data.Siding$ArrivalConsumer");
            // Checked against BOTH published 4.1.0-beta.2 JARs. Never hook a compiler-generated lambda.
            var accept = consumerType.getDeclaredMethod("accept", Vehicle.class, Trip.class, int.class, Trip.StopTime.class,
                    long.class, long.class, boolean.class, long.class, int.class, long.class);
            if (accept.getReturnType() != void.class) throw new NoSuchMethodException("ArrivalConsumer.accept");
            iterateArrivals = Siding.class.getDeclaredMethod("iterateArrivals", long.class, long.class, long.class, long.class, consumerType);
            iterateArrivals.setAccessible(true);
            platformIndexInRoute = Class.forName("org.mtr.core.data.VehicleExtraData$VehiclePlatformRouteInfo").getDeclaredField("platformIndexInRoute");
            platformIndexInRoute.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MTR Navigation requires the verified MTR 4.1.0-beta.2 Core interface", e);
        }
    }
    public Snapshot capture(Simulator simulator, Settings settings) {
        long now = simulator.getCurrentMillis();
        var stations = new ArrayList<Model.Station>(); var platforms = new ArrayList<Model.Platform>(); var routes = new ArrayList<Model.Route>();
        for (var station : simulator.stations) {
            var center = station.getCenter();
            stations.add(new Model.Station(id(station), station.getName(), center.getX(), center.getY(), center.getZ(),
                    station.connectedStations.stream().map(s -> id(s)).sorted().toList()));
            for (var platform : station.savedRails) if (platform.getTransportMode() == TransportMode.TRAIN) {
                var position = platform.getMidPosition();
                platforms.add(new Model.Platform(id(platform), id(station), platform.getName(), position.getX(), position.getY(), position.getZ()));
            }
        }
        var validPlatforms = new HashSet<String>(); platforms.forEach(p -> validPlatforms.add(p.id()));
        for (var route : simulator.routes) {
            if (route.getTransportMode() != TransportMode.TRAIN || route.getHidden()) continue;
            // Keep every occurrence, including repeated platforms. Invalid routes are excluded as a whole.
            var ids = new ArrayList<String>();
            boolean valid = true;
            for (var stop : route.getRoutePlatforms()) {
                var p = stop.getPlatform();
                if (p == null || !validPlatforms.contains(id(p))) { valid = false; break; }
                ids.add(id(p));
            }
            if (valid && ids.size() >= 2) routes.add(new Model.Route(id(route), route.getName(), route.getColor(), ids,
                    route.getCircularState() != org.mtr.core.data.Route.CircularState.NONE));
        }
        stations.sort(Comparator.comparing(Model.Station::id)); platforms.sort(Comparator.comparing(Model.Platform::id)); routes.sort(Comparator.comparing(Model.Route::id));
        // Record hashes include full names, coordinates, connected IDs and ordered stop occurrences.
        long revision = hash(stations, platforms, routes);
        var catalog = new Catalog(simulator.dimension, revision, stations, platforms, routes);
        var validRoutes = new HashSet<String>(); routes.forEach(r -> validRoutes.add(r.id()));
        var events = new ArrayList<StopEvent>();
        for (var siding : simulator.sidings) {
            if (siding.getTransportMode() != TransportMode.TRAIN || siding.area == null) continue;
            var ids = new LinkedHashSet<Long>();
            for (var route : siding.area.routes) if (validRoutes.contains(id(route))) for (var stop : route.getRoutePlatforms()) {
                if (stop.getPlatform() != null) ids.add(stop.getPlatform().getId());
            }
            Object callback = Proxy.newProxyInstance(consumerType.getClassLoader(), new Class<?>[]{consumerType}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) {
                    var vehicle = (Vehicle) args[0]; var trip = (Trip) args[1]; var stop = (Trip.StopTime) args[3];
                    if (!validRoutes.contains(id(trip.route))) return null;
                    if (events.size() >= MAX_EVENTS) throw new IllegalStateException("MTR navigation event budget exceeded");
                    long deviation = (long) args[7];
                    events.add(new StopEvent(new RunKey(id(siding), trip.tripIndexInBlock, (int) args[8], (long) args[9]),
                            vehicle == null ? "" : vehicleId(siding, vehicle), id(trip.route), Long.toHexString(stop.platformId), (int) args[2],
                            stop.customDestination, (long) args[4] + deviation, (long) args[5] + deviation, deviation, (boolean) args[6]));
                    return null;
                }
                return switch (method.getName()) { case "toString" -> "MTR Navigation arrivals consumer"; case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0]; default -> null; };
            });
            for (long platform : ids) {
                try { iterateArrivals.invoke(siding, now, platform, 120_000L, settings.horizonHours() * 3_600_000L, callback); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException("Native arrivals bridge failed", e); }
            }
        }
        var eventsByVehicle = new HashMap<String, List<StopEvent>>();
        for (var event : events) if (!event.vehicleId().isEmpty()) eventsByVehicle.computeIfAbsent(event.vehicleId(), ignored -> new ArrayList<>()).add(event);
        var riders = new HashMap<UUID, Riding>();
        for (var siding : simulator.sidings) if (siding.getTransportMode() == TransportMode.TRAIN) siding.iterateVehicles(vehicle -> {
            var extra = vehicle.vehicleExtraData;
            String id = vehicleId(siding, vehicle);
            List<StopEvent> vehicleEvents = eventsByVehicle.getOrDefault(id, List.of());
            // Native route index and platform, not projected map coordinates or station names.
            int index = -1;
            if (siding.area != null) try { index = platformIndexInRoute.getInt(siding.area.getVehiclePlatformRouteInfo(extra.getStopIndex())); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            final int nativeIndex = index;
            boolean doorsOpen = !vehicle.isMoving() && extra.getDoorMultiplier() > 0;
            var atStop = vehicleEvents.stream().filter(e -> e.realtime() && doorsOpen && e.arrival() <= now + 500 && e.departure() >= now - 500
                            && (e.platformId().equals(Long.toHexString(extra.getThisPlatformId())) || e.platformId().equals(Long.toHexString(extra.getNextPlatformId()))))
                    .sorted(Comparator.comparingLong(StopEvent::arrival)).toList();
            StopEvent target = atStop.size() == 1 ? atStop.getFirst() : null;
            boolean atPlatform = target != null;
            if (target == null && !doorsOpen) {
                target = vehicleEvents.stream().filter(e -> e.realtime() && e.departure() >= now
                                && e.stopIndex() == nativeIndex && e.routeId().equals(Long.toHexString(extra.getThisRouteId()))
                                && e.platformId().equals(Long.toHexString(extra.getNextPlatformId())))
                        .min(Comparator.comparingLong(StopEvent::arrival)).orElse(null);
            }
            var riding = target == null ? new Riding(id, "", "", -1, false, null)
                    : new Riding(id, target.routeId(), target.platformId(), target.stopIndex(), atPlatform, target.run());
            extra.iterateRidingEntities(entity -> { if (entity.isOnVehicle()) riders.put(entity.uuid, riding); });
        });
        return new Snapshot(catalog, now, events, riders);
    }
    private static String id(org.mtr.core.data.NameColorDataBase value) { return Long.toHexString(value.getId()); }
    private static String vehicleId(Siding siding, Vehicle vehicle) { return id(siding) + ":" + id(vehicle); }
    private static long hash(List<Model.Station> stations, List<Model.Platform> platforms, List<Model.Route> routes) {
        long value = 0xcbf29ce484222325L;
        for (Object item : List.of(stations, platforms, routes)) { value ^= item.hashCode(); value *= 0x100000001b3L; }
        return value;
    }
}
