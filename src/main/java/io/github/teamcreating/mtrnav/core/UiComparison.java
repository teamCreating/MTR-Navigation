package io.github.teamcreating.mtrnav.core;

import java.util.*;

/** In-memory review state. Deliberately independent of the live client and persisted preferences. */
public final class UiComparison {
    public enum Variant { AE2, BEDROCK, JAVA, HYBRID;
        public Variant next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public enum Page { QUERY, STATIONS, HUD, COMPONENTS }
    public enum View { ROUTES, DEPARTURES, ACTIVE }
    public enum Scenario { NORMAL, LONG_NAMES, EMPTY, LOADING, ERROR, REFERENCE, MANY;
        public Scenario next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public record Station(String id, String chinese, String english, int distance) {
        public String name() { return chinese + " " + english; }
        public String group() { return english.substring(0, 1).toUpperCase(Locale.ROOT); }
    }
    public record Journey(String id, int minutes, int transfers, String line, String via) {}
    public static final List<Station> STATIONS = List.of(
            new Station("nanyuan", "南苑", "Nanyuan", 180),
            new Station("binhai", "滨海北", "Binhai North", 1360),
            new Station("airport", "滨海国际机场交通中心", "Airport Transport Centre", 4200),
            new Station("central", "中央车站", "Central", 620),
            new Station("east", "东港", "East Harbour", 980),
            new Station("garden", "花园路", "Garden Road", 2100),
            new Station("hill", "山城", "Hill City", 3100),
            new Station("lake", "湖滨", "Lakeside", 840),
            new Station("north", "北部新区综合交通枢纽", "North District Integrated Transport Hub", 5300),
            new Station("park", "公园", "Park", 1150),
            new Station("river", "江岸", "Riverside", 1750),
            new Station("south", "南站", "South Station", 2500),
            new Station("university", "大学城", "University", 3800),
            new Station("west", "西门", "West Gate", 720));
    public static final List<Journey> JOURNEYS = List.of(
            new Journey("direct", 28, 0, "1 号线 Local", "直达 · 无需换乘"),
            new Journey("airport", 31, 1, "Local → Airport Express", "中央车站换乘 · 1 → 2 站台"),
            new Journey("river", 36, 1, "Local → Riverside", "湖滨换乘 · 2 → 4 站台"),
            new Journey("north", 42, 2, "Local → Central → North", "中央车站、东港换乘"),
            new Journey("walk", 45, 1, "Local → 步行 → North", "步行换乘 4 分钟 · 260 米"));

    public Variant variant = Variant.JAVA;
    public Page page = Page.QUERY;
    public View view = View.ROUTES;
    public Scenario scenario = Scenario.NORMAL;
    public String origin = STATIONS.getFirst().name(), destination = STATIONS.get(1).name();
    public String stationSearch = "", selectedStation = "", index = "", notice = "";
    public boolean pickingOrigin, nearby, distanceOrder, fewestTransfers, queryCollapsed, details, activeTrip;
    public int selectedJourney, stationScroll, detailScroll, settingsScroll;
    public final EnumMap<View, Integer> listScroll = new EnumMap<>(View.class);
    public final HudSettings hud = new HudSettings();
    private HudSnapshot savedHud;

