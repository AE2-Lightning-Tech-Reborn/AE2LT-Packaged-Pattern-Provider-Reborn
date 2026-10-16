# AE2LTPP 实机派发验证清单（游戏内手动测试）

适用：AE2LTPP 1.2.0-beta.9+（Forge 1.20.1 分支）。
环境：Minecraft 1.20.1 + Forge 47.4.10 + AE2 15.4.10 + 更新后的 AE2LT 2.1.0（包含无线目标准入接口）+ Thunderbolt 2.0.1 + 对应可选 mod。

无线连接回归：未安装核心拒绝连接并提示；错误核心拒绝；正确核心允许对应机器与灵火；Ctrl 批量连接逐个校验；普通箱子/石头拒绝；更换或移除核心不删除已有连接；客户端供方区块未加载时点击灵火仍由服务端验证。旧的同版本号 AE2LT 2.1.0 构建不能替代更新后的构建。
无线清理优化：周期校验仍按 100 tick 间隔、最多 64 个目标轮转；只记录实际删除的连接，清理结束后再移除对应 ownership，不再每轮复制全量连接或逐项查找差集。1024 连接回归覆盖 160 轮有效目标扫描、有效/未加载/失效混合目标、游标绕回及手动全清理；真实 GameTest 覆盖一目标预算和仅删除目标的持久状态清理。
无线候选缓存修复：时间回退、差值溢出或时钟绕回时立即重新校验，不会将负时间差当成缓存命中。行为回归执行 10,000 轮目标卸载/恢复与时间回退，同时检查正常 20 tick 缓存窗口和过期边界；独立覆盖 long 溢出与绕回。
相邻机器索引优化：缓存命中复用不可变面列表，只在失效或 200 tick 到期时更新；显式失效仍保留旧适配器观察供 ownership 清理使用，旧快照不会随重建改变。源码行为回归覆盖有效及空索引各 10,000 次查询、过期边界、区块未加载/恢复、时间回退及差值溢出；GameTest 验证无方块实体的灵火识别、删除/重放、失效与快照复用。
批量输出优化：交付受阻的余量使用有序键映射聚合，消除逐项线性查找；保留首次出现顺序、精确键匹配和数量溢出饱和行为，不改变插入或能量结算顺序。存档直接遍历待交付键值，不再为物品/流体创建整批临时堆栈快照。回归覆盖 1024 种键的重复余量、部分交付重试、结算/唤醒异常、1024 种带 NBT 物品加流体的重复存档；GameTest 验证无电供方受阻 flush、重载和排空后的数量守恒。
批次持久化优化：有积压时，只在首次初始化 flush 时钟或实际 flush 后触发保存；未到交付周期的重复调用不再反复标记区块或唤醒设备。真实 GameTest 用普通/无线两台供方验证 40,000 次无变化调用不会标脏，同时检查初始化、受阻 flush 和最后排空仍必须持久化。新增单测验证 flush 时钟的周期边界、时间回退、NBT 重建和清空。
每组测试都需要：ME 网络供电、Pattern Provider（本 mod 的多方块变体）、编码好的 pattern、目标机器。
启动时确认：日志无 `[ae2ltpp]` ERROR、无 `Reflection bootstrap failed`、装 Botania 时无 `missed critical handles`。

自动验证命令（不替代下面的实机步骤）：

```powershell
.\gradlew.bat test build -Puse_local_upstream=true --offline --no-daemon
.\gradlew.bat test -Puse_local_upstream=true -Penable_avaritia_tests=true --offline --no-daemon
.\gradlew.bat runGameTestServer -Puse_local_upstream=true -Penable_integration_runtime=true --offline --no-daemon
```

GameTest 使用独立存档 `run/gametest-world`，开发服务端仍使用 `run/world`。不要为重跑测试删除开发存档。炽炉夹具初始化外围组件的拆除还原状态，并在回收检查后拆除整台测试机器，验证中心与外围组件均已还原。

