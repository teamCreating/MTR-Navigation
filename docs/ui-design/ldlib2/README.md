# LDLib2 主导航试点

用户于2026-11-12同意先验证MC1.21.1 NeoForge的LDLib2主导航。之后明确要求将三种风格都做出来比较：当前原版MC控件、LDLib2编辑器外层、Create材质。三版共用内容、布局、控件尺寸和字体，不把其中任何一版提前视为已选方案。

## 运行

先运行`python tools/ui/fetch_ldlib2.py`获取固定官方依赖，再使用：

```powershell
./dev.ps1 -Platform neoforge -MinecraftVersion 1.21.1 -GradleArgs @('runClient','-Pldlib2Preview','-Pquickplay=NavTest','--offline')
```

仅这个参数启用`src/ldlib2`源码与资源，构建目录为`build/targets/1.21.1-neoforge-ldlib2`，游戏目录为`run/ui-review/1.21.1-neoforge-ldlib2`。正常四目标构建没有LDLib2编译或运行依赖。

## 三种对照

- **原版控件**：通过`VanillaSpriteTexture`使用当前游戏的`minecraft:widget/button`、输入框和页签，以及原版悬停/禁用贴图。布局由LDLib2运行，不能称为原版`Screen`/`Button`实现；原版资源包可以影响这些贴图。
- **编辑器风格**：使用LDLib2内置MC控件材质，灰色框体、内嵌内容区、凹凸按钮和绿色选中提示。
- **Create材质**：本地参考试验使用此前已保存的Create贴图，配深色衬里、纸质输入框和Create按钮。源文件与上游许可见主项目`NOTICE`及`assets/mtrnav/textures/gui/create/SOURCES.json`。没有据此主张公开分发许可。

F5或顶部风格按钮切换三版；F6切换中文字体；F7切换五方案、长站名、无结果、无班次。F8重载当前稿；F9打开可视化模板；F10打开XML编辑器。所有查询和导航按钮均使用标明的测试数据。

## 可编辑文件

`src/ldlib2/resources/assets/mtrnav/ui/ldlib2/navigation-{native,editor,create}.xml`分别保存三种独立可编辑稿。`navigation.xml`是共用初始布局；`tools/ui/build_ldlib2_variants.py`生成三份对照文件，手动编辑后不要无意重新生成覆盖。

可视化编辑器第一次打开某种风格时，会在同目录生成对应的`navigation-<风格>.ui.nbt`。三套模板现已实际保存并重新加载验证。保存按钮写入这个原生LDLib2模板，返回时加载保存稿并重新绑定交互。模板与XML是两条编辑路径，模板修改不会反向写入XML。切换风格会加载对应XML；关闭后重新按N打开则从原版控件XML开始。使用F9再打开已保存模板。

字体未定稿；三版默认使用同一套Noto中文候选与MC英文数字进行比较。字体服务、编辑光标及Modern UI组合仍需按实际截图与交互检查验收。

## 范围与兼容

本试点固定LDLib2 2.2.46.a，来源及SHA256在`dependency.lock.json`。仅确认官方1.21分支的MC1.21.1 NeoForge开发包；没有宣称LDLib2可直接用于项目另外三个构建目标。原有多版本导航代码和网络协议保持原样。

隔离实例分别运行过MTR，以及MTR＋Modern UI 3.13.0.1＋复制的用户字体配置。用户Axolotl实例只读，没有替换模组或配置。

三套1920×1080对照和854×480窄窗口截图保存在本目录的`screenshots`，均为游戏F2截图。具体通过项和待检查项见[验证记录](verification.md)；文件哈希与构建结果见`verification.json`。
