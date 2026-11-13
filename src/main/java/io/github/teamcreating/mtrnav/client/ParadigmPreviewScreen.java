package io.github.teamcreating.mtrnav.client;

import com.google.gson.GsonBuilder;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.core.UiComparison.*;
import io.github.teamcreating.mtrnav.core.UiLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.UiComparisonSkin.*;

/** Nine comparable native screens plus the hybrid; all actions stay inside this review session. */
final class ParadigmPreviewScreen extends Screen {
    private final UiComparison state = new UiComparison();
    private UiTypography.Face face = "smooth".equals(System.getProperty("mtrnav.uiPreviewFont")) ? UiTypography.Face.SMOOTH : UiTypography.Face.PIXEL;
    private UiComparisonLayout layout;
    private UiComparisonSkin skin;
    private final Map<String, EditBox> fields = new LinkedHashMap<>();
    private final Map<String, InputPosition> fieldPositions = new HashMap<>();
    private final Map<String, AbstractWidget> controls = new LinkedHashMap<>();
    private final List<AbstractWidget> hudControls = new ArrayList<>();
    private Rect listBounds, detailBounds, stationBounds;
    private HudArrangement hudArrangement;
    private double restoreScale = Double.NaN, grabX, grabY, scrollbarGrab;
    private boolean initialLayout = true, indexOpen, stopsExpanded, dragging;
    private String draggingScrollbar = "";
    private int selectedDeparture, phase, detailHeight;
    private long lastAudit;
    private String lastAuditJson = "";
    private String lastInput = "";
    private String pressedControl = "";
    private final Set<String> detailWarnings = new HashSet<>();
    private final List<String> renderedDetailText = new ArrayList<>();
    private record InputPosition(int cursor, int highlight, String value) {}
    private record DetailLine(FormattedCharSequence text, int color, int height) {}
    private enum Icon { SWAP, SORT, STATION, HUD, COMPONENTS }

