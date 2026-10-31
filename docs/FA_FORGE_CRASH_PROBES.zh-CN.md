# 赫菲托斯锻炉崩溃探针与分 tick 启动

## 原因和修复

封包适配器原先在同一个 tick 内投料并启动原生仪式。投料产生的方块同步尚未发出，仪式已进入运行状态，因此投料包同时带上 `active_ritual`。完成包省略该标签时，FA 客户端仍保留旧仪式，随后校验已清空的材料并进入服务端失败逻辑。客户端管理器没有服务端坐标，最终发生空指针和网络协议断线。

原生 FA 2.6.1 的复现调用链为：

```text
HephaestusForgeBlockEntity.onDataPacket
  → handleUpdateTag → loadAdditional
  → RitualManager.onDataChanged → failRitual
  → reset → setActiveRitual → ChunkPos
```

FA JS 2101.1.3 同样复现；它的 reset 防护取消了重置，但后续 failRitual 仍访问空坐标。服务端 GameTest 通过不能证明客户端同步安全。

时序对照采用真实 ServerPlayer 和原生锤子 `useItemOn`：投料后等待 20 tick 再敲击通过；将相同原生操作放在同 tick 则断线。封包路径关闭 GUI 和重载也能复现，说明这两个探针步骤不是必要触发条件。

修复只修改封包适配器，**不新增 Mixin**：

1. 投料 tick 预约物品、CPU 提供的精华和从 ME 提取的缺额，保存 `waiting` JOB；不启动仪式。
2. 后续 `ServerTickEvent.Post` 确认 gameTime 已推进，让投料时的非活动状态先同步，再检查结构、当前配方、物料及产物，调用原生启动方法。
3. 启动成功后转为 `ritual`，由原生 ticker 加工。四类精华逐类结算，某一类原生通知抛错不再阻断后续余额恢复。
4. 启动前校验失败转为 `refunding`，只收回仍与预约一致的物品，保存退款回执；玩家替换或取走的物品不会被删除或凭空补出。供应器只领取一次回执和预约精华。
5. 等待状态、目标位置及预约数量写入锻炉持久化数据；区块加载后重新登记，且再留出非活动同步 tick。启动不依赖输出轮询，因此关闭自动回收仍能启动。

故障注入还发现了独立的精华结算问题：初始 `[25, 3, 130, 17]`、成本 `[20, 1, 100, 10]`，应余 `[5, 2, 30, 7]`，首个结算通知抛错后旧实现实际为 `[5, 0, 0, 0]`。修复后的逐类隔离保留精确余额，已启动的仪式继续拥有物料，避免重复退款。顺序为 Aureal、Souls、Blood、Experience。

## 验证范围

测试环境：Minecraft 1.21.1、NeoForge 21.1.230、Java 21、Valhelsia Core 1.1.4、AE2LT 2.1.3、Thunderbolt 2.0.3。

| 环境 | 服务端 GameTest | 真实客户端 |
| --- | --- | --- |
| FA 2.6.1 | 81 / 81 | 分 tick 投料、等待状态重载、原生 GUI、法阵及产物渲染、一次性回收 |
| FA JS 2101.1.3 | 19 / 19 | 同上，加载真实 FA JS、KubeJS 与 Productive Bees |
| FA 2.5.17 | 44 / 44 | 同上 |

JUnit 138 / 138 通过，发布 JAR 构建通过且不含测试、探针、KubeJS 脚本或新增 FA Mixin。未安装 FA 的真实客户端也通过进入世界的可选依赖检查。

服务端新增 14 项探针覆盖：损坏缓存的派发入口、启动前后 RuntimeException / LinkageError、结算回调持续抛错、原生 ticker 异常边界、运行中取走主材料或基座物品、规划后替换锻炉、运行中新实体 NBT 重载、等待状态新实体重载且不轮询输出、等待状态替换主材料后的精确退款。

原生 ticker 注入项是边界诊断：测试在 ticker 外侧捕获人工异常并恢复缓存；它通过不表示原生 ticker 已具备异常隔离。所有人工异常带 `FA_FORGE_INJECTED_FAULT`，不能当作玩家存档真实根因。

客户端默认重载的是**等待启动状态**；服务端另测运行中状态的 NBT 重载。本修复避免封包同 tick 投料触发的错误同步，不修改 FA 客户端对缺失 `active_ritual` 的处理，也不保证玩家在仪式运行中重新接收活动状态的所有情况（例如重新进入区块）均安全。

