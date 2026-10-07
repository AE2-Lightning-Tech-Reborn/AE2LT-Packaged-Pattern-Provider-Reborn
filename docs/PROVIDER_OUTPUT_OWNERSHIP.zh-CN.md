# 封包供应器：材料归属与恢复

本次保留适配器、绑定缓存、限速和冷却接口，集中整理收发材料的归属。

## 不变量

- 投料完全未接受时返回失败，输入仍由 AE2 CPU 负责；供应器不能再生成一份退款。
- 任意材料已进入机器后，供应器接受这次输入归属。实际投料不足时，保存当前材料余量、同目标后续材料、后续目标全部材料，避免 AE2 重试整组输入导致重复。
- 部分投料不执行 `DispatchPlan.onCommit`。只有全部材料实际投入后才执行该回调；回调异常会记录错误，不能再把已转移的材料报告为未投入。
- 后续插入调用在接收前抛异常时，已确认投入的材料仍保持归属；当前项和后续所有材料进入持久化余料队列。第一项尚未接收便异常时则返回失败，全部材料仍由 CPU 持有。
- 尚未交付的虚拟产物、已从机器提取的产物和投料退款，都归 `PendingOutputBuffer` 所有。
- 每次交付按返回的实际接受数量扣账。库存满或没有足够电量时不扣账；合并数量超过 long 上限时分段保存，不能整数回绕。
- 出队端必须返回真实接受数量，不能先修改库存后抛异常而不给出接受量。此约束与第三方插入接口一样，无法仅凭模拟插入构造跨模组事务回滚。

## 保存与移除

新增可选 NBT 字段 `ae2ltpp_pending_outputs`，使用 AE2 的 GenericStack 编解码保留键、组件和数量。旧存档的新旧队列字段均不存在时视为空队列，既有供应器 NBT 字段和注册 ID 不变。

与普通 batch 整合后，也读取旧版 `ae2ltpp_virtual_outputs` 作为迁移来源；若新旧字段同时存在，以新字段为准，防止重复记账。保存时仅写新字段并删除旧字段。`ae2ltpp_batch_inputs` 保持原格式及原用途，用于保存 Mek 整批接管后尚未交付的输入；这些材料继续向原端口重试，不与普通投料余料混用。

入队时标记方块待保存并唤醒网格 tick。正常卸载、保存和重启从同一队列恢复；正常拆除的物品掉落包含待交付物品，沿用 AE2 对不同键类型的掉落规则。`clearContent` 清理队列和瞬态音效状态。

历史版本已经遗失、且没有写入存档的产物无法据此恢复。

## 实现职责

- `PendingAmounts<K>`：纯数量归属与逐笔回执；不访问游戏世界。
- `PendingOutputBuffer`：AE2 键映射、NBT 和定期交付。
- `PackagedPatternProviderLogic`：触发收发、保存、掉落和电量消耗。
- `DispatchExecutor`：预检查、顺序插入、收集全部未交付材料；退款接收端拥有持久化责任。

部分投料后的机器可能尚未凑齐配方。这次修复保障未投材料可恢复，不承诺对任意第三方机器自动撤回已经投入的材料或完成该配方；需要时仍应清理机器内的部分输入。模拟阶段只用于预检查，不是对目标库存的预留。

## 验证

普通 `test` 包含 `PendingAmountsTest`，覆盖部分交付、满库存、断电式拒绝、后续交付异常、long 溢出、非法回执和快照恢复。项目原有测试继续运行。

真正的 Minecraft/AE2 类型和 NBT 在 `src/runtimeTest` 的 `ProviderOwnershipProbe` 中验证，避免普通测试中的 AE2 空壳类掩盖真实调用。测试源码独立，不打入产品 JAR。

使用 JDK 21，并通过既有 `ae2lt_jar`、`thunderbolt_jar` 属性指定配套依赖：

```sh
./gradlew runServer --no-configuration-cache -PownershipProbe \
  -Pae2lt_jar=/absolute/path/to/ae2lt.jar \
  -Pthunderbolt_maven_notation= -Pthunderbolt_jar=/absolute/path/to/thunderbolt.jar

./gradlew runServer --no-configuration-cache -PownershipProbe -PownershipProbeReload=true \
  -Pae2lt_jar=/absolute/path/to/ae2lt.jar \
  -Pthunderbolt_maven_notation= -Pthunderbolt_jar=/absolute/path/to/thunderbolt.jar
```

第一阶段创建并重置专用的 `build/ownership-test-server`，监听本机 `127.0.0.1:25587`；测试投料、退款、NBT、旧存档、掉落、满库存、电量和溢出，然后把未交付物品保存到磁盘并停服。第二阶段保留该目录，重新启动，验证恢复供电后恰好交付一次。

任务检查测试程序生成的完成标记；崩服、断言失败或没有完成标记都会让 Gradle 任务失败，而不是仅依据服务器进程退出码判断成功。

完整的本地依赖验证可叠加 `-I scripts/batch-test.init.gradle -I scripts/refactor-test.init.gradle`，并指定 `-PppRefactorModsDir=/absolute/path/to/test-mods`。批量与整合 GameTest 还需 `-PppBatchModsDir=/absolute/path/to/mekmm-and-table-mods`；执行 `test build runGameTestServer --no-build-cache`。测试模组目录应放在 `build/` 之外，以便执行 `clean`。

`src/refactorTest` 补充验证旧输出队列迁移、重复载入、组件和流体数量、普通投料异常前后的归属，以及真实 Mek 储罐在提供器重载后补足 27 + 32 输入。无尽工作台、无尽锻造台与灵火均验证实际 batch 入口，而非仅检查类或方法存在。
