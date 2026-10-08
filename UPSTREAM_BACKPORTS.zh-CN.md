# 上游功能移植：Minecraft 1.20.1 / Forge

参考仓库：[AE2LT-Packaged-Pattern-Provider-Reborn](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn)。
本次把 `main` 的相关功能适配到 Java 17、Minecraft 1.20.1、Forge 47.4.10；保留本仓库 LGPL-3.0 许可证和原有作者信息。
下列提交链接记录移植来源，包括复用的算法、适配器和空槽纹理；第三方测试模组的 JAR 不随源码或产物重新分发。

| 来源 | 移植行为 |
| --- | --- |
| [d193242](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/d193242) | DE 派发前刷新原生注入器位置，支持新建、拆除和重建。沿用本地已有 Malum 空闲催化剂保护。 |
| [fdb3ebc](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/fdb3ebc) | Ars 注魔样板可声明产物及可重复使用的实际催化剂，严格核对数量与 NBT。 |
| [95afe59](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/95afe59) | EC / Avaritia 工作台按各输入槽自己的模糊标志绑定多个候选配方，派发时用实际 NBT 执行原生校验。纯工作台支持 Thunderbolt 批量接口，取消固定 64 次上限；保留能耗、锁定和溢出门控。 |
| [84c5abf](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/84c5abf) | MekMM 可向运行中的机器继续投料，合并未收获订单的输出收据；按配方与模式退避，按控制器自适应轮询。 |
| [25d0ef7](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/25d0ef7) | 统一雕刻核心，Chipped 工作台转换以及 Rechiseled / Chisel 供应器内部转换通道。 |
| [7a60007](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/7a60007) | 两种供应器 GUI 的空核心槽提示纹理。 |
| [0267d70](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/0267d70a798e94902d62fea0244b03e63aaf4f8e) | Ars 灌注室常驻催化剂：仅核心原料样板使用预放催化剂，跨重载保留；声明催化剂的样板继续借用并返还。保留 Forge 的候选材料匹配。 |
| [955f4ce](https://github.com/AE2-Lightning-Tech-Reborn/AE2LT-Packaged-Pattern-Provider-Reborn/commit/955f4ce1c254fb1cef26a5ad3aa5f65055a44378) | 显示名称移除 Reborn / 重生，同步中英文语言、指南、模组描述和命名测试；更新 Forge 版变更记录。 |

## Forge 适配要点

- Chipped 3.0.7 使用各工作台独立的配方类型，必须存在主半块和朝向一致的侧半块；无线连接只接受主半块。这个版本没有凿子，所以雕刻核心也能用任意 Chipped 工作台制作和显示。
- Rechiseled 1.2.6 直接调用原生 worth 换算 API，支持块与半砖等比例转换。拒绝剩余原料、夸大产量和虚构产物 NBT。两种供应器都可在没有邻接机器和无线目标的情况下完成转换，但必须安装雕刻核心。
- Mekanism 10.4 的气体、灌注、颜料、浆液使用四种 Forge capability。通过 Applied Mekanistics 1.4.3 的化学 AEKey 接口收发，不使用 1.21 的统一 capability。
- MekMM 保留本地已有的保守端口匹配：多个同类输入端口仍拒绝歧义路由。本次没有移植另一项原生配方数量路由重构，也没有放宽空输出基线、订单归属及失败补偿规则。
- Chisel 的两个上游反射路径保留；尚未用对应 Forge 1.20.1 实现做原生运行验证。Rechiseled 1.1 的旧接口回退也未在本轮测试。
- 批量功能使用已有 AE2LT 2.1.0 / Thunderbolt 2.0.1 接口，本次未升级这些依赖。包装样板和过载样板沿用单次派发。

## 复现验证

使用 Java 17；以下命令的 `use_local_upstream` 使用仓库已有的相邻本地依赖构建配置。

```powershell
./gradlew.bat test build -Puse_local_upstream=true -Penable_avaritia_tests=true --offline --no-daemon --max-workers=1
./gradlew.bat runGameTestServer -Puse_local_upstream=true -Penable_integration_runtime=true -Penable_chiseling_runtime=true -Penable_appmek_runtime=true --no-daemon --max-workers=1
```

雕刻运行时依赖使用指定的 Forge Modrinth 版本 ID；可用 `-Pchiseling_runtime_mods=chipped` 或 `rechiseled` 单独验证。
离线环境可用 `-Pchiseling_reference_maven=<本地 Maven 目录>` 提供同坐标、未修改的公开参考 JAR。
正常构建不加载这些可选测试依赖。

原生测试覆盖 DE 注入器重建、Ars 原生注魔与催化剂返还、ME 批量接口发现、Avaritia 130 次批量及 NBT 重载、
Chipped 原生工作台与无线门控、Rechiseled 比例与内部通道、MekMM 原生电解连续订单及模式切换。
客户端验证覆盖核心渲染和两种供应器屏幕。服务端运行和客户端编译必须串行，避免共享编译目录在类加载期间变化。

2026-10-08 已通过 299 项 JUnit 测试（无失败、错误或跳过）和 81 项必需 Forge GameTest。
原生目标版本为 DE 3.1.2.621、Ars Nouveau 4.12.6、EC 6.0.5、AvaritiaNeo 1.1.6 Forge、
Mekanism 10.4.16.80、MekMM 1.20.1-1.2.1、Applied Mekanistics 1.4.3、Chipped 3.0.7、Rechiseled 1.2.6；
AE2 为 15.4.10。MekMM 电解测试使用两个 100 mB 订单，在原生机器仍有输入且已产生输出时追加第二笔订单，
重载供应器持久状态后逐次收获，最终只返还 200 mB 氢气和 100 mB 氧气。

真实 Forge 客户端已通过：仅安装 Chipped 的雕刻组合下，33 种核心完成连续渲染，
普通和无线供应器屏幕均正常打开，空核心槽图标已目视核对；测试世界正常保存并返回菜单。
客户端启动器的 `earlyWindowControl=false` 仅用于本地隔离目录，未加入模组资源或默认配置。

本次开始前工作区已有的启动兼容修复、测试和 README / MANUAL_TESTING 修改保持独立；本次移植验证在该现有工作区基础上进行。

后续全局压测已通过 299 项全套 JUnit、1,024 键专项和 83 项必需原生 GameTest。
扩大负载包括 160 万次随机操作、Avaritia 累计 1.28 亿次虚拟合成、MekMM 128 笔连续订单，以及 8 台供应器跨 6,000 tick 的阻塞重载。
实际负载、结果、复现命令与覆盖边界见 [GLOBAL_STRESS.zh-CN.md](GLOBAL_STRESS.zh-CN.md)。

2026-10-09 补齐上述常驻催化剂和显示名称移植后，当前默认前置组合再次通过完整构建、299 项 JUnit（400 万次随机操作）及全部 86 项必需 Forge GameTest。
新增常驻模式使用 Ars Nouveau 4.12.6 的真实火焰精华配方和原生 ticker，完成 64 次合成、128 次灌注室实体重建，保留基座催化剂的 NBT 与数量；
同时覆盖派发前材料变化、错误 / 缺少 / 多余催化剂、完成后材料变化、重复回收和常驻转借还模式。
实体重建验证 Forge NBT 序列化，不等同于真实磁盘重启。生产 JAR 的显示名称与资源验证见 [DEPENDENCY_COMPATIBILITY.zh-CN.md](DEPENDENCY_COMPATIBILITY.zh-CN.md)。
