# 谐振核心与扩展封包供应器：Forge 1.20.1 移植验证

后续[全面测试记录](COMPREHENSIVE_TESTING.zh-CN.md)包含客户端分页缺陷修复、真实界面检查及正式发行 JAR 的存档验证；本文件下方保留最初移植验证结果。

本轮将上游 `alpha` 的 [94a5660](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/94a566039c0af1326b16c9437d3e135eb06b1f0b)
适配到 Minecraft 1.20.1 / Forge。该提交的父版本为 `df98635f90e784b9f332d7abb505afc072072eec`，
2026-10-15 收尾时远端 `alpha` 仍指向此提交。保留项目 LGPL-3.0 许可证与原作者署名。
本轮验证使用当前合并工作区，也包含 [main 移植记录](UPSTREAM_BACKPORTS.zh-CN.md) 中的配方、批量与数量修复。

## 同步内容

- 谐振核心保存 144 个输入栏位和 36 个输出口的相对位置、接口方向及锚点方块类型；布局随锚点水平朝向旋转。
- 支持普通处理样板，以及水晶科技真实谐振样板中的显式输入目标。使用 Forge 1.20.1 水晶科技 1.2.0 验证。
- 一次加工只选择一套完整机器布局。部分接收后的余料按原位置继续补发，跨重载、更换核心和模式切换保留归属。
- 扩展封包供应器保存 144 张样板，分四页显示，可切换相邻和无线派发；原有供应器保持各自容量与模式。
- 中英文指南已接入索引；游戏内操作见 [谐振布局](src/main/resources/assets/ae2ltpp/ae2guide/_zh_cn/resonant.md)。

## Forge 适配及已修复问题

1. 上游 Data Components、网络载荷及 capability API 属于 1.21.1。改用 ItemStack NBT、Forge SimpleChannel 和 1.20.1 capability；配方与掉落使用本平台目录和序列化格式。
2. 隐藏页仍能被服务端取放：AE2 1.20.1 的 `setActive(false)` 只控制显示。现在按页设置可见性和编辑权限，并在 `quickMoveStack` 入口拒绝隐藏槽位的区域快捷移动。保留槽位启用状态，让隐藏页继续接收库存同步，避免翻页时出现过期内容。GameTest 验证隐藏页禁止取放、直接点击与区域快捷移动，同时仍能接收库存更新。
3. 已接收加工的余料未进入破坏掉落：将尚未投出的材料加入掉落，并在方块移交掉落后清除其归属。原生测试验证 8 个铁锭仅接收 3 个时恰好掉落剩余 5 个，重复查询不会再次生成余料；普通重置仍保留余料。
4. 无线锚点不能仅依赖机器适配器识别。为谐振布局保存锚点类型并持久化，卸下核心后保留连接，目标被替换成其他方块时拒绝继续补发。
5. 水晶科技使用可选反射入口读取稀疏输入和位置标记，未安装该模组也能加载。真实模组测试验证显式目标覆盖默认绑定、相对重定位以及跨维度标记拒绝。
6. 翻页需要重用首屏槽位坐标。发行 JAR 包含最小 Slot x/y Access Transformer，并已检查重混淆字段名。

上述产品问题均先说明原因再修正。验证过程中另修复测试模板命名、源码提取测试的辅助方法适配，以及隔离运行目录中的模板复制位置。
不同模组组合不能共用已有测试世界：移除 Blood Magic 后读取含其维度的世界会提前退出，甚至仍显示 Gradle 成功。
因此必须同时检查 GameTest 的完成数量和通过行。脚本现在支持 `ppTestRunDir` 同步指定运行目录及模板目录。

## 2026-10-15 验证结果

环境：Java 17.0.20、Forge 47.4.10、AE2 15.4.10、AE2LT 2.1.2-beta、Thunderbolt 2.0.3-beta。

