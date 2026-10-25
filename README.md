# MTR Navigation

Minecraft 原生界面的铁路导航：站点搜索、换乘查询、车站到发倒计时，以及途中 HUD。

支持 Minecraft **1.21.1**、Java **21**、MTR **4.1.0-beta.2**，提供 NeoForge 和 Fabric 两个独立安装包。

## 安装和使用

1. 客户端安装与加载器对应的一个 Navigation JAR；Fabric 还需要 Fabric API。
2. 在游戏内按 **N**，或输入 **`/mtrnav`**。可在控制设置中改键。
3. 输入起终点的中文或英文名称。唯一的完整站名会自动选中；重名时点击带坐标的候选项。按回车或点击“查询”。
4. 点击方案卡片比较行程，在线路颜色时间轴中查看上下车站、站台和候车时间。点击详情标题展开／收起途经站，滚轮查看长行程。
5. 点击“开始导航”进入当前行程页。上车后点击“已上车”，到站下车后点击“已下车，继续”；底部的“上一步”用于校正。重新查询后需再次选择方案才会替换当前行程。
6. 关闭窗口后，右上角三行小 HUD 显示下一操作、站台和发车／到达倒计时；尺寸上限约 270×65 实际像素，不随较大的 GUI 缩放继续放大。按实际绑定的导航键返回行程。
7. 顶部“车站到发”进入独立到发页，可直接搜索任意车站，不需要填写起终点。查看线路方向、站台、到达／发车倒计时和实时／计划标识；也可点击行程中的上下车站名进入。切换回“换乘查询”保留原来的起终点，“当前导航”保留正在执行的行程。

界面采用 A“铁路行程册”布局和暖灰／浅金配色：宽屏左右分栏，小窗口压缩方案卡片或采用横排；搜索候选仅在输入时展开。没有活动行程时隐藏上车、下车和结束按钮。

小窗口查到方案后自动收起搜索区，点击“修改站点”可再次编辑。途中提醒区分等待上车、乘车、换乘和完成；原生兼容模式的临近到站提示明确标为预计，仍需核对站名后手动确认。

### 服务端已有 MTR 和 MTR Map Overlay

**基本查询不要求服务端加装 Navigation。** 客户端会尝试读取 Overlay 1.5.2 已同步的全网车站快照，再通过 MTR 自带的游戏网络请求获取线路时刻和站台到发。无需客户端直接连接服务器的网页端口。

这一模式需要服务端的 MTR 原生系统地图服务正常运行。Overlay 的地图快照本身不含班次时刻；如果时刻服务不可用，界面会显示原因，不会用猜测时间代替。

导航仅在打开界面或已有行程时按需获取数据，原生全网时刻至多每三秒刷新，到发页至多每秒刷新；同一时间只保留一个请求。服务失败后停止自动请求，15 秒后可点击“重试连接”。关闭界面且无行程时停止轮询，不额外触发 Overlay 的全量同步。

- 换乘方案使用 MTR 原生地图的运行时长、停站顺序和发车数据，标为计划时刻估算；车站到发页保留原生接口给出的“实时／计划”标识。
- 因为此接口不公开完整的实际车辆身份，**行程进度需手动确认**。
- 没有 Overlay 也可以尝试直接从 MTR 原生服务查询全网；安装 Overlay 后优先复用它的车站位置。
- 不修改 Overlay 的显示或配置；地图区间高亮留给后续扩展接口。

### 服务端也安装 Navigation

自动使用 Navigation 的权威全网服务，保留 siding、班次循环和重复停靠身份。可可靠关联玩家所乘车辆时自动提示下车／换乘；不能确认时保留手动校正。

服务端配置 `config/mtrnav.properties`：默认查询未来六小时、最多四次换乘，步行按站台距离估算并预留 30 秒换乘余量。只允许同站和 MTR 显式连接的车站换乘。当前版本仅查询当前维度的铁路客运。

## 从源码构建

先设置 Java 21 的 `JAVA_HOME`。Windows 启动脚本在未设置时尝试本机的 `C:\Program Files\Java\jdk-21`。

```powershell
python tools/download_dependencies.py
.\dev.ps1 -Platform neoforge build testFabricCore
.\dev.ps1 -Platform fabric build
```

Linux/macOS 对应使用 `./dev.sh neoforge build testFabricCore` 和 `./dev.sh fabric build`。

- NeoForge：`build/libs/MTR-Navigation-neoforge-0.1.0.jar`
- Fabric：`fabric/build/libs/MTR-Navigation-fabric-0.1.0.jar`

请通过 `dev.ps1` 或 `dev.sh` 启动构建。这些脚本把 Gradle、依赖及工具缓存放在 `.cache`，临时文件放在 `.tmp`；构建和测试报告在 `build`／`fabric/build`，开发实例在 `run`／`run-server`。分发后的配置使用玩家自己的游戏实例目录。

开发运行：

```powershell
.\dev.ps1 -Platform neoforge runClient
.\dev.ps1 -Platform fabric runClient -PruntimeMtr
```

NeoForge 需要把指定 MTR JAR 放到 `run/mods`；Fabric 的 `-PruntimeMtr` 会把项目缓存中的 MTR 加入开发运行依赖。

## 后续地图整合

`io.github.teamcreating.mtrnav.api.NavigationApi` 提供当前不可变 `JourneySelection` 及选择、更新、取消事件。乘车段包含线路 ID、上下车站台 ID、停站序号和方向，保留重复站台及环线出现次序。

监听器在客户端线程调用，`listen` 返回可关闭的注销句柄。Overlay 不需要成为硬依赖。如果原生兼容路径尚未获得可靠的站台 ID，则不对外发布不完整的地图选择。

实测记录与边界见 [验证记录](docs/validation/verification.md)。许可证及复用代码署名见 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。
