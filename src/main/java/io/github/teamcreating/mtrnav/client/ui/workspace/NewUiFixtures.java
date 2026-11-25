package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.core.*;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/**
 * Isolated test fixtures for -PnewUiPreview -PnewUiFixtures visual QA.
 * Never activates during normal gameplay.
 */
public final class NewUiFixtures {
    private static int scene;
    private static long baseMillis;
    private static List<Journey> sampleJourneys = List.of();

    private NewUiFixtures() {}

    public static boolean enabled() {
        return Boolean.getBoolean("mtrnav.newUiFixtures");
    }

    public static void ensureLoaded() {
        if (!enabled()) return;
        if (catalog == null) loadScene(0);
        refreshClock();
    }

    public static void refreshClock() {
        if (!enabled()) return;
        long now = System.nanoTime();
        CLOCK.sample(System.currentTimeMillis(), now);
        if (!boardStation.isEmpty()) {
            boardSampleMillis = System.currentTimeMillis();
            boardUpdatedNanos = now;
        }
    }

    public static void cycleScene() {
        if (!enabled()) return;
        loadScene((scene + 1) % 5);
    }

    public static String sceneLabel() {
        return switch (scene) {
            case 1 -> "长双语站名";
            case 2 -> "空状态";
            case 3 -> "线路参考（无班次）";
            case 4 -> "活动行程";
            default -> "五方案（苔灯群岛）";
        };
    }

    public static void loadScene(int index) {
        scene = index;
        baseMillis = System.currentTimeMillis();
        usingNative = false;
        origin = "a";
        destination = "d";
        tracking = null;
        setReferencePlans(List.of());
        vehicleCondition = VehicleCondition.none();
        fleet = List.of("m_train_cab_1", "m_train_trailer", "sp1900_cab_1", "crh_cab");
        status = "mtrnav.ready";

        String fromName = scene == 1
                ? "苔灯港星瀚航站综合交通枢纽|Mosslight Harbour Aster Terminal Hub"
                : "苔灯港|Mosslight Harbour";
        String toName = scene == 1
                ? "星沼湾月影新区综合换乘中心|Starfen Bay Moonshadow Interchange"
                : "星沼湾|Starfen Bay";

        catalog = new Catalog("minecraft/overworld", 1,
                List.of(
                        new Station("a", fromName, 0, 64, 0, List.of()),
                        new Station("c", "雾松丘|Mistpine Hill", 160, 64, 30, List.of()),
                        new Station("b", "风铃渡|Windchime Crossing", 340, 64, 70, List.of()),
                        new Station("d", toName, 520, 64, 190, List.of()),
                        new Station("e", "潮音角|Tidebell Point", 390, 64, -90, List.of()),
                        new Station("f", "银桦南|South Birch", 150, 64, 210, List.of())
                ),
                List.of(
                        new Platform("a1", "a", "1", 0, 64, 0),
                        new Platform("a2", "a", "2", 0, 64, 4),
                        new Platform("c1", "c", "1", 160, 64, 30),
                        new Platform("b1", "b", "星汐快线", 340, 64, 70),
                        new Platform("b4", "b", "B 站台", 340, 64, 74),
                        new Platform("d4", "d", "B 站台", 520, 64, 190),
                        new Platform("e1", "e", "1", 390, 64, -90),
                        new Platform("f1", "f", "1", 150, 64, 210)
                ),
                List.of(
                        new Route("star-tide", "星汐快线", 0x4FA3D9, List.of("a1", "c1", "b1", "e1"), false, RouteType.HIGH_SPEED),
                        new Route("firefly", "萤环线", 0xE8A13C, List.of("b4", "d4", "f1", "b4"), true, RouteType.NORMAL),
                        new Route("cloud-bay", "岚湾轻轨", 0xB78BE0, List.of("a2", "c1", "b1"), false, RouteType.LIGHT_RAIL),
                        new Route("sea-green", "海绿支线", 0x3EC98F, List.of("a2", "f1", "d4"), false, RouteType.NORMAL)
                ));

        var all = new ArrayList<Journey>();
        for (int i = 0; i < 5; i++) {
            long dep = baseMillis + (3 + i * 2) * 60_000L;
            long arr = baseMillis + (22 + i * 5) * 60_000L;
            String r1 = i == 1 ? "cloud-bay" : i == 2 ? "sea-green" : "star-tide";
            var first = new RideLeg(
                    new RunKey("sample-" + i, 0, i, 0), "test", r1,
                    "a", i == 2 ? "f" : "b", i == 0 ? "a1" : "a2", i == 2 ? "f1" : "b1", 0, 2,
                    i == 2 ? "银桦南" : "风铃渡", dep, dep + 7 * 60_000L, i % 2 == 0,
                    List.of("c"),
                    new Consist(List.of(i == 1 ? "sp1900_cab_1" : "m_train_cab_1", "m_train_trailer", "m_train_cab_1"), StockSource.ACTUAL));
            var second = new RideLeg(
                    new RunKey("sample-last-" + i, 0, i, 0), "test", "firefly",
                    i == 2 ? "f" : "b", "d", i == 2 ? "f1" : "b4", "d4", 0, 1,
                    "星沼湾", dep + 13 * 60_000L, arr, false, List.of(),
                    new Consist(List.of("m_train_cab_1", "m_train_trailer"), StockSource.PLANNED));
            var walk = new WalkLeg("test", "b", "b", "b1", "b4", 180, first.arrival(), 1, "personal");
            all.add(new Journey("sample-" + i, "minecraft/overworld", 1, "a", "d",
                    baseMillis, arr, 1, List.of(first, second), i == 2 ? List.of() : List.of(walk), 0));
        }

        sampleJourneys = List.copyOf(all);
        journeys = sampleJourneys;
        fastestArrival = sampleJourneys.getFirst().arrival();
        if (scene == 2) {
            journeys = List.of();
            origin = destination = "";
            status = "mtrnav.choose_stations";
        } else if (scene == 3) {
            journeys = List.of();
            setReferencePlans(ConnectionPlan.findAlternatives(catalog, "a", "d", sort, 5));
            referenceReason = "mtrnav.reference_no_service";
        } else if (scene == 4) {
            tracking = new JourneyTracker.State(sampleJourneys.getFirst(), 0, JourneyTracker.Phase.RIDING, "", false);
        }

        populateBoard("a");
        refreshClock();
    }

