# 2026-10-15 全面测试记录

验证对象为 `AE2LT-Packaged-Pattern-Provider-1.20.1-ref` 的当前合并工作区。
环境：Java 17.0.20、Minecraft 1.20.1、Forge 47.4.10、AE2 15.4.10、AE2LT 2.1.2-beta、Thunderbolt 2.0.3-beta、水晶科技 1.2.0。
本轮发现并修复一个客户端分页缺陷；修复后的完整单元测试、原生测试、客户端交互和正式服存档验证均通过。未提交、未推送、未发布。

| 检查 | 实际结果 |
| --- | --- |
| 完整 JUnit | 修复前后各 344 项、75 个套件；两轮均为 0 失败、0 错误、0 跳过，包含 4 项 NBT 迁移测试 |
| 随机守恒压力 | 初轮 8 个种子 × 500,000 次操作、128 种键；最终轮 4 个种子 × 50,000 次操作、1,024 种键；合计 4,200,000 次操作全部通过 |
| 持续背压与恢复 | 两轮各 60,000 个模拟 tick、128 种键、3,840,000 个产品、300 次 NBT 重载、6,000 次 flush，均守恒 |
| 缓存并发与时钟 | 两轮均通过 8 线程各 10,000 次绑定循环、10,000 次配方重载，以及 10,000 次时钟回退与溢出检查 |
| 完整原生 GameTest | 修复前后各 116 项 required 全通过；两轮均安装水晶科技，两个真实谐振样板用例实际执行 |
| 原生机器路由 | 33 种机器识别和 6 种反例，各 5,000 轮，通过 |
| 原生长期背压 | 初轮 8 个供应器、每个 128 种键、300 轮、2,400 次检查，跨 6,000 个服务器 tick；最终回归使用 10 轮，完整 116 项再次通过 |
| 原生高负载 | Avaritia 128,000,000 次虚拟合成及 128 次重载；MekMM 128 笔订单与 16 次重载；Ars 64 次合成与 128 次机器重载，均通过 |
| Re-Avaritia 独立组合 | 1 项原生测试通过，核对五种核心配方、四级工作台要求与锻造的三种材料选项 |
| 客户端 | 37 种核心完成实际渲染；普通、无线、扩展供应器 × 854×480、960×600、1280×720，共 9 组交互检查通过；保存 23 张截图 |
| 客户端分页及网络 | 四页各显示 36 槽；第 144 槽实际取出、放回；滚轮翻页、模式与自动回收按钮、核心绑定及潜行滚轮均通过真实菜单／数据包验证 |
| 客户端磁盘重进 | 关闭并重新加载同一单人存档后，144 张样板和无线模式全部保留 |
| 正式 Forge 服务器 | 使用发行 JAR 和 43 个模组 JAR；启动、实际区块卸载／重载、保存停服及磁盘重启均通过，逐项匹配 144 张样板及模式 NBT，两次正常退出 |
| 发布与构建 | 12 项发布脚本测试、Forge 发布仓库解析回归、`build`、`jarJar`、`reobfJarJar` 通过 |
| 资源及包内容 | 102 个主资源 JSON 无解析错误或重复键；发行 JAR 635 个条目，无重复、无本轮客户端／谐振测试辅助类；保留 Slot x/y AT 与 refmap |

客户端首次翻到第二页时，服务端已有样板，但客户端显示空槽。
原因是 `showPage` 同时调用 `setSlotEnabled(false)`；AE2 的禁用槽会忽略 `set(ItemStack)`，翻页同步可能在下一次界面渲染启用槽位之前到达。
修复后各页持续同步，使用 `setActive` 控制显示、`setAllowEdit` 控制取放；另外拦截隐藏槽的 `quickMoveStack`，覆盖 AE2 整区移动直接调用该方法的路径。
原生回归现验证隐藏槽接收同步数据、拒绝直接点击和快捷移动；真实客户端的九组交互也全部复测通过。

