# AE2LTPP 实机派发验证清单（游戏内手动测试）

适用：AE2LTPP 1.2.0-beta.8+（分支 `fix/dispatch-compatibility-1.20.1`，7 个修复/优化提交）。
环境：Minecraft 1.20.1 + Forge 47.4.10 + AE2 15.4.10 + AE2LT/Thunderbolt beta.4 + 对应可选 mod。
每组测试都需要：ME 网络供电、Pattern Provider（本 mod 的多方块变体）、编码好的 pattern、目标机器。
启动时确认：日志无 `[ae2ltpp]` ERROR、无 `Reflection bootstrap failed`、装 Botania 时无 `missed critical handles`。

通用验收原则（每个适配器都适用）：
- 派发成功 → 输出在承诺数量内被回收，多余的同物物品留在机器里。
- 派发失败/部分失败 → 已确认塞入的输入被精确退回；无法证明安全的部分宁可留着（fail-closed），不会重复退款。
- 没有本 mod 派发记录时，手动/其他自动化放入的物品不会被误收。

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
- 装 **AvaritiaNeo (byAqua3)**：开服日志零配方 ERROR（条件门控生效）；适配器拒绝派发（fail-closed，符合设计）。
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
	`SUMMARY pass=N fail=M`。不要把 Gradle 单测或旧的 39/39 日志当成这次已实机跑过。

已验证的固定结论（也编码在测试预期里）：
- **Avaritia 双 fork 分流**：AvaritiaNeo (byAqua3) 注册了同名方块
  `avaritia:extreme_crafting_table`，但其 BE 类不是 fork 的
  `TierCraftTile` —— 适配器按设计保持惰性（不识别、不派发），
  4 张 fork 桌与极限锻造台在该服上正确缺席。
- Occultism 的 `spirit_fire` 无方块实体，适配器按方块状态识别（已验证）。
- 12 个适配 mod、23 个活跃适配器全部在线，无 mod-loaded 门失效。