    public static void populateBoard(String stationId) {
        boardStation = stationId;
        boardLoading = false;
        long now = System.currentTimeMillis();
        var list = new ArrayList<StopEvent>();
        String[] routes = {"star-tide", "cloud-bay", "star-tide", "firefly", "cloud-bay", "sea-green"};
        String[] dests = {"风铃渡 · 潮音角", "风铃渡", "潮音角", "星沼湾", "雾松丘", "银桦南 · 星沼湾"};
        String[] plats = {"a1", "a2", "a1", "b4", "a2", "a2"};
        for (int i = 0; i < routes.length; i++) {
            list.add(new StopEvent(
                    new RunKey("fixture-" + i, 0, i, 0), "test", routes[i], plats[i], 0, dests[i],
                    now + (2 + i * 3) * 60_000L + 40_000L,
                    now + (3 + i * 3) * 60_000L + 40_000L,
                    0, i % 2 == 0,
                    new Consist(i % 2 == 0
                            ? List.of("m_train_cab_1", "m_train_trailer", "m_train_cab_1")
                            : List.of("sp1900_cab_1", "sp1900_cab_1"),
                            i % 2 == 0 ? StockSource.ACTUAL : StockSource.PLANNED)));
        }
        arrivals = List.copyOf(list);
        refreshClock();
    }

    public static void query() {
        if (origin.isBlank() || destination.isBlank() || catalog == null) {
            journeys = List.of();
            setReferencePlans(List.of());
            status = "mtrnav.choose_stations";
            return;
        }
        if (origin.equals(destination)) {
            journeys = List.of();
            setReferencePlans(List.of());
            status = "mtrnav.same_station";
            return;
        }
        List<Journey> candidates;
        if (origin.equals("a") && destination.equals("d")) {
            candidates = sampleJourneys;
        } else {
            candidates = buildDynamicFixtureJourneys(origin, destination);
        }
        journeys = vehicleCondition.mode() == VehicleMode.REQUIRE
                ? candidates.stream().filter(vehicleCondition::matches).toList()
                : vehicleCondition.mode() == VehicleMode.PREFER
                ? candidates.stream().sorted(Comparator.comparing(j -> !vehicleCondition.matches(j))).toList()
                : candidates;
        fastestArrival = journeys.isEmpty() ? 0 : journeys.getFirst().arrival();
        setReferencePlans(List.of());
        status = journeys.isEmpty() ? "mtrnav.no_route" : "mtrnav.ready";
        refreshClock();
    }

    private static List<Journey> buildDynamicFixtureJourneys(String fromId, String toId) {
        var found = ConnectionPlan.find(catalog, fromId, toId);
        if (found.isEmpty()) return List.of();
        var plan = found.get();
        long now = System.currentTimeMillis();
        var list = new ArrayList<Journey>();
        for (int i = 0; i < 3; i++) {
            long cursor = now + (2 + i * 4) * 60_000L;
            var rideLegs = new ArrayList<RideLeg>();
            var walkLegs = new ArrayList<WalkLeg>();
            for (int k = 0; k < plan.legs().size(); k++) {
                var pl = plan.legs().get(k);
                if (k > 0) {
                    walkLegs.add(new WalkLeg("fixture-walk-" + k, pl.fromStationId(), pl.fromStationId(),
                            plan.legs().get(k - 1).toPlatformId(), pl.fromPlatformId(), 120, cursor, k, "personal"));
                    cursor += 180_000L;
                }
                long rideMs = Math.max(3, (pl.viaStations().size() + 1) * 3) * 60_000L;
                long dep = cursor;
                long arr = dep + rideMs;
                rideLegs.add(new RideLeg(
                        new RunKey("dyn-" + i + "-" + k, 0, i, 0), "test", pl.routeId(),
                        pl.fromStationId(), pl.toStationId(), pl.fromPlatformId(), pl.toPlatformId(),
                        pl.fromStopIndex(), pl.toStopIndex(), pl.direction(), dep, arr, i % 2 == 0,
                        pl.viaStations(),
                        new Consist(List.of("m_train_cab_1", "m_train_trailer", "m_train_cab_1"),
                                i == 0 ? StockSource.ACTUAL : StockSource.PLANNED)));
                cursor = arr;
            }
            list.add(new Journey("dyn-" + fromId + "-" + toId + "-" + i, catalog.dimension(), catalog.revision(),
                    fromId, toId, now, cursor, Math.max(0, rideLegs.size() - 1), rideLegs, walkLegs, 0));
        }
        return List.copyOf(list);
    }
}
