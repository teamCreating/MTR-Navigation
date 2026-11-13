# v2 原生界面

本轮以用户提供的`docs/ui-preview/v2/index.html`为布局参考，将查询、到发和HUD调整到共用原生界面层。用户随后明确了这些要求：辅助操作改为图标，站名／站台／时间分层，顶部加米白像素横梁，字体跟随当前Minecraft，主体使用参考Create的深色纹理内衬。整屏橄榄绿色方案已否定并撤回。

## 当前实现

- 默认方案为中性石墨内衬，界面配色内部仍使用旧的`STEEL`标识以保留偏好兼容；界面显示名称为“Create 深色内衬”。原版灰、黄铜暗夜保留在配色切换中，不将它们作为本轮默认。
- 细纹理只用于阅读面板，摘要、选中票卡、线路标题、站台标签和步行提示使用不同明度。绿色与黄铜用于主要按钮；正文没有绿色色调。
- 全部正式页面通过`Minecraft.getInstance().font`取当前字体，包括输入度量、裁切、按钮和HUD。不强制缝合像素或Noto字体；早期字体对照仅保留在历史预览入口。
- 查询页宽窗口为票卡／时间轴双栏，窄窗口为列表／详情切换。1920×1080、有效2×时五条票卡完整可见。辅助图标保持18 GUI单位并提供完整提示；主要操作保留在页脚。
- 到发页使用线路分组与倒计时，点击班次可行内设置提醒。原独立提醒设置页仍可从“更多”访问；事件匹配、过期及刷新逻辑不变。
- HUD使用时刻进度轨道；仅有新鲜时刻且处于乘车阶段时计算时间比例，不把该比例当成列车实时空间位置。未知时用占位符。

`graphite_lining.png`是原创、周期性像素纹理，来源记录在相邻`graphite_lining.source.json`，可用`tools/ui/generate_graphite_lining.py`复现。顶部横梁与按钮直接用整数像素绘制。本轮正式界面不读取Create原贴图；正常四目标安装包排除`textures/gui/create`历史参考素材，LDLib2本地试验仍可读取。历史参考素材的许可仍见NOTICE和原来源记录。

## 隔离验证入口

```powershell
./dev.ps1 -Platform neoforge -MinecraftVersion 1.21.1 -GradleArgs @('runClient','-Pv2Review','-Pquickplay=NavTest','--offline')
```

Fabric可使用相同参数，只需切换`-Platform fabric`。游戏目录是`run/ui-review/<版本>-<加载器>-v2`，只使用明确标注的测试数据，不发真实查询。F5切换配色，F6打开HUD设置，F7切换五方案、长站名、空结果、无班次、到发、HUD与进行中场景。

运行开发客户端与多版本构建需要串行。Gradle切换同一工程的构建输出目录时会清理旧类目录，不能让开发客户端继续依赖正在清理的目录。

正式四目标构建命令为`tools/build_all.ps1 -Offline`。具体构建、截图与未覆盖的检查见`verification.json`；前一轮中间结果保留为`interim-checks.json`，不能作为最终配色的验证记录。

用户Axolotl实例未替换。首轮v2前的源文件回退点位于`build/ui-backups/before-v2-20261113`；本轮不提交或公开发布。
