# Re-Avaritia / Extended Crafting 虚拟合成边界与脚本兼容

本专项覆盖 Re-Avaritia 四级工作台、终焉锻造台和 Extended Crafting 四级工作台。统一入口为 `scripts/virtual-tables-test.init.gradle`，包含既有批量/重载样板回归、锻台专项，以及新增的工作台边界与真实 KubeJS 测试。测试代码、资源、原生依赖和脚本不进入发布 JAR。

## 已确认的问题与处理

### 配方重载后的旧绑定

修复前，工作台或锻台绑定持有配方 Java 对象；脚本删除或以同 ID 替换配方后，旧对象仍能用于虚拟合成。供应器的 `onRecipesReloaded()` 虽然存在，但源码中没有实际服务端事件调用它，仅手动调用该方法的测试无法覆盖真实重载缺口。负绑定也可能在脚本加入配方后继续缓存，直到 40 tick TTL 到期。

修复包括：

- 两组工作台和锻台在规划前核对配方 ID 及当前 RecipeManager 中的对象身份，拒绝被删除或被替换的配方。
- 供应器在查询绑定缓存前检查服务端不可变配方表的 values 视图身份。原生加载和 `replaceRecipes` 发布新视图后，统一清除正、负绑定并重新发现配方；普通投递只增加一次身份比较。
- 缓存快照使用弱引用，空闲供应器不会因此保留整个过期配方表。

真实 KubeJS 回归先为四个供应器（两种模组 × 有线/无线）分别预热成功和失败缓存，再改写测试目录内的脚本并调用 `MinecraftServer.reloadResources`，走与 `/reload` 相同的资源重载路径。测试不手动调用重载钩子、不重新插拔样板：删除的旧配方立即拒绝，新增配方立即接受。修复前实际重载回归失败，修复后四个组合全部通过。

### 跨计数器数量溢出

两组工作台与先前锻台相同，曾使用未检查的 long 加法合并实际输入。`Long.MAX_VALUE + Long.MAX_VALUE + 5` 回绕为 3，可能冒充合法三份材料。两组工作台均改为 `Math.addExact`，溢出即拒绝，输入所有权不发生转移。

### Re-Avaritia 的 KubeJS 锻台 schema

本地锁定的 Re-Avaritia 1.3.9.9 将 `template`、`base`、`addition` 声明为 KubeJS 的 `item_stack`；原生锻台序列化器实际读取 `Ingredient`。符合原生格式的 `{ "item": "minecraft:paper" }` 因而被脚本 schema 当作缺少 `id` 的物品栈。

真实运行确认了两个不同结果：

| 加载方式 | 本地结果 |
| --- | --- |
| 原始数据包 JSON，包括 `kubejs/data/ae2ltpp/recipe/js_smithing_raw.json` | KubeJS 报 schema 解析警告并回退原生加载；RecipeManager 中仍有配方，封包正常绑定与合成 |
| `ServerEvents.recipes` 中 `event.custom()` 创建同等原生 JSON | KubeJS 创建配方失败，RecipeManager 中没有该配方；封包没有可读取的条目 |

专项测试明确断言并记录此旧版本的**不支持路径**；“62 项测试通过”不表示损坏 schema 的 `event.custom()` 已受支持。可用的规避方式是将锻台配方放进原始数据包 JSON，不让它经过错误的脚本构造 schema。本项目没有注入或覆盖第三方 KubeJS schema。