    ParadigmPreviewScreen() {
        super(Component.literal("站间物语 · UI 范式对照"));
        try { state.variant = Variant.valueOf(System.getProperty("mtrnav.uiPreviewVariant", "java").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { state.variant = Variant.JAVA; }
        try { state.scenario(Scenario.valueOf(System.getProperty("mtrnav.uiPreviewScene", "normal").toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ignored) { state.scenario(Scenario.NORMAL); }
    }

    @Override protected void init() {
        var window = minecraft.getWindow();
        if (Double.isNaN(restoreScale)) restoreScale = window.getGuiScale();
        int normal = window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
        window.setGuiScale(Math.min(2, normal));
        width = window.getGuiScaledWidth(); height = window.getGuiScaledHeight();
        if (initialLayout) {
            state.queryCollapsed = UiComparisonLayout.at(width, height, state.variant).compact();
            initialLayout = false;
        }
        build();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        super.removed();
        if (!Double.isNaN(restoreScale)) {
            double previewScale = minecraft.getWindow().getGuiScale();
            minecraft.getWindow().setGuiScale(restoreScale);
            try {
                Files.writeString(minecraft.gameDirectory.toPath().resolve("mtrnav-paradigm-exit.json"),
                        new GsonBuilder().create().toJson(Map.of("previewScale", previewScale, "enteredScale", restoreScale,
                                "restoredScale", minecraft.getWindow().getGuiScale())));
            } catch (java.io.IOException e) { com.mojang.logging.LogUtils.getLogger().warn("Cannot write UI exit audit", e); }
        }
    }
    @Override public void onClose() { minecraft.setScreen(null); }
    @Override public void resize(Minecraft mc, int w, int h) { super.resize(mc, w, h); }

    private void rememberInputs() {
        fields.forEach((id, box) -> {
            int cursor = box.getCursorPosition(), length = box.getHighlighted().length();
            int end = Math.min(box.getValue().length(), cursor + length);
            int highlight = box.getValue().substring(cursor, end).equals(box.getHighlighted()) ? end : Math.max(0, cursor - length);
            fieldPositions.put(id, new InputPosition(cursor, highlight, box.getValue()));
        });
    }
    private void build() {
        String focus = fields.entrySet().stream().filter(e -> e.getValue().isFocused()).map(Map.Entry::getKey).findFirst().orElse("");
        rememberInputs(); clearWidgets(); fields.clear(); controls.clear(); hudControls.clear();
        dragging = false; draggingScrollbar = "";
        font = UiTypography.font(face); skin = new UiComparisonSkin(state.variant);
        layout = UiComparisonLayout.at(width, height, state.variant);
        listBounds = detailBounds = stationBounds = null;
        var header = layout.header();
        button("variant", at(header.right() - 218, header.y(), 94, 20), UiComparisonSkin.label(state.variant), this::nextVariant)
                .setTooltip(Tooltip.create(Component.literal("F5：AE2 / BE / Java / 融合；保留输入、选择与滚动位置")));
        button("font", at(header.right() - 120, header.y(), 96, 20), face.label, this::nextFont)
                .setTooltip(Tooltip.create(Component.literal("F6：只比较中文字体；英文数字保持 Minecraft 字体")));
        button("close", at(header.right() - 18, header.y() + 1, 18, 18), "×", this::onClose)
                .setTooltip(Tooltip.create(Component.literal("关闭预览并恢复 GUI 缩放")));
        buildNavigation();
        switch (state.page) {
            case QUERY -> buildQuery();
            case STATIONS -> buildStations();
            case HUD -> buildHud();
            case COMPONENTS -> buildComponents();
        }
        if (fields.containsKey(focus)) setInitialFocus(fields.get(focus));
        else if (state.page == Page.STATIONS) setInitialFocus(fields.get("stationSearch"));
        lastAudit = 0;
    }
    private static Rect at(int x, int y, int w, int h) { return new Rect(x, y, Math.max(0, w), Math.max(0, h)); }
    private ReviewButton button(String id, Rect r, String text, Runnable action) { return button(id, r, text, false, false, action); }
    private ReviewButton button(String id, Rect r, String text, boolean primary, boolean selected, Runnable action) {
        var b = new ReviewButton(id, r, text, primary, selected, null, action);
        controls.put(id, addRenderableWidget(b)); return b;
    }
    private void icon(String id, Rect r, String text, Icon icon, Runnable action) {
        var b = new ReviewButton(id, r, text, false, false, icon, action);
        b.setTooltip(Tooltip.create(Component.literal(text))); controls.put(id, addRenderableWidget(b));
    }
    private void field(String id, Rect r, String label, String value, java.util.function.Consumer<String> changed) {
        var box = state.variant == Variant.AE2 || state.variant == Variant.BEDROCK ? new InsetField(r, label)
                : new EditBox(new PaperFont(font), r.x(), r.y(), r.width(), r.height(), Component.literal(label));
        box.setMaxLength(256); box.setValue(value); box.setTextColor(0xFFF4F4F4);
        box.setFormatter((text, position) -> FormattedCharSequence.forward(text, UiTypography.style(face)));
        box.setHint(Component.literal(label).withStyle(UiTypography.style(face)));
        var previous = fieldPositions.get(id);
        if (previous != null && previous.value.equals(value)) {
            box.setCursorPosition(previous.cursor); box.setHighlightPos(previous.highlight);
        } else { box.setCursorPosition(0); box.setHighlightPos(0); }
        box.setTooltip(Tooltip.create(UiTypography.component(face, label + "：" + value)));
        box.setResponder(text -> { box.setTooltip(Tooltip.create(UiTypography.component(face, label + "：" + text))); changed.accept(text); });
        fields.put(id, box); controls.put(id, addRenderableWidget(box));
    }
    private void nextVariant() { state.variant(state.variant.next()); build(); }
    private void nextFont() { face = face.next(); build(); }
    private void page(Page page) { state.page = page; indexOpen = false; build(); }
    private void view(View view) { state.page = Page.QUERY; state.view = view; build(); }

    private void buildNavigation() {
        var n = layout.navigation(); int x = n.x();
        String[] names = {"路线", "到发", "行程", "选站", "HUD", "组件"};
        for (int i = 0; i < names.length; i++) {
            if (state.variant == Variant.BEDROCK && !layout.compact() && i >= 3) continue;
            if (state.variant == Variant.BEDROCK && !layout.compact() && i == 0) x = layout.content().x();
            final int index = i;
            boolean selected = i < 3 ? state.page == Page.QUERY && state.view.ordinal() == i : state.page.ordinal() == i - 2;
            button("nav" + i, at(x, n.y(), 48, 20), names[i], false, selected, () -> {
                if (index < 3) view(View.values()[index]);
                else if (index == 3) { state.openStations(false); build(); }
                else page(index == 4 ? Page.HUD : Page.COMPONENTS);
            });
            x += 52;
        }
        var rail = layout.rail();
        if (rail.width() == 0) return;
        if (state.variant == Variant.BEDROCK) {
            String[] labels = {"行程规划", "车站目录", "显示与位置", "控件状态"};
            for (int i = 0; i < 4; i++) {
                final int index = i;
                button("rail" + i, at(rail.x() + 4, rail.y() + 22 + i * 28, rail.width() - 8, 20), labels[i], false,
                        state.page.ordinal() == i, () -> page(Page.values()[index]));
            }
        } else {
            Icon[] icons = {Icon.SORT, Icon.STATION, Icon.HUD, Icon.COMPONENTS};
            String[] titles = {"切换最快 / 少换乘排序", "打开车站目录", "编辑 HUD", "查看控件状态"};
            for (int i = 0; i < icons.length; i++) {
                final int index = i;
                icon("rail" + i, at(rail.x() + 2, rail.y() + i * 24, 18, 18), titles[i], icons[i], () -> {
                    if (index == 0) { state.fewestTransfers = !state.fewestTransfers; build(); }
                    else page(Page.values()[index]);
                });
            }
        }
    }
    private void buildQuery() {
        var q = layout.query(state.queryCollapsed);
        if (state.queryCollapsed) {
            button("expandQuery", at(q.right() - 160, q.y(), 48, 20), "条件", () -> { state.queryCollapsed = false; build(); });
            button("stations", at(q.right() - 108, q.y(), 48, 20), "选站", () -> { state.openStations(false); build(); });
            button("query", at(q.right() - 56, q.y(), 56, 20), "查询", true, false, () -> { state.query(); build(); });
        } else {
            int w = Math.max(20, (q.width() - 94) / 2);
            field("origin", at(q.x(), q.y() + 12, w, 20), "起点", state.origin, value -> { state.origin = value; updatePrimary(); });
            icon("swap", at(q.x() + w + 4, q.y() + 13, 18, 18), "交换起点与终点", Icon.SWAP, () -> {
                String old = state.origin; state.origin = state.destination; state.destination = old; build();
            });
            field("destination", at(q.x() + w + 26, q.y() + 12, w, 20), "终点", state.destination, value -> { state.destination = value; updatePrimary(); });
            button("query", at(q.right() - 64, q.y() + 12, 64, 20), "查询", true, false, () -> { state.query(); build(); });
            int x = q.x();
            button("pickOrigin", at(x, q.y() + 36, 56, 20), "选起点", () -> { state.openStations(true); build(); }); x += 60;
            button("pickDestination", at(x, q.y() + 36, 56, 20), "选终点", () -> { state.openStations(false); build(); }); x += 60;
            button("nearby", at(x, q.y() + 36, 48, 20), "附近", () -> { state.openStations(true); state.nearby = true; state.distanceOrder = true; state.filterChanged(); build(); }); x += 52;
            button("sort", at(x, q.y() + 36, 72, 20), state.fewestTransfers ? "少换乘" : "最快到达", () -> {
                setJourneyOrder(!state.fewestTransfers);
            });
            button("collapseQuery", at(q.right() - 48, q.y() + 36, 48, 20), "收起", () -> { state.queryCollapsed = true; build(); });
        }
        listBounds = layout.list(state.queryCollapsed); detailBounds = layout.details(state.queryCollapsed);
        if (!layout.compact() || !state.details) buildJourneyRows();
        if (!layout.compact() || state.details) {
            String label = state.view == View.ROUTES ? stopsExpanded ? "收起经停" : "展开经停" : state.view == View.ACTIVE ? "推进测试步骤" : "查看提醒 HUD";
            var b = button("detailAction", at(detailBounds.x() + 8, detailBounds.bottom() - 22, Math.min(112, detailBounds.width() - 16), 20), label, () -> {
                if (state.view == View.ROUTES) stopsExpanded = !stopsExpanded;
                else if (state.view == View.ACTIVE) phase = Math.min(4, phase + 1);
                else page(Page.HUD);
                build();
            });
            b.active = itemCount() > 0;
        }
        var footer = layout.footer();
        if (layout.compact()) button("details", at(footer.x(), footer.y(), 76, 20), state.details ? "返回列表" : "查看详情",
                () -> { state.details = !state.details; build(); });
        else button("scenario", at(footer.x(), footer.y(), 96, 20), scenarioLabel(), () -> { state.scenario(state.scenario.next()); build(); });
        String action = state.view == View.ROUTES ? "开始导航" : state.view == View.DEPARTURES ? "提醒此班次" : state.activeTrip ? "结束测试导航" : "返回路线";
        var primary = button("primary", at(footer.right() - 104, footer.y(), 104, 20), action, true, false, () -> {
            if (state.view == View.ROUTES) { state.start(); phase = 0; }
            else if (state.view == View.DEPARTURES) state.notice = "已设置测试提醒 · 提前 3 分钟";
            else { state.activeTrip = false; state.view = View.ROUTES; }
            build();
        });
        primary.active = state.view != View.ROUTES || state.canStart();
        if (!primary.active) primary.setTooltip(Tooltip.create(Component.literal(state.scenario == Scenario.REFERENCE ? "当前只有线路参考，没有可乘班次" : "请选择起终点并取得可用方案")));
    }
    private void updatePrimary() {
        var b = controls.get("primary"); if (b != null && state.view == View.ROUTES) b.active = state.canStart();
    }
    private void setJourneyOrder(boolean fewest) {
        String chosen = state.journeys().isEmpty() ? "" : state.journeys().get(Math.min(state.selectedJourney, state.journeys().size() - 1)).id();
        state.fewestTransfers = fewest;
        for (int i = 0; i < state.journeys().size(); i++) if (state.journeys().get(i).id().equals(chosen)) state.selectedJourney = i;
        build();
    }
    private int itemCount() { return state.view == View.ROUTES ? state.journeys().size() : state.view == View.DEPARTURES ? 8 : state.activeTrip ? 5 : 0; }
    private void buildJourneyRows() {
        int visible = layout.visibleRows(listBounds), count = itemCount();
        int scroll = Math.clamp(state.listScroll.get(state.view), 0, Math.max(0, count - visible));
        // Keep the requested offset in state; a narrower or wider comparison must not overwrite it.
        for (int row = 0; row < Math.min(visible, count - scroll); row++) {
            int i = row + scroll; String first, second; boolean selected;
            if (state.view == View.ROUTES) {
                var journey = state.journeys().get(i);
                first = journey.minutes() + " 分钟 · " + (journey.transfers() == 0 ? "直达" : "换乘 " + journey.transfers() + " 次");
                second = journey.line(); selected = state.selectedJourney == i;
                if (state.scenario == Scenario.REFERENCE) { first = "线路参考 · 无可乘班次"; second = "路径可达 · 班次未确认"; }
            } else if (state.view == View.DEPARTURES) {
                first = (i % 2 == 0 ? "1 号线 Local" : "Airport Express") + " · " + (i * 4 + 3) + " 分钟";
                second = "08:" + String.format(Locale.ROOT, "%02d", 12 + i * 4) + " · " + (i % 3 + 1) + " 站台"; selected = selectedDeparture == i;
            } else {
                first = new String[]{"前往上车站", "乘坐 1 号线", "中央车站换乘", "乘坐 North 线", "到达目的地"}[i];
                second = i < phase ? "已完成" : i == phase ? "当前步骤 · 点击下一步" : "等待前一步完成"; selected = phase == i;
            }
            var b = new ReviewRow(layout.row(listBounds, row), first, second, selected, () -> {
                if (state.view == View.ROUTES) state.selectJourney(i);
                else if (state.view == View.DEPARTURES) { selectedDeparture = i; state.detailScroll = 0; }
                else phase = Math.min(4, i + 1);
                build();
            });
            b.setTooltip(Tooltip.create(Component.literal(first + "\n" + second)));
            controls.put("row" + i, addRenderableWidget(b));
        }
    }
    private void buildStations() {
        var r = layout.content();
        field("stationSearch", at(r.x(), r.y() + 12, r.width() - 48, 20), "搜索中文或英文站名", state.stationSearch, value -> {
            state.stationSearch = value; state.filterChanged(); rebuildStationResults();
        });
        button("clearSearch", at(r.right() - 44, r.y() + 12, 44, 20), "清空", () -> { state.stationSearch = ""; state.filterChanged(); build(); });
        button("stationSort", at(r.x(), r.y() + 36, 80, 20), state.distanceOrder ? "距离排序" : "字母排序", () -> {
            state.distanceOrder = !state.distanceOrder; state.filterChanged(); build();
        });
        button("nearbyStations", at(r.x() + 84, r.y() + 36, 80, 20), state.nearby ? "附近：开" : "附近：关", false, state.nearby,
                () -> { state.nearby = !state.nearby; state.filterChanged(); build(); })
                .setTooltip(Tooltip.create(Component.literal("以固定测试位置筛选 1500 米内车站，不读取玩家位置")));
        button("index", at(r.x() + 168, r.y() + 36, 88, 20), "索引：" + (state.index.isEmpty() ? "全部" : state.index), () -> { indexOpen = !indexOpen; build(); });
        stationBounds = at(r.x(), r.y() + 64, r.width(), r.height() - 64);
        if (indexOpen) {
            var groups = new ArrayList<String>(); groups.add("");
            UiComparison.STATIONS.stream().map(Station::group).distinct().sorted().forEach(groups::add);
            int columns = Math.max(1, stationBounds.width() / 52);
            for (int i = 0; i < groups.size(); i++) {
                String group = groups.get(i); int y = stationBounds.y() + i / columns * 24;
                if (y + 20 > stationBounds.bottom()) break;
                button("index" + i, at(stationBounds.x() + i % columns * 52, y, 48, 20), group.isEmpty() ? "全部" : group,
                        false, group.equals(state.index), () -> { state.index = group; state.filterChanged(); indexOpen = false; build(); });
            }
        } else buildStationRows();
        var footer = layout.footer();
        button("back", at(footer.x(), footer.y(), 56, 20), "返回", () -> page(Page.QUERY));
        var apply = button("applyStation", at(footer.right() - 112, footer.y(), 112, 20), state.pickingOrigin ? "设为起点" : "设为终点", true, false,
                () -> { if (state.pickStation()) build(); });
        apply.active = state.stations().stream().anyMatch(s -> s.id().equals(state.selectedStation));
    }
    private void buildStationRows() {
        var stations = state.stations(); int visible = layout.visibleRows(stationBounds);
        int scroll = Math.clamp(state.stationScroll, 0, Math.max(0, stations.size() - visible));
        for (int row = 0; row < Math.min(visible, stations.size() - scroll); row++) {
            var station = stations.get(row + scroll);
            var b = new ReviewRow(layout.row(stationBounds, row), station.chinese(), station.english() + " · " + station.distance() + " m",
                    station.id().equals(state.selectedStation), () -> { state.selectedStation = station.id(); build(); });
            b.setTooltip(Tooltip.create(Component.literal(station.name() + "\n测试距离 " + station.distance() + " 米")));
            controls.put("station-" + station.id(), addRenderableWidget(b));
        }
    }
    private void rebuildStationResults() {
        // Updating results outside the EditBox responder preserves native cursor/selection while typing.
        resultsDirty = true;
    }
    private boolean resultsDirty;

    private void buildHud() {
        var r = layout.hudSettings(); var h = state.hud;
        String[] rows = {"编辑：" + (h.reminder ? "班次提醒" : "导航框"), "大小：" + h.percent() + "%（切换）",
                "自动适配：" + (h.autoFit ? "开" : "关"), "放到左上角", "放到右上角", "放到左下角", "放到右下角",
                "提醒展开：" + (h.expanded ? "开" : "关"), "恢复所选默认值", "恢复本次保存值"};
        int visible = Math.max(1, (r.height() - 16) / 24);
        state.settingsScroll = Math.clamp(state.settingsScroll, 0, Math.max(0, rows.length - visible));
        for (int row = 0; row < Math.min(visible, rows.length - state.settingsScroll); row++) {
            int i = row + state.settingsScroll;
            if (i == 1 && (state.variant == Variant.BEDROCK || state.variant == Variant.HYBRID)) {
                var slider = new ReviewSlider(hudSettingRect(r, i, row));
                controls.put("hudSetting1", addRenderableWidget(slider)); hudControls.add(slider); continue;
            }
            var b = button("hudSetting" + i, hudSettingRect(r, i, row), rows[i], false,
                    i == 0 && h.reminder || i == 2 && h.autoFit || i == 7 && h.expanded, () -> {
                switch (i) {
                    case 0 -> h.reminder = !h.reminder;
                    case 1 -> h.size(h.percent() == 200 ? 100 : h.percent() + 50);
                    case 2 -> h.autoFit = !h.autoFit;
                    case 3 -> h.position(0, 0);
                    case 4 -> h.position(1, 0);
                    case 5 -> h.position(0, 1);
                    case 6 -> h.position(1, 1);
                    case 7 -> h.expanded = !h.expanded;
                    case 8 -> h.reset();
                    case 9 -> state.reloadHud();
                    default -> { }
                }
                build();
            });
            if (i == 9) b.active = state.hasSavedHud();
            hudControls.add(b);
        }
        var footer = layout.footer();
        button("back", at(footer.x(), footer.y(), 56, 20), "返回", () -> page(Page.QUERY));
        button("saveHud", at(footer.right() - 104, footer.y(), 104, 20), "保存预览设置", true, false, () -> { state.saveHud(); build(); });
        updateHudArrangement();
    }
    private boolean groupedHud() {
        return !layout.compact() && layout.hudSettings().height() >= 248
                && (state.variant == Variant.BEDROCK || state.variant == Variant.HYBRID);
    }
    private Rect hudSettingRect(Rect r, int index, int row) {
        if (!groupedHud()) return at(r.x() + 4, r.y() + 12 + row * 24, r.width() - 12, 20);
        if (index < 3) return at(r.x() + 4, r.y() + 20 + index * 24, r.width() - 12, 20);
        if (index < 7) {
            int w = (r.width() - 16) / 2;
            return at(r.x() + 4 + (index - 3) % 2 * (w + 4), r.y() + 112 + (index - 3) / 2 * 24, w, 20);
        }
        return at(r.x() + 4, r.y() + 176 + (index - 7) * 24, r.width() - 12, 20);
    }
    private void buildComponents() {
        var r = layout.content();
        String[] names = {"普通按钮", "主要操作", "选中状态", "禁用按钮"};
        for (int i = 0; i < names.length; i++) {
            var b = button("component" + i, at(r.x() + 8, r.y() + 24 + i * 28, Math.min(152, r.width() - 16), 20), names[i], i == 1, i == 2,
                    () -> state.notice = "原生按钮支持 Tab 焦点与 Enter 激活");
            b.active = i != 3;
        }
        if (r.height() >= 188) field("sampleInput", at(r.x() + 8, r.y() + 148, Math.min(220, r.width() - 16), 20), "输入与选区", "中文 ABC 123 输入测试", value -> {});
        button("back", at(layout.footer().x(), layout.footer().y(), 56, 20), "返回", () -> page(Page.QUERY));
    }

    private String scenarioLabel() { return switch (state.scenario) {
        case NORMAL -> "五个方案"; case LONG_NAMES -> "长站名"; case EMPTY -> "无结果";
        case LOADING -> "加载中"; case ERROR -> "查询失败"; case REFERENCE -> "无可乘班次"; case MANY -> "多方案滚动";
    }; }

    @Override public void tick() {
        super.tick();
        if (resultsDirty) { resultsDirty = false; build(); }
    }
    private void label(GuiGraphics g, String text, int x, int y, int width, int color) {
        UiTypography.text(g, face, text, x, y, width, color);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        g.fill(0, 0, width, height, 0x9B111111);
        skin.panel(g, layout.panel());
        var h = layout.header();
        label(g, "站间物语 · 测试预览", h.x() + 4, h.y() + 5, h.width() - 228, skin.p.headerText());
        if (layout.rail().width() > 22) {
            skin.recess(g, layout.rail());
            label(g, "工作区", layout.rail().x() + 8, layout.rail().y() + 5, layout.rail().width() - 16, skin.p.muted());
        }
        switch (state.page) {
            case QUERY -> renderQuery(g, mx, my);
            case STATIONS -> renderStations(g);
            case HUD -> renderHud(g);
            case COMPONENTS -> renderComponents(g);
        }
        var f = layout.footer();
        int start = f.x() + (state.page == Page.QUERY ? layout.compact() ? 82 : 102 : 62);
        String notice = state.notice.isEmpty() ? "测试数据 · F5 风格 / F6 字体 / F7 场景 / F8 页面" : state.notice;
        label(g, notice, start, f.y() + 5, Math.max(0, f.right() - 112 - start), skin.p.muted());
        if (layout.panel().bottom() + 16 < height)
            label(g, UiComparisonSkin.label(state.variant) + " · " + face.label + " · " + scenarioLabel() + " · 九套对照与融合方案",
                    layout.panel().x() + 8, layout.panel().bottom() + 6, layout.panel().width() - 16, 0xFFE5E5E5);
        // Native widgets keep focus, narration, tooltips and text editing in the Minecraft event pipeline.
        // Modern UI hooks Screen.render to blur the background. Calling it here would blur our
        // already-drawn text; rendering native children directly keeps every foreground layer sharp.
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
            renderable.render(g, mx, my, delta);
        writeAudit();
    }
    private void renderQuery(GuiGraphics g, int mx, int my) {
        var q = layout.query(state.queryCollapsed);
        if (state.queryCollapsed) label(g, state.origin + " → " + state.destination, q.x(), q.y() + 5, q.width() - 168, skin.p.text());
        else {
            var from = fields.get("origin"); var to = fields.get("destination");
            label(g, "起点", from.getX(), q.y(), from.getWidth(), skin.p.muted());
            label(g, "终点", to.getX(), q.y(), to.getWidth(), skin.p.muted());
        }
        if (!layout.compact() || !state.details) {
            skin.recess(g, listBounds);
            if (itemCount() == 0) {
                String message = state.view == View.ACTIVE ? "尚未开始导航" : emptyTitle();
                label(g, message, listBounds.x() + 8, listBounds.y() + 16, listBounds.width() - 16, skin.p.text());
                label(g, "使用上方查询或 F7 切换场景", listBounds.x() + 8, listBounds.y() + 34, listBounds.width() - 16, skin.p.muted());
            }
            drawScrollbar(g, listBounds, itemCount(), layout.visibleRows(listBounds), state.listScroll.get(state.view));
        }
        if (!layout.compact() || state.details) renderDetails(g);
    }
    private String emptyTitle() { return switch (state.scenario) {
        case LOADING -> "正在查询路线…"; case ERROR -> "查询失败，请重试";
        default -> "没有找到可用方案";
    }; }
    private Rect readingBounds() { return at(detailBounds.x(), detailBounds.y(), detailBounds.width(), detailBounds.height() - 26); }
    private void detail(List<DetailLine> result, String text, int color, int gap) {
        renderedDetailText.add(text);
        var wrapped = font.split(UiTypography.component(face, text), Math.max(12, detailBounds.width() - 34));
        for (int i = 0; i < wrapped.size(); i++) result.add(new DetailLine(wrapped.get(i), color, 12 + (i == wrapped.size() - 1 ? gap : 0)));
    }
    private void renderDetails(GuiGraphics g) {
        renderedDetailText.clear();
        skin.recess(g, detailBounds);
        var r = readingBounds(); var lines = new ArrayList<DetailLine>();
        if (state.view == View.ROUTES && state.journeys().isEmpty()) {
            detail(lines, emptyTitle(), skin.p.text(), 12);
            detail(lines, state.scenario == Scenario.LOADING ? "测试加载状态。查询完成后会在这里显示方案。"
                    : state.scenario == Scenario.ERROR ? "测试连接失败。点击“查询”可重试。"
                    : "请调整起终点或查询条件。", skin.p.muted(), 8);
        } else if (state.view == View.ACTIVE && !state.activeTrip) {
            detail(lines, "当前没有进行中的行程", skin.p.text(), 12);
            detail(lines, "从路线页选择一个方案，再点击开始导航。", skin.p.muted(), 8);
        } else {
            String title;
            var journey = state.journeys().isEmpty() ? UiComparison.JOURNEYS.getFirst()
                    : state.journeys().get(Math.min(state.selectedJourney, state.journeys().size() - 1));
            if (state.view == View.ROUTES) {
                title = state.scenario == Scenario.REFERENCE ? "线路参考 · 无可乘班次" : journey.minutes() + " 分钟 · " + journey.line();
            } else title = state.view == View.DEPARTURES ? "班次详情 · 测试时刻" : "进行中的测试行程 · 步骤 " + (phase + 1) + "/5";
            detail(lines, title, skin.p.text(), 10);
            if (state.scenario == Scenario.REFERENCE && state.view == View.ROUTES)
                detail(lines, "路径可以换乘，但当前没有可乘班次。这里只展示线路参考，暂不能开始导航。", skin.p.accent(), 12);
            boolean reference = state.scenario == Scenario.REFERENCE && state.view == View.ROUTES;
            int departure = 8 * 60 + 12 + (state.view == View.DEPARTURES ? selectedDeparture * 4 : 0);
            detail(lines, state.origin, skin.p.text(), 2);
            detail(lines, reference ? "上车 · 1 站台 · 班次未确认"
                    : String.format(Locale.ROOT, "%02d:%02d 上车 · 1 站台", departure / 60, departure % 60), skin.p.muted(), 10);
            detail(lines, state.view == View.DEPARTURES ? selectedDeparture % 2 == 0 ? "1 号线 Local" : "Airport Express"
                    : journey.line().split(" → ")[0], skin.p.accent(), 4);
            if (stopsExpanded) for (String stop : List.of("花园路 Garden Road", "东港 East Harbour", "湖滨 Lakeside", "公园 Park", "西门 West Gate", "中央车站 Central"))
                detail(lines, stop, skin.p.muted(), 2);
            else detail(lines, "途经 6 站 · 可展开经停站", skin.p.muted(), 10);
            if (state.view == View.DEPARTURES) {
                detail(lines, "本班次 " + (selectedDeparture * 4 + 3) + " 分钟后出发", skin.p.text(), 8);
                detail(lines, "可在页脚设置提前 3 分钟的测试提醒。", skin.p.muted(), 8);
            } else if (journey.transfers() > 0) {
                detail(lines, journey.id().equals("river") ? "湖滨 Lakeside" : "中央车站 Central", skin.p.text(), 2);
                detail(lines, "08:22 到达 · 2 站台", skin.p.muted(), 6);
                detail(lines, journey.via(), skin.p.accent(), 4);
                if (journey.id().equals("walk")) detail(lines, "步行 260 米到 4 站台，08:30 继续乘车。", skin.p.muted(), 10);
                if (journey.transfers() > 1) {
                    detail(lines, "东港 East Harbour", skin.p.text(), 2);
                    detail(lines, "08:36 换乘 North 线 · 4 站台", skin.p.muted(), 10);
                }
            }
            detail(lines, state.destination, skin.p.text(), 2);
            int arrival = departure + journey.minutes();
            detail(lines, reference ? "下车 · 4 站台 · 班次未确认"
                    : String.format(Locale.ROOT, "%02d:%02d 到达 · 4 站台", arrival / 60, arrival % 60), skin.p.muted(), 12);
            detail(lines, "固定测试数据 · 非实时班次", skin.p.muted(), 4);
        }
        detailHeight = 16 + lines.stream().mapToInt(DetailLine::height).sum();
        int offset = Math.clamp(state.detailScroll, 0, Math.max(0, detailHeight - r.height()));
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        int y = r.y() + 10 - offset;
        for (var line : lines) {
            if (y + 12 > r.y() && y < r.bottom()) g.drawString(font, line.text, r.x() + 16, y, line.color, false);
            y += line.height;
        }
        g.disableScissor();
        drawScrollbar(g, r, detailHeight, r.height(), state.detailScroll);
    }
    private void renderStations(GuiGraphics g) {
        var r = layout.content();
        label(g, "选择" + (state.pickingOrigin ? "起点" : "终点") + " · " + state.stations().size() + " 个测试车站", r.x(), r.y(), r.width(), skin.p.muted());
        skin.recess(g, stationBounds);
        if (!indexOpen && state.stations().isEmpty()) {
            label(g, "没有匹配车站", stationBounds.x() + 10, stationBounds.y() + 14, stationBounds.width() - 20, skin.p.text());
            label(g, "可清空关键词、索引或附近筛选", stationBounds.x() + 10, stationBounds.y() + 30, stationBounds.width() - 20, skin.p.muted());
        }
        if (!indexOpen) drawScrollbar(g, stationBounds, state.stations().size(), layout.visibleRows(stationBounds), state.stationScroll);
    }
    private void updateHudArrangement() {
        var r = hudCanvas(); var h = state.hud;
        var nav = previewPlacement(r, h.navigationPercent, h.navigationX, h.navigationY, false);
        var reminder = previewPlacement(r, h.reminderPercent, h.reminderX, h.reminderY, true);
        hudArrangement = HudArrangement.arrange(nav, reminder, r.width(), r.height(), 6);
    }
    private HudPlacement previewPlacement(Rect region, int percent, double x, double y, boolean reminder) {
        double scale = percent / 150.0;
        int baseWidth = 154, baseHeight = reminder ? state.hud.expanded ? 50 : 26 : 62;
        // Auto-fit may reserve room for both boxes, but even manual sizes must fit the canvas.
        scale = Math.min(scale, Math.min(Math.max(1, region.width() - 12) / (double)baseWidth,
                Math.max(1, region.height() - 12) / (double)baseHeight));
        if (state.hud.autoFit) scale = Math.min(scale, Math.max(1, region.width() - 18) / (2.0 * baseWidth));
        double w = baseWidth * scale, h = baseHeight * scale, margin = Math.min(6, Math.min(region.width(), region.height()) * .02);
        double ax = Math.max(0, region.width() - w - 2 * margin), ay = Math.max(0, region.height() - h - 2 * margin);
        return new HudPlacement(margin + ax * x, margin + ay * y, w, h, scale, margin, ax, ay);
    }
    private Rect hudRect(boolean reminder) {
        var region = hudCanvas(); var box = reminder ? hudArrangement.reminder() : hudArrangement.navigation();
        return at(region.x() + (int)Math.round(box.x()), region.y() + (int)Math.round(box.y()),
                (int)Math.ceil(box.width()), (int)Math.ceil(box.height()));
    }
    private Rect hudCanvas() {
        var r = layout.hudPreview().inset(4);
        return at(r.x(), r.y(), r.width(), r.height() - (layout.compact() ? 0 : 30));
    }
    private void renderHud(GuiGraphics g) {
        var settings = layout.hudSettings(); var preview = layout.hudPreview();
        skin.recess(g, settings); skin.recess(g, preview);
        label(g, groupedHud() ? "显示" : "显示与位置", settings.x() + 4, settings.y() + 1, settings.width() - 12, skin.p.muted());
        if (groupedHud()) {
            label(g, "位置", settings.x() + 4, settings.y() + 96, settings.width() - 12, skin.p.muted());
            label(g, "行为与恢复", settings.x() + 4, settings.y() + 160, settings.width() - 12, skin.p.muted());
        }
        updateHudArrangement();
        g.enableScissor(preview.x(), preview.y(), preview.right(), preview.bottom());
        for (boolean reminder : new boolean[]{false, true}) {
            var placement = reminder ? hudArrangement.reminder() : hudArrangement.navigation(); var box = hudRect(reminder);
            fill(g, box, skin.p.surface()); edge(g, box, state.hud.reminder == reminder ? skin.p.accent() : skin.p.edge());
            g.pose().pushPose(); g.pose().translate(box.x(), box.y(), 0); g.pose().scale((float)placement.scale(), (float)placement.scale(), 1);
            label(g, reminder ? "班次提醒 · 测试" : "下一步 · 乘坐 Local", 6, 5, 142, skin.p.text());
            if (!reminder || state.hud.expanded) {
                label(g, "中央车站 · 1 站台", 6, 19, 142, skin.p.muted());
                label(g, "08:12 出发 · 还有 03:20", 6, 33, 142, skin.p.text());
            }
            if (!reminder) {
                g.fill(6, 51, 148, 54, skin.p.inset()); g.fill(6, 51, 64, 54, skin.p.accent());
            }
            g.pose().popPose();
        }
        g.disableScissor();
        for (var b : hudControls) b.visible = !dragging;
        drawScrollbar(g, settings, 10, Math.max(1, (settings.height() - 16) / 24), state.settingsScroll);
        if (!layout.compact()) {
            label(g, "拖动所选框 · Shift + 方向键微调", preview.x() + 6, preview.bottom() - 26, preview.width() - 12, skin.p.muted());
            label(g, "滚轮缩放 · 自动避让 · 本次预览内保存", preview.x() + 6, preview.bottom() - 13, preview.width() - 12, skin.p.muted());
        }
    }
    private void renderComponents(GuiGraphics g) {
        var r = layout.content(); skin.recess(g, r);
        label(g, "控件状态 · 可用 Tab / Enter 检查焦点", r.x() + 8, r.y() + 6, r.width() - 16, skin.p.text());
        if (!layout.compact()) {
            int x = r.x() + 244, w = r.width() - 252;
            label(g, "统一尺度", x, r.y() + 28, w, skin.p.text());
            label(g, "2 GUI 单位网格 · 4 / 8 间距", x, r.y() + 48, w, skin.p.muted());
            label(g, "图标 18 · 标准控件 20", x, r.y() + 66, w, skin.p.muted());
            label(g, "中文 A / B · MC 英文数字", x, r.y() + 84, w, skin.p.muted());
            label(g, "所有状态均使用原生交互控件", x, r.y() + 112, w, skin.p.muted());
        }
    }
    private void drawScrollbar(GuiGraphics g, Rect r, int total, int visible, int offset) {
        if (total <= visible || visible <= 0) return;
        var bar = ScrollBarGeometry.of(r.y() + 2, Math.max(1, r.height() - 4), total, visible, offset, 8);
        g.fill(r.right() - 6, r.y() + 2, r.right() - 2, r.bottom() - 2, skin.p.edge());
        g.fill(r.right() - 6, bar.thumbTop(), r.right() - 2, bar.thumbTop() + bar.thumbHeight(), skin.p.accent());
    }
    private Map<String, Rect> scrollRegions() {
        var regions = new LinkedHashMap<String, Rect>();
        if (state.page == Page.QUERY) {
            if (!layout.compact() || !state.details) regions.put("list", listBounds);
            if (!layout.compact() || state.details) regions.put("detail", readingBounds());
        } else if (state.page == Page.STATIONS && !indexOpen) regions.put("stations", stationBounds);
        else if (state.page == Page.HUD) regions.put("settings", layout.hudSettings());
        return regions;
    }
    private int total(String region) { return switch (region) {
        case "list" -> itemCount(); case "detail" -> detailHeight; case "stations" -> state.stations().size(); default -> 10;
    }; }
    private int visible(String id, Rect r) { return switch (id) {
        case "detail" -> r.height(); case "settings" -> Math.max(1, (r.height() - 16) / 24); default -> layout.visibleRows(r);
    }; }
    private int offset(String id) { return switch (id) {
        case "list" -> state.listScroll.get(state.view); case "detail" -> state.detailScroll;
        case "stations" -> state.stationScroll; default -> state.settingsScroll;
    }; }
    private void offset(String id, int value) {
        switch (id) {
            case "list" -> state.listScroll.put(state.view, value); case "detail" -> state.detailScroll = value;
            case "stations" -> state.stationScroll = value; default -> state.settingsScroll = value;
        }
    }
    @Override public boolean mouseClicked(double x, double y, int mouseButton) {
        lastInput = "mouse " + x + "," + y + " button=" + mouseButton;
        if (mouseButton == 0) pressedControl = controls.entrySet().stream().filter(e -> e.getValue().isMouseOver(x, y)).map(Map.Entry::getKey).findFirst().orElse("");
        if (mouseButton == 0) for (var e : scrollRegions().entrySet()) {
            var r = e.getValue(); int visible = visible(e.getKey(), r), total = total(e.getKey());
            if (total > visible && visible > 0 && x >= r.right() - 8 && x < r.right() && y >= r.y() && y < r.bottom()) {
                var bar = ScrollBarGeometry.of(r.y() + 2, r.height() - 4, total, visible, offset(e.getKey()), 8);
                draggingScrollbar = e.getKey(); scrollbarGrab = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0;
                dragScroll(y); return true;
            }
        }
        if (super.mouseClicked(x, y, mouseButton)) return true;
        if (state.page == Page.HUD && mouseButton == 0 && layout.hudPreview().contains(x, y)) {
            updateHudArrangement();
            for (boolean reminder : new boolean[]{true, false}) if (hudRect(reminder).contains(x, y)) {
                state.hud.reminder = reminder; var box = hudRect(reminder);
                grabX = x - box.x(); grabY = y - box.y(); dragging = true;
                for (var b : hudControls) b.visible = false;
                setFocused(null); return true;
            }
        }
        return false;
    }
    private void dragScroll(double y) {
        String id = draggingScrollbar; var r = scrollRegions().get(id);
        if (r == null) return;
        var bar = ScrollBarGeometry.of(r.y() + 2, r.height() - 4, total(id), visible(id, r), offset(id), 8);
        offset(id, bar.offsetAtThumbTop(y - scrollbarGrab));
        build(); draggingScrollbar = id;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && !draggingScrollbar.isEmpty()) { dragScroll(y); return true; }
        if (button == 0 && dragging) {
            var region = hudCanvas(); var placement = state.hud.reminder ? hudArrangement.reminder() : hudArrangement.navigation();
            state.hud.position(placement.relativeX(x - grabX - region.x()), placement.relativeY(y - grabY - region.y()));
            updateHudArrangement(); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        pressedControl = "";
        if (button == 0 && (dragging || !draggingScrollbar.isEmpty())) { dragging = false; draggingScrollbar = ""; build(); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        if (state.page == Page.HUD && layout.hudPreview().contains(x, y)) {
            state.hud.size(state.hud.percent() + (vertical < 0 ? -50 : 50)); build(); return true;
        }
        for (var e : scrollRegions().entrySet()) if (e.getValue().contains(x, y)) {
            int delta = e.getKey().equals("detail") ? 24 : 1;
            int max = Math.max(0, total(e.getKey()) - visible(e.getKey(), e.getValue()));
            offset(e.getKey(), Math.clamp(offset(e.getKey()) + (vertical < 0 ? delta : -delta), 0, max)); build(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        lastInput = "key " + key;
        if (key == GLFW.GLFW_KEY_F5) { nextVariant(); return true; }
        if (key == GLFW.GLFW_KEY_F6) { nextFont(); return true; }
        if (key == GLFW.GLFW_KEY_F7) { state.scenario(state.scenario.next()); build(); return true; }
        if (key == GLFW.GLFW_KEY_F8) { page(Page.values()[(state.page.ordinal() + 1) % Page.values().length]); return true; }
        if (key == GLFW.GLFW_KEY_F9 && state.page == Page.QUERY) { state.details = !state.details; build(); return true; }
        if (key == GLFW.GLFW_KEY_F10) { page(Page.COMPONENTS); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (indexOpen) { indexOpen = false; build(); }
            else if (state.page != Page.QUERY) page(Page.QUERY); else onClose();
            return true;
        }
        if (state.page == Page.HUD && hasControlDown() && key == GLFW.GLFW_KEY_S) { state.saveHud(); build(); return true; }
        if (state.page == Page.HUD && hasShiftDown() && key >= GLFW.GLFW_KEY_RIGHT && key <= GLFW.GLFW_KEY_UP) {
            double x = state.hud.reminder ? state.hud.reminderX : state.hud.navigationX;
            double y = state.hud.reminder ? state.hud.reminderY : state.hud.navigationY;
            state.hud.position(x + (key == GLFW.GLFW_KEY_LEFT ? -.02 : key == GLFW.GLFW_KEY_RIGHT ? .02 : 0),
                    y + (key == GLFW.GLFW_KEY_UP ? -.02 : key == GLFW.GLFW_KEY_DOWN ? .02 : 0)); return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && getFocused() instanceof EditBox) {
            if (state.page == Page.QUERY) { state.query(); build(); return true; }
            if (state.page == Page.STATIONS && state.stations().size() == 1) {
                state.selectedStation = state.stations().getFirst().id(); state.pickStation(); build(); return true;
            }
        }
        if (!(getFocused() instanceof EditBox) && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) {
            String id = state.page == Page.STATIONS ? "stations" : state.page == Page.HUD ? "settings" : layout.compact() && state.details ? "detail" : "list";
            var r = scrollRegions().get(id);
            if (r != null) { offset(id, Math.clamp(offset(id) + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * visible(id, r), 0, Math.max(0, total(id) - visible(id, r)))); build(); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private final class ReviewButton extends Button {
        private final boolean primary, selected;
        private final String id, caption;
        private final Icon icon;
        ReviewButton(String id, Rect r, String caption, boolean primary, boolean selected, Icon icon, Runnable action) {
            super(r.x(), r.y(), r.width(), r.height(), Component.literal(caption + (selected ? " · 已选中" : "")), b -> action.run(), DEFAULT_NARRATION);
            this.id = id; this.caption = caption; this.primary = primary; this.selected = selected; this.icon = icon;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            var r = at(getX(), getY(), width, height);
            boolean ore = state.variant == Variant.BEDROCK, pressed = id.equals(pressedControl);
            boolean toggle = (ore || state.variant == Variant.HYBRID) && Set.of("hudSetting2", "hudSetting7", "nearbyStations").contains(id);
            boolean dropdown = ore && id.equals("index");
            if (id.startsWith("nav")) skin.tab(g, r, selected, active, isHoveredOrFocused());
            else if (ore && id.startsWith("rail")) skin.navigation(g, r, selected, isHoveredOrFocused());
            else if (toggle) skin.toggle(g, r, selected, isHoveredOrFocused());
            else if (dropdown) skin.field(g, r, isHoveredOrFocused());
            else if (ore && Set.of("hudSetting0", "stationSort", "sort").contains(id)) {
                int half = width / 2;
                boolean second = id.equals("hudSetting0") ? state.hud.reminder : id.equals("stationSort") ? state.distanceOrder : state.fewestTransfers;
                skin.button(g, at(r.x(), r.y(), half, height), !second, !second, true, isHoveredOrFocused(), pressed);
                skin.button(g, at(r.x() + half, r.y(), width - half, height), second, second, true, isHoveredOrFocused(), pressed);
                String firstLabel = id.equals("hudSetting0") ? "导航" : id.equals("stationSort") ? "字母" : "最快";
                String secondLabel = id.equals("hudSetting0") ? "提醒" : id.equals("stationSort") ? "距离" : "少换";
                label(g, firstLabel, r.x() + 4, r.y() + 4, half - 8, second ? 0xFF1E1E1F : 0xFFFFFFFF);
                label(g, secondLabel, r.x() + half + 4, r.y() + 4, half - 8, second ? 0xFFFFFFFF : 0xFF1E1E1F);
                return;
            } else skin.button(g, r, primary, selected, active, isHoveredOrFocused(), pressed);
            int color = skin.buttonInk(primary, selected, active);
            if (id.startsWith("nav") && state.variant != Variant.AE2 || ore && id.startsWith("rail") || toggle || dropdown) color = skin.p.text();
            if (icon == null) {
                int reserved = toggle ? 40 : dropdown ? 20 : 8;
                String text = UiTypography.fit(face, caption, Math.max(0, width - reserved));
                boolean javaTab = id.startsWith("nav") && (state.variant == Variant.JAVA || state.variant == Variant.HYBRID);
                label(g, text, getX() + (toggle || dropdown || ore && id.startsWith("rail") ? 4 : Math.max(4, (width - font.width(text)) / 2)),
                        getY() + (height - 10) / 2 + (ore && pressed && !toggle ? 1 : 0) + (javaTab && !selected ? 2 : 0), width - reserved, color);
                if (javaTab && selected) {
                    int w = Math.min(font.width(text), width - 4), x = getX() + (width - w) / 2;
                    g.fill(x, getY() + height - 2, x + w, getY() + height - 1, 0xFFFFFFFF);
                }
                if (dropdown) {
                    int x = r.right() - 12, y = r.y() + 8;
                    g.fill(x, y, x + 7, y + 1, color); g.fill(x + 1, y + 1, x + 6, y + 2, color); g.fill(x + 2, y + 2, x + 5, y + 3, color);
                }
            } else drawIcon(g, icon, getX() + width / 2 - 5, getY() + height / 2 - 5, color);
        }
        @Override public void onClick(double x, double y) {
            if (state.variant == Variant.BEDROCK && id.equals("hudSetting0")) { state.hud.reminder = x >= getX() + width / 2.0; build(); }
            else if (state.variant == Variant.BEDROCK && id.equals("stationSort")) { state.distanceOrder = x >= getX() + width / 2.0; state.filterChanged(); build(); }
            else if (state.variant == Variant.BEDROCK && id.equals("sort")) setJourneyOrder(x >= getX() + width / 2.0);
            else super.onClick(x, y);
        }
    }
    private final class InsetField extends EditBox {
        InsetField(Rect r, String name) {
            super(new PaperFont(font), r.x(), r.y(), r.width(), r.height(), Component.literal(name));
            setBordered(false);
        }
        @Override public int getInnerWidth() { return Math.max(0, getWidth() - 8); }
        @Override public void onClick(double x, double y) { super.onClick(x - 4, y); }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            skin.field(g, at(getX(), getY(), width, height), isFocused());
            g.pose().pushPose(); g.pose().translate(4, (height - 8) / 2, 0);
            super.renderWidget(g, mx - 4, my - (height - 8) / 2, delta); g.pose().popPose();
        }
    }
    private final class ReviewSlider extends AbstractSliderButton {
        ReviewSlider(Rect r) { super(r.x(), r.y(), r.width(), r.height(), Component.literal("HUD 大小"), (state.hud.percent() - 100) / 100.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("大小：" + state.hud.percent() + "%")); }
        @Override protected void applyValue() { state.hud.size(100 + (int)Math.round(value * 2) * 50); value = (state.hud.percent() - 100) / 100.0; }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            var r = at(getX(), getY(), width, height);
            if (isHoveredOrFocused()) edge(g, r, 0xFFFFFFFF);
            label(g, getMessage().getString(), getX() + 4, getY(), width - 8, skin.p.text());
            int x = getX() + 4, y = getY() + 14, thumb = x + (int)Math.round((width - 16) * value);
            g.fill(x, y, getX() + width - 4, y + 4, 0xFF1E1E1F);
            g.fill(x + 1, y + 1, thumb + 4, y + 3, 0xFF3C8527);
            fill(g, at(thumb, y - 4, 8, 10), 0xFF1E1E1F); fill(g, at(thumb + 1, y - 3, 6, 7), 0xFFD0D1D4);
            g.fill(thumb + 1, y - 3, thumb + 7, y - 2, 0xFFFFFFFF);
        }
    }
    private final class ReviewRow extends Button {
        private final String first, second;
        private final boolean selected;
        ReviewRow(Rect r, String first, String second, boolean selected, Runnable action) {
            super(r.x(), r.y(), r.width(), r.height(), Component.literal(first + ", " + second + (selected ? ", 已选中" : "")), b -> action.run(), DEFAULT_NARRATION);
            this.first = first; this.second = second; this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            var r = at(getX(), getY(), width, height); skin.row(g, r, selected, isHoveredOrFocused());
            int top = height >= 36 ? 6 : 2;
            label(g, first, getX() + 8, getY() + top, width - 16, skin.p.text());
            label(g, second, getX() + 8, getY() + top + 14, width - 16, skin.p.muted());
        }
    }
    private static void drawIcon(GuiGraphics g, Icon icon, int x, int y, int color) {
        switch (icon) {
            case SWAP -> {
                g.fill(x, y + 2, x + 10, y + 3, color); g.fill(x + 7, y, x + 8, y + 5, color);
                g.fill(x, y + 7, x + 10, y + 8, color); g.fill(x + 2, y + 5, x + 3, y + 10, color);
            }
            case SORT -> { for (int i = 0; i < 3; i++) g.fill(x, y + i * 4, x + 10 - i * 3, y + i * 4 + 2, color); }
            case STATION -> {
                edge(g, at(x + 1, y, 8, 8), color); g.fill(x + 3, y + 2, x + 7, y + 5, color);
                g.fill(x + 1, y + 9, x + 3, y + 10, color); g.fill(x + 7, y + 9, x + 9, y + 10, color);
            }
            case HUD -> { edge(g, at(x, y + 1, 10, 7), color); g.fill(x + 4, y + 8, x + 6, y + 10, color); }
            case COMPONENTS -> { for (int i = 0; i < 4; i++) edge(g, at(x + i % 2 * 6, y + i / 2 * 6, 4, 4), color); }
        }
    }
    private void writeAudit() {
        long now = System.currentTimeMillis(); if (now - lastAudit < 250) return; lastAudit = now;
        var audit = new LinkedHashMap<String, Object>();
        audit.put("variant", state.variant); audit.put("page", state.page); audit.put("view", state.view);
        audit.put("lastInput", lastInput);
        audit.put("scenario", state.scenario); audit.put("font", face.name());
        audit.put("pixelSize", List.of(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()));
        audit.put("guiSize", List.of(width, height)); audit.put("guiScale", minecraft.getWindow().getGuiScale());
        audit.put("frame", rect(layout.panel())); audit.put("compact", layout.compact());
        audit.put("origin", state.origin); audit.put("destination", state.destination); audit.put("selectedJourney", state.selectedJourney);
        audit.put("selectedStation", state.selectedStation); audit.put("stationSearch", state.stationSearch);
        audit.put("stationCount", state.stations().size()); audit.put("stationScroll", state.stationScroll);
        audit.put("index", state.index); audit.put("nearby", state.nearby); audit.put("indexOpen", indexOpen);
        audit.put("listScroll", state.listScroll); audit.put("detailScroll", state.detailScroll);
        audit.put("queryCollapsed", state.queryCollapsed); audit.put("details", state.details); audit.put("activeTrip", state.activeTrip);
        audit.put("canStart", state.canStart()); audit.put("notice", state.notice);
        audit.put("hud", state.hud.snapshot()); audit.put("hudReminderSelected", state.hud.reminder);
        audit.put("settingsScroll", state.settingsScroll); audit.put("hudSaved", state.hasSavedHud()); audit.put("dragging", dragging);
        if (listBounds != null) {
            audit.put("list", rect(listBounds)); audit.put("detail", rect(detailBounds));
            audit.put("visibleRows", layout.visibleRows(listBounds)); audit.put("detailHeight", detailHeight);
            if (!layout.compact() || state.details) audit.put("detailText", renderedDetailText);
        }
        if (stationBounds != null) audit.put("stationList", rect(stationBounds));
        if (state.page == Page.HUD) {
            audit.put("settings", rect(layout.hudSettings())); audit.put("preview", rect(layout.hudPreview()));
            audit.put("navigationHud", rect(hudRect(false))); audit.put("reminderHud", rect(hudRect(true)));
        }
        var entries = new ArrayList<Object>();
        controls.forEach((id, b) -> {
            if (b.visible) entries.add(Map.of("id", id, "label", b.getMessage().getString(), "rect", List.of(b.getX(), b.getY(), b.getWidth(), b.getHeight()), "active", b.active, "focused", b.isFocused()));
        });
        audit.put("controls", entries);
        var inputs = new LinkedHashMap<String, Object>();
        fields.forEach((id, b) -> inputs.put(id, Map.of("value", b.getValue(), "cursor", b.getCursorPosition(), "selected", b.getHighlighted())));
        audit.put("inputs", inputs);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(audit);
        if (json.equals(lastAuditJson)) return;
        try { Files.writeString(minecraft.gameDirectory.toPath().resolve("mtrnav-paradigm-audit.json"), json); lastAuditJson = json; }
        catch (java.io.IOException e) {
            if (detailWarnings.add("audit")) com.mojang.logging.LogUtils.getLogger().warn("Cannot write paradigm review audit", e);
        }
    }
    private static List<Integer> rect(Rect r) { return List.of(r.x(), r.y(), r.width(), r.height()); }
}
