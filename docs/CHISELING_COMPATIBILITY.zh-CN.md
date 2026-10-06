# 凿子封包与适配器无线绑定（1.21.1 alpha）

只有一个 `ae2ltpp:chiseling_packaged_core`。基础封包核心加 Chipped、Rechiseled、Chisel Reborn 或 Chisel Modern 的凿子，在普通合成栏制作。对应模组未安装时隐藏核心和配方。

- Chipped 4.0.2：七种工作台共用上游转换配方，但必须有完整的实体工作台；绑定或贴靠可见的主体，拆掉任一半即停止。
- Rechiseled 1.1.6a / 1.2.6：无需目标；读取上游注册组，兼容连接纹理。1.2 调用公开换算接口，支持方块/半砖比例，拒绝有剩余材料的样板。
- Chisel Reborn 2.0.1 / Chisel Modern 1.4.1：无需目标；按实际加载的同名 mod 的 API 读取转换组，按 1:1 转换。两个实现不能同时安装。
- 输入和产物必须符合实际转换，不允许虚增数量、额外材料或伪造产物组件。普通和无线封包共用原有耗电、限速、待回收输出与持久化路径。

## 无线连接

先插入核心，再用连接器选择无线封包和目标。由对应适配器判断目标，而不以“存在 BlockEntity”作为通行条件。未插核心或核心不匹配时给出提示；没有适配器支持的箱子等方块也不能绑定到封包。

Chipped 工作台与 Occultism 灵火均支持无线绑定、调度与失效清理。临时取下核心保留连接但停止相应配方。LT 的普通供应器默认仍使用原来的 BlockEntity 规则。

这项功能包含配套 LT 的公开 `WirelessPatternProviderHost.acceptsWirelessTarget` 扩展和连接器修改，需要一起更新 LT 与 LTPP。没有修改第三方模组。

## 验证

- LT：1303 项单元测试；LTPP：72 项单元测试。
- 原生 NeoForge 21.1.230 / AE2 19.2.17：Reborn + Rechiseled 1.2 + Chipped + Occultism、Modern + Rechiseled 1.1 + Chipped、仅 Chipped、全部缺席四种环境。
- 原生测试按已加载模组执行：真实核心配方、七种工作台、比例/余料/组件/溢出拒绝、普通与无线内部批量、NBT 恢复、原生连接器服务端处理、缺失/错误核心拒绝、灵火无线合成和失效清理。
- 测试资源与第三方 JAR 不进入产品 JAR。尚未做图形客户端手动操作验收。
- 精简测试环境未安装 ExtendedAE，因此 LT 原有 `ae2lt:assembler/overload_processor` 的 `extendedae:config` 条件产生一条配方加载错误；不涉及本次凿子配方，后者已在原生测试中验证。

测试入口是 `scripts/chisel-test.init.gradle`：设置 `ae2lt_jar`、清空 `thunderbolt_maven_notation` 并设置 `thunderbolt_jar`，通过 `ppChiselModsDir` 指定第三方测试依赖目录，`ppChiselProfile` 选择 `reborn`、`modern`、`chipped`；不传目录与 profile 时测试全部缺席环境。执行 `test jar runGameTestServer`。测试源码在 `src/chiselTest`。
