<p align="center">
  <img src="src/main/resources/assets/mtrnav/icon.png" width="168" alt="MTR：站间物语">
</p>

<h1 align="center">MTR：站间物语</h1>

<p align="center"><sub>MTR Navigation · 一个 Minecraft Transit Railway 导航模组</sub></p>

<p align="center">
  <img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-3f7d57?style=flat-square">
  <img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-4c8b3f?style=flat-square">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-b07219?style=flat-square">
  <img alt="NeoForge 21.1" src="https://img.shields.io/badge/NeoForge-21.1-8f6b2f?style=flat-square">
  <img alt="Fabric" src="https://img.shields.io/badge/Fabric-0.16.14-8f6b2f?style=flat-square">
  <img alt="MTR 4.1.0-beta.2" src="https://img.shields.io/badge/MTR-4.1.0--beta.2-2f6b8f?style=flat-square">
</p>

<p align="center">查车站、比较换乘方案、看发车倒计时——上车之后，再用一个小 HUD 安静地提示下一步。</p>

## 简介

MTR：站间物语把铁路导航做进了 Minecraft 原生界面：输入起终点即可查询换乘方案，在线路颜色时间轴里比较每一段行程；也可以单独查任意车站的到发时刻。

模组支持 Minecraft **1.21.1**、Java **21**、MTR **4.1.0-beta.2**，同时提供 **NeoForge** 和 **Fabric** 两个独立安装包，两者共用同一套查询与界面代码。

仓库名沿用 `MTR-Navigation`，mod id 为 `mtrnav`。

## 功能

- **站点搜索**：中文或英文完整站名可直接输入，也可点击独立的“全部车站”按钮进入选站页，浏览并搜索当前维度的全部车站。同名站显示坐标，列表支持滚轮、拖动滚动条和键盘选择；原界面不再弹出遮挡方案的下拉列表，输入框右侧可一键清空。
- **换乘方案**：按线路颜色绘制时间轴，标出上下车站、站台和候车时间；点击详情标题展开或收起途经站，长行程可用滚轮查看。
- **行程进度**：开始导航后进入当前行程页，按“已上车”“已下车，继续”推进；按“上一步”可校正误操作。
- **车站到发**：独立搜索任意车站，按线路分类，点击线路展开当前已同步的全部班次；同名同色线路的不同方向放在同一组，各班次保留方向、站台、到达／发车倒计时及实时／计划标识。展开状态在自动刷新时保留。
- **途中 HUD**：三行显示下一操作、站台和倒计时。默认位于右上角，默认大小在大窗口约 405×97 实际像素，不随 GUI 缩放继续放大；可拖动位置、调整大小或开启小窗口自动适配。
- **远途线路参考**：即使当前没有可乘班次，也会根据有向停站顺序显示一条换乘路径。超过换乘上限、暂无班次和计算未完成分别提示；线路参考不包含虚构到发时间，也不能直接开始班次导航。
- **两种服务端形态**：只有 MTR 时走原生兼容查询；服务端也装了本模组时改用权威全网数据，并在能确认车辆身份时自动提示下车／换乘。

界面采用“铁路行程册”布局，保留 MC 原版石质按钮、像素边框，加入机械动力风格的黄铜包角、铆钉和轨道刻度；不需要安装机械动力。宽屏左右分栏，小窗口压缩方案卡片或改为横排；没有活动行程时隐藏上车、下车和结束按钮。小窗口查到方案后会自动收起搜索区，点“修改站点”可再次编辑。

## 安装

客户端只需安装与加载器对应的一个 JAR，服务端**可选**。