本轮产品代码只修改 `PackagedPatternProviderMenu.java`，并扩展 `ResonantCoreGameTests.java` 的回归断言。
对 450 个源码、脚本和构建输入文件进行前后指纹比较，变化仅限这两个文件。
最终发行包相对此前已验证产物只变化主菜单 class 和内部槽位 class；后者的可执行字节码相同，仅调试行号变化。

测试准备过程另处理了 Windows GC 日志参数、临时客户端夹具编译、实验性世界首次确认、缺少鼠标松开事件，以及载入卡顿时过早检查渲染帧数的问题。
正式服初次启动还暴露导出的 AE2 依赖为开发映射 JAR；更换为同版本官方 Forge 发行文件并校验 SHA-512 后，通过全部正式服检查。
上述准备问题与分页缺陷的失败日志、截图均单独保留，未计为通过结果。

第三方日志仍有数据告警／错误：MekMM 部分掉落表引用未注册物品，兼容配方引用未安装模组，以及 `forge:fusion_fuel` 空标签等。
这些第三方配方可能被跳过，本轮未修改第三方模组，也未将它们记为已修复。通过结论针对表中实际执行的断言。
客户端自动化使用 Forge 开发环境，正式服独立使用发行 JAR；未覆盖全部第三方组合、远程多人联机、所有显卡／GUI 缩放设置。
测试期间机器还有其他任务，本报告中的负载数量用于功能守恒验证，不作为独占机器的 TPS 或性能基准。

可在项目根目录使用 Java 17 复现主要检查；对应本地依赖目录需已准备好 Forge JAR：

```powershell
$testArgs = @(
  '--no-daemon', '--max-workers=1', '--console=plain',
  '-Puse_local_upstream=true',
  '-Pae2lt_build_libs=build/test-dependencies/libs',
  '-Pthunderbolt_build_libs=build/test-dependencies/libs',
  '-Penable_integration_runtime=true', '-Penable_chiseling_runtime=true',
  '-Pchiseling_reference_maven=build/test-dependencies/runtime-maven'
)

.\gradlew.bat build jarJar reobfJarJar @testArgs `
  -Penable_avaritia_tests=true `
  -Pstress_trials=500000 -Pstress_seeds=8 -Pstress_keys=128

# 最终宽键配置为 stress_trials=50000、stress_seeds=4、stress_keys=1024。
# 将 Crystal Science 路径替换为经过校验的 Forge 1.20.1 开发 JAR。
.\gradlew.bat runGameTestServer @testArgs `
  -I scripts/resonant-test.init.gradle `
  '-PppTestNamespaces=ae2ltpp,ae2ltpp_resonant' `
  -PppTestRunDir=build/comprehensive-repro/run-full `
  '-PppCrystalScienceJar=<开发 JAR 的完整路径>' `
  -Pstress_rounds=5000 -Pstress_soak_rounds=300

python .github/scripts/test_release.py
python .github/scripts/test_forge_release_repositories.py
```

各项 Gradle 操作顺序执行，避免运行中修改共享编译目录；不同模组组合使用独立存档。
依赖已缓存时可加 `--offline -Dnet.minecraftforge.gradle.check.certs=false`。
客户端、Re-Avaritia、正式服夹具及本次执行参数保存在本地归档的 `fixtures/`，复用时需调整目录和依赖路径。

本地证据位于被 Git 忽略的 [build/comprehensive-verification](build/comprehensive-verification/RESULTS.json)，包含两轮 JUnit XML／HTML、原生与客户端日志、正式服检查结果、截图、依赖哈希和 `SHA256SUMS.json`。
最终产物为该目录下的 `ae2ltpp-forge-1.20.1-1.2.2-beta.jar`，1,442,050 字节，SHA-256：

```text
12523a23ec912c442d659518be83c03de625df3c5ab43b7b36a2f7af7f788317
```

原始日志、测试世界和第三方测试 JAR 不加入提交；本轮归档中的新版发行文件包含分页修复。
