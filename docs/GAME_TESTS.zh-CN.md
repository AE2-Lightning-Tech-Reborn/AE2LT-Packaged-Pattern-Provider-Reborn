# 游戏环境兼容测试

测试在真实 Minecraft 1.21.1 / NeoForge 服务端运行，加载目标模组的方块、配方序列化器、库存、能量 capability 和原生 ticker。可以自动运行 GameTestServer，也可以启动测试客户端后用 `/test` 查看结构和执行测试。

## 2026-10-07 本地验证结果

环境：Java 21、NeoForge 21.1.230、AE2 19.2.17、AE2LT 2.1.1、Thunderbolt 2.0.0。

| 测试组 | 结果 |
| --- | --- |
| 主兼容 GameTest | 65 / 65 |
| Reborn + Rechiseled 1.2.6 + Chipped + Occultism | 10 / 10 |
| Modern + Rechiseled 1.1.6a + Chipped | 10 / 10 |
| 仅 Chipped 及其依赖 | 10 / 10 |
| 雕刻模组全部缺席 | 10 / 10 |
| JUnit | 113 / 113，无失败、错误或跳过 |
| 发布 JAR | 未包含 GameTest 类、测试配方或嵌入的第三方 JAR |

共 105 次 GameTest 执行，其中雕刻组在不同环境复用测试方法，并非 105 个独立用例。目标版本：AA 1.3.25、Ars 5.11.5、DE 3.1.4.632、EC 7.0.8、Re-Avaritia 1.3.9.9、MA 8.0.26、Occultism 1.215.0、Malum 1.8.2、Mekanism 10.7.19.85、MekMM 1.3.3、Applied Mekanistics 1.6.3，以及下方雕刻组合表列出的版本。