    public UiComparison() { for (var v : View.values()) listScroll.put(v, 0); }
    public void variant(Variant value) { variant = Objects.requireNonNull(value); }
    public List<Journey> journeys() {
        if (scenario == Scenario.EMPTY || scenario == Scenario.LOADING || scenario == Scenario.ERROR) return List.of();
        if (scenario == Scenario.REFERENCE) return List.of(JOURNEYS.getFirst());
        var result = new ArrayList<>(JOURNEYS);
        if (scenario == Scenario.MANY) for (int i = 5; i < 16; i++)
            result.add(new Journey("extra-" + i, 46 + i, i % 3, "Local → Central", "备用方案 " + (i + 1)));
        if (fewestTransfers) result.sort(Comparator.comparingInt(Journey::transfers).thenComparingInt(Journey::minutes));
        return List.copyOf(result);
    }
    public boolean canStart() {
        return !origin.isBlank() && !destination.isBlank() && scenario != Scenario.REFERENCE && !journeys().isEmpty();
    }
    public void start() {
        if (!canStart()) return;
        activeTrip = true; view = View.ACTIVE; notice = "测试导航已开始 · 不连接真实班次";
    }
    public void query() {
        scenario = origin.isBlank() || destination.isBlank() ? Scenario.EMPTY : Scenario.NORMAL;
        selectedJourney = 0; detailScroll = 0; listScroll.put(View.ROUTES, 0); view = View.ROUTES;
        notice = "查询完成 · 使用固定测试数据";
    }
    public void scenario(Scenario value) {
        scenario = value; selectedJourney = 0; detailScroll = 0;
        for (var v : View.values()) listScroll.put(v, 0);
        if (value == Scenario.LONG_NAMES) {
            origin = "滨海国际机场交通中心 Binhai International Airport Transport Centre";
            destination = STATIONS.get(8).name();
        } else { origin = STATIONS.getFirst().name(); destination = STATIONS.get(1).name(); }
        notice = "";
    }
    public void openStations(boolean from) { pickingOrigin = from; page = Page.STATIONS; }
    public List<Station> stations() {
        String search = stationSearch.strip().toLowerCase(Locale.ROOT);
        var comparator = distanceOrder ? Comparator.comparingInt(Station::distance)
                : Comparator.comparing(Station::english, String.CASE_INSENSITIVE_ORDER);
        return STATIONS.stream().filter(s -> search.isEmpty() || s.name().toLowerCase(Locale.ROOT).contains(search))
                .filter(s -> !nearby || s.distance() <= 1500)
                .filter(s -> index.isEmpty() || s.group().equals(index)).sorted(comparator).toList();
    }
    public void filterChanged() { stationScroll = 0; }
    public boolean pickStation() {
        var selected = stations().stream().filter(s -> s.id().equals(selectedStation)).findFirst();
        if (selected.isEmpty()) return false;
        if (pickingOrigin) origin = selected.get().name(); else destination = selected.get().name();
        page = Page.QUERY; notice = "已选择" + (pickingOrigin ? "起点" : "终点") + " · 测试数据"; return true;
    }
    public void selectJourney(int index) {
        if (index >= 0 && index < journeys().size()) { selectedJourney = index; detailScroll = 0; }
    }
    public void saveHud() { savedHud = hud.snapshot(); notice = "已保存到本次预览 · 不写入正式设置"; }
    public boolean hasSavedHud() { return savedHud != null; }
    public void reloadHud() { if (savedHud != null) { hud.restore(savedHud); notice = "已恢复本次预览保存值"; } }

    public record HudSnapshot(int navigationPercent, int reminderPercent, double navigationX, double navigationY,
                              double reminderX, double reminderY, boolean autoFit, boolean expanded) {}
    public static final class HudSettings {
        public boolean reminder, autoFit = true, expanded = true;
        public int navigationPercent = 150, reminderPercent = 150;
        public double navigationX = .05, navigationY = .1, reminderX = .7, reminderY = .1;
        public int percent() { return reminder ? reminderPercent : navigationPercent; }
        public void size(int value) {
            value = Math.clamp(value, 100, 200);
            if (reminder) reminderPercent = value; else navigationPercent = value;
        }
        public void position(double x, double y) {
            x = HudPlacement.position(x, 0); y = HudPlacement.position(y, 0);
            if (reminder) { reminderX = x; reminderY = y; } else { navigationX = x; navigationY = y; }
        }
        public void reset() { size(150); position(reminder ? .7 : .05, .1); }
        public HudSnapshot snapshot() { return new HudSnapshot(navigationPercent, reminderPercent, navigationX, navigationY, reminderX, reminderY, autoFit, expanded); }
        public void restore(HudSnapshot s) {
            navigationPercent = s.navigationPercent; reminderPercent = s.reminderPercent;
            navigationX = s.navigationX; navigationY = s.navigationY; reminderX = s.reminderX; reminderY = s.reminderY;
            autoFit = s.autoFit; expanded = s.expanded;
        }
    }
}
