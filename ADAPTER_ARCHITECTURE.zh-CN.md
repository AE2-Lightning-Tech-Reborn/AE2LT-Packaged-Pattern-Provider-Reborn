# 适配器架构说明

本文记录当前 Forge 1.20.1 实现中可供新适配器复用的边界。上游 NeoForge 分支的 `*Access` 分层不能直接复制到本分支；这里以现有 `MultiblockAdapter`、反射辅助类和 Forge capability 为准。

## 运行边界

- `MultiblockAdapterRegistry` 负责按主方块选择适配器，适配器负责识别结构、绑定配方、规划输入和收获输出。
- `BindingMode.REAL` 表示材料要真实进入目标机器；`BindingMode.VIRTUAL` 表示只登记虚拟合成结果，不修改第三方机器。
- 目标模组是可选依赖。识别、反射查找、配方类型解析和状态读取失败时必须返回“不支持”或空结果。
- `DispatchPlan` 的规划阶段应保持无副作用；真实修改只能出现在 `onCommit` 或确认的输入插入回调中。
- `onPartialCommit` 只能回收已经确认仍在机器中的输入。无法证明机器未启动时必须保留归属，避免重复退款。

炽炉的外围组件兼容使用 `ClibanoBlockEntityLoadMixin`：当 `load` 发生在绑定世界之前，使用方块注册表读取 NBT 中的 `State`，恢复上游漏读的拆除还原状态。Mixin 使用 `@Pseudo` 保持目标模组可选；目标类无需编译依赖，Minecraft 1.20.1 的开发方法 `load` 与生产 SRG 方法 `m_142466_` 均已按实际上游 JAR 核对并列为注入目标。组件已绑定世界时保留上游原有加载路径，补丁不生成缺失存档中的原方块信息。

## 接口选择

| 场景 | 首选路径 | 备注 |
| --- | --- | --- |
| 普通物品、流体投料 | Forge capability / AE2 目标接口 | 只有需要槽位语义时才使用自定义插入器 |
| 机器库存 | 模组公开库存或 capability | 反射字段只作为版本兼容回退 |
| 配方匹配 | Minecraft `Recipe` 和注册表 | 不用字符串拼接模拟配方结果 |
| 仪式启动 | 模组专用方法或状态回调 | 调用后必须验证真实启动状态 |
| 输出收获 | 归属状态 + 精确物品/流体匹配 | 无本模组归属记录时不收获 |
| 可选模组探测 | 一次性 reflection bootstrap | 失败结果要稳定发布，不能并发暴露半初始化句柄 |

## 新适配器检查表

1. 主方块和必要结构只在目标模组存在且类型确认后识别。
2. `bind` 拒绝多输出、输入歧义、数量溢出和无法确认的配方。
3. `planWithBinding` 的模拟路径不写库存、不启动机器、不登记输出归属。
4. 提交顺序明确区分：输入接受、机器启动、输出归属写入和异常补偿。
5. 收获只匹配本次归属的目标、数量和组件；超额或外部产物留在机器中。
6. 补偿失败时保留状态并阻止覆盖，宁可等待人工处理也不重复扣料。
7. 增加纯单元测试、反射契约测试和至少一个实际模组 GameTest。
8. 更新 `README.md`、`MANUAL_TESTING.md` 和对应 GuideME 文档。

## 验证命令

```powershell
.\gradlew.bat test build --offline --no-daemon
.\gradlew.bat runGameTestServer -Penable_integration_runtime=true --offline --no-daemon
```

生产服还可以使用 `/ae2ltdiag connections` 检查适配器识别、配方类型和空闲收获的 fail-closed 行为。