Chisel Modern 1.4.1 使用[官方仓库提交 e04dfce](https://github.com/Leclowndu93150/Chisel-Modern/tree/e04dfce38a119b0ff84fb03efd295ca97b722e37)本地构建。构建时将 NeoForge 对齐到 21.1.230、Parchment 对齐到本项目配置，先执行上游 `runData`，再单独执行 `jar`；未修改上游 Java 代码。直接构建源码会遗漏生成的转换标签，不能作为完整模组测试输入。此次完整 JAR 包含 228 个原生物品转换标签。

## 运行主兼容组

需要 JDK 21、AE2LT、Thunderbolt，以及 Mekanism More Machines 1.3.3 JAR。其余主兼容模组使用 `build.gradle` 中锁定的版本，由 Gradle 下载。所有路径参数指向本地真实文件，不要使用包含整套游戏模组的目录作为主兼容组依赖。

```powershell
.\scripts\run-compat-tests.ps1 -Suite core `
  -Ae2ltJar 'C:\path\ae2lt-2.1.1.jar' `
  -ThunderboltJar 'C:\path\thunderbolt-2.0.0.jar' `
  -MekmmJar 'C:\path\mekmm-1.21.1-1.3.3.jar' `
  -JavaHome 'C:\path\jdk-21'
```

`JavaHome` 可省略，此时使用现有 `JAVA_HOME`。主兼容组依次运行 JUnit、构建发布 JAR、运行 GameTestServer。结果保存在 `build/reports/compatibility/core.log` 和 `core.json`；只有进程正常退出且 GameTest 明确报告必选测试全部通过，脚本才会报告成功。JUnit 报告在 `build/reports/tests/test/index.html`。

直接运行 Gradle 的等价入口：

```powershell
.\gradlew.bat --no-configuration-cache -I scripts/compat-test.init.gradle `
  '-Pae2lt_jar=C:\path\ae2lt-2.1.1.jar' `
  '-Pthunderbolt_maven_notation=' `
  '-Pthunderbolt_jar=C:\path\thunderbolt-2.0.0.jar' `
  '-PppMekmmJar=C:\path\mekmm-1.21.1-1.3.3.jar' `
  runGameTestServer
```

将最后一个任务换成 `runClient`，在独立的 `build/compat-client` 游戏目录创建创造模式测试世界，开启作弊，使用 `/test runall`。测试期间可以看到真实放置的机器。测试客户端也需要同一套依赖。`runServer` 使用 `build/compat-server`，自动测试使用 `build/compat-gameTestServer`。

## 雕刻模组组合

Chisel Reborn 和 Chisel Modern 使用相同 mod id，必须分别运行。每个目录只放本组目标及其依赖 JAR，不放 AE2、LT、Thunderbolt 或 LTPP 的重复副本。

| Suite | 目录内容 |
| --- | --- |
| `reborn` | Chisel Reborn 2.0.1、Rechiseled 1.2.6、Chipped 4.0.2；Architectury、Cryonic Config、SuperMartijn642 Core/Config Lib、Fusion、Athena、Resourceful Lib；可再加 Occultism 及其依赖以运行灵火无线连接场景 |
| `modern` | Chisel Modern 1.4.1、Rechiseled 1.1.6a、Chipped 4.0.2；CTM、BaguetteLib、SuperMartijn642 Core/Config Lib、Fusion、Athena、Resourceful Lib |
| `chipped` | Chipped 4.0.2、Athena、Resourceful Lib |
| `absent` | 不传模组目录，验证雕刻模组缺席时不注册适配器、不加载核心配方、不开放内部合成通道 |

```powershell
.\scripts\run-compat-tests.ps1 -Suite reborn `
  -Ae2ltJar 'C:\path\ae2lt-2.1.1.jar' `
  -ThunderboltJar 'C:\path\thunderbolt-2.0.0.jar' `
  -ChiselModsDir 'C:\path\reborn-test-mods' `
  -JavaHome 'C:\path\jdk-21'
```

其他组合更换 `Suite` 与目录。`absent` 不传 `ChiselModsDir`。每组均有独立日志与 JSON 报告。雕刻组包含 10 个 GameTest 方法，部分方法仅对本组已安装模组执行正向场景；不能将缺席环境的通过视为对应模组已安装时的功能验证。

## 覆盖范围

新增 `src/compatTest` 的 13 项测试，与已有批量、路由、工作台、Ars 催化剂回归组合为 65 项主兼容 GameTest。

| 模组 | 检查内容 |
| --- | --- |
| Actually Additions | 原子再构机真实配方、精确 FE 扣除、无电/错料拒绝；充能器四展示台结构、供能和原生加工完成 |
| Ars Nouveau | 附魔装置奥术核心及两种基座、实际产物；灌注室 64 次催化剂循环、组件和容器清空；AE2/Thunderbolt 原生规划器 |
| Mystical Agriculture | 灌注祭坛八基座和觉醒祭坛四容器/四基座；缺结构拒绝、真实配方投料和原生加工 |
| Extended Crafting | 四级工作台及无线提供器、超大批量、组件匹配与容器余料；末影/通量合成器原生有形状配方、组合合成核心加工 |
| Re-Avaritia | 工作台各级、终极锻造台、无线/批量、容器余料及输出持久化 |
| Draconic Evolution | 新建与拆换注入器后的发现、缺注入器拒绝、原生融合加工和产物回收 |
| Malum | 灵魂灌注含基座额外材料；坩埚完整性、首 tick 前催化剂保护、实际聚焦产物和保留耐久消耗的催化剂返还 |
| Occultism | 实体 `craft_foliot` 法阵、缺法阵拒绝、原生仪式与倒置碗产物回收；灵火批量；雕刻 Reborn+Occultism 组合另测无线绑定与失效清理 |
| Mekanism + Applied Mekanistics + MekMM | 多种化学/流体/物品路由、配方比例、双输出、真实槽容量、部分投料保存恢复、普通/无线回收调度 |
| Chipped | 七种完整工作台、拆半失效、真实核心配方、无线连接 |
| Rechiseled | 1.1 与 1.2 API、双向转换、连接纹理；1.2 半砖比例与余料拒绝 |
| Chisel Reborn / Modern | 各自原生转换组双向转换、数量/组件/多料拒绝；共享核心与普通/无线内部批量路径 |
| 提供器通用 | 待回收物品/流体 NBT、组件保留、旧数据迁移、批量 CPU 记账、异常写入后的归属与防重复返还 |

主兼容测试使用 5 个仅在测试环境加载的配方，分别调用 AA、Ars、EC、DE、Occultism 的真实序列化器，其余新增机器场景使用目标模组已有配方。测试配方降低部分资源成本、缩短个别加工时间，以稳定验证投料和回收；不代表穷举所有模组配方。物理加工由原生 ticker 完成，没有直接向输出槽写入预期产物。

旧 MekMM 与工作台测试结构由 1×1×1 扩大到 24×12×24，防止测试在申报范围之外放置机器，使相邻测试互相干扰。

## 此次修复与限制

- DE 的 `getInjectors()` 只读取缓存，新建装置在尚未手工投料时没有扫描结果。适配层在读取前调用原生 `updateInjectors()`，解除首次投料无法开始的问题，也会识别后续拆换。
- Malum 坩埚在 `updateRecipe()` 选定配方后、首个原生 tick 设置加工状态前，曾被判断为可回收催化剂。现在同时要求没有待加工配方，测试核对首 tick 前不回收以及加工后精确返还一枚带原生耐久消耗的催化剂。
- 一项旧异常投料测试沿用已废弃的退款预期；现在注入“物品已经写入后抛异常”，验证机器持有的材料和确定未访问的材料不会被重复返还。
- Botania 与 Astral Sorcery 本次按要求跳过。原有星辉专用测试入口仍保留。
- 这是服务端自动化功能验证，不包括 GUI、渲染、玩家手动操作或长期性能压测。未知版本仍需重跑。
- 精简环境可见 LT 的缺少 ExtendedAE 条件、Malum 部分第三方修复配方、MekMM 部分未注册条目的既有资源加载日志。主兼容组明确检查全部新增测试配方存在，缺依赖或测试配方解析失败会使测试失败。

测试源码、测试配方和第三方模组均不打入发布 JAR。可用 `jar tf build/libs/ae2ltpp-1.2.0-beta.1.jar` 检查当前产物；不应包含 `GameTests`、`/compat/` 测试配方或嵌入的第三方 JAR。