| 加载器 | 需要的文件 |
| --- | --- |
| NeoForge 21.1.x | `MTR-Navigation-neoforge-0.1.0.jar` |
| Fabric | `MTR-Navigation-fabric-0.1.0.jar`，另需 [Fabric API](https://modrinth.com/mod/fabric-api) |

前置：Minecraft 1.21.1、Java 21、MTR 4.1.0-beta.2，仅客户端安装也能使用。

## 使用

1. 游戏内按 **N**，或输入 **`/mtrnav`**；按键可在控制设置中修改。
2. 输入起终点的完整站名，或点击 **全部车站** 搜索并选中车站，再点“设为起点”“设为终点”；回到查询页后回车或点“查询”。
3. 点击方案卡片比较行程，确认后点“开始导航”。
4. 上车后点“已上车”，到站下车后点“已下车，继续”；底部的“上一步”用于校正。
5. 关闭窗口后由 HUD 继续提示；按导航键返回行程。
6. 顶部“车站到发”可随时切到独立到发页，不需要填写起终点；点击线路标题展开／收起班次，可拖动右侧滚动条查看下方内容。
7. 顶部 **HUD** 打开设置：拖动提示框选择任意位置，滚轮或加减按钮调节大小；也可选择四角预设。拖动时设置面板收起，HUD 放到底部后面板移到顶部，避免遮挡。

重新查询后需再次选择方案才会替换当前行程；在“换乘查询”“当前导航”“车站到发”之间切换时，起终点和正在执行的行程都会保留。

HUD 大小可在 75%～200% 之间调整；“自动适配”开启时，小窗口会进一步限制占用面积。位置按可用屏幕区域的比例保存，切换分辨率后仍保持在屏幕内。设置保存在当前游戏实例的 `config/mtrnav-client.properties`，不需要修改服务端配置。

起点、终点及到发搜索框右侧的 **×** 会清空对应输入。修改起终点后旧查询结果会清除，迟到的旧响应不会恢复旧方案；正在执行的导航继续保留。到发页展示当前来源提供的近期班次，不是全天时刻表；请求范围上限为每站台 32 班、每车站 512 班，原生服务可能只返回更少数据。

“全部车站”是独立页面，搜索留空时显示当前目录的全部车站。上下方向键选择，双击车站或按回车使用金色边框标出的默认用途，也可点底部按钮明确选择用途；按 Esc 或右上角 × 返回，不修改原行程。目录页同样提供“查看到发”，可直接进入所选车站的到发页。

## 服务端

### 只装了 MTR（以及可选的地图 Overlay）

**基本查询不要求服务端加装本模组。** 客户端会尝试读取 Overlay 1.5.2 已同步的全网车站快照，再通过 MTR 自带的游戏网络请求获取线路时刻和站台到发，无需客户端直接连接服务器的网页端口。

这一模式依赖服务端的 MTR 原生系统地图服务正常运行；Overlay 的地图快照本身不含班次时刻，时刻服务不可用时界面会显示原因，不会用猜测时间代替。安装 Overlay 后优先复用它提供的车站位置；没有 Overlay 也可以尝试直接查询 MTR 原生服务。模组不修改 Overlay 的显示或配置。

### 也安装了本模组

自动使用本模组的权威全网服务，保留 siding、班次循环和重复停靠身份。能可靠关联玩家所乘车辆时自动提示下车／换乘，无法确认时保留手动校正。

服务端配置位于 `config/mtrnav.properties`：默认查询未来六小时、最多四次换乘，步行按站台距离估算并预留 30 秒换乘余量；只允许同站和 MTR 显式连接的车站之间换乘。当前版本仅查询当前维度的铁路客运。

### 请求节奏

导航仅在打开界面或存在行程时按需获取数据：原生全网时刻至多每三秒刷新，到发页至多每秒刷新，同一时间只保留一个请求。服务失败后停止自动请求，15 秒后可点“重试连接”；关闭界面且没有行程时停止轮询，也不会额外触发 Overlay 的全量同步。

## 从源码构建

先设置 Java 21 的 `JAVA_HOME`；Windows 启动脚本在未设置时会尝试本机的 `C:\Program Files\Java\jdk-21`。

```powershell
python tools/download_dependencies.py
.\dev.ps1 -Platform neoforge build testFabricCore
.\dev.ps1 -Platform fabric build
```

Linux／macOS 对应使用 `./dev.sh neoforge build testFabricCore` 和 `./dev.sh fabric build`。

- NeoForge：`build/libs/MTR-Navigation-neoforge-0.1.0.jar`
- Fabric：`fabric/build/libs/MTR-Navigation-fabric-0.1.0.jar`

请通过 `dev.ps1` 或 `dev.sh` 启动构建：它们把 Gradle、依赖和工具缓存放在 `.cache`，临时文件放在 `.tmp`；构建与测试报告在 `build`／`fabric/build`，开发实例在 `run`／`run-server`。分发后的配置使用玩家自己的游戏实例目录。

开发运行：

```powershell
.\dev.ps1 -Platform neoforge runClient
.\dev.ps1 -Platform fabric runClient -PruntimeMtr
```

NeoForge 需要把指定的 MTR JAR 放进 `run/mods`；Fabric 的 `-PruntimeMtr` 会把项目缓存中的 MTR 加入开发运行依赖。

## 兼容性与已知限制

- 原生接口不公开完整的实际车辆身份，因此**行程进度需手动确认**；原生兼容模式下的临近到站提示会明确标为预计，仍需核对站名。
- 上述“手动确认”是指：模组能查到列车时刻，但仅凭这些数据无法可靠知道你实际上了哪一班车。上车和下车后在行程页确认即可；“预计到站”按所选班次时间提醒，不代表已识别你所在的列车。
- 原生兼容路径尚未获得可靠的站台 ID 时，不对外发布不完整的地图选择。
- 换乘方案使用 MTR 原生地图的运行时长、停站顺序和发车数据，标为计划时刻估算；车站到发页保留原生接口给出的“实时／计划”标识。
- 当前版本仅查询当前维度的铁路客运；时刻数据不可用时显示原因，不用猜测时间代替。
- “暂无可乘班次”仅描述当前查询范围内的结果，可能是不在运营时段，也可能是原生时刻数据不完整；不能据此确认整条线路停运。线路参考可以显示超过四次换乘的远途路径，但不会放宽有时间方案的服务端查询限制。
- 地图区间高亮留给后续的扩展接口。

## 开发接口

`io.github.teamcreating.mtrnav.api.NavigationApi` 提供当前不可变 `JourneySelection` 及选择、更新、取消事件。乘车段包含线路 ID、上下车站台 ID、停站序号和方向，保留重复站台及环线出现次序。

监听器在客户端线程调用，`listen` 返回可关闭的注销句柄。Overlay 不需要成为硬依赖。

给 MTR Map Overlay 开发者的接口、生命周期、区间映射约束和接入示例见 **[Overlay 对接指南](docs/overlay-integration.md)**。未确认班次的线路参考不会发布为活动导航选择。

## 文档与验证

- 实测记录与边界：[docs/validation/verification.md](docs/validation/verification.md)
- 界面交互演示（浏览器预览，示例数据）：[docs/ui-preview](docs/ui-preview/README.md)

## 许可证

本项目以 MIT 许可证开源，版权归 TeamCreating 所有。复用的 Gradle wrapper、MTR accessor 思路和测试网络生成器来自 MTR Map Overlay，其 MIT 授权全文见 [NOTICE](NOTICE)。
