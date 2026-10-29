# 乐事烹饪锅封包（Minecraft 1.21.1 / NeoForge）

## 范围与使用

本次支持农夫乐事普通烹饪锅和无尽乐事终极烹饪锅。砧板、炉灶、煎锅、作物提取机和其他附属独立设备不在本次实现范围。

- 普通锅：`farmersdelight:cooking_pot`，6 个食材槽、6 号未盛装食物槽、7 号餐具槽、8 号成品槽。
- 终极锅：`avaritia_delight:extreme_cooking_pot`，81 个食材槽、81 号未盛装食物槽、82 号餐具槽、83 号成品槽。
- 核心：`ae2ltpp:fd_cooking_pot_packaged_core`、`ae2ltpp:ad_extreme_cooking_pot_packaged_core`；终极核心同时覆盖普通锅。
- 支持相邻供应器和无线供应器。保留锅的热源，将供应器设为自动回收。
- 样板输入为一份原生配方的全部食材，以及每份成品一个碗、瓶等配方容器；输出为原生配方的成品及数量。多份重复食材会分开投入食材槽。容器物品本身若也是食材，会额外计量。
- 每口锅只接受一份配方，锅内食材、未盛装食物、餐具和成品均清空后再接受下一份。不与手动烹饪或其他投料管道混用。更大订单由 CPU 重复派发或连接多口锅处理。
- 复用 `farmersdelight:cooking` / `avaritia_delight:extreme_cooking` 类型的附属和数据包配方通过原生配方管理器匹配。独立配方类型、独立锅实体和动态食物制作不据此宣称支持。

普通核心在普通锅内由基础核心、工程处理器、福鲁伊克斯水晶、铁锭、红石烹饪得到；终极核心的原生配方在终极锅内消耗普通核心及六种材料，也提供既有的供应器原位升级配方。制作核心没有餐具消耗。原生配方输入旧核心时属于制造备用核心，产物回网；原位升级由既有核心订单流程安装结果。

## 原生规则与材料归属

适配器走真实投料和原生 tick，保留加热判断、烹饪时间、原生餐具消耗和配方优先级。只提取成品槽，禁止从未盛装食物槽拿取结果。食材容器余物仍按上游原规则弹出锅旁，可另接漏斗收集；不会扫描周围掉落物，也不会吞取玩家手工掉落的物品。供应器关闭自动回收时仍可用原生管道取走成品。

使用两个锅的公开 `getInventory()` 获取已核实的槽位布局，并通过 `IItemHandler` 插入和提取，保留原生库存变更回调。无尽乐事 1.6.3 的侧面 capability 复用了农夫乐事只处理 0–5、7、8 槽的 `CookingPotItemHandler`，无法表示终极锅布局，因此不能拿其侧面视图作为完整库存。

终极锅先检查前六槽的普通配方，再检查 81 槽的终极配方。终极配方尽量从后方食材槽开始布置，投料前后均确认原生优先匹配仍是绑定配方；若 81 格配方被前六格的普通配方遮蔽，则拒绝派发，避免错误消耗全部食材。数据包重载后旧配方对象失效，旧绑定不能继续投料。

输入和输出按实际键及组件核对。ID_ONLY 输出只放宽匹配，不修改机器真实产物。输入数量求和、乘法检查溢出。81 格重叠 ingredient 使用增广路径匹配，不做阶乘级回溯。

部分投料仅从原机器、原槽位回收已确认插入的精确物品；剩余材料由既有 `DispatchExecutor` 保留。异步烹饪过程和餐具状态由上游方块实体保存，已提取输出由供应器现有持久化队列负责。本次不新增旁路配方执行器或隐藏加工库存。

## 验证

本次验证结果：138 项单元测试通过；安装上述两个乐事模组时 11 项原生 GameTest 通过；不安装乐事时，启动、注册隐藏与条件配方检查通过（其余依赖模组的用例跳过）。产品 JAR 不含测试类和测试配方。

原生版本：Farmer's Delight 1.21.1-1.3.4、Avaritia's Delight 1.6.3、Re-Avaritia 1.4.2-release、NeoForge 21.1.230，配套 LT 2.1.2-beta4 / TB 2.0.3-beta3。

测试入口：`scripts/delight-test.init.gradle`。测试资源和可选模组均不打入产品 JAR。

```sh
JAVA_HOME=/path/to/jdk21 bash ./gradlew \
  -I scripts/delight-test.init.gradle \
  -PppDelightModsDir=/path/to/optional-mods \
  -Pae2lt_jar=/path/to/ae2lt.jar \
  -Pthunderbolt_maven_notation= -Pthunderbolt_jar=/path/to/thunderbolt.jar \
  test build runGameTestServer --no-configuration-cache
```

`ppDelightModsDir` 只选取 `FarmersDelight-*`、`avaritia_delight-*`、`Re-Avaritia-neoforge-*` 和 `architectury-*` JAR。省略该属性可以验证未安装两个乐事模组时的加载和条件配方。原有 EC 单元测试所需依赖仅保留在单元测试类路径。

覆盖重复食材分槽、81 格分配、餐具缺失/过量/错误类型、严格组件匹配、实际输入复核、无热源、占用库存、成品槽与未盛装食物隔离、原生耗时、原生配方优先级、部分投料退款、原生方块保存恢复、食材桶余物、初始核心订单和无线备用核心订单。纯单元测试另覆盖歧义材料重新分配及 81 格无法满足的重叠条件。
