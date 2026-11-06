# 上游功能移植：Minecraft 1.20.1 / Forge

参考仓库：[AE2LT-Packaged-Pattern-Provider-Reborn](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn)。
本次把 `main` 的相关功能适配到 Java 17、Minecraft 1.20.1、Forge 47.4.10；保留本仓库 LGPL-3.0 许可证和原有作者信息。
下列提交链接记录移植来源，包括复用的算法、适配器和空槽纹理；第三方测试模组的 JAR 不随源码或产物重新分发。

| 来源 | 移植行为 |
| --- | --- |
| [73df3dc](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/73df3dcce77839a0d213e9fa12ca718d98104b32) | `alpha` 终端核心合成与安装：原生确认页显示去向及拒绝原因，首核批量分流，原位升级限单枚，JEI 展示备用核心配方。Forge 适配与验证入口见 `CORE_ORDER_BACKPORT.zh-CN.md`。 |
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


## 2026-10-20：alpha / 1.20.1-alpha 增量

本轮参考 `alpha` 的 [3cd874d](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/3cd874dab82e7637f9fc1c7b36053d802ecd5166)、[8c89712](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/8c89712c54e7069710495dffb6e423a95fa91a6c)，以及 `1.20.1-alpha` 的 [13e61ab](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/13e61ab86134ee97fdf7e01ab2199ceedb6fb1de)。保留原许可证与作者信息。

- EC 组合合成派发时刷新原生配方缓存，回收依据当前配方状态和本提供器的输出归属，不再要求轮询曾见到正进度。范围内任一基座占用或未加载时拒绝新订单。
- 物理批量保存控制器归属、校验所有输入数量，并在背压期间仅续期未回收产物。不明插入回执独立持久化、阻止重放与退款；已确认未插入的材料仍可在拆除时返还。
- 核心自动下单通过实际 ME 合成计算与 CPU 请求收据安装。首个核心允许对应真实机器自举。EC / Avaritia 支持消耗本提供器旧核心的升级，以及显式旧核心输入后把产物返回 ME 的升级。满库存结果保留并可重载。
- FA 2.2.6 使用原生 ItemInput / BloodInput / ExtractEnchantmentsInput，不使用 2.6 的新输入 API。精华为 AE2 自定义资源，通过 Forge capability 与 ME 总线交换，JEI 使用 AE2 15 的公开 IngredientConverter。转换执行原生 tick，记录库存基线和独立归属收据；容器及随机经验按实际产量返还。
- 黑洞沿用上游虚拟流体转换：默认 `forge:experience` 1,200 mB → 石化经验球 ×1；检查当前数据包配方与实际输入，不修改黑洞经验。XP 流体标签中的物品来自可选模组，不会凭空添加流体。
- 未移植 FA 精华罐和 Astral 核心升级：前者不存在于 FA 2.2.6，后者没有本仓库可验证的 Forge 1.20.1 依赖。FA 原有真实仪式和等级升级凭证机制保留；本轮没有用新版本适配器替换它们。


本轮验证（Java 17 / Forge 47.4.10 / AE2 15.4.10 / FA 2.2.6）：346 项 JUnit、144 项必需 GameTest 全部通过，原生测试包含真实水晶科技和 MekMM/FA/EC 运行时。`build`、`jarJar`、`reobfJarJar`、12 项发布脚本测试和发布仓库解析回归通过。真实图形客户端验证核心选择页保留菜单、下单与取消同步、返回恢复槽位、JEI 资源转换和四种精华的图标及本地化。

未覆盖远程多人、正式服磁盘重启、所有第三方版本组合；客户端核心选择页本轮直接验证普通供应器。发行包不包含额外客户端测试夹具、黑洞测试配方或谐振测试数据包。

### 核心订单整合补充

在 `27deee3` 基础上补入核心 JEI 原生配方镜像与下单、旧核心催化剂显示、共振核心占槽时的首核制作及严格产物回收。保留 FA 精华与黑洞，以及批量输入所有权保护。移植范围与合并后的验证入口见 [CORE_ORDER_BACKPORT.zh-CN.md](CORE_ORDER_BACKPORT.zh-CN.md)；此前各轮测试数字只对应各自源码快照。


## 2026-10-20：女仆祭坛、资源回收与核心配方递进

参考上游 `alpha` 的 [f3c59de](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/f3c59de29e7c6b74087d479344f7ace8e61329f7)，保留原许可证和作者署名。