最近一次验证结果（2026-10-07）：更新后的 AE2LT 2.1.0 + Thunderbolt 2.0.1 下，启用 AvaritiaNeo 测试依赖后，282 项单元测试全部通过（零失败、零错误、零跳过），70 项必需 GameTest 全部通过，完整 `build` 打包成功。默认单测命令会按条件跳过 6 项 AvaritiaNeo 测试，需执行上面的可选测试命令才能覆盖。`StablePatternProviderNbtMigrationTest` 需要反混淆 AE2LT 开发依赖，目前不包含在此测试集内。回归涵盖无线目标准入、核心更换后保留连接、预算清理、时钟边界、相邻机器索引、批量输出守恒及复用 NBT 存档时的旧字段清理；真实 ME CPU 下单覆盖炽炉、血祭坛、锻造熔炉的一种物品仪式及 AvaritiaNeo 原生有序/无序配方。实际客户端在空和非空复制配置下均通过 70 项测试及 JEI 分类查询，常规/无线供方交互正常；各模组全部配方的手动验收仍需按下面步骤进行。第三方模组自身的可选配方缺失、联网奖励文件超时等日志需要单独判断。

通用验收原则（每个适配器都适用）：
- 派发成功 → 输出在承诺数量内被回收，多余的同物物品留在机器里。
- 派发失败/部分失败 → 已确认塞入的输入被精确退回；无法证明安全的部分宁可留着（fail-closed），不会重复退款。
- 没有本 mod 派发记录时，手动/其他自动化放入的物品不会被误收。

## 自动压力测试

```powershell
.\gradlew.bat test build -Puse_local_upstream=true -Penable_avaritia_tests=true -Pstress_trials=200000 -Pstress_seeds=5 -Pstress_keys=32 --offline --no-daemon
.\gradlew.bat test -Puse_local_upstream=true --tests '*VirtualBatchAccumulatorStressTest' -Pstress_trials=10000 -Pstress_seeds=2 -Pstress_keys=1024 --offline --no-daemon
.\gradlew.bat runGameTestServer -Puse_local_upstream=true -Penable_integration_runtime=true -Pstress_rounds=3000 -Pstress_soak_rounds=300 --offline --no-daemon
```

`stress_trials` 为每种子的随机操作数（1–1,000,000），`stress_seeds` 为连续种子数（1–32），`stress_keys` 为不同 NBT 物品/流体键数（1–1024）。随机场景每步检查生产量 = 已交付量 + 待交付量，覆盖部分交付、整批受阻、重入入队、重复存档/重载、排空及时间回退；末尾全量排空再次检查守恒。参数会传入实际测试 JVM，结果中记录每种子实际操作数及各类操作次数。

持续背压场景固定执行 60,000 个模拟 tick，128 种物品/流体键累计生产 3,840,000 个产品单位；前半段完全受阻，后半段限量恢复交付，夹杂交付前异常、时间回退和 300 次 NBT 重建。每 tick 检查数量守恒，末尾排空；模拟 tick 数不等于实际运行时长。

绑定缓存另有 8 线程各 10,000 次写入/查询/失效与 10,000 次配方重载的并发测试；这不表示 Minecraft 世界访问可多线程执行。GameTest 中 8 台普通/无线供方每台 128 种 NBT 输出，每隔 20 个服务端 tick 检查受阻交付和逻辑重载，最后排空；`stress_soak_rounds` 控制持续轮数（1–300），默认 10，300 轮对应 2400 次供方交付/逻辑重载检查和约 6000 个服务端 tick。另有 4 台供方各 6 轮完整方块实体存档、移除、创建新实例并恢复，共 24 次重建，检查积压与 ownership 保留；这是完整方块实体生命周期模拟，不是实际区块卸载或磁盘重启。`stress_rounds` 控制兼容机器识别及同场路由循环（1–10,000），默认分别为 300/100。相同参数运行 `test` 可能命中 Gradle 缓存，需要强制重复时加 `--rerun-tasks`。

这些测试是行为压力回归，不是长时间满载真实合成链的 TPS/内存基准；同场路由包含空闲派发/回收安全检查，不代表每个模组的所有配方都完成过实际生产。

