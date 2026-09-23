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