| 检查 | 结果 |
| --- | --- |
| 完整集成与 Avaritia JUnit | 344 项，75 个套件；0 失败、0 错误、0 跳过 |
| 完整集成 GameTest，未安装水晶科技 | 116 项 required 全通过；含既有 98 项及本轮 18 项，其中 2 项水晶科技专用测试提前结束 |
| 安装水晶科技 1.2.0 的独立 GameTest | 18 项 required 全通过，真实水晶科技测试实际执行；18:58:53 完成 |
| 发布脚本 | 12 项通过，Forge 发布仓库解析回归通过 |
| 构建产物 | `jarJar` / `reobfJarJar` 成功；无重复条目，未打包本轮测试类或测试模板 |
| 资源及差异检查 | 102 个主资源 JSON 可解析，`git diff --check` 通过 |
| 独立测试目录配置 | 运行目录、SNBT 模板复制及独立 sourceSet 检查通过 |

测试还覆盖物品和流体容量预留、重复原料不同端口、异常插入、防止重复投料、锁定、输出回收、未加载目标、144 槽存取与持久化，以及模式切换后的原始投料目标。

本轮未启动客户端目视检查新增分页、模式按钮和潜行滚轮交互；这些客户端代码已编译，菜单行为通过服务端测试。
测试覆盖范围内未发现未解决的失败，尚不能据此保证所有第三方模组组合都没有问题。

## 复现

使用 Java 17。下列本地依赖目录需已准备好对应 Forge JAR；水晶科技使用其 **Forge 1.20.1 开发 JAR**，不随本项目分发。
各项 Gradle 命令顺序执行，避免共享编译目录在游戏运行时变化。

```powershell
$verificationArgs = @(
  '--no-daemon', '--max-workers=1', '--console=plain',
  '-Puse_local_upstream=true',
  '-Pae2lt_build_libs=build/test-dependencies/libs',
  '-Pthunderbolt_build_libs=build/test-dependencies/libs'
)

.\gradlew.bat test jarJar reobfJarJar @verificationArgs `
  -Penable_avaritia_tests=true -Penable_integration_runtime=true `
  -Penable_chiseling_runtime=true `
  -Pchiseling_reference_maven=build/test-dependencies/runtime-maven

.\gradlew.bat runGameTestServer @verificationArgs `
  -I scripts/resonant-test.init.gradle `
  '-PppTestNamespaces=ae2ltpp,ae2ltpp_resonant' `
  -PppTestRunDir=build/resonant-verification/run-full `
  -Penable_integration_runtime=true -Penable_chiseling_runtime=true `
  -Pchiseling_reference_maven=build/test-dependencies/runtime-maven

# 将下面的路径换成已校验的水晶科技 Forge 1.20.1 开发 JAR。
.\gradlew.bat runGameTestServer @verificationArgs `
  -I scripts/resonant-test.init.gradle `
  -PppTestRunDir=build/resonant-verification/run-crystal `
  '-PppCrystalScienceJar=<ae2cs-1.20.1-1.2.0-dev.jar 的完整路径>'

python .github/scripts/test_release.py
python .github/scripts/test_forge_release_repositories.py
```

依赖完整缓存后可加 `--offline`。本轮离线运行另关闭 ForgeGradle 的启动连通性探测：`-Dnet.minecraftforge.gradle.check.certs=false`。
普通默认构建不启用 `resonantTest`，也不会装入水晶科技测试 JAR。

## 本地产物

验证产物与日志归档在被 Git 忽略的 `build/resonant-verification/`；`VERIFICATION.md` 记录本轮日志及发行 JAR 校验值。
发行文件为 `ae2ltpp-forge-1.20.1-1.2.2-beta.jar`，版本号沿用当前分支设置，尚未发布。
第三方测试模组和个人环境日志均不加入提交。

后续已针对最终分页修复完成真实客户端交互、实际区块卸载重载和正式服磁盘重启验证，
详情见 [COMPREHENSIVE_TESTING.zh-CN.md](COMPREHENSIVE_TESTING.zh-CN.md)。
PR 上传前还补跑完整 344 项 JUnit 与水晶科技环境的 18 项谐振 GameTest，全部通过；
最新产物校验值和本地证据路径见 [GLOBAL_STRESS.zh-CN.md](GLOBAL_STRESS.zh-CN.md)。
