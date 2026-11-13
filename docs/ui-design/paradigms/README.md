# AE2 / 基岩版 / Java UI 对照

2026-11-13 本轮在独立原生预览中实现三种方向的查询、选站、HUD，共九套界面，并增加同样三页的融合候选。当前正式 v2 的石墨内衬、用户选定的跟随 MC 字体、历史 LDLib2 与 Create 对照均保留。本轮不会因切换预览写入正式偏好。

## 打开预览

```powershell
./dev.ps1 -Platform neoforge -MinecraftVersion 1.21.1 -GradleArgs @('runClient','-PparadigmPreview','-Pquickplay=NavTest','--offline')
```

四目标均支持：MC 1.21.1 / 1.21.4，NeoForge / Fabric。专用目录为 `run/ui-review/<版本>-<加载器>-paradigms`，窗口标题为 `MTRNav UI Review <版本>-<加载器>-paradigms`。需要项目 NavTest 世界；验证工具只从本项目已有隔离实例复制，不读取或操作用户游戏安装。

- F5：AE2 → BE → Java → 融合，保留输入、车站与方案选择、各自滚动位置和本次 HUD 设置。
- F6：切换缝合像素 / Noto Sans SC 中文；英文数字沿用 MC。默认 Java 方向、像素中文，正式页面仍跟随当前 MC 字体。
- F7：五方案、长站名、空结果、加载中、失败、无可乘班次、多方案滚动。
- F8：查询、选站、HUD、组件状态。F9 在窄查询页切换列表与详情；F10 进入组件状态。
- 原生 Tab / Enter 与输入选区可用；搜索到唯一车站后 Enter 返回选站结果。Esc 从子页面返回查询，再按退出。
- HUD 滚轮调整所选框大小；拖动或 Shift + 方向键定位；Ctrl + S 保存到当前预览会话。重新启动预览会重置测试设置。

可用 `-PuiPreviewVariant=ae2|bedrock|java|hybrid` 与 `-PuiPreviewFont=pixel|smooth` 指定初始对照，也可通过 `-PuiPreviewScene=normal|long_names|empty|loading|error|reference|many` 指定场景。旧 `-PuiPreview`、`-Pv2Review` 与 `-Pldlib2Preview` 入口不变。

## 区别与共用能力

- AE2：对照官方 1.21.1 资源的冷灰紫框体与深色凹入输入框，18 单位侧边工具条、相邻搜索与文本结果；工具含完整提示与原生焦点。
- BE：只读核对本机基岩版 1.26.6301.0 的真实 Ore UI 资源，使用浅色页首、中灰主体、扁平侧栏、独立页签、带深色底座的主/次按钮，以及真正可操作的开关、滑块与分段选择。
- Java：运行时引用当前游戏的按钮、输入框、高亮、禁用和四种页签贴图，跟随原版选中下划线与非选中页签文字偏移；列表使用原版式白边选择。
- 融合：Java 原生按钮，紧凑查询工具区和两栏结果，BE 式分组与边缘选中标记；框体与内容采用中性灰。

所有方向共用同一份测试数据和输入状态。查询支持起终点、交换、选站、附近、排序、列表/详情独立滚动、经停展开、模拟开始导航；到发和当前行程沿用同一页面框架。选站独立返回，支持中英文搜索、距离/字母排序、字母索引、附近筛选。HUD 使用生产已有避让算法，导航与提醒独立缩放/定位，设置区和预览区分离。

预览实现与正式界面的全局调色、查询、导航追踪和用户偏好相互独立。新增数据类型仅为客户端预览内部用途；没有修改服务端协议或正式偏好格式。Java 控件纹理从运行中的游戏读取；AE2 / BE 的像素框体为原创绘制，未打包上游 UI 贴图。

## 验证与交付

`components.md` 记录组件和布局规则，`source-review.md` 记录原始资源核对与修正，`verification.md` 记录本轮实际检查。`gallery.html` 由实际截图记录生成，按页面和方向筛选，点击查看完整 F2 截图。每张图片相邻的 `.audit.json` 保存实际控件坐标、缩放、状态和可见行数；不是网页模拟截图。

```powershell
python tools/ui/run_paradigm_review.py --target 1.21.1-neoforge --full
python tools/ui/run_paradigm_review.py --target 1.21.1-fabric
python tools/ui/run_paradigm_review.py --target 1.21.4-neoforge
python tools/ui/run_paradigm_review.py --target 1.21.4-fabric
./tools/build_all.ps1 -Offline
```

开发客户端与四目标构建串行运行，避免 Gradle 清理另一个目标正在使用的类。实际通过项记录在各目标的 `*-verification.json`；构建结果见 `build-verification.json`。中文字符输入检查不能替代系统输入法候选窗/组合输入验收。融合方案和字体仍需用户实际体验后选择，之后再接入正式核心页面。
