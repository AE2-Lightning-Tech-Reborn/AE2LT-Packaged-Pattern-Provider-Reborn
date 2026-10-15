# 新版前置兼容验证：Forge 1.20.1

检查日期：2026-10-08。仅检查 Minecraft 1.20.1 / Forge 发布包，使用 Java 17、Forge 47.4.10。
所有下载文件均在本地忽略目录中校验，不重新分发第三方 JAR。

## 版本与范围

| 依赖 | 当前默认构建版本 | 本轮验证版本 | 项目声明的运行范围 |
| --- | --- | --- | --- |
| AE2 | 15.4.10 | [15.4.11](https://github.com/AppliedEnergistics/Applied-Energistics-2/releases/tag/forge/v15.4.11) | `[15.4.10,16)` |
| AE2LT | 2.1.0 | [2.1.2-beta](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/releases/tag/1.20.1-2.1.2-beta) | `[2.1.0,3.0.0)` |
| Thunderbolt | 2.0.1 | [2.0.3-beta](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/releases/tag/1.20.1-2.0.3-beta) | `[2.0.1,2.1.0)` |
| GuideME | 20.1.15 | 20.1.15，已是对应 Forge 分支最新版本 | AE2LT 要求 `[20.1.7,20.2.0)` |
| AE2WTLib | 15.3.3-forge | 15.3.3-forge，已是对应 Forge 分支最新版本 | 可选集成 |
| Curios | 5.14.1+1.20.1 | 5.14.1+1.20.1，已是对应 Forge 分支最新版本 | 可选集成 |
| JEI | 15.21.0.144 | 15.62.0.219 | AE2LT 要求 `[15.20.0.129,16)` |
| Jade | 11.13.3（文件 ID 8479276） | 11.13.3，已是对应 Forge 分支最新版本 | AE2LT 要求 `[11.13.2,12)` |

三个核心新版均落在当前模组声明的运行范围内。AE2LT 的公开版本和相邻本地源码、Thunderbolt 的公开版本和相邻本地构建存在版本差异，
本轮以已校验的公开 Forge 发布包为准，不把相邻仓库的未发布源码当作发布包验证。
GitHub 上相同版本号还有 Minecraft 1.21.1 的发布包；本轮选择带 `1.20.1-` 的发布标签和 `forge-1.20.1` 文件。

## 已完成检查

- 官方发布包大小和 GitHub 提供的 SHA256 digest 全部匹配。JEI、Jade 使用 Modrinth 官方 SHA512 校验。
- 用三个核心新版重新编译全部源码并打包成功，运行 299 项 JUnit，失败、错误和跳过均为 0。
- 对上一轮已交付正式 JAR 的引用做静态检查：104 个前置类、217 个成员符号，无缺类或明确缺失成员。
  其中 3 个 `defaultBlockState` 调用来自 Minecraft 的父类，需以 Forge 原生运行补足验证。
- 用旧依赖和新依赖分别构建得到的 363 个正式 class 文件逐个比较，字节码完全相同。
  因此本次没有通过修改供应器代码来绕过接口变化。
- 新版无线 host 增加了带默认实现的端点快照；旧无线连接入口仍在。
  新版 Thunderbolt 保留三参数批量入口，job 参数入口有默认转发；包装供应器继续遵循借用模板只读与拒绝共享输入的约定。

## 原生运行结果

真实 Forge GameTestServer 已注册并运行全部 83 项必需测试，全部通过。
实际运行日志与 Gradle 解析清单均确认加载 AE2 15.4.11、AE2LT 2.1.2-beta、Thunderbolt 2.0.3-beta 和 JEI 15.62.0.219。

覆盖无线目标与安装核心门控、连续目标收集、批量接口发现、借用模板不变、工作台 128 批 × 1,000,000 次虚拟合成、
MekMM 128 笔运行中追加订单及 16 次归属重载、普通 / 无线供应器积压持久化与完整实例重建、真实 ME 存储恢复，
以及 DE、Ars、EC、Avaritia、Chipped、Rechiseled 和既有兼容适配器。
本轮运行默认 10 轮供应器浸泡、200 个服务端 tick；上一轮的 300 轮 / 6,000 tick 压测使用旧前置，不能算作新版前置验证。

客户端正在验证 33 种核心渲染、普通与无线供应器屏幕，以及新版依赖的客户端 Mixin / JEI 加载。

## 复现配置

本轮将经过校验的原始 JAR 放到 `build/dependency-update-check/maven`，通过本地临时 init script 优先解析。
旧默认版本仍保留；未更改项目运行兼容范围。
临时 Maven 中给 Thunderbolt 使用本项目已有坐标别名，内容是官方发布 JAR，未修改字节码；不代表该别名已由上游正式发布。

```powershell
./gradlew.bat test build -I build/dependency-update-check/references.init.gradle '-Pae2_version=15.4.11' '-Pae2lt_version=2.1.2-beta' '-Pthunderbolt_version=2.0.3-beta' -Penable_avaritia_tests=true --offline --no-daemon --max-workers=1
./gradlew.bat runGameTestServer -I build/dependency-update-check/references.init.gradle -I build/dependency-update-check/runtime.init.gradle -I build/compatibility-test.init.gradle '-Pcompatibility_test_root=build/dependency-update-check/server' '-Pae2_version=15.4.11' '-Pae2lt_version=2.1.2-beta' '-Pthunderbolt_version=2.0.3-beta' '-Pjei_version=15.62.0.219' -Penable_integration_runtime=true -Penable_chiseling_runtime=true -Penable_appmek_runtime=true -Pchiseling_reference_maven=build/upstream-reference/maven --offline --no-daemon --max-workers=1
./gradlew.bat runClient -I build/dependency-update-check/references.init.gradle -I build/dependency-update-check/runtime.init.gradle -I build/upstream-client-validation.init.gradle '-Pclient_simulation_root=build/dependency-update-check/client' '-Pae2_version=15.4.11' '-Pae2lt_version=2.1.2-beta' '-Pthunderbolt_version=2.0.3-beta' '-Pjei_version=15.62.0.219' -Penable_integration_runtime=true -Penable_chiseling_runtime=true -Penable_appmek_runtime=true -Pchiseling_reference_maven=build/upstream-reference/maven --offline --no-daemon --max-workers=1
```

编译、服务端和客户端运行按顺序执行；原始日志、JUnit XML / HTML 报告、版本解析清单、校验值和字节码比较结果保存在忽略的 `build/dependency-update-check/`。
现有 `compatibility-test.init.gradle` 的相对服务端目录解析实际生成了 `build/build/dependency-update-check/server`，同样在仓库忽略目录内；
复现时可把 `compatibility_test_root` 传为绝对路径指定其他本地隔离目录。
本轮不验证 GTL 专用前置组合、其他 Minecraft / 加载器版本或所有可选目标模组的新发布版本。
JUnit 沿用已有配置，`StablePatternProviderNbtMigrationTest` 未纳入选择；不能以 299 项通过代替该迁移测试。
