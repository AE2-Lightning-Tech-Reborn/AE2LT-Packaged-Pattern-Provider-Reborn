# 上游功能移植：Minecraft 1.20.1 / Forge

参考仓库：[AE2LT-Packaged-Pattern-Provider-Reborn](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn)。
本次把 `main` 的相关功能适配到 Java 17、Minecraft 1.20.1、Forge 47.4.10；保留本仓库 LGPL-3.0 许可证和原有作者信息。
下列提交链接记录移植来源，包括复用的算法、适配器和空槽纹理；第三方测试模组的 JAR 不随源码或产物重新分发。

| 来源 | 移植行为 |
| --- | --- |
| [d193242](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/d193242) | DE 派发前刷新原生注入器位置，支持新建、拆除和重建。沿用本地已有 Malum 空闲催化剂保护。 |
| [fdb3ebc](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/fdb3ebc) | Ars 注魔样板可声明产物及可重复使用的实际催化剂，严格核对数量与 NBT。 |
| [95afe59](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/95afe59) | EC / Avaritia 工作台按各输入槽自己的模糊标志绑定多个候选配方，派发时用实际 NBT 执行原生校验。纯工作台支持 Thunderbolt 批量接口，取消固定 64 次上限；保留能耗、锁定和溢出门控。 |
| [84c5abf](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/84c5abf) | MekMM 可向运行中的机器继续投料，合并未收获订单的输出收据；按配方与模式退避，按控制器自适应轮询。 |
| [25d0ef7](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/25d0ef7) | 统一雕刻核心，Chipped 工作台转换以及 Rechiseled / Chisel 供应器内部转换通道。 |
| [7a60007](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/7a60007) | 两种供应器 GUI 的空核心槽提示纹理。 |

## Forge 适配要点

- Chipped 3.0.7 使用各工作台独立的配方类型，必须存在主半块和朝向一致的侧半块；无线连接只接受主半块。这个版本没有凿子，所以雕刻核心也能用任意 Chipped 工作台制作和显示。
- Rechiseled 1.2.6 直接调用原生 worth 换算 API，支持块与半砖等比例转换。拒绝剩余原料、夸大产量和虚构产物 NBT。两种供应器都可在没有邻接机器和无线目标的情况下完成转换，但必须安装雕刻核心。
- Mekanism 10.4 的气体、灌注、颜料、浆液使用四种 Forge capability。通过 Applied Mekanistics 1.4.3 的化学 AEKey 接口收发，不使用 1.21 的统一 capability。
- MekMM 保留本地已有的保守端口匹配：多个同类输入端口仍拒绝歧义路由。本次没有移植另一项原生配方数量路由重构，也没有放宽空输出基线、订单归属及失败补偿规则。
- Chisel 的两个上游反射路径保留；尚未用对应 Forge 1.20.1 实现做原生运行验证。Rechiseled 1.1 的旧接口回退也未在本轮测试。
- 批量功能使用已有 AE2LT 2.1.0 / Thunderbolt 2.0.1 接口，本次未升级这些依赖。包装样板和过载样板沿用单次派发。

## 复现验证

使用 Java 17；以下命令的 `use_local_upstream` 使用仓库已有的相邻本地依赖构建配置。

```powershell
./gradlew.bat test build -Puse_local_upstream=true -Penable_avaritia_tests=true --offline --no-daemon --max-workers=1
./gradlew.bat runGameTestServer -Puse_local_upstream=true -Penable_integration_runtime=true -Penable_chiseling_runtime=true -Penable_appmek_runtime=true --no-daemon --max-workers=1
```

雕刻运行时依赖使用指定的 Forge Modrinth 版本 ID；可用 `-Pchiseling_runtime_mods=chipped` 或 `rechiseled` 单独验证。
离线环境可用 `-Pchiseling_reference_maven=<本地 Maven 目录>` 提供同坐标、未修改的公开参考 JAR。
正常构建不加载这些可选测试依赖。

原生测试覆盖 DE 注入器重建、Ars 原生注魔与催化剂返还、ME 批量接口发现、Avaritia 130 次批量及 NBT 重载、
Chipped 原生工作台与无线门控、Rechiseled 比例与内部通道、MekMM 原生电解连续订单及模式切换。
客户端验证覆盖核心渲染和两种供应器屏幕。服务端运行和客户端编译必须串行，避免共享编译目录在类加载期间变化。

2026-10-08 已通过 299 项 JUnit 测试（无失败、错误或跳过）和 81 项必需 Forge GameTest。
原生目标版本为 DE 3.1.2.621、Ars Nouveau 4.12.6、EC 6.0.5、AvaritiaNeo 1.1.6 Forge、
Mekanism 10.4.16.80、MekMM 1.20.1-1.2.1、Applied Mekanistics 1.4.3、Chipped 3.0.7、Rechiseled 1.2.6；
AE2 为 15.4.10。MekMM 电解测试使用两个 100 mB 订单，在原生机器仍有输入且已产生输出时追加第二笔订单，
重载供应器持久状态后逐次收获，最终只返还 200 mB 氢气和 100 mB 氧气。