- 六条 EC / Avaritia 高级核心的原生制作配方改为消耗前一级核心，与已有 `core_upgrade` 的前置要求一致；保留 Forge 1.20.1 配方格式、工作台等级和 Re-Avaritia 物品名。
- 女仆祭坛适配 Touhou Little Maid **1.5.3 Forge 1.20.1** 的原生 `altar_crafting`、六槽 `AltarRecipeInventory` 与 `PowerCapabilityProvider.POWER_CAP`，使用原生祭坛执行物品合成。拒绝实体仪式、错误产物、缺少 P 点及占用或不完整结构。实际产物在同一服务端提交中取得归属并进入供应器返还队列，不收取周围原有掉落。
- P 点使用独立 AE2 key type，100 个内部单位 = 1 P；支持女仆信标上半部的原生存储、输入／输出／存储总线及破坏面板收取原生 P 点实体。小数与大容量浮点转移按实际余额计算回执。祭坛可将 P 点物品 ×1 转换为 0.6 P。
- JEI 沿用 AE2 15 的公开 IngredientConverter；女仆祭坛配方和核心镜像包含真实 P 点输入。继续保留本地延迟读取 JEI 原生布局的生命周期修复。
- 破坏面板将经验球按 1 XP = 20 mB 转成 `ae2ltpp:experience` 标签内的经验流体；优先网络中存量最多的种类。无合适流体或空间不足时保留经验球。合并球逐份计数，部分回执记录剩余额度并持久化，禁止未完成兑换的球被原版玩家收取或合并后重复支付。
- 没有移植渡鸦灌注：上游依赖 `apothic_enchanting:raven_enchanting_table` 与其 InfusionRecipe / Attachment API；已检查 Apotheosis 7.4.8 Forge 1.20.1 实际 JAR，其中没有对应机器与类。星辉魔法 2.0.2 配方修正同样缺少本仓库可验证的 Forge 1.20.1 目标依赖。

可选原生验证：`gradlew runGameTestServer -I scripts/maid-test.init.gradle -Penable_maid_runtime=true`。正常构建不会下载女仆模组；测试代码与水／熔岩经验标签夹具位于独立 `maidTest` 源集，不进入发行 JAR。`-PppNoExperienceFixture=true` 验证没有任何可兑换经验流体；省略 `enable_maid_runtime` 验证未安装女仆时的加载和注册。

本轮在 Java 17 / Forge 47.4.10 / AE2 15.4.10 下通过 356 项 JUnit（0 失败、错误或跳过），`build`、`jarJar` 与 `reobfJarJar` 成功。完整集成环境的 159 项必需 GameTest 全部通过，其中包含 8 项新增女仆与资源回收测试；另在 Re-Avaritia 1.4.1 的独立环境通过 1 项专项，验证五条核心配方的原生反序列化、材料、产物与工作台等级。未安装女仆且无经验流体的独立环境也通过 8 项测试，其中女仆专属用例提前结束，仅用于验证可选集成关闭及无流体时保留经验球。

真实 Forge 客户端验证六项女仆核心材料与 1 P 输入、0.6 P 转换、AE2 JEI 数量转换及核心／资源图标。116 个主资源 JSON 均能解析且无重复键；发行包保留许可证和经验球 Mixin 映射，只内嵌既有 MixinExtras，不包含女仆模组、额外测试代码或水／熔岩标签夹具。常规集成环境使用 AvaritiaNeo，本轮没有重新验证 Crystal Science 的原生路径或远程多人。


## 2026-10-21：MekMM 配方重载、机器识别与无线提示

移植上游 main 的 [49b41fa](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/49b41faae6c62fd73238d63f3973148c037ee841)、[ef0442c](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/ef0442ccfb481ba430ad13d6bc6991d31ca0541b) 和 [a5ae38b](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/a5ae38bb7cfa16b519324a17ffe7693343267239)，保留原项目许可证与署名。

- MekMM 投料改为按 ID 取得当前服务端 Recipe，校验类型后以当前配方重新验证输入数量和产物。内容相同但实例不同的配方不再被拒绝；配方删除、类型变化或输入／产物变化不能复用旧配方绕过校验。
- 菜单每 10 次服务端广播刷新机器数量与名称，支持相邻、无线和谐振锚点；未加载区块不被主动加载。MekMM 占位块归并控制器，同一台机器多个连接只计一次。绿色“已识别”仅表示发现机器，投料仍需核心、样板和运行条件匹配。
- 无线模式常驻粉色连接工具提示，扩展供应器切换模式时同步显示。沿用本地小屏布局：识别标记移到右侧，底部提示缩小放入快捷栏下空白区域，避免遮挡订单按钮、分页与槽位；AE2 15 的控件尺寸在 resize 时显式更新。
- 使用 Forge 1.20.1 的 Recipe、数组样板和分类型化学 capability API；原生投料夹具采用该版本的氢／氯 → 氯化氢配方。没有移植 main 的 NeoForge 依赖或版本号。

