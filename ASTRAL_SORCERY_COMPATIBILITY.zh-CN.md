# 星辉魔法 1.21.1 适配

## 对齐版本

- 上游：`HellFirePvP/AstralSorcery` 的 `1.21.1` 分支，提交 `5d94b0e8bd950071a787a560f3189105d5d9b4e3`。
- 本地构建版本：`astralsorcery-2.0.0.local`；ObserverLib `1.10.3.31`。
- 联动原生测试：Minecraft 1.21.1、NeoForge 21.1.235、Java 21、AE2 19.2.17、Thunderbolt 2.0.0、AE2LT 2.1.0。
- 默认构建及无星辉环境：NeoForge 21.1.230。星辉保持可选依赖，不随 LTPP 打包。
- 此次依据官方开发分支，不是 CurseForge 上的 1.16.5 版本。后续开发版若修改接口，需要重新验证。

## 支持范围

四级合成祭坛（Illumination / Resonance / Luminance / Radiance）和 Starlight Infuser，共五种封包核心。高级祭坛核心覆盖低级；核心在对应祭坛制作，灌注器核心在 Resonance 祭坛制作。

沿用现有 `MultiblockAdapter`、`DispatchExecutor` 和提供器持久化标记：绑定时查找配方，运行时只检查已绑定配方；单次执行一份。向原生网格、中继器投放材料，额外材料生成真实掉落物。实际加工和消耗由星辉执行，回收原生掉落产物及容器余料。不会提前创建预览产物。

结构、夜晚、焦点星座、星辉、流明、焦点破碎概率、额外流体、加工时长及灌注液消耗均保留原生规则。环境资源自行供应，不作为 ME 流体输入虚拟扣除。单次配方最多分配 256 个物品单位，超限拒绝投料。

晶体属性等确定性修改按实际输入校验；随机组件需要超载样板的仅 ID 输出匹配。方块替换、玩家研究解锁及未知修改器不作为物品加工执行。聚星缸、调谐祭坛和世界转化尚未纳入本次范围。

加工中断后原料留在原生机器中供恢复/取出。应开启自动回收，避免手工投料混用。提供器保存的加工归属标记防止提前回收、重复回收及接管后续手工作业。高级祭坛会把掉落物挤出中心方块，回收区域覆盖原生七格捕获范围。

## 上游数量兼容修正

该上游提交的 `InventoryView.insertItem` 把 `stackSizeLimiter` 返回的上限直接当作插入数量，未与传入堆叠数量取最小值：向空槽插入一枚铁锭可变成 64 枚，原生插回的一只空桶可变成 16 只。

`AstralInventoryViewMixin` 仅在该方法调用槽位上限函数处限制 `min(limit, offeredCount)`。这一修正同样覆盖原生加工余料回填；不改变配方消耗或槽位上限。它是可选的 `@Pseudo` Mixin，星辉缺席时不加载目标类；上游修正同一问题后重复取最小值仍保持原语义。原生数量测试覆盖模拟、单件投料、部分插入回执和空桶余料。

## 验证与复现

- 原生星辉测试 10 项：真实中继器加工/提供器 NBT 恢复/再次加工，额外材料捕获消耗，空桶和后续手工产物保护，灌注结构与液体，高级祭坛等级及连续四次加工，缺料/多料/占槽/缺中继拒绝，原生插入数量守恒，确定性组件与世界修改器拒绝，随机 UUID 输出，夜晚限制与一格原生液体消耗。
- 不安装星辉的独立原生启动测试 1 项：可选 Mixin、核心隐藏、原生配方条件跳过、联动注册停用。
- 现有 JUnit 测试 72 项全部通过；生产 JAR 不包含原生测试类、测试配方或第三方 JAR。
- 最小测试环境中 AE2LT 的 `blocks/overloaded_power_supply` 掉落表和缺少 ExtendedAE 条件的 `assembler/overload_processor` 有既存加载错误；星辉与 LTPP 配方无新增解析错误。此次不修改无关联的 LT 资源。
- 本次为服务端自动化验证，不包含客户端人工界面操作。

将官方 1.21.1 星辉开发版和 ObserverLib JAR 放在一个本地测试目录，再运行：

```sh
bash ./gradlew --no-configuration-cache \
  -I scripts/astral-test.init.gradle \
  -PppAstralModsDir="$ASTRAL_TEST_MODS" -Pneo_version=21.1.235 \
  -Pthunderbolt_maven_notation= -Pthunderbolt_jar="$CORE_JAR" \
  -Pae2lt_jar="$LT_JAR" runGameTestServer

bash ./gradlew --no-configuration-cache \
  -I scripts/astral-test.init.gradle -PppAstralAbsent \
  -Pthunderbolt_maven_notation= -Pthunderbolt_jar="$CORE_JAR" \
  -Pae2lt_jar="$LT_JAR" runGameTestServer

bash ./gradlew --no-configuration-cache \
  -Pthunderbolt_maven_notation= -Pthunderbolt_jar="$CORE_JAR" \
  -Pae2lt_jar="$LT_JAR" test assemble
```