已通过的扩展压力验证：32 键共 1,000,000 次随机操作、1024 键共 20,000 次随机操作，均最终排空守恒；8 线程共 80,000 次绑定缓存循环与 10,000 次配方重载通过。真实服务端中，33 台兼容机器同场完成 3000 轮路由，8 台供方完成 300 轮、2400 次背压交付/逻辑重载检查，另有 24 次完整供方实体重建和 40,000 次无变化持久化检查。最终 66 项 GameTest 回归保留这些测试，持续轮数采用默认 10 轮；300 轮扩展结果来自前次专项运行。测试启动阶段出现过落后告警，仍需单独测量长期 TPS 和内存。

---

## 1. Extended Crafting — Ender Crafter
- 结构：Ender Crafter + pattern（如 4×铁锭 → 铁块类配方）。
- 正向：provider 推单 → 机器开始 → 完成后输出被回收进 ME。
- 部分回收：派发前手动在某个输入槽塞一个与 pattern 不同的物品使提交部分失败 → 已塞入的输入应被退回，且 crafter 未开始。
- 输出门控：完成后立即手动再放一个同类超额物品 → 只回收承诺数量，超额留下。
- 补偿边界：accept 插入后立刻拔走一个 stand 的物品（机器未开始）→ 严格补偿只退回精确匹配的那份。

## 2. Extended Crafting — Flux Crafter
- 同 Ender 的四条；额外验证 Fe(能量)不足时派发被模拟拒绝、无输入进入。

## 3. Extended Crafting — Combination Crafter
- 正向派发后：core 里是"结果"，pedestal 里是催化剂——只回收结果，催化剂不被当成输出吸走。
- 结果精确匹配：手动放入不同 NBT 的同类物品 → 不回收（fail-closed）。

## 4. Extended Crafting 桌子（Basic/Advanced/Elite/Ultimate）
- 用一个含**替代输入**的配方（如铜锭/锡锭二选一的 tag）编码 pattern：两个备选都应能实际绑定成功（不再是只试 `possible[0]`）。
- 大配方（如 5×5/7×7/9×9）全 slots 填满时绑定成功。

## 5. Draconic Evolution — Fusion Crafting
- 正向：核心+injectors 派发 → `startCraft` 确认进入 crafting → 完成回收。
- 非消耗输入：配方声明不消耗的 injector 输入，完成后应被精确退回，injector 不留残物阻塞下一单。
- 启动失败：占用 core 使 `startCraft` 失败 → 输入精确退回，不留 ownership。
- 不确定来源：合成进行到一半时手动投入额外物品 → fail-closed，不清理 injector。

## 6. Mystical Agriculture — Infusion / Awakening Altar
- 正向：activation 成功后 ownership 记录；完成后只从 seed 输出位回收，数量不超过 expected。
- 无 state 时不回收：手动完成的 altar 产物留在原地。
- NBT 不同（附魔种子等）→ 不回收。

## 7. Botania — Terra Plate
- 正向：派发 → 输出实体生成 → 按 baseline 增量回收。
- 预存在实体：派发前在盘上放一个同类物品实体 → 不被误收。
- 手动拿走输出后：stale state 不会被无声永久锁死（有诊断路径，后续派发可继续）。
- 外部同类实体：派发后旁边丢入同类实体 → 不吸（无完整来源证明时不抽取）。

## 8. Botania — Runic Altar
- 正向派发 → 完成 → 回收。
- 遗留 flag：先手动打断一次合成制造 stale flag → 只在祭坛真正空闲且无有效 ownership 时才清理并允许新派发。
- 旧 flag-only 存档数据 → fail-closed，不按当前 live recipe 取货。
- void reagent 配方部分失败 → 已投入的可退试剂精确退回。

## 9. Botania — Petal Apothecary / Mana Pool / Alfheim Portal
- 正向各一轮；确认 virtual lane 限流生效（同刻多单不超每 tick 上限）、lane 准入前无副作用。

## 10. Ars Nouveau — Imbuement Chamber / Enchanting Apparatus
- 正向各一轮（Imbuement 的 `getInput`/`getPedestalItems` 走字段回退路径也应正常）。
- Apparatus 含 `hasSourceNearby` 检查的配方在无源质处被拒绝。