上游已有 [issue #375](https://github.com/Nova-Committee/Re-Avaritia/issues/375) 报告同类警告，报告者当时未确认 gameplay 影响。本轮补充了脚本新建配方确实丢失的本地对照。[1.4.2 发布标签的源码](https://github.com/Nova-Committee/Re-Avaritia/blob/v1.4.2-release-neoforge/src/main/java/committee/nova/mods/avaritia/init/compat/kubejs/schema/ExtremeSmithingRecipeSchema.java)仍使用 `ItemStackComponent`；[当前开发分支](https://github.com/Nova-Committee/Re-Avaritia/blob/neo/1.21.1/src/main/java/committee/nova/mods/avaritia/init/compat/kubejs/schema/ExtremeSmithingRecipeSchema.java)已改成 `IngredientComponent`。未实测其他发布版本，不将开发分支修正等同于已发布且已验证的修复。

## 是否存在 FA JS 那样的配方来源变化

本轮检查的 Re-Avaritia、Extended Crafting 脚本配方都仍使用原生 RecipeManager 和 RecipeType：

| 配方 | serializer ID | RecipeType ID |
| --- | --- | --- |
| Re-Avaritia 有形状/无形状工作台 | `avaritia:shaped_table` / `avaritia:shapeless_table` | `avaritia:crafting_table_recipe` |
| Re-Avaritia 锻台 | `avaritia:extreme_smithing` | `avaritia:extreme_smithing_recipe` |
| EC 有形状/无形状工作台 | `extendedcrafting:shaped_table` / `extendedcrafting:shapeless_table` | `extendedcrafting:table` |

真实脚本探针记录了 recipe ID、Java 类、serializer 和 type，新增脚本工作台配方正常绑定。没有在本轮版本中发现 FA JS 那种需要切换配方仓库才能读取的机制；schema 创建失败、原生 tier 限制和过期缓存已经能造成“脚本改过后读不到/仍读旧配方”的表象。

Re-Avaritia 自带 [KubeJS 插件](https://github.com/Nova-Committee/Re-Avaritia/blob/neo/1.21.1/src/main/java/committee/nova/mods/avaritia/init/compat/kubejs/AvaritiaKubeJSPlugin.java)，无需另装一个 FA JS 风格附属。Extended Crafting 的 [CraftTweaker 集成](https://github.com/BlakeBr0/ExtendedCrafting/blob/1.21/src/main/java/com/blakebr0/extendedcrafting/compat/crafttweaker/TableCrafting.java)创建原生 table recipe，并通过 transformer 实现工具/材料余料；本轮用相同的原生 transformer API 验证了有形状和无形状配方的命名、损伤工具返还。其 [DynamicRecipeManager](https://github.com/BlakeBr0/ExtendedCrafting/blob/1.21/src/main/java/com/blakebr0/extendedcrafting/crafting/DynamicRecipeManager.java)注册的是奇点压缩配方，没有另建工作台配方仓库。

另有名为 [Kubejs Extended Crafting 的附属](https://www.curseforge.com/minecraft/mc-mods/kubejs-extended-crafting/files)，其作者发布页面当前列出的是 Minecraft 1.20.1 Forge 文件，不能直接当成此项目 1.21.1 NeoForge 的兼容证据。这里验证 EC 使用真实 KubeJS 的 `event.custom()` 原生 JSON 路径，不声称覆盖了所有第三方附属。

## 测试覆盖

| 组 | 内容 |
| --- | --- |
| 新增共享 JUnit：6 项 | 空洞与矩形布局、精确组件栈副本、形状或材料数量不匹配、歧义材料分配回溯、long 余料计数、物理余料冲突/容量及裁剪偏移 |
| 新增工作台 GameTest：11 项 | 两种模组全部八种尺寸的满格配方；额外材料拒绝；矩形/空洞与过宽配方；原生精确 tier 与自动 tier；有形状/无形状材料；组件敏感原料；错料/少料/多料/流体/多输出；拆换和脏网格；整数边界和 long 溢出；桶余料及物理偏移；删除/同 ID 替换；原生异常；脚本 transformer 返还损伤命名工具 |
| 锻台 GameTest：16 项 | 原有五槽、反射冷启动、组件、算术及异常注入，加上旧锻台配方删除后的句柄失效回归，详见 [锻台专项](AVARITIA_SMITHING_TESTS.zh-CN.md) |
| 既有工作台/超载组：31 项 | 各级有线/无线、257 次批量、同 tick 65 次投递、超大 CPU 批量、容器余料、持久化、阻塞/锁定、逐输入/输出独立的严格与 ID_ONLY 模式、组件候选配方切换 |
| 真实 KubeJS：4 项 | 原生配方来源与类型、新增/删除/同 ID 替换、旧锻台 schema 的失败/数据包对照、四种供应器的真实资源重载 |

原生 tier 的正数表示精确等级，0 表示原生自动规则；高阶封包核心覆盖低阶核心，不代表机器可以忽略配方精确 tier。测试按原生 `matches` 结果判断，避免把 tier 设置错误误诊成配方丢失。

## 运行

需要 Java 21 和符合项目版本范围的 AE2LT、Thunderbolt JAR。以下使用路径占位符，按本机文件修改：

```powershell
$env:JAVA_HOME = 'C:\JAVA\jdk-21'
.\gradlew.bat --no-configuration-cache -I scripts/virtual-tables-test.init.gradle `
  '-Pae2lt_jar=C:\path\ae2lt.jar' `
  '-Pthunderbolt_maven_notation=' `
  '-Pthunderbolt_jar=C:\path\thunderbolt.jar' `
  test build runGameTestServer
```

真实 JS 组增加 `-PppVirtualKubeJsDir=C:\path\kubejs-mods`。该目录须包含真实 KubeJS、Rhino JAR；脚本只加入匹配这两个名称的文件，不会顺带加载同目录内的 FA 等模组。

运行目录分别为 `build/virtual-tables-native` 和 `build/virtual-tables-js`。脚本仅复制、改写这些可丢弃目录内的测试文件。JS 重载测试会将测试脚本的 revision 从 1 改到 2，检查后恢复文件；下次进程启动仍从 1 开始。测试不读取或重载玩家世界。

原始 JSON 绕过示例可直接参考 `src/virtualTableJsTest/kubejs/data/ae2ltpp/recipe/js_smithing_raw.json`；原生数据包目录为 `data/<namespace>/recipe/`，Minecraft 1.21.1 使用单数 `recipe`。

## 2026-10-31 验证结果与证据

环境：Java 21、Minecraft 1.21.1、NeoForge 21.1.230、AE2LT 2.1.3、Thunderbolt 2.0.3、Re-Avaritia 1.3.9.9、EC 7.0.8、Cucumber 8.0.16；JS 组为 KubeJS 2101.7.2-build.348、Rhino 2101.2.7-build.81。

- JUnit：149 / 149，失败、错误和跳过均为 0。
- 不带 KubeJS 的原生组：58 / 58。
- 带真实 KubeJS 的组：62 / 62，其中包含明确断言上游 schema 不支持路径的测试。
- `build` 成功；发布 JAR 未含测试、脚本、结构或 Re-Avaritia/EC 第三方类。

本地完整日志为 `build/virtual-tables-native-validation.log`、`build/virtual-tables-js-validation.log`。修复前的 `build/virtual-tables-js-baseline.log` 记录了旧配方句柄、计数溢出、真实重载缓存三项失败。

每次自动运行会清空对应游戏目录的 `virtual-table-probe.jsonl`、`smithing-probe.jsonl`。重点阶段：

- `js-recipe-source`：脚本配方实际的类、serializer、type。
- `js-smithing`：明确区分被 schema 拒绝的 custom 路径与正常原生 JSON 路径。
- `js-reload-complete`：应分别看到两组模组 × 有线/无线的四条成功证据。
- `bind` / `plan`：目标等级、原点、tick、接受结果和实际产物。

未启动完整 CraftTweaker 运行时、未逐一安装所有第三方附属，也未覆盖其他 Minecraft/模组版本。没有运行每一种新增边界配方的完整终端下单/CPU 收货流程；既有批量组继续验证真实 Thunderbolt 批量执行器和供应器路径。
