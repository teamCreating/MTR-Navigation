# 当前正式界面：新一代「现代轨道导视 × 候车厅 PIDS」工作台（v4）

MTR：站间物语已于 2026-11-24 正式告别旧版 Ore UI，全面切换至零外部依赖的自研现代原生 UI 引擎与双 HUD 体系。设计 Token 规范见 `src/main/resources/assets/mtrnav/ui/design.json`。

## 核心架构与视觉原则

- **方向 C（MC 深板岩外框 + 100% 纯净现代内部）**：最外层工作台主框采用呼应官方 `assets/mtrnav/icon.png` 的 3px 像素切角深板岩边框与四角钢灰包角；内部按钮、胶囊、输入框、列表卡片和 2D 地图完全保持干净平直的现代轨道导视风格，避免圆角与硬边像素线混搭。
- **一体化左侧导航工作台 + 右侧滑出抽屉**：`WorkspaceScreen` 统一承载「换乘查询 / 车站到发 / 当前导航 / 步行换乘 / HUD 设置」五大模块，选站、车型筛选、班次提醒、步行编辑与隐私确认均通过 `SlideDrawer` 右侧滑出完成，不再多层跳转窗口。
- **换乘规划（时间轴 + 手机导航级 2D 地图融合）**：右侧可一键切换高清垂直时间轴（含可展开途经站与车厢编组）或 2D 实景线网地图（`CartographicMapLayer` 在真实存档中自动采样已加载区块地表与山影，叠加平滑弯道轨道、动态流动方向箭头、水滴形起终点图钉、换乘气泡、玩家 GPS 蓝点与朝向光锥、指北针与动态米制比例尺）。
- **候车厅 PIDS 电子大屏**：实时时钟、线路筛选胶囊、大号倒计时、图形化 `ConsistBar` 编组条与一键滑出到站提醒抽屉。
- **半透明新一代双 HUD 与交互式设置**：`LcdNavigationHud`（动态列车门 LCD 线路条）与 `CapsuleReminderHud`（顶部提醒胶囊）采用高透暗色玻璃质感；`HudSettingsView` 提供 16:9 可直接拖拽/滚轮缩放的实时屏显预览与 1:1 真机近景预览，并支持一键进入全屏实景拖拽模式。
- **零外部依赖与字体原则**：完全基于自研原生矢量原语（`core/ui/` + `client/ui/` + `compat/MinecraftGui`）与原创 32×32 SDF 图标图集（`assets/mtrnav/textures/gui/icons.png`），字形完全跟随 Minecraft 当前默认字体，原生兼容 ModernUI、资源包字体及中文输入法。
- **与 `MTR-Map-Overlay` 生态互补**：保持 `NavigationApi`（`JourneySelection` v1）100% 向后兼容，`NavigationUi` 暴露 `openStationBoard` / `openPlanWithStation` 深链入口；站间连线为轻量化拓扑/平滑拟合，真实全服轨道几何与离线世界地图仍由 `MTR-Map-Overlay` 承载。

## 代码结构

- `src/main/java/io/github/teamcreating/mtrnav/core/ui/`：纯 Java 几何、缓动、滚动、拓扑投影引擎（`UiRect`、`UiMotion`、`ScrollState`、`TopologyProjection`）。
- `src/main/java/io/github/teamcreating/mtrnav/client/ui/theme/`：设计系统与绘制原语（`UiTokens`、`UiDraw`、`UiIcons`、`NavFormat`）。
- `src/main/java/io/github/teamcreating/mtrnav/client/ui/framework/`：自研组件库（`UiContext`、`UiView`、`SlideDrawer`、`UiButton`、`UiToggle`、`UiTextInput`、`UiSlider`、`ConsistBar`）。
- `src/main/java/io/github/teamcreating/mtrnav/client/ui/workspace/`：工作台与核心视图（`WorkspaceScreen`、`JourneyPlanView`、`TimelineDetailPane`、`TopologyCanvasPane`、`CartographicMapLayer`、`PidsBoardView`、`ActiveTripView`、`WalkingNetworkView`、`HudSettingsView`、`NewUiFixtures`）。
- `src/main/java/io/github/teamcreating/mtrnav/client/ui/drawer/`：6 个右侧滑出抽屉（`StationPickerDrawer`、`VehicleFilterDrawer`、`ReminderDrawer`、`WalkingEditorDrawer`、`ConfirmDrawer`、`TextPromptDrawer`）。
- `src/main/java/io/github/teamcreating/mtrnav/client/ui/hud/`：双新 HUD（`LcdNavigationHud`、`CapsuleReminderHud`）。

## 本地开发与夹具验收

- 真实存档运行：`.\dev.ps1 -Platform neoforge -MinecraftVersion 1.21.1 runClient`
- 内置五场景测试夹具（按 `F7` 切换）：`.\dev.ps1 -Platform neoforge -MinecraftVersion 1.21.1 -GradleArgs @('runClient','-PnewUiFixtures','-Pquickplay=NavTest','--offline')`。
- 四端构建目标保持一致：Minecraft `1.21.1` / `1.21.4` × `NeoForge` / `Fabric`。
