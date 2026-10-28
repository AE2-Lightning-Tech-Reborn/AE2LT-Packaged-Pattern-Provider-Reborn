# 赫菲托斯锻炉封包验证

验证日期：2026-10-28。环境：Minecraft 1.21.1、NeoForge 21.1.230、Java 21、Valhelsia Core 1.1.4、AE2LT 2.1.2-beta4、Thunderbolt 2.0.3-beta4。

## 结果

| 环境 | 验证范围 | 结果 |
| --- | --- | --- |
| Forbidden Arcanus 2.6.1 | 核心组 30 项，加锻炉专项 37 项 | 67 / 67 |
| Forbidden Arcanus 2.5.17 | 核心组，包含 7 项 FA 测试 | 30 / 30 |
| JUnit | 当前项目单元测试 | 138 / 138，无失败、错误或跳过 |
| 发布 JAR | `ae2ltpp-1.2.2-beta3.jar` | 构建成功，不包含测试类、测试配方或第三方模组类 |

这 97 次 GameTest 执行包含两版本重复运行的基础场景，不能视为 97 个不同的锻炉配方。核心组还包含通用核心制作、冶炼炉和黑洞测试。2.5.17 仅运行基础组；专项中的新护甲转化与 `transmute_input` 测试配方要求 2.6.1。

GameTest 在真实游戏服务端加载原生模组、注册表、方块实体和 ticker。仪式产物、精华转换与去附魔均由 FA 原生加工完成，没有直接向输出槽写入预期产物。此次不包含 GUI 渲染或人工鼠标操作验证；JEI 验证覆盖其原生配方数据生成。

## 发现并修复的问题

1. 主材料与基座材料可以是同一种物品的不同组件版本。原先先分配原料、再匹配产物，可能把另一把附魔工具选为主材料，导致合法的转化样板无法绑定。现在先按主材料要求及预期产物组件选择主材料，再为基座分配余下原料；绑定与实际派发均重新验证。两个相反组件版本的测试均能完成原生转化。
2. FA 2.5.17 的 `CreateItemResult` 没有 2.6.x 的 `getResultItem(ItemStack)`。新增兼容读取，并供仪式绑定和 JEI 数据共用。旧版升级仪式没有物品产物，读取时返回空，不调用会改变世界的 `apply`。
3. 原测试初始化脚本的相对 JAR 路径可能按脚本目录解析，文件未加载时 FA 场景又会按模组缺席通过。现在明确按项目目录解析并校验文件，专项入口同时强制要求 FA 实际加载。

项目当前默认 Thunderbolt 配置为 beta3，而本地 AE2LT JAR 要求 beta4；本次仅通过命令参数覆盖测试依赖，没有修改全局版本配置。

## 专项覆盖

- 全部 18 种原生物品产出仪式：十种常规产物，以及 Draco Arcanus、Tyr 各四件护甲。护甲主材料带名称、附魔和损伤值，核对原生产物组件及四类精华精确扣费。
- 八基座输入、主材料与八个基座完全同物品、零精华成本、三件产物及带组件产物。
- 两把不同名称、附魔和损伤值的同种工具分别作为主材料，确保转化保留正确工具的数据。
- 四槽同时转换、物品原生最大堆叠、带 57 点血液的部分容器、去附魔工具及保留诅咒；核对随机经验范围、余料组件、原有点数保留和一次性回收。
- 转换中与仪式中保存重载；精华不足、满容量和刚好装满、错误产量、超量堆叠、同槽两种输入、实际输入变化及被占用槽位。
- 缺少底座、第八个基座、等级或强化物品时拒绝投料；运行中不覆盖；玩家替换成同物品同数量但不同组件的产物时不回收。
- 真实 ME 网络模拟可用、实际只扣出部分点数时回滚物品及点数；网络拒收退款时把已扣点数保存在锻炉，核对总量守恒。
- 真实 AE2 CPU 执行“原料转精华，再进行仪式”的链路，以及首个锻炉核心制作后自动安装。
- 原生四种锻炉等级升级改变世界方块，没有背包物品产物，不能编码为“产出一台高等级锻炉”的处理样板；验证此类样板被拒绝且不消耗材料。

三个专项测试配方只存在于 `src/forgeTest/resources`，使用 FA 的真实仪式序列化器及原生 ticker，设置 40 tick 以缩短边界测试。它们不会进入发布 JAR。

## 复现

在项目根目录执行，替换两个 JAR 路径；相对路径现在也按项目根目录解析。

```powershell
$env:JAVA_HOME = 'C:\JAVA\jdk-21'
.\gradlew.bat --no-configuration-cache --console=plain `
  -I scripts/core-test.init.gradle -I scripts/forge-test.init.gradle `
  '-Pthunderbolt_maven_notation=com.moakiee.thunderbolt:thunderbolt-reborn:2.0.3-beta4' `
  '-PppForbiddenJar=C:\path\forbidden_arcanus-2.6.1.jar' `
  '-PppValhelsiaJar=C:\path\valhelsia_core-neoforge-1.21.1-1.1.4.jar' `
  test jar runGameTestServer
```

2.5.17 基础回归去掉 `-I scripts/forge-test.init.gradle`，并切换 FA JAR。必须看到目标模组版本被加载、`All 67 required tests passed`（基础组为 30）以及成功退出；单独的 `BUILD SUCCESSFUL` 不足以证明 GameTest 执行或 FA 场景未跳过。

专项世界位于 `build/forge-native`，基础组世界位于 `build/core-native`，与正常游戏存档分开。本次日志位于 `build/fa-qa/final-2.6.1.log` 和 `build/fa-qa/final-2.5.17.log`；JUnit 报告位于 `build/reports/tests/test/index.html`。