## 11. Occultism — Ritual（Golden Bowl）
- 正向：sacrifice/物品使用完成路径派发成功（proxy 状态走 hierarchy-aware 字段查找）。
- 无法验证 upstream ritual 状态时不支付材料、不静默卡死（有 WARN）。
- 激活独占：同一祭坛同刻两单 → 只有一单激活。

## 12. Occultism — Spirit Fire
- 正向一轮；扔物实体的回收范围与归属验证。

## 13. Malum — Spirit Infusion / Focusing
- 正向各一轮；output ownership 门控同 MA。

## 14. Mekanism 系（Applied Mekanistics / MekMM 机器）
- 化学/流体/物品多端口机器正向派发；提取按 tank-indexed 精确匹配（配同类型双 tank 机器，确认不会从错误 tank 抽）。
- 超大数量 pattern（>int 范围）→ fail-closed 拒绝派发而非截断。
- 同类型多端口歧义机器（无法证明哪个输入进哪个口）→ 拒绝派发。
- partial compensation：机器 idle 时撤单 → 已确认输入逐口退回；机器已启动 → 不再回收。

## 15. Actually Additions（装 Actually Subtractions 1.2.14.20）
- Empowerer：正向派发；4 个 display stand 有残留物品时绑定被拒；能量不足时模拟拒绝。
- Atomic Reconstructor：虚拟批次派发 + 每次冲刷扣能（BASE 1000 FE/次）；批量多单一次冲刷。
- 两条 packaged_core 配方（empowering/laser 类型）可正常合成 packaged core。

## 16. Avaritia（按安装的 fork 二选一预期）
- 装 **AvaritiaNeo (byAqua3)**：极限合成桌使用虚拟批次路径；匹配配方成功派发并回收承诺输出，错误配方或不匹配输出拒绝派发。开服日志应无本 mod 的 packaged core 配方 ERROR（条件门控生效）。
- 自动化 ME CPU 回归直接读取上游原生 `endest_pearl`（9×9 有序）及 `cosmic_meatballs`（无序、每次两个产物）配方，各执行一次及三次合成订单；检查手动占用桌面时等待且不动手动物品、清空后恢复派发、精确材料与产物数量、完成后供方 NBT 重载无重复交付，以及原料耗尽后报告全部缺料并拒绝下单。
- 装 extreme-smithing 系 fork：4 张合成桌 + 极限锻造台 packaged core 配方可合成，桌面派发正常。

---

## 压力场景（可选）
- 同一 provider 挂多个 pattern 连续派发，观察 virtual lane 限流与无交叉污染。
- 派发中途断电/卸载区块 → 恢复后无幽灵 ownership（下次派发不受阻）。

---

## 17. 自动化连接压测（/ae2ltdiag connections）

已内置自动化连接压测，覆盖"适配器 ↔ 目标 mod 连接点"：放真实机器方块 →
`MultiblockAdapterRegistry.find` 循环 300 次识别断言 → 生产同款
`AdapterRecipeTypes.find` 解析配方类型 → 支撑方块反向不识别 → 全部机器
同场共存重复路由 → 空闲 `extractOutputs`/`bind`/`planWithBinding` 失败关闭
（无归属不得收货，未匹配 dummy pattern 不得绑上或改世界；虚拟 plan 不
`commit()`）。这不是 MANUAL_TESTING 1–16 的整张 ME 网正向派发。运行方式：

```
/ae2ltdiag connections      # OP 权限 ≥ 2；在主世界执行
```

	方块在世界出生点附近放置并还原原始方块/方块实体（火焰类方块临时垫圆石并还原），
	conn38 额外等待 40 tick 再复扫。命令在 settling 期间返回 0（未完成），最终结果
	在 40 tick 后的 `ae2ltdiag finished` 消息里。日志标记 `[ae2ltpp-conn]`，结尾输出
	`SUMMARY pass=N fail=M`。不要把 Gradle 单测或 GameTest 结果当成完整 ME 网络实机派发的替代品。

已验证的固定结论（也编码在测试预期里）：
- **Avaritia 双 fork 分流**：AvaritiaNeo (byAqua3) 的
  `avaritia:extreme_crafting_table` 走虚拟批次路径，不依赖另一 fork 的
  `TierCraftTile`；tiered table 系列与极限锻造台只在提供对应类的 fork 中启用，
  缺席时保持失败关闭，不将两套实现混用。
