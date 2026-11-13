# 原始资源复核与修正

2026-11-13 用户指出首版 Ore UI 元素不准确，随后要求复核其他方向。本文件记录本轮实际对照来源；功能测试通过与视觉选择分开记录。

## AE2

参考 AE2 官方仓库 `1.21.1` 分支的 `terminal.png`、`text_field.png`、`states.png` 和 `screens/terminals/base_terminal.json`。终端与输入框参考文件分别为：

- `terminal.png` SHA256：`9ce91eccf149e1703960906b349093af726e6deb753985c7e85b5d0db359b3e4`
- `text_field.png` SHA256：`73bba41174d3ec15d83947e439915873611735fe436ad0cbc7653eca15e23ad1`

源纹理的主要颜色为 `#CBCCD4`、`#ADB0C4`、`#9A9FB4`、`#413F54`，输入框使用 `#4D4D67`。首稿的中性灰外框与黑输入框不准确，现改为冷灰紫、浅边和凹入输入层次。侧边工具仍保持18单位；结果为适合站名和路线的文本列表，没有将物品槽硬套到导航内容。

官方来源：[终端布局](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1.21.1/src/main/resources/assets/ae2/screens/terminals/base_terminal.json)、[GUI 资源目录](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/1.21.1/src/main/resources/assets/ae2/textures/guis)。参考文件只在 `.cache/ui-references/ae2/1.21.1`，不进入安装包。

## 基岩版 Ore UI

官方创建世界设计文章的实际截图作为页面参考，同时只读核对本机 `Microsoft.MinecraftUWP_1.26.6301.0_x64` 安装中的 `data/gui/dist/hbui`。核对内容包括菜单主题、基础输入框边框、主/次按钮状态、页签、颜色、开关与滑块。没有启动或修改用户的基岩版游戏。

本机主题中确认的基础颜色：主操作 `#3C8527`，主操作底座 `#1D4D13`，次按钮 `#D0D1D4`、底座 `#58585A`，主体 `#48494A`，输入/侧栏 `#313233`，深边 `#1E1E1F`，浅色页首 `#E6E8EB`，提示/滑块 `#6CC349`。

首稿把侧栏、选项和开关全部画成灰色按钮，次按钮底边也错误地染绿。现在分别实现扁平侧栏、下划线页签、主/次按钮各自的底座与按下状态、内凹输入框、开关、真正可拖动的原生滑块、分段选择及字母索引菜单。HUD 的显示、位置、行为分组各有清晰标题。

官方页面参考：[Creating “Create New World”](https://www.minecraft.net/en-us/article/creating-create-new-world)。本机 Ore UI 的 JavaScript、字体与贴图均不复制进本项目资源包；新的框体与部件由整数像素代码绘制。预览统一使用 MC GUI 单位和共同的中文候选字体，因此属于按真实组件规则适配的设计对照，不声称是基岩版页面逐像素复刻。

## Java

只读核对本机官方 `minecraft_1.21.1_client.jar` 的按钮、输入框和 `widget/tab*` 资源，以及对应 `TabButton` 的文字布局。修正首稿把页签当作按钮、给普通主要按钮添加蓝色下划线、列表任意使用蓝色选中块的问题。

当前 Java 方向引用游戏运行时的四种页签贴图、三种按钮状态和原生输入框。选中页签带原版式文字下划线，非选中页签的文字下移；列表使用白色选框。1.21.4 通过版本兼容层读取该版本的同名资源。

## 融合与内容一致性

融合方案是本项目候选，不标作任何上游原生界面。其按钮和页签采用 Java 资源，工具区与状态共享对照实现，HUD 借鉴 Ore UI 的分组、开关和滑块。

另外纠正了首稿中“直达方案的详情仍显示换乘”的测试内容问题：详情现在按选中方案的换乘次数和线路显示，抵达时间由相同的出发时间与方案分钟数计算。无班次场景继续标明只作线路参考并禁用导航。

最终游戏截图、边界记录和通过项见同目录各目标的验证 JSON；正式迁移仍等待用户选择方案和字体。
