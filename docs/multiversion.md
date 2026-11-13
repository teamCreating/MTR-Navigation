# 多版本构建

开发分支以 MTR 4.1.0-beta.2 的发布范围为目标：Minecraft 1.21.1 和 1.21.4，分别生成 NeoForge、Fabric 安装包。目标配置不代表已经完成游戏内验收；公开 Alpha 附件的支持范围仍以其发布说明为准。

MTR 的 [Stonecutter 分支配置](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/stonecutter/settings.gradle.kts)同样按 Minecraft 版本与加载器分别构建。本站间物语工程保留现有 Gradle 入口，共享 `src/main` 的导航、协议、存储和界面逻辑；不同 Minecraft API 的实现放在 `src/minecraft/<版本>/java`，加载器入口分别保留在现有 NeoForge、Fabric 目录。每次编译只加入一个 Minecraft 适配目录。

`gradle/minecraft-targets.json` 是 Minecraft、MTR、NeoForge 和 Fabric 依赖版本的统一配置。新增目标必须同时完成 API 适配、构建和验证，不能只放宽模组声明里的版本要求。

## 构建

需要 Python 3.11 或更新版本、PowerShell 7、Java 21。工具和依赖缓存继续使用项目 `.cache`；临时文件在 `.tmp`。

```powershell
# 下载对应 MC 版本的两个官方 MTR 依赖，按官方 SHA512 校验。
python tools/download_dependencies.py --minecraft 1.21.4

# 单独构建一个目标；不指定 MinecraftVersion 时仍构建 1.21.1。
./dev.ps1 -Platform neoforge -MinecraftVersion 1.21.4 -GradleArgs @('test', 'testFabricCore', 'jar')
./dev.ps1 -Platform fabric -MinecraftVersion 1.21.4 -GradleArgs @('build')

# 顺序构建所有目标，运行两个 MC 版本各自的 NeoForge/Fabric MTR Core 回归。
./tools/build_all.ps1 -DownloadDependencies

# 依赖缓存已齐备时可离线复建。
./tools/build_all.ps1 -Offline
```

原有 1.21.1 构建产物位置保持兼容。其他版本写入 `build/targets/<MC版本>-<加载器>`；统一构建脚本将经过检查的包复制至 `build/multiversion`，文件名带 MC 版本，并生成含 SHA256 的 `manifest.json`。脚本检查加载器入口、MC 依赖声明、模组版本、Java 21 字节码及 Mixin 文件完整性，避免混用不同版本的包。

不同 MC 版本使用独立开发游戏目录；1.21.1 沿用原目录。此构建流程不会安装或替换玩家实例里的任何模组。

## 兼容边界

多版本构建不等于一个 JAR 通吃所有 Minecraft 或加载器版本。玩家应选择文件名与实例相符的安装包。

本轮目标仍是 MTR 4.1.0-beta.2。MTR 3.x、MTR 4.0.x、其他 4.1 测试版和第三方修改版没有因此获得兼容承诺；这些版本的数据结构和协议需独立验证。客户端与服务端跨 MC 联机也不在此改动范围内。

构建通过只能证明编译、包结构及所运行的核心回归通过。实际字体、界面操作、客户端原生 MTR 查询、专服连接及不同加载器之间的协议互通需要分别验收。
