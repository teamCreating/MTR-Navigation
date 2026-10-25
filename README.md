<p align="center">
  <a href="https://github.com/teamCreating/MTR-Navigation/blob/main/src/main/resources/assets/mtrnav/icon.png">
    <img src="https://cdn.jsdelivr.net/gh/teamCreating/MTR-Navigation@main/src/main/resources/assets/mtrnav/icon.png" width="144" height="144" alt="MTR：站间物语图标">
  </a>
</p>

<h1 align="center">MTR：站间物语</h1>

<p align="center">查换乘、看班次，让下一段旅程更清楚。</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-528A3E?style=flat-square" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/MTR-4.1.0--beta.2-386F91?style=flat-square" alt="MTR 4.1.0-beta.2">
  <img src="https://img.shields.io/badge/Java-21-B47A34?style=flat-square" alt="Java 21">
  <img src="https://img.shields.io/badge/License-MIT-82704A?style=flat-square" alt="MIT License">
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> ·
  <a href="#安装与兼容">安装与兼容</a> ·
  <a href="docs/overlay-integration.md">Overlay 对接</a> ·
  <a href="https://github.com/teamCreating/MTR-Navigation/issues">反馈问题</a> ·
  <a href="#english">English</a>
</p>

MTR：站间物语是一个独立的 Minecraft Transit Railway 导航附属模组。你可以查找车站、比较换乘方案、查看站台到发，或为某一班列车设置到站提醒。界面采用 Minecraft 原版控件，配合像素边框与黄铜装饰；**不需要安装机械动力**。

**当前为开发测试版。** 提供 NeoForge 和 Fabric 构建包；NeoForge 已有联机实测记录，Fabric 的游戏内完整验证及两个加载器的专服完整验收尚未完成。新增界面和提醒也仍需实机复查，详见[验证记录](docs/validation/verification.md)。

## 可以做什么

- **找车站**：直接输入中文或英文完整站名，或打开独立的“全部车站”页面搜索、滚动选择。同名站显示坐标，选站列表不会盖住换乘方案。
- **查换乘**：支持“最快到达”“少换乘”排序，最多展示三个候选；查看方向、上下车站台、途经站、候车时间和预计到达。
- **看班次**：到发页按线路分组，展开查看已同步的班次，分别显示到达与发车倒计时，以及来源提供的实时／计划标识。
- **设提醒**：点击具体班次，默认到站前 **9 分钟**提醒，可调为 **1～60 分钟**。关闭窗口后继续更新，顶部通知条在触发时展开并播放一次提示音。
- **途中导航**：开始行程后，由 HUD 提示下一操作；可手动确认上车、下车或返回上一步，也可调整 HUD 的位置和大小。
- **看线路参考**：有换乘路径但暂时找不到可乘班次时，仍展示线路连接，并说明无班次、超出范围或计算未完成；不编造到发时间。

## 安装与兼容

需要 **Minecraft 1.21.1、Java 21、MTR 4.1.0-beta.2**。