可选机器识别回归入口：`gradlew runGameTestServer -I scripts/discovery-test.init.gradle -Penable_integration_runtime=true`。独立 `discoveryTest` 源集包含原生机器放置、普通／无线投料、菜单同步、连接去重、目标移除及谐振识别，不进入发行 JAR。配方重载的 5 项 JUnit 通过真实 RecipeManager 和轻量夹具适配，无需引入 Mockito。

本轮 Java 17 / Forge 47.4.10 / AE2 15.4.10 下，361 项 JUnit 全部通过，0 失败、错误或跳过；全新世界的 164 项必需原生 GameTest 全部通过，含六种 MekMM 机器、四个朝向的 72 个端口及两项谐振识别测试；`build`、`jarJar`、`reobfJarJar` 成功。真实图形客户端的 6 组检查覆盖普通／无线／扩展供应器和 854×480／960×600 窗口，验证 Component 与数量同步、目标移除后的清除、悬停提示、模式切换、提示可见性与控件边界。116 个主资源 JSON 均能解析且无重复键；发行包保留许可证，仅内嵌既有 MixinExtras，不含新增测试夹具。

本轮没有测试远程多人或其它 Minecraft／加载器版本；原生测试使用本地已固定的集成依赖，未重新覆盖全部第三方版本组合。

验证环境说明：复用上一轮 GameTest 世界时，三项自然灵气祭坛夹具受残留状态干扰而失败；相同源码在全新世界全部通过。完整组合回归应为每轮指定新的 `ppTestRunDir`，避免把旧世界状态带入掉落计数和树仪式。


### 2026-10-21 真实客户端补充验证

使用简体中文，在真实 Forge 图形客户端完成普通、无线和扩展供应器的 6 组大小窗口检查，以及 8 次原生生产（包括扩展供应器切换无线模式）。每次氢、氯各投料 32 份，原生机器产出氯化氢 32 份；供应器 NBT 状态重载后按归属回收，第二次回收为空。窗口尺寸为 854×480 和 960×600，覆盖机器识别同步、目标移除后的清除、模式切换及提示边界。此项 NBT 状态重载未等同于游戏进程重启；本轮未测试远程多人。

同一轮验证包含 361 项 JUnit（无失败、错误或跳过）、全新测试世界的 164 项必需 Forge GameTest，以及完整构建。测试夹具、运行日志和本机路径分别保留在测试源集与本机证据目录，发行 JAR 不包含额外测试夹具或第三方模组。


## 2026-10-25：main 共振范围与资源守恒

参考上游 `main` 的 [df00bdd](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/df00bddcc11acc1789abd547b8b4d91262101dba)，保留 LGPL-3.0 许可证与原作者署名。本轮只移植该 main 增量，不包含 alpha 的性能与新增机器功能。

- 共振子输入到机器锚点默认限制为直线 32 格。Forge 服务端配置 `resonantInputRange` 可设为 1–1024，文件位于世界的 `serverconfig/ae2ltpp-server.toml`。绑定、相对布局派发及水晶科技绝对标记均检查范围；已有超范围输入同样需要调整配置或重新绑定。坐标相减前使用 long 检查，避免整数回绕。
- 新布局只从当前主方块的连接面回收样板声明的产物。已有子输出布局继续使用原路线；物品交互不再新增或修改子输出。144 个输入栏位、NBT 格式、复制功能及本地任一邻接面寻找锚点的行为保留。
- 供应器拆除前将返还库存、虚拟产物和已确认未插入的材料回存 ME，按每次实际回执扣减，再处理剩余掉落。虚拟批次的正常定时 flush 不变；不明投料回执仍禁止重试与退款。没有网络或网络拒收时保留原有掉落能力，不承诺把没有实体掉落形式的资源转成物品。
- 花药台按完整 AEItemKey 分别计量同物品的不同 NBT，原生 ingredient 允许时可共同满足批量需求。保留本地配方候选搜索预算，使用容量分配避免宽泛材料抢占严格 NBT 材料；无需按批量数量展开物品列表。
- 本地 Mekanism 10.4 已有四种化学 capability、指定罐模拟/提取、实际类型校验及订单归属；Runic / Terra / Alfheim 已有精确 AEItemKey 匹配。本轮保留这些实现，不用上游 1.21 的统一接口覆盖它们。