- Occultism 的 `spirit_fire` 无方块实体，适配器按方块状态识别（已验证）。
- 当前集成测试环境注册 26 个活跃适配器，并验证全部预期目标模组方块在线，无 mod-loaded 门失效。

## 18. Forbidden Arcanus — Clibano

- 火焰门槛：普通火焰且灵魂槽为空时，灵魂火焰配方拒绝派发；补充 soul 后允许派发，enchanted_soul 满足附魔火焰门槛。当前高等级火焰也可处理低等级配方。
- 实际合成：燃料另供；`gilded_darkstone` 合成 `deorum_nugget` 后仅回收一次，原料未消耗或合成未完成时不回收，归属恢复后仍能回收，手动放入的后续输出留下。残渣合成会共用输出槽；同类输出超过承诺数量时只回收承诺部分，余量须另行取出才能继续派发。
- 自动回归枚举所有已加载炽炉配方及当前火焰、排队灵魂材料组合，计划阶段不修改原料槽；生命周期回归实际等待机器自然 tick 完成合成。
- 生命周期回归额外在合成开始前放入同类手动物品，验证原料尚在时不会回收；实际合成完成后再增加两个同类物品，确定性验证限量回收、余量守恒与归属只消费一次。
- 上游 1.20.1-2.2.6 的外围组件 `load` 仅在已有 level 时恢复 `replaceState`。已增加可选 `ClibanoBlockEntityLoadMixin`，在绑定世界前使用方块注册表恢复 NBT 中的原状态，避免读档后拆炉传入空状态。组件已绑定世界时保留上游加载路径；开发与生产方法名已核对。补丁不会恢复先前已经丢失 `State` 的存档信息。
- 读档回归先复现首轮重建丢失楼梯朝向，修复后 5 个真实外围组件各执行 100 轮 `BlockEntity.loadStatic` 重建（共 500 次），每次验证绑定世界前及绑定后读档、主方块方向保存，并检查最终拆除恢复楼梯、原木等方块属性。此回归覆盖真实 NBT 创建路径，不等同于客户端操作、磁盘重启或实际区块卸载验收。
- 磁盘重启回归通过：首次进程保存 5 个外围组件后正常停服，第二进程启动后拆除主方块，原木、石砖、圆石、苔石及带朝向的石砖楼梯全部恢复。先前已经丢失 `State` 的存档信息无法由补丁重建。

## 19. Blood Magic — 血祭坛

- 正向：安装祭坛核心，准备满足配方等级的祭坛和足够 LP，编码加工样板；承诺输出应精确进入 ME。
- 无 LP、等级不足、配方或承诺输出不匹配时不消耗输入；无归属的手动产物留在祭坛。
- 自动化回归使用 4000 LP 完成两次石头到空白刻印的真实 ME CPU 订单，检查精确输入、输出、空闲 CPU、祭坛清空、供方重载无重复交付及耗尽原料后拒绝下单。

## 20. Forbidden Arcanus — Hephaestus Forge

- 物品仪式：准备正确底座、八个基座及精华；派发、激活、完成和回收均须保持配方与产物归属一致。
- 缺少精华时 CPU 等待，主槽和基座保持为空；补足后正常派发，不提前消耗原料。
- 自动化回归覆盖两次铁磁混合物订单，每次消耗五种原料、100 Aureal、1250 Blood 和 2 Souls；仪式中及完成后重载供方，产物精确入网，槽位和归属清空，原料耗尽后拒绝提交。
- 升级仪式使用升级凭证进行完成确认；另行验收凭证归属、重载及重复订单，尚未完成这一类仪式的真实 ME CPU 下单测试。

## 21. 兼容模组自动化覆盖矩阵

下表对应 `enable_integration_runtime=true` 的固定 Forge 1.20.1 依赖组合，不代表其他版本或同名 fork 已通过验证。连接回归检查真实机器识别、配方类型路由、同场共存、未匹配样板拒绝派发及无归属输出不回收；不能将这一层覆盖当成所有配方的正向生产验收。