- **NeoForge 21.1.x**：安装 `MTR-Navigation-neoforge-0.1.0.jar`。
- **Fabric Loader 0.16.14 或更新版本**：安装 `MTR-Navigation-fabric-0.1.0.jar`，另需 Minecraft 1.21.1 对应的 [Fabric API](https://modrinth.com/mod/fabric-api)。

将对应的一个 JAR 放进游戏实例的 `mods` 文件夹。更新前退出游戏，移走旧 Navigation JAR，确保只保留一份；不要把两个加载器的包一起安装。

**客户端需要安装本模组，服务端可选。**

- **服务端只有 MTR**：客户端尝试通过 MTR 原生地图服务获取线路和到发数据。该服务必须可用，行程进度需手动确认。
- **服务端也安装本模组**：使用专用全网查询服务；只有可靠关联到玩家乘坐的车辆时，才使用自动进度，否则继续提供手动校正。
- **MTR Map Overlay 是可选兼容项**：可以只读复用其已经同步的车站目录，但地图快照不能代替班次时刻。已有 Overlay 不代表原生时刻服务必然可用。

仓库名为 `MTR-Navigation`，模组 ID 为 `mtrnav`。当前查询仅支持**本维度的铁路客运**。

## 快速开始

### 查询与导航

1. 按 **N** 或输入 **`/mtrnav`** 打开界面，快捷键可在控制设置中修改。
2. 输入起终点的完整站名；也可点 **全部车站**，选中后分别设为起点、终点。
3. 点 **查询**，切换排序并比较候选方案，确认后点 **开始导航**。
4. 上车后点 **已上车**，下车后点 **已下车，继续**；操作错误时用 **上一步** 校正。
5. 关闭窗口后 HUD 继续提示，按打开键返回。

点击行程详情中的车站可查看到发；点击详情标题可展开途经站。重新查询不会直接替换正在执行的行程，需要再次选择方案。三个搜索框右侧的 **×** 可清空输入。

### 到发与班次提醒

打开 **车站到发**，选择车站，点击线路标题展开班次。列表支持滚轮与拖动右侧滚动条；自动刷新保留线路展开状态。

点击某一班次可设置提醒，时间以**该列车到达所选车站**为准。保存时已经进入提前窗口，会在数据确认后立即触发。

- 提醒条位于顶部中央，平时收起，设置和触发时短暂展开；大小跟随 HUD 缩放设置。
- 数据超过五秒未更新、采样过旧或不能确认所选记录时，显示 **班次待确认**，暂停可靠倒计时与提醒。
- 发车时间已过时显示相应提示，不直接判断你是否已经上车，也不会自动换成下一班。
- 顶部 **提醒** 可更新或取消提醒。只有提醒、没有导航行程时，按打开键优先进入提醒管理。

每次连接最多保留一个提醒；替换其他班次需要明确点击替换。提醒不跨会话保存，断线、切服或切维度时清除，最后已知发车时间过去十分钟后过期。

到发页展示的是当前数据范围内的班次，**不是全天时刻表**。请求上限为每站台 32 班、每车站 512 班，实际来源可能返回更少数据。

### 调整 HUD

点顶部 **HUD** 打开设置：拖动提示框选择位置，滚轮或加减按钮调节大小，支持四角预设、自动适配与恢复默认。拖动时设置面板收起，提示框移到底部后面板自动避让。

大小范围为 75%～200%。位置按可用屏幕区域的比例保存，切换分辨率后仍保持在屏幕内；配置位于游戏实例的 `config/mtrnav-client.properties`。

## 数据与使用边界

**查到时刻，不等于知道你实际上了哪一班车。** 原生兼容模式缺少完整车辆身份，上车和下车需手动确认；预计到站提醒也不是对玩家所在列车的定位。到发页的“实时／计划”标识以 MTR 返回的数据为准。

默认有时间方案的查询范围是未来六小时、最多四次换乘，换乘额外预留 30 秒；只允许同站及 MTR 明确连接的车站之间换乘。服务端安装本模组后，可通过 `config/mtrnav.properties` 调整这些参数。

“暂无可乘班次”只描述当前查询结果，可能是不在运营时段、超出查询范围或时刻数据不完整，不能据此确认整条线路停运。线路参考可以展示更远的拓扑路径，但没有确认班次时不能启动班次导航。

查询按需进行：没有打开导航界面、活动行程或提醒时停止轮询。原生请求串行处理；服务失败后暂停自动请求，至少十五秒后可由用户重试。同站的到发和提醒复用数据，不触发 Overlay 全量同步。

## 给开发者

- [Overlay 对接指南](docs/overlay-integration.md)：公共 API、选择／更新／取消事件、环线重复停靠、精确乘车区间与接入示例。
- [验证记录](docs/validation/verification.md)：已完成的构建、回归、实测及尚未覆盖的边界。
- [早期布局演示](docs/ui-preview/README.md)：浏览器原型与示例数据，供布局讨论使用，不代表当前界面截图。

`NavigationApi` 发布不可变的 `JourneySelection`，不要求 Overlay 成为硬依赖。**实际地图区间高亮尚未实现**；线路参考及班次提醒不会替换活动导航的地图选择。

### 从源码构建

先准备 Java 21，并设置 `JAVA_HOME`。通过项目脚本启动，确保缓存和临时文件保存在项目内。

```powershell
python tools/download_dependencies.py
.\dev.ps1 -Platform neoforge build testFabricCore
.\dev.ps1 -Platform fabric build
```

Linux／macOS 对应使用 `./dev.sh neoforge build testFabricCore` 和 `./dev.sh fabric build`。

产物分别位于 `build/libs` 和 `fabric/build/libs`。Gradle、依赖与工具缓存位于 `.cache`，临时文件位于 `.tmp`，开发游戏实例位于 `run`／`run-server`；这些生成目录不提交到仓库。分发后的配置使用玩家自己的游戏实例目录。

## English

**MTR: Between Stations** is an independent navigation add-on for Minecraft Transit Railway. It provides a searchable station directory, transfer planning, grouped station arrivals and departures, journey guidance, and reminders for individual trains.

Requires **Minecraft 1.21.1, Java 21 and MTR 4.1.0-beta.2**. Separate NeoForge 21.1.x and Fabric builds are available; Fabric also requires Fabric API. Install one matching JAR on the client. Installing Navigation on the server is optional, but client-only operation requires the server's native MTR map service to work.

Press **N** or use **`/mtrnav`**. Use **All stations** to choose an origin, destination or arrivals station. Compare connections, start a journey, and confirm boarding or alighting when automatic progress is unavailable. Close the screen to keep the journey HUD active.

Click a train on the arrivals board to set a reminder, **9 minutes before arrival** by default, adjustable from 1 to 60 minutes. A compact notification at the top centre expands when triggered. Stale or ambiguous data pauses the reminder; it never silently switches to the next train. One reminder is retained per connection and is cleared on disconnect or dimension change.

Native compatibility mode cannot reliably identify the player's actual train, so journey progress needs manual confirmation. Live and scheduled labels follow the source data. Queries cover passenger rail in the current dimension; a route reference without confirmed train times cannot start a timed journey. Full-day timetables and map-track highlighting are not included.

This is a **development build**. NeoForge has multiplayer test records; complete Fabric in-game and dedicated-server validation remain unfinished. See the [verification notes](docs/validation/verification.md) and [Overlay integration guide](docs/overlay-integration.md) for precise scope.

## 许可与致谢

本项目使用 [MIT 许可证](LICENSE)。部分 Gradle wrapper、MTR accessor 思路和测试网络生成器复用自 **MTR Map Overlay**，其原作者署名与 MIT 授权见 [NOTICE](NOTICE)。
