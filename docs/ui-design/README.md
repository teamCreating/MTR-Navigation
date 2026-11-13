# 当前方向：全页面 Ore UI

用户已授权正式页面统一为 Ore UI、接真实数据并保持当前 MC 字体。实现与实际检查记录见 [Ore UI 版本](ore/README.md)。以下为此前对照和探索记录。

# UI 设计与字体对照

2026-11-13 新增 [AE2 / 基岩版 / Java 九套原生对照与融合候选](paradigms/README.md)。本轮使用独立 `-PparadigmPreview` 入口比较查询、选站和 HUD；正式 v2 与已选定的跟随 MC 字体保持原样，视觉选择后再迁移。

本目录保存可编辑设计、组件规范和验证记录。2026-11-13用户提供v2参考并要求修改正式界面，随后选定“字体跟随MC”和“参考Create的深色纹理内衬”。最新实现与记录见[v2原生界面](v2/README.md)。以下Pencil、字体A/B与LDLib2部分是此前探索记录，不代表当前正式页面的字体或默认配色。

最新方向：用户要求把原版MC控件、LDLib2编辑器风格和Create材质都做成游戏内对照，再选择方案。三套独立XML与可视化模板、运行方式及实际检查记录见[LDLib2试点](ldlib2/README.md)。该试点仅启用于1.21.1 NeoForge；下述原配色是此前的候选稿，不代表用户已经选定。

设计约束以 `src/main/resources/assets/mtrnav/ui/design.json` 为准：2 GUI单位网格、4/8单位间距、18单位图标按钮、20单位标准控件、12单位行距。米白用于标签和编辑区，黄铜用于层级及选中提示，深色用于正文，木色只出现在框体结构。组件采用原创整数像素绘制，不将Create原贴图作为本轮新设计的分发素材。

字体对照仅改变中文，英文数字沿用Minecraft默认字体：A为缝合像素字体10px简中比例版，B为Noto Sans SC Regular。两版共用同样内容和布局，必须检查实际游戏中的基线、输入光标、选区及长站名，不能凭网页字体截图定稿。

通用UI设计辅助的检索结果仅用于排版、对比度和交互检查。其网页落地页、霓虹街机、44像素触摸目标等建议不适用于本项目的MC原生GUI单位；以本目录规范和用户反馈为准。

Pencil CLI固定为0.3.15，桌面版1.2.22安装在用户指定的`D:\Apps\Pen`。用户本人登录后，已验证画布读取、组件修改、PNG导出和项目文件保存；账号凭据不进入本项目。可编辑文件为`mtrnav.pen`，包含6个共享组件及7块画板。Pencil版式稿使用Noto Sans SC，字体选择以`exports/game`的实际游戏对照为准。

画板包括组件规范、五方案结果、长中英文站名、无结果、线路参考无可乘班次、HUD编辑和854×480窄窗口。`exports/pencil`是Pencil导出，`exports/game`是游戏F2截图，两者均明确使用测试数据。

## 本地预览

使用`dev.ps1 -Platform neoforge -MinecraftVersion 1.21.4 -GradleArgs @('runClient','-PuiPreview','-Pquickplay=NavTest','--offline')`打开隔离预览。F6切换字体，F7切换场景。此入口只在显式启用`uiPreview`时生效，沿用本模组原生输入框和键盘焦点；不连接真实查询或修改正式偏好。

测试目录固定为`run/ui-review/<MC>-<加载器>`，窗口标题为`MTRNav UI Review <MC>-<加载器>`。工具只给该精确标题发送操作，不使用全局鼠标，不修改Axolotl实例。实际测试及尚未验收的项目记录在`verification.md`。

字体来源、许可和哈希见`src/main/resources/assets/mtrnav/ui/font-sources.json`，许可全文位于`font/ui`。Noto使用官方TrueType源固定常规字重400，避免Minecraft无法加载CFF格式的问题。字体下载可用`tools/ui/fetch_fonts.py`复现；需FontTools 4.61.1。

四个构建目标保持不变。设计未确认前，四个包包含可选预览实现，正常打开仍使用原正式页面；安装包并不代表本轮视觉已获批准。