| 模组 | 测试版本 | 连接与失败关闭 | 真实 ME CPU 正向生产 |
| --- | --- | --- | --- |
| Actually Subtractions（Actually Additions API） | 1.2.14.20+mc1.20.1 | 覆盖 | 尚未覆盖 |
| Ars Nouveau | 4.12.6 | 覆盖 | 尚未覆盖 |
| Mystical Agriculture | 7.0.9 | 覆盖 | 尚未覆盖 |
| Extended Crafting | 6.0.5 | 覆盖 | 尚未覆盖 |
| Occultism | 1.100.0 | 覆盖 | 尚未覆盖 |
| Draconic Evolution | 3.1.2.621 | 覆盖 | 尚未覆盖 |
| Malum | 1.6.7 | 覆盖 | 尚未覆盖 |
| Botania | 1.20.1-456-forge | 覆盖 | 尚未覆盖 |
| Mekanism / Mekanism More Machine | 10.4.16.80 / 1.2.1 | 覆盖 | 尚未覆盖 |
| AvaritiaNeo | 1.1.6-forge | 覆盖 | 原生 9×9 有序及无序配方 |
| Blood Magic | 3.3.8-50 | 覆盖 | 一级祭坛空白刻印 |
| Forbidden Arcanus | 2.2.6（Valhelsia Core 1.1.2） | 覆盖 | Clibano 金粒、Hephaestus 铁磁混合物仪式 |

- 全部 33 台预期机器同场路由通过；安装全部依赖的环境中运行 70 项必需 GameTest。
- Avaritia 的另一套 extreme-smithing fork 不在此环境中，其缺席保护不等于安装后的完整生产测试。
- Malum 1.6.7 已知四类、九种金属的条件配方进度缺少 `advancement` 或使用空 `criteria`/`requirements`。兼容补丁仅修复这些生成缺陷，补充持有实际输入物品的解锁条件及原配方奖励，保留原标签条件和正常/自定义定义；真实回归检查条件满足时进度和对应配方都存在，不满足时进度仍不加载。此前相关进度解析错误不再出现。
- Mekanism More Machine 1.2.1 空复制配置返回 null，导致 JEI 配方注册中止。可选 Mixin 仅将 null 返回值变为独立、可变的空映射，不清空非空配置，也不修改配置文件或依赖 JAR。客户端实际检查七个 JEI 分类：空配置两类复制配方为零；独立非空配置中石头和水各一条配方均可查询，两轮客户端各通过 70 项 GameTest，此前 JEI 空指针不再出现。
- 这些补丁不是全局日志屏蔽；其他依赖中引用未安装可选模组的配方错误仍可能出现。

## 发布验证范围

- 最终完整构建、282 项单测及 70 项必需 GameTest 通过；重复增量打包保留 Mixin refmap，并在缺失编译映射输出时重新编译或拒绝打包。
- 发布 JAR 的 546 个归档条目均可读取，包含 Mixin 配置、refmap、`META-INF/LICENSE` 与 JarJar 元数据，无客户端模拟辅助类、开发日志或 SNBT 测试结构。
- 未启用可选兼容模组时，基础服务端正常启动、停止及保存，缺席目标的可选 Mixin 被跳过。
- 完整兼容依赖客户端加载 36 个模组，在真实集成服务端世界中通过全部 70 项 GameTest（包括以上 ME CPU 订单），渲染 32 个核心图标，并通过客户端交互打开常规及无线供方界面，正常保存、返回主菜单和退出。全新配置客户端此前亦到达主菜单；这些自动化验证不等同于所有配方的手动验收。
- GuideME 20.1.15 曾出现读取未加载配置的时序异常，后续启动未复现；尚未证明根因已修复。
- 通电 ME 网络恢复测试覆盖两种供方的真实存储：没有存储元件时保留全部积压，安装存储元件后精确回收，并在交付前后重载而无重复输出。
- 仍需验收其他可选配方族的完整 ME CPU 链路、升级仪式、实际区块卸载与长期 TPS/内存；现有行为压力测试不能替代这些检查。
