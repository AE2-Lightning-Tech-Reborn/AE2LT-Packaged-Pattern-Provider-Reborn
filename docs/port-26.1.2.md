# Minecraft 26.1.2

验证环境：Java 25、NeoForge 26.1.2.109、AE2 26.1.12-beta。此分支保留原有架构，仅适配不兼容的构建、存储、资源、渲染及模组 API。

## 构建

将三个仓库的 `26.1.2` 分支放在同级目录，目录名分别为 `Thunderbolt-Core-Reborn`、`AE2-Lightning-Tech-Reborn`、`AE2LT-Packaged-Pattern-Provider-Reborn`，按此顺序执行：

```sh
bash ./gradlew check jar
```

使用 Java 25。首次构建需要联网解析依赖；依赖已缓存后可加 `--offline`。LT 默认读取相邻 Thunderbolt 的构建 JAR，PP 默认读取相邻 LT 和 Thunderbolt 的构建 JAR；自定义位置可用项目 Gradle 属性覆盖。构建结果在各项目的 `build/libs`。运行所需的第三方模组不随这三个 JAR 一起分发。

## 适配与验证

基线 main：`eb75545bc42d1c759a2c87ab0d26dc5051777995`。

- 保留方块实体、样板供应器、批次缓冲与多方块适配器结构；适配注册、ValueIO/NBT、AE2 配方查询、客户端模型及菜单。
- LT 平面 NBT 桥接使用公共 `api.compat.ValueIO`；PP 保留自身 AEKey/物品序列化及客户端兼容代码。
- 补全 26.1 物品模型映射，迁移配方和真实联动模组的物品资源处理器、输入及产物查询接口。

2026-09-23 验证：`bash ./gradlew check jar` 的 69 项测试全部通过。使用 Extended Crafting 8.0.2、Cucumber 9.0.6、Mystical Agriculture 9.0.9、Re:Avaritia 1.4.2、Occultism 1.254.0 及相应依赖，联合开发 GameTest 26 项必需测试通过。

测试入口为 `bash ./gradlew -Pae2ltppBatchPortTest runBatchPortGameTestServer`；可选模组通过 `-Pport_optional_mods=坐标1,坐标2,...` 提供。`-PavailableAdapterProbe runServer` 另提供可选真实配方适配探针。测试源集与测试配方不进入发布 JAR。

GameTest 覆盖 EC、Re:Avaritia 批次产出与容器返还、MA 原生灌注/觉醒祭坛输入归属和产出、Occultism 灵火及原生仪式分发、代理费用、实体献祭和容器产物只提取一次。联合专用服务器加载通过；实际客户端完成普通/无线 PP 菜单及自动返还开关网络往返。

## 可选联动与限制

- EMI 暂不考虑。JEI 29.40.0.102 随 LT 完成联合客户端验证。
- 截至 2026-09-23，Actually Additions、Ars Nouveau、Mekanism More Machines 未取得可验证的 NeoForge 26.1.2 版本，相应适配器与配方源文件保留但排除出本次构建；物品注册 ID 保留，暂不可用的核心不显示在创造栏。相关测试同步排除。
- Draconic Evolution、Malum、Botania、Veil、Mekanism 未取得可验证的对应版本，保留源码并跳过运行时联动验证；旧 API 编译通过不代表这些运行时已经支持。
- Re:Avaritia 自带 `avaritia:ae2_creative_energy_cell` 配方因旧配料格式解析失败；联合客户端中 MA、ExtendedAE、Applied Flux 自身创造栏重复物品错误继续保留。这些第三方问题未在 PP 修补。
- LT 保留的四项原 main 性能失败见相邻 LT 仓库的同名说明。

## 2026-10-13 alpha 最终状态同步

以 `efcb1c4` 为目标分支基线，核对并同步远端 alpha `df98635` 的可用最终改动；不复制过程提交。

- 分发器记录确认插入量，并在提交失败后按实际回收量补偿；抛出异常的插入回执保持所有权未知，只返还尚未尝试的输入，避免 CPU 重试或退款造成复制。
- EC 与 Re:Avaritia 工作台、终焉锻造台取消供应器每 tick 64 份上限，保留 CPU、能源、锁定和阻塞约束；支持超载样板的输入与输出匹配模式分别生效，并在缓存绑定内选择符合实际组件的配方。
- MA 灌注及觉醒祭坛验证激活结果，并在确认未启动时回收对应插入；保留 EC 对有效组合配方等待 FE 时的输入保护。
- 新增统一凿子封包核心：Rechiseled 1.2 通过原生 worth/conversion API 验证方块、半砖比例；Chisel Reborn 使用真实雕刻分组；Chipped 5.x 遍历共享配方的 `entries`，校验完整工作台。便携凿刻支持普通和无线 PP，无需机器目标。
- 无线目标按安装核心和适配器校验；临时取出核心保留已有连接，拆掉目标或 Chipped 工作台半体后停止旧缓存合成。
- 同步已验证模组的核心配方成本、凿刻指南与物品模型，适配 26.1 物品模型定义和配料编码；空核心槽显示专用图案。

