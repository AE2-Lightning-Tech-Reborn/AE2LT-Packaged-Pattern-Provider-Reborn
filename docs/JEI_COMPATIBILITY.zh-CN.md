# JEI 19.x 兼容

适用环境：Minecraft 1.21.1、NeoForge、AE2LT 2.1.2-beta4。JEI 为客户端可选依赖，支持范围为 `[19.19.6,20)`；开发运行时保持 `19.27.0.336`，编译 API 固定为 `19.19.6.236`。不把不同 Minecraft 版本的 JEI 混用，也不承诺 JEI 20 的二进制兼容。

## 版本差异与处理

| 差异 | 本项目的处理 |
| --- | --- |
| 19.19 系列允许分类只实现宽高，且已经提供当前使用的槽位与输入 API | 以 19.19.6 为最低编译和运行基线；编译仅依赖 common / NeoForge API，不依赖 JEI 内部实现 |
| 槽位列表可能包含空白轮播项；原生分类可能延迟初始化滚动组件 | 先调用公开的 `IRecipeLayoutDrawable.tick()` 初始化 extras，再通过完整 transfer view 和 `getAllIngredients()` 提取材料；保留物品、流体、自定义资源的替代项和数量，过滤空白项 |
| 新版输入处理会区分模拟点击与实际点击 | 使用 `IJeiInputHandler`；模拟点击只报告可处理，实际点击才跳转原生配方；提示改用 rich tooltip API |
| 自定义材料持久化依赖稳定类型 UID 和 Codec | 注册 `ae2ltpp:resource` 及 Codec；保存资源类型和原始整数数量；保留旧版必需的 helper / renderer 方法 |
| 19.52 引入 `IRecipeTransferContext` 与转移服务，19.64 调整按钮调用位置 | 封包扩展继续通过 AE2 JEI Integration 的 `IngredientConverters` 暴露资源，不另行注入 JEI 内部转移方法；天枢终端的旧版 / 新版转移分支由 AE2LT 负责 |
| 第三方分类可能与运行中的 JEI 不匹配 | 对单个分类 / 配方的运行时错误和链接错误记录警告并隔离；不将读取失败的配方推测为缺省材料配方 |

同一个原生配方对象在不同分类中可能代表不同材料，因此按分类分别去重。JEI runtime 重建时重新生成快照，沿用原有 generation 检查，避免上一个 runtime 的排队任务注册到新会话。AE2 材料转换器只注册一次。

核心页固定为 `176 × 140`，材料区每行 7 格、最多显示 3 行，超出的输入和产物使用 JEI 的公开滚动组件，完整材料仍保留在 transfer view 中。自定义资源数量使用与原版物品数量相同的字号和右下角位置，并按原版物品装饰的深度 `z=200` 绘制，避免被物品图标遮挡。

上游核对基于 JEI 1.21.1 分支的提交 `98e703a70a728a13c0d22f9509adf044a23cc738`：

- [分类 API](https://github.com/mezz/JustEnoughItems/blob/98e703a70a728a13c0d22f9509adf044a23cc738/Common/src/api/java/mezz/jei/api/recipe/category/IRecipeCategory.java)
- [槽位 API](https://github.com/mezz/JustEnoughItems/blob/98e703a70a728a13c0d22f9509adf044a23cc738/Common/src/api/java/mezz/jei/api/gui/ingredient/IRecipeSlotView.java)
- [材料注册 API](https://github.com/mezz/JustEnoughItems/blob/98e703a70a728a13c0d22f9509adf044a23cc738/Common/src/api/java/mezz/jei/api/registration/IModIngredientRegistration.java)
- [19.52 转移上下文](https://github.com/mezz/JustEnoughItems/blob/98e703a70a728a13c0d22f9509adf044a23cc738/Common/src/api/java/mezz/jei/api/recipe/transfer/IRecipeTransferContext.java)

## 复现验证

先用 `scripts/core-test.init.gradle runGameTestServer` 生成隔离测试世界，再运行：

```powershell
.\gradlew.bat -I scripts/core-test.init.gradle -I scripts/jei-compat-test.init.gradle runClient -Pjei_version=19.70.0.467 -Pneo_version=21.1.238 --no-configuration-cache
```

`jei_version` 只切换运行时，编译 API 保持最低基线。可用 `-Pjei_api_version=...` 单独验证其他编译 API。结果和截图分别写入 `build/jei-compat/<版本>/client-result.txt` 与 `core-recipe.png`，脚本要求结果明确为 `PASS`。

所测的较新 JEI 构建声明 NeoForge 最低版本为 `21.1.238`。只替换 JEI、继续使用项目默认的 `21.1.230` 时，加载器会在运行本模组代码之前拒绝启动；必须同时满足 JEI 自身的加载器要求。

探针在真实客户端中检查原生滚动槽位、空白替代项、127 / 64 个物品、619400 mB 流体、250 单位资源、同对象跨分类镜像、Codec 往返、AE2 转换器、原位升级与备用核心，以及模拟点击不跳转。探针源码和初始化脚本不进入发布 JAR。

2026-10-30 客户端验证结果：

| JEI | NeoForge | AE2LT 构建 | 结果 |
| --- | --- | --- | --- |
| 19.19.6.236 | 21.1.238 | alpha 3638888b | PASS |
| 19.27.0.336 | 21.1.238 | alpha 3638888b | PASS |
| 19.52.0.424 | 21.1.238 | alpha 3638888b | PASS |
| 19.64.0.455 | 21.1.238 | 既有本地 2.1.2-beta4 | PASS |
| 19.70.0.467 | 21.1.238 | alpha 3638888b | PASS |

19.19.6.236 和 19.27.0.336 另外在项目默认 NeoForge 21.1.230 下通过过客户端探针。表中各版本覆盖材料数据、配方页显示、滚动布局和模拟点击；不据此宣称覆盖所有第三方模组或天枢终端的完整自动上传流程。另通过项目全部 138 项单元测试及发布 JAR 构建。

未安装 JEI 的隔离服务端通过 `scripts/core-test.init.gradle runGameTestServer`，确认客户端可选依赖没有阻止服务端加载。

本地 AE2LT JAR 要求 Thunderbolt `2.0.3-beta4`，而当前开发默认坐标仍为 `beta3`。复测此组合时，通过已有的 `thunderbolt_maven_notation` / `thunderbolt_jar` 参数切换到匹配的 NeoForge `beta4` JAR；不混用旁边的 Forge 1.20.1 JAR。

旧的本地 AE2LT `2.1.2-beta4` JAR 在 JEI `19.70.0.467` 打开配方界面时触发 `RecipeTransferButtonController.onPress` 的 `VerifyError`：旧 Mixin 在已复用局部变量 0 的字节码处注入了实例方法。AE2LT 当前 `alpha` 源码已改用静态 `WrapOperation`，对应修正在 `54e2dccb`；使用 `alpha` 提交 `3638888b` 的独立源码快照重新构建后，同一探针在 `19.70.0.467` 通过。复测最新版 JEI 时必须使用含该修复的 AE2LT 构建，可通过 `-Pae2lt_jar=...` 指定；仅凭 `2.1.2-beta4` 版本号不能区分这两个本地 JAR。未改动旁边项目的当前检出或已有 JAR。