本轮在 Java 17 / Forge 47.4.10 / AE2 15.4.10 下，375 项 JUnit 全部通过，0 失败、错误或跳过；`build`、`jarJar`、`reobfJarJar` 成功。完整集成环境在全新世界通过 184 项必需原生 GameTest，覆盖新增的主机回收、旧布局兼容、相对与绝对坐标范围、拆除回存及 NBT 材料守恒；保留原有背压、重载、批量和投料归属回归。

首次 JUnit 中一项源码片段断言仍要求旧花药台匹配器，已改由实际 NBT 材料行为测试验证。首次原生全套有一项树仪式未返回产物，失败世界中存在木台缺失，当时尚未确认来源。加入仓库外事件跟踪后的完整重跑通过 184 项；随后将树仪式放入独立测试批次，保持原有 240 tick 产物断言，移除跟踪后在新世界再次通过全部 184 项。后续固定种子对照已定位为普通地形进水，详见下一节；独立批次不能消除该原因。原失败证据与重跑结果一并保留，未通过延长等待或放宽产物断言规避失败。

女仆与水晶科技未安装、没有经验流体夹具的独立组合通过 34 项原生测试，验证可选注册、共振及没有可兑换流体时保留经验球；缺席模组专属用例会提前结束，不代表执行其原生玩法。该组合保留黑洞用例必需的 Forbidden Arcanus 等集成依赖；首次误关全部集成依赖造成黑洞夹具绑定失败，修正运行组合后通过，未因此修改产品代码。

发行包内 118 个 JSON 可解析且无重复键，保留许可证与 Mixin 映射，仅内嵌既有 MixinExtras，不含本轮新增测试夹具或诊断跟踪。本轮没有重新执行图形客户端、远程多人或全部第三方版本组合。测试世界、日志、报告和发行包保留在仓库外，不进入提交。

## 2026-10-26：验证失败原因复核

三类失败均做针对性复核：

| 失败 | 确认的原因与证据 |
| --- | --- |
| 花药台源码断言 | 旧断言要求已替换的匹配器源码字符串；针对完整 NBT、宽窄 ingredient 重排、批量容量的行为测试及兼容契约测试共 34 项通过。 |
| 树仪式没有产物 | 原测试世界为随机种子的普通地形，仪式夹具位于 Y=-60 附近。固定原种子 `6355374979481900555`，在原坐标复建夹具，217 tick 时流水进入木台位置，随后缺失 4 个木台且没有产物；没有在该区域执行其他破坏性测试。受控进水对照复现相同取消行为。相同种子、相同坐标改用平坦世界后没有进水，木台完整，正常取得远古树苗 ×2；8 次没有防水外壳的树仪式重复测试也通过，仍使用原 240 tick 产物断言。 |
| 黑洞夹具绑定失败 | 关闭 Forbidden Arcanus 后，独立负例精确复现 `native datapack black-hole binding`；安装该依赖的普通地形与平坦世界两组均通过原黑洞测试。属于测试组合缺失依赖。 |

GameTest 启动前现生成固定种子的平坦世界配置，默认种子为 0。默认运行目录为独立的 `run/gametest-server`，结构模板随实际测试目录同步。已有世界默认要求换用全新的 `ppTestRunDir`；刻意做存档验证时可用 `ppReuseTestWorld=true`。拒绝复用的检查已验证不会修改旧世界或其配置。已撤去树仪式的独立批次，恢复其与完整套件一起运行；模组业务逻辑与产物断言未因这次诊断放宽。

针对性原生检查为普通地形 11 项、平坦世界 11 项、缺失 FA 的预期失败校验 1 项，全部通过。诊断夹具、固定种子世界及日志留在仓库外，不进入发行包。

最终固定平坦环境的完整回归通过 375 项 JUnit（无失败、错误或跳过）与 184 项必需原生 GameTest；树仪式与其余默认批次测试一起通过。`build`、`jarJar`、`reobfJarJar` 成功。本次补充解决测试环境的不确定性，未重新执行图形客户端或远程多人测试。