真实 Forge 客户端已通过：仅安装 Chipped 的雕刻组合下，33 种核心完成连续渲染，
普通和无线供应器屏幕均正常打开，空核心槽图标已目视核对；测试世界正常保存并返回菜单。
客户端启动器的 `earlyWindowControl=false` 仅用于本地隔离目录，未加入模组资源或默认配置。

本次开始前工作区已有的启动兼容修复、测试和 README / MANUAL_TESTING 修改保持独立；本次移植验证在该现有工作区基础上进行。

后续全局压测已通过 299 项全套 JUnit、1,024 键专项和 83 项必需原生 GameTest。
扩大负载包括 160 万次随机操作、Avaritia 累计 1.28 亿次虚拟合成、MekMM 128 笔连续订单，以及 8 台供应器跨 6,000 tick 的阻塞重载。
实际负载、结果、复现命令与覆盖边界见 [GLOBAL_STRESS.zh-CN.md](GLOBAL_STRESS.zh-CN.md)。

## main 补充移植（2026-10-15）

对照上游 `main` 的 `e6d226c`（NeoForge 1.21.1）。本轮为 Java 17 / Forge 47.4.10 单独适配：

| 来源 | 本轮补齐 |
| --- | --- |
| [885058b](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/885058b) | 20 种专用核心配方降本，保留目标机器与原有工作台等级。EC 高级核心使用对应等级组件；DE 降为 DRACONIUM、32,000 RF；Occultism 改为 Foliot 仪式。 |
| [265f8ac](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/265f8ac) | 极限锻造保留 addition 数组中的三种材料；Forge Re-Avaritia 1.4.1 将它们解析为同一槽位的三个可选项，按其原生语义执行。使用实际存在的晶体矩阵锭替代 diamond_lattice。 |
| [84c5abf](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/84c5abf) 与 main 初始快照 | MekMM 同一请求仅在当前 tick 抑制失败，下一个 tick 即可重试；不同样板、控制器和模式仍相互独立。 |
| [main](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/tree/e6d226c) 的 LinearVirtualCraftingAdapter / PendingAmounts / 返还库存实现 | Chipped、内部雕刻与灵火显式允许线性批量，每通道每 tick 共用 64 次预算；纯工作台沿用 CPU 预算。产物与退款按 long 大小分段，累计超长数量不再截断；返还库存只回报实际插入量。 |

保留 `recipes/` 复数目录、Forge 条件、1.20.1 的 ItemStack/NBT 格式，以及 Ars 的 reagent 数组、DE 的 `tier` / `total_energy`、EC 的 camelCase 字段。不会套用 NeoForge 的资源 schema。

本地已有 Nature's Aura、常驻佐料、雕刻、EC/Avaritia 批量和 MekMM 配方数量绑定，不重复移植。Forge 的气体/灌注/颜料/浆液 capability、Blood Magic、Forbidden Arcanus、AvaritiaNeo 与既有所有权保护继续保留。

星辉魔法适配基于官方 `1.21.1` 开发分支及 ObserverLib，不存在本项目已验证的 Forge 1.20.1 对应依赖；本轮不加入其物品、配方或反射适配。构建工具、Data Components、NeoForge 菜单/网络 API 和主分支依赖升级不适用于本目标版本。

2026-10-15 本轮验证：完整 `build`（包含 jarJar / reobf）成功；344 项 JUnit 全部通过，无失败、错误或跳过；完整集成环境的 98 项必需 Forge GameTest 全部通过。另用 Re-Avaritia 1.4.1 的独立 Forge 环境执行一项专项 GameTest，覆盖其余五种核心配方的原生加载、产物、四级工作台等级与锻造材料选项，全部通过。12 项发布脚本测试及 Forge 仓库解析回归也通过。

新增回归覆盖下一 tick 重试、64 次线性批量配额与样板输入保护、累计数量超过 long 后的部分交付和 NBT 重载，以及返还库存接近上限时的准确回执。主集成环境覆盖其余 15 种改动配方的原生反序列化。

本轮在 `ad074b5` 的隔离副本中验证，仅包含此前构建依赖修复与本次补充移植。工作区并行进行的共振核心、扩展供应器、菜单和网络改动未纳入这组结果；本轮没有重新执行图形客户端验证。详细日志、复现配置、JUnit 报告、产物和本轮独立补丁保存在被 Git 忽略的 `build/upstream-backport-20261015/`，不进入提交。