核对的目标版本源码：

| 项目 | 分支或标签 | 提交 |
| --- | --- | --- |
| Extended Crafting | `26.1` | [`e54a4a4170d9`](https://github.com/BlakeBr0/ExtendedCrafting/commit/e54a4a4170d990dbd49973afae07043d68f81a14) |
| Mystical Agriculture | `26.1` | [`af9c0d0ce535`](https://github.com/BlakeBr0/MysticalAgriculture/commit/af9c0d0ce535b5379967dc532aab8215406102e5) |
| Re:Avaritia | `neo/26.1.2` | [`3d3eae6a29e8`](https://github.com/Nova-Committee/Re-Avaritia/commit/3d3eae6a29e8290b64cb4b1e661a7d0128cb8d21) |
| Occultism | `version/26.1.2` | [`bc8404e1897e`](https://github.com/klikli-dev/occultism/commit/bc8404e1897e29ee10da48535b0ceabebcc8a2f9) |
| Rechiseled | `neoforge-26.1` | [`6368c72c2eca`](https://github.com/SuperMartijn642/Rechiseled/commit/6368c72c2ecaed0159d6e1c2fd894d2264565f88) |
| Chisel Reborn | `26.1.2-2.2.1` | [`3f184c2f66aa`](https://github.com/matthewperiut/Chisel-Fabric/commit/3f184c2f66aa99520eecbd9298fd5382e06b8219) |
| Chipped | `26.1` | [`0cf4fd05af71`](https://github.com/terrarium-earth/Chipped/commit/0cf4fd05af710a1ec9648542cc7d9c361da1d0b8) |
| Mekanism: MoreMachine | `26.1` | [`f4bc6151c664`](https://github.com/lostmyself8/Mekanism-MoreMachine/commit/f4bc6151c664d13c55b93e939619c63c245a6bb4) |

验证依赖：EC 8.0.3、Cucumber 9.0.6、MA 9.0.10、Re:Avaritia 1.4.2、Occultism 1.256.1、Rechiseled 1.2.6、Chisel Reborn 2.2.1，以及对应依赖。Chipped 尚未取得 26.1.2 发行包，使用其上述目标分支源码在 Java 25 下构建 5.0.0，配合 Resourceful Lib 4.0.1、Athena 4.7.3 验证；此构建包不随 PP 分发。

`check jar` 的 103 项测试全部通过，无跳过。联合 GameTest 67 项全部通过，覆盖普通/无线工作台、3,000,000,000 份批次、容器返还、输入/输出组件独立匹配、分发所有权、原生 MA/Occultism 及三个凿刻适配器。Rechiseled 由测试模组补齐 GameTestServer 绕过的 `ServerMainMixin.finalizePlugins` 启动步骤；生产代码不改变第三方初始化。

凿刻模组全部缺席的配置中，核心隐藏、适配器注册为零及原生配方条件校验通过。实际客户端在 Chisel Reborn 配置下渲染并检查普通/无线 PP 的空核心槽和凿子核心模型；完整联动集合的客户端在 1 GiB 堆下耗尽内存，客户端验证未覆盖该集合，服务器联合测试覆盖完整集合。

测试入口仍为 `-Pae2ltppBatchPortTest runBatchPortGameTestServer`。可选模组坐标通过 `port_optional_mods` 传入，源码构建的模组及依赖 JAR 可用 `port_optional_jars` 传入逗号分隔路径；这些依赖仅参与开发运行，不进入发布 JAR。

本次跳过 Nature’s Aura、Astral Sorcery、Actually Additions、Ars Nouveau、Draconic Evolution、Malum、Botania 的新兼容及对应资源，未取得可验证的 NeoForge 26.1.2 版本。Mekanism: MoreMachine 已有明确以 26.1.2 为目标的源码分支，但没有对应发行包，依赖第三方 Mekanism `26.1-SNAPSHOT`；本次保留既有排除配置，跳过其新调度和流体/化学品数量改动。Chisel Modern 未取得对应版本，仅启用已验证的 Chisel Reborn。

第三方限制：MA 在同时安装 Chisel Reborn 时仍引用不存在的 `chisel:limestone_raw` 与 `chisel:marble_raw`；Chipped 源码包仍有使用旧 `ingredients` 字段的银方块转换配方。上述第三方资源解析问题未在 PP 修改。