真实客户端探针不注入异常，实际观察相机视锥内的锻炉并保存帧截图；未覆盖完整整合包、着色器或其他渲染模组。测试世界位于 `build/`，不读取玩家存档。探针及测试脚本不进入发布 JAR。

## 运行方式

原生 FA 完整服务端回归：

```powershell
$env:JAVA_HOME = 'C:\JAVA\jdk-21'
.\gradlew.bat --no-configuration-cache --console=plain `
  -I scripts/core-test.init.gradle -I scripts/forge-test.init.gradle `
  -I scripts/forge-probe.init.gradle `
  '-PppForbiddenJar=build/fa-qa/forbidden_arcanus-2.6.1.jar' `
  '-PppValhelsiaJar=build/fa-qa/valhelsia_core-neoforge-1.21.1-1.1.4.jar' `
  '-Pae2lt_jar=../AE2-Lightning-Tech-Reborn/build/libs/ae2lt-2.1.3.jar' `
  '-Pthunderbolt_maven_notation=' `
  '-Pthunderbolt_jar=../Thunderbolt-Core-Reborn/build/libs/thunderbolt-2.0.3.jar' `
  runGameTestServer
```

FA 2.5.17 去掉 `forge-test.init.gradle` 并替换 FA JAR。FA JS 使用 `core-test.init.gradle`、`fa-js-test.init.gradle`、`forge-probe.init.gradle`，将两个原生 JAR 参数替换成 `-PppFaJsModsDir=build/fa-js-mods`；该目录放入真实 FA、Valhelsia Core、FA JS、KubeJS、Rhino、Productive Bees 及其库。

FA JS 只转换 FA 命名空间中的仪式，因此专用 `forge_probe.js` 提供等价的封包核心仪式供共用故障探针使用；既有五项脚本测试仍使用 `forge.js` 的屠夫蜜蜂蛋配方，覆盖完整物品组件、配方重载及真实 CPU 链路。

真实客户端将服务端专项脚本替换成 `forge-client-probe.init.gradle`，任务改为 `runClient`；FA JS 仍需先加载 `fa-js-test.init.gradle`。首次运行需先用 `forge-test.init.gradle` 执行原生 GameTest，生成 `build/forge-native/world/level.dat` 临时世界种子。

| 客户端参数 | 行为 |
| --- | --- |
| `-PppForgeProbeRun=NAME` | 独立目录，NAME 仅允许字母、数字、下划线、连字符 |
| `-PppForgeProbeReload=false` | 关闭重载对照，默认封包模式重载等待状态 |
| `-PppForgeProbeGui=false` | 关闭原生 GUI 对照 |
| `-PppForgeProbeManual=true` | 原生玩家交互对照，投料与敲击相隔 20 tick，无封包 JOB |
| `-PppForgeProbeAbsent=true` | 不加载 FA，验证可选依赖缺失时真实客户端能进入世界 |

手动模式若启用重载，会重载运行中仪式；复现普通手动时序请同时设置 `ppForgeProbeReload=false`。

## 证据文件

服务端 JSONL：`build/forge-probe-server/forge-probe.jsonl`，FA JS 为 `build/forge-probe-js-server/forge-probe.jsonl`。每次运行前清空；包含阶段、线程、坐标、实体有效性、活动仪式、JOB、精华余额和异常根因。

最终运行日志：`build/forge-staged-native-final.log`、`build/forge-staged-js-final.log`、`build/forge-staged-old-final.log`；客户端日志为 `build/forge-staged-client-native.log`、`build/forge-staged-client-js.log`、`build/forge-staged-client-old.log`。三组客户端均写入 PASS，分别观察到 1487、591、1467 个法阵帧，以及各 36 个产物帧。

最终 JUnit、JAR 及原生 GameTest 联合检查为 `build/forge-staged-final-build.log`，可选依赖缺失对照为 `build/forge-staged-client-absent.log`。

客户端结果位于 `build/forge-probe-client-NAME/`：

- `forge-client-result.txt`：完成全部断言后才写入 PASS，Gradle 会检查结果，缺失不算通过。
- `forge-probe.jsonl`：应观察到 `inputs-reserved` 时 inactive、`started-next-tick` 时 active；封包对照断言不曾收到投料包中的活动仪式。
- `01-active-ritual.png`、`02-native-forge-gui.png`、`03-native-core-output.png`：真实截图。
- `logs/`、`crash-reports/`、`debug/disconnect-*-client.txt`：原生错误证据。

修复前关键对照：`build/forge-probe-packaged-native-no-gui-2.log`、`build/forge-probe-manual-native-delayed.log`、`build/forge-probe-js-client.log`。这些仅为基线，不能代替最终实现的验证结果。
