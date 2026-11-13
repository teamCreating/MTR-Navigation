# 车型功能检查记录

2026-11-14：四目标构建与四组 MTR Core 回归通过，每组 128 项（125 通过、3 项按条件跳过），共 500 通过、12 跳过。包括延后班次车型匹配、混合编组、接驳、偏好窗口、编组复核、旧数据默认值和有序行程链。

真实运营验证使用项目独立的 VehicleReview 测试存档；MTR 自身生成三组车库、六条有向线路及数千个原生停站事件，车型包括 M-Train、SP1900 和混合编组。1.21.1 NeoForge 的完整检查记录见 [JSON](1.21.1-neoforge.json)，包含必须车型、优先车型、清空条件、混合编组、导航开始、到发、HUD 三档缩放及退出恢复 GUI 缩放；此实例安装 Modern UI。

其余三个目标已完成构建和对应 MTR Core 回归；新增车型交互的完整图形客户端检查尚未完成，不将编译当作该项实机验收。之前四目标 Ore UI 检查属于旧的界面验证记录，见 [Ore UI](../../ui-design/ore/README.md)。

README 的新截图来自「苔灯群岛」虚构存档场景，名称与时刻均为演示数据，拍摄记录见 [capture.json](../../screenshots/0.1.1/capture.json)。这些截图用于介绍界面，不替代真实班次验证。

独立生成运营世界：`./dev.ps1 -Platform neoforge -MinecraftVersion 1.21.1 -GradleArgs @('test','--tests','*VehicleRuntimeFixtureTest','-PgenerateVehicleFixture','-PfixtureRoot=build/fixtures/vehicles-release','--offline')`。目标必须是不存在的新目录；不会覆盖现有测试存档或用户存档。

运行正向流程：`python tools/ui/run_vehicle_smoke.py --target 1.21.1-neoforge`。截图工具只在明确启用的隔离游戏中分发原生事件并读取实际帧缓冲，不操作其他游戏窗口。中文输入法组合态、专服多客户端、手柄与触屏不在这份记录的验证范围。
