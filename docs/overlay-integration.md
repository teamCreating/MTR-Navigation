# MTR Map Overlay 对接指南

本文对应 MTR：站间物语（MTR Navigation）0.1.0 的 `JourneySelection` **v1**。目标环境为 Minecraft 1.21.1、MTR 4.1.0-beta.2，NeoForge 与 Fabric 共用此接口。

源码仓库：[teamCreating/MTR-Navigation](https://github.com/teamCreating/MTR-Navigation)。本文描述当前已实现的接口；地图区间高亮仍需要 Overlay 侧实现。

## 两个模组各自负责什么

Navigation 负责车站搜索、时刻查询、换乘方案和用户选择的行程。Overlay 负责地图数据、轨道几何及地图显示。Navigation 不引用 Overlay 内部类来发布行程，也不会更改其地图渲染。

当前还有一条独立的只读兼容路径：`client/OverlayBridge.java` 通过反射读取 Overlay 1.5.2 已同步的 `MapDataCache`，获取原生车站 ID、名称与坐标。它不会主动触发 Overlay 全量同步，也不会把地图快照当成班次数据。该桥接依赖的内部结构不属于双方长期公共协议；Overlay 升级时建议复核。

车站快照、去重后的地图轨道和列车位置不足以恢复完整班次、重复停靠及到发时刻。Navigation 的时刻仍来自 MTR 原生接口或 Navigation 服务端。

## 接入点

公共源码：

- [`NavigationApi.java`](../src/main/java/io/github/teamcreating/mtrnav/api/NavigationApi.java)：读取当前选择、注册监听、注销。
- [`JourneySelection.java`](../src/main/java/io/github/teamcreating/mtrnav/api/JourneySelection.java)：不可变 v1 行程与有序乘车段。
- [`NavigationCoreTest.java`](../src/test/java/io/github/teamcreating/mtrnav/NavigationCoreTest.java)：两个精确乘车区间及取消事件的契约测试。

Overlay 应将 Navigation 作为可选依赖。在检测到 `mtrnav` 后，才加载引用这些类的独立客户端适配器，避免缺少 Navigation 时触发类加载错误。开发时可以使用对应加载器 JAR 的 `compileOnly` 文件依赖；当前没有发布单独的 Maven API 包。不要将 Navigation 类打包到 Overlay，也不要把另一加载器的 JAR 加入运行环境。

## v1 数据字段

`JourneySelection` 包含：

- `version`：目前为 `1`。不支持的版本应清除或跳过高亮。
- `dimension`：MTR 维度标识，形如 `minecraft/overworld`；不要直接当作 `minecraft:overworld` 比较，应明确转换命名空间与路径。
- `topologyRevision`：Navigation 自身目录的拓扑修订。用于判断同一来源的选择是否失效；它与 Overlay 的快照版本不共用编号，不应直接比较数值相等。
- `journeyId`：此行程的标识。重新选择方案会产生另一行程；不要用站名或线路名充当身份。
- `segments`：有序且不可修改的乘车段列表。为空可能表示同站行程，Overlay 应显示为空选择。

每个 `Segment` 包含：

- `routeId`：MTR 原生线路 ID。
- `fromPlatformId`、`toPlatformId`：上下车站台的原生 ID。
- `fromStopIndex`、`toStopIndex`：该线路有向停站序列中的**从零开始**的停靠序号。
- `direction`：展示用乘车方向，不能作为可靠身份或几何匹配键。

生产数据的原生 ID 使用无符号十六进制字符串，小写且不保留前导零。转换为数值时可使用 `Long.parseUnsignedLong(id, 16)`；避免用有符号十进制解析。若 Overlay 使用 `station:<id>` 等带前缀标识，应先分离对象类型，再规范化 ID。

v1 不包含轨道 ID 列表、车辆身份、当前活动段索引或各段到发时间。请勿根据 `UPDATED` 次数推断乘车进度，也不要从此接口构建到发看板。

## 事件及生命周期

`NavigationApi.current()` 返回 `Optional<JourneySelection>`，可供 Overlay 刚打开地图或刚完成适配器注册时恢复当前选择。

`NavigationApi.listen(listener)` 返回 `AutoCloseable`；关闭句柄即可注销。监听回调在客户端线程同步执行，包含：

- `SELECTED`：从无选择变为有选择。
- `UPDATED`：已有选择被替换或重新发布；内容可能相同，不保证每秒一次。应按不可变选择内容判断是否重算几何。
- `CANCELLED`：选择已清除，传入的 Optional 为空。重复取消空选择不会再发事件。

关闭查询窗口不会取消导航。取消导航、断线、切服或切维度会清理选择；地图应同时监听自己的连接生命周期，清除旧的几何缓存。完成最后一段后，行程仍可留在界面中，直到用户结束导航；v1 没有单独的“行程完成”事件。

监听器应只更新状态或提交后台映射任务，不要在回调里进行重型轨道遍历、网络请求或调用 `select`／`cancel`，以免阻塞客户端或产生递归事件。若在后台计算轨道，完成后回到客户端线程，并核对维度、行程内容与 Overlay 自身快照版本；过期结果必须丢弃。

接入逻辑示例（`applyToMap`、`clearMapSelection` 为 Overlay 自行实现的方法）：

```java
private AutoCloseable navigationListener;

void attachNavigation() {
    // 仅从检测到 mtrnav 后加载的客户端适配器调用。
    navigationListener = NavigationApi.listen((change, selection) -> {
        if (change == NavigationApi.Change.CANCELLED || selection.isEmpty()) {
            clearMapSelection();
        } else {
            acceptSelection(selection.get());
        }
    });
    NavigationApi.current().ifPresent(this::acceptSelection);
}

void acceptSelection(JourneySelection selection) {
    if (selection.version() != 1 || selection.segments().isEmpty()) {
        clearMapSelection();
        return;
    }
    applyToMap(selection); // 校验维度、线路顺序、站台身份后再映射轨道。
}

void detachNavigation() throws Exception {
    if (navigationListener != null) {
        navigationListener.close();
        navigationListener = null;
    }
    clearMapSelection();
}
```

导入类位于 `io.github.teamcreating.mtrnav.api`。实际接入还应防止重复注册，并在适配器销毁时注销。Overlay 无需调用公共的 `select`／`cancel`；它们由 Navigation 自己的行程管理逻辑维护。

## 精确区间映射规则

逐段使用 `routeId + fromPlatformId + toPlatformId + fromStopIndex + toStopIndex` 查找线路有向区间。上下车序号均对应停站出现位置，终点停靠也包含在区间中。先核对线路停站数组在对应序号处的 ID 与段字段一致，再映射相邻停靠之间的实际轨道。

必须保留停站顺序，不得按站台 ID 去重或排序。环线、往返及重复停台可能在同一条线路上多次出现相同 ID；仅取首个匹配会高亮错误方向。若几何缓存缺少有向轨道关联，应显示站点或提示无法高亮，不要用两站之间的几何最短路径代替乘车区间。

目录修订、Overlay 快照更新或线路找不到时，使旧映射失效并重新校验。一个站名相同的其他车站、附近未连接车站、其他线路经过的同名站台，都不能代替原生 ID 匹配。

契约样例是“滨海北乘 C2 到南苑，再换 2 号线到甘地”：

1. 第一段只包含 C2 的滨海北上车停靠 → 南苑下车停靠。
2. 第二段只包含 2 号线的南苑上车停靠 → 甘地下车停靠。
3. 南苑的换乘步行不属于任一段的乘车轨道，不能把两条线路的轨道拼成一条随意最短路径。
4. 用户取消导航后，两段高亮均清除。

上述契约由 `exactTwoLegMapContractAndCancel` 测试覆盖。测试中的简写 ID 是夹具标识，接入真实地图应使用事件实际提供的原生 ID，不要硬编码站名或样例线路号。

## 原生兼容模式与线路参考的边界

只有 MTR 的服务端也能提供原生地图时刻，但该接口未必提供完整原生站台 ID。此时 Navigation 内部会显式使用 `map:<routeId>:<stopIndex>` 占位，待获得可靠原生身份后再补齐。

**含占位站台 ID 的行程不发布到 NavigationApi。** 若原先有可发布选择但新行程身份不完整，会取消旧选择，防止地图继续显示不对应的行程。Overlay 不应自行把占位 ID 转换成猜测的原生 ID。

“线路参考”仅说明拓扑上存在有向换乘路径，可能没有当前可乘班次、超过查询上限或时刻计算尚未完成。它没有班次到发时间，不允许直接启动班次导航，也不会发布为 `JourneySelection`，因此不会替换已有活动导航的地图选择。

原生兼容路径要求用户手动确认上车／下车；预计到站不等于已识别玩家所乘列车。即使收到可发布的选择，也不能据此推断实际车辆 ID 或玩家当前乘车位置。

## 建议的联调清单

- 两个模组分别缺席时，另一个仍能正常加载。
- 两段样例准确高亮，切换方案后替换，取消后清除。
- 环线相同站台重复出现，序号不同仍映射到正确方向。
- 同名不同 ID 的车站不混用；连接车站的步行不当作乘车轨道。
- 断线、切维度、目录变化、几何后台任务迟到时不残留旧高亮。
- 未解析的原生站台身份不高亮；线路参考不覆盖活动导航。
- 地图打开／关闭不额外触发 Navigation 或 Overlay 全量网络同步。
- 监听器异常不会影响其他监听器，注销后不再收到事件。

当前自动测试验证了公共选择契约和读取 Overlay 1.5.2 快照；Overlay 侧轨道映射与高亮还未实现，不能将契约测试视为两个模组已完成实际地图联动。
