# AE2LT Packaged Pattern Provider Reborn — Forge 1.19.2

封装样板供应器与无线封装样板供应器，将 AE2/LT 样板分发接入多方块及虚拟配方加工。
本分支基于 Reborn `1.20.1` 分支移植，使用 Minecraft 1.19.2、Forge 43.5.2、Java 17、AE2 12.9.12、LT 2.1.2-beta2 和 TB 2.0.3-beta3。

## 构建

先构建相邻目录中的 `Thunderbolt-Core-Reborn` 与 `AE2-Lightning-Tech-Reborn` 的 1.19.2 分支：

```sh
./gradlew test build -Puse_local_upstream=true
./gradlew runGameTestServer -Puse_local_upstream=true
```

可通过 `-Pae2lt_build_libs=/path/to/build/libs` 和 `-Pthunderbolt_build_libs=/path/to/build/libs` 指定位置。
也可将对应 Maven 坐标安装到 Maven Local 后省略 `-Puse_local_upstream=true`。
成品位于 `build/libs/ae2ltpp-forge-1.19.2-1.2.2-beta.jar`；不要分发 `-slim.jar`。

## 运行验证

默认 GameTest 命名空间 `ae2ltpp_core` 覆盖核心无线接入、内存卡、邻居索引、虚拟批次及批量分发服务。
可选集成测试使用独立命名空间，保持原生模组配方、催化物、库存和存档断言。

```sh
# Chipped 2.1.5 + Rechiseled 1.2.6：原生转换、批量配额、拒绝增殖及模板保持
./gradlew runGameTestServer -Puse_local_upstream=true -Penable_chiseling_runtime=true -Pgametest_namespaces=ae2ltpp_core,ae2ltpp_chiseling
# Ars Nouveau 3.22.3 + Nature's Aura 38.2：催化物所有权、原生消耗与重载
./gradlew runGameTestServer -Puse_local_upstream=true -Penable_integration_runtime=true -Pintegration_mods=ars,naturesaura -Pgametest_namespaces=ae2ltpp_ars,ae2ltpp_naturesaura
```

`enable_integration_runtime` 还提供 agriculture、extendedcrafting、occultism、botania、forbidden、mekanism 的 1.19.2 依赖配置。这些配置只声明可复现的运行依赖，不代表全部对应行为已经通过整合包实测。未加载的可选模组不影响核心功能。1.20.1 专用兼容对象（如 AvaritiaNeo、Mekanism More Machines 等）不能将其新版本 JAR 直接用于本分支。

历史测试仍保留于 `ae2ltpp` 命名空间，必须先安装各测试所需、且与 1.19.2 匹配的模组。不要把缺少测试依赖导致的失败当作核心测试失败。

## 许可证与 API

源码保留 GNU LGPL 3.0 与原作者署名。LT/TB 依赖继续使用稳定公共 API；本模组自有 1.19.2 渲染与方向适配位于 `packaged.client.compat` 和 `packaged.compat`，不依赖 LT 的内部兼容包。
