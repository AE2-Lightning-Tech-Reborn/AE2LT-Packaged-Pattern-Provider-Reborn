# 终焉锻造台边界测试与探针

专用入口为 `scripts/smithing-test.init.gradle`。测试使用真实 Re-Avaritia 的配方类、反射接口、原生数据包配方和供应器，在 `build/smithing-gameTestServer` 的独立世界运行；测试类、故障注入和测试配方不进入发布 JAR。

## 运行

需要 JDK 21，以及符合本项目版本范围的 AE2LT 和 Thunderbolt JAR。Re-Avaritia 默认使用项目锁定的 1.3.9.9，允许通过 `-PppSmithingJar=/path/to/Re-Avaritia.jar` 替换本地版本，不修改发布依赖。

```powershell
$env:JAVA_HOME = 'C:\JAVA\jdk-21'
.\gradlew.bat --no-configuration-cache -I scripts/smithing-test.init.gradle `
  '-Pae2lt_jar=C:\path\ae2lt.jar' `
  '-Pthunderbolt_maven_notation=' `
  '-Pthunderbolt_jar=C:\path\thunderbolt.jar' `
  test build runGameTestServer
```

上述命令的 `test` 运行全部 JUnit，`runGameTestServer` 运行 16 项专项原生测试。将任务替换成 `runClient` 或 `runServer`，可在独立测试目录使用 `/test runall`。GameTest 必须明确报告全部必选测试通过，不能只看进程启动成功。Re-Avaritia 缺席不会被当成原生测试通过。

## 覆盖

| 层次 | 检查内容 |
| --- | --- |
| JUnit：5 项 | 五个输入的全部 120 种顺序；模板、基底和附加料角色重叠时回溯；同种材料占多个独立槽；材料缺失、超量与无解；全部 32,768 种角色资格图与独立的全排列判定器对照 |
| 原生接口 | 七种反射入口分别冷启动；五个输入槽均使用独立副本，修改原生输入不会污染源栈或其他槽 |
| 配方和组件 | 重复附加料、混合替代材料、打乱顺序；基底命名与损伤继承；配方结果组件覆盖；组件敏感 Ingredient 在绑定和实际投料时均校验；Re-Avaritia 自带无尽时钟配方与原生 `assemble` 对照 |
| 数量和类型 | 缺料、多料、错料、额外材料、流体输入、空输入、错误绑定句柄；错误产物 ID、数量、多输出、流体输出；原生一次产出两件时的比例和不可整除输出 |
| 批量算术 | 1、2、64、65、257、`Integer.MAX_VALUE` 次配方比例；超过当前单样板整数上限时迅速拒绝；跨计数器合并；输入数量乘数和合并加法的 long 溢出；成功或拒绝规划均不修改原输入计数器 |
| 故障注入 | 预览、模板/基底/附加料谓词、`matches`、`assemble` 的 `RuntimeException` 和 `LinkageError`；绑定后再注入故障；异常后健康配方仍可使用；移走工作台后旧绑定不能继续合成 |
| 供应器 | 有线、无线分别执行 257 次批量加同 tick 的 65 次普通投递；实际产物组件进入待回收队列；NBT 保存恢复后数量不变；配方重载清除旧绑定，恢复健康配方后重新绑定且不重复产出 |

32,768 个图和 120 种排列是 JUnit 方法内部的检查次数，不计为额外测试方法。故障配方替换在同步测试方法内用 `try/finally` 恢复全局 RecipeManager；不会跨 tick 将故障配方留给其他场景。供应器的 NBT 恢复后使用重新解码的当前样板对象，避免把已经移出的旧对象误当成可用样板。

## 探针证据

自动服务端运行前清空 `build/smithing-gameTestServer/smithing-probe.jsonl`。JSONL 包含场景、测试结构原点、tick、线程、Re-Avaritia 版本、绑定/规划是否成功，以及实际输入计数器和产物的 key、数量、组件。其他阶段包括七个 `cold-*` 入口、`inject` 异常注入、`counter-overflow`、`native-recipe` 和 `provider-complete`。手动客户端/服务端使用各自游戏目录中的同名文件，日志会追加。

原生 `recipeMatches`、`assemble`、`resultItem` 的异常现在通过现有 `CompatibilityDiagnostics` 记录一次，并保持拒绝合成；相同操作的重复错误会被抑制。测试中的 `smithing probe:` 异常为主动注入，须结合 GameTest 最终结果判断，而不是将这些预期日志当作崩溃。

绑定失败时先检查配方是否加载、样板输入的五槽比例和产物数量；绑定成功但规划失败时，比较实际 counters 的类型、数量、组件与配方要求。当前单样板比例上限为 `Integer.MAX_VALUE`，与供应器跨多份样板的批量数量不是同一个限制。

## 2026-10-31 本地结果

验证环境：Java 21、Minecraft 1.21.1、NeoForge 21.1.230、AE2LT 2.1.3、Thunderbolt 2.0.3、Re-Avaritia 1.3.9.9。

- JUnit：149 项，失败、错误和跳过均为 0；其中锻台匹配器 5 项，后续工作台共享边界新增 6 项。
- 锻台 16 项全部在统一虚拟合成组中通过；统一原生组 58 / 58、真实 KubeJS 组 62 / 62，见[虚拟合成与脚本兼容](VIRTUAL_TABLES_COMPATIBILITY.zh-CN.md)。
- `build` 成功，发布 JAR 检查未包含专项测试类、测试配方、结构或第三方 Re-Avaritia 类。
- 本地完整日志：`build/virtual-tables-native-validation.log`、`build/virtual-tables-js-validation.log`；JUnit 报告：`build/reports/tests/test/index.html`。

跨计数器加法溢出的复现输入为 `Long.MAX_VALUE + Long.MAX_VALUE + 5`：它回绕成 3，恰好冒充三份附加材料。`available` 和样板 `provided` 的合并均改为 `Math.addExact`，溢出即拒绝；乘法溢出的原有拒绝行为也有独立检查。

[统一专项](VIRTUAL_TABLES_COMPATIBILITY.zh-CN.md)覆盖真实 KubeJS 与 `/reload` 资源链，验证过期绑定修复，并复现上游锻台 schema 创建脚本配方失败；独立专项中的供应器测试直接调用重载钩子。未运行完整终端下单至 CPU 收货流程、多人网络、客户端画面或完整整合包的脚本。组件继承按现有终焉锻台策略保留真实结果，测试不把按 ID 接受输出等同于已验证 CPU 对不同组件产物的收货行为。
