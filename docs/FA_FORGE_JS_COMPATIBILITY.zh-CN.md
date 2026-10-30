# Forbidden Arcanus JS 锻炉配方兼容

## 问题与修复

蜂蜂空坊 Beebeeblock: The Skyhive v3.07.8 在 `kubejs/server_scripts/mod/forbidden/bac.js` 中使用 Forbidden Arcanus JS 添加屠夫蜜蜂蛋仪式。其产物为 `productivebees:spawn_egg_configurable_bee`，`minecraft:entity_data` 同时包含 `id: productivebees:configurable_bee` 和 `type: productivebees:butcher`。

Forbidden Arcanus JS 2101.1.3 将原生 `RitualManager.updateValidRitual` 的候选来源替换为 `RecipeManager` 内的 `forbidden_arcanus:ritual` 配方。封包以前只遍历 `forbidden_arcanus:hephaestus_forge/ritual` 注册表，因此无法绑定脚本新增的仪式；精华转换仍可执行，最终物品材料却不会发配。

新增 `FARituals` 统一配方枚举和有效性校验：安装 Forbidden Arcanus JS 时只使用它的 RecipeManager 来源，未安装时沿用 FA 原生注册表。绑定、派发及封包 JEI 数据共用该入口。派发核对当前 ID、配方类型及对象身份，拒绝配方删除或重载替换后的旧绑定。不会将已删除的配方从原生注册表重新引入，也不放宽产物组件匹配。

## 测试

`src/faJsTest` 是独立的真实模组 GameTest，不进入发布 JAR。测试脚本沿用 BBB 屠夫蜜蜂蛋的产物、精华成本、等级、两个重复基座输入及强化物品要求；仅将深血桶和仿制血肉替换为水桶和腐肉，以免依赖整个整合包。未加载目标模组会明确失败。

覆盖：

- 脚本新增配方只存在于 RecipeManager 时，绑定、JEI 展示、真实发料和原生 ticker 完成；带蜜蜂品种的完整产物组件一致，回收一次，四类精华精确扣费。
- 原生仪式运行中保存重载，验证 FA JS 的直接 Holder 序列化与封包回收状态。
- 错误蜂种、缺失组件、错误产量和缺少强化物品时拒绝投料。
- 删除已转入 RecipeManager 的原生配方，验证旧绑定、重新绑定和 JEI 均拒绝，不回退到仍然存在的原生注册表。
- 同 ID、同内容的新配方对象替换旧对象，旧绑定拒绝发料，新绑定可用。
- 真实 AE2 CPU 从零 Aureal、零 Souls 开始，先转换三瓶 Aureal 和十个灵魂，再执行脚本仪式并向 ME 交付正确蜜蜂蛋；核对原料与精华余额及订单结束。

同时运行不含 FA JS 的原生 FA 2.6.1 专项和 FA 2.5.17 基础回归，确保两种配方来源相互独立。

2026-10-30 验证结果（Minecraft 1.21.1、NeoForge 21.1.230、Java 21、AE2LT 2.1.2-beta4、Thunderbolt 2.0.3-beta4）：

| 环境 | 结果 |
| --- | --- |
| FA JS、KubeJS、Productive Bees 的新增兼容组 | 5 / 5 |
| 不含 FA JS，FA 2.6.1 核心及锻炉专项 | 67 / 67 |
| 不含 FA JS，FA 2.5.17 核心组 | 30 / 30 |
| JUnit | 138 / 138，无失败、错误或跳过 |

将旧版 `FAForgeAdapter` 和 `FARecipeViews` 放入隔离源码目录，使用相同的脚本与模组运行绑定、CPU 两个复现测试：两项均失败，分别停在“scripted bee pattern binds”和最终蜜蜂蛋未交付。恢复修复后的源码后，完整新增组 5 项通过。

此次日志为 `build/fa-js-baseline.log`、`build/fa-js-test.log`、`build/fa-js-regression-2.6.1.log` 和 `build/fa-js-regression-2.5.17.log`。构建的 `build/libs/ae2ltpp-1.2.2-beta3.jar` 包含兼容实现，不包含测试类、脚本或第三方模组。

## 复现

将以下测试依赖放入一个本地目录：

- Forbidden Arcanus 2.6.1、Valhelsia Core 1.1.4。
- Forbidden Arcanus JS 2101.1.3。
- KubeJS 2101.7.2-build.348、Rhino 2101.2.7-build.81。
- Productive Bees 1.21.1-13.6.5（自带 Productive Lib 1.21.0-0.1.9）。

JEI 由初始化脚本解析。上述模组仅用于测试，不改变发布依赖。执行：

```powershell
$env:JAVA_HOME = 'C:\JAVA\jdk-21'
.\gradlew.bat --no-configuration-cache --console=plain `
  -I scripts/core-test.init.gradle -I scripts/fa-js-test.init.gradle `
  '-PppFaJsModsDir=C:\path\fa-js-mods' `
  '-Pthunderbolt_maven_notation=com.moakiee.thunderbolt:thunderbolt-reborn:2.0.3-beta4' `
  runGameTestServer
```

必须看到 `All 5 required tests passed`，并确认 `forge.js` 成功加载、新增一条脚本配方且无脚本错误。单独的 `BUILD SUCCESSFUL` 不能证明服务端和测试实际启动成功。

世界和脚本位于 `build/fa-js-native`。完整 BBB 整合包及玩家实际存档不属于此次测试范围。
