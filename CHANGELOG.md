# Changelog

## Unreleased — Forge 1.20.1

- Route core orders through the native terminal confirmation and status screens, with first-core installation, spare-core batches and single-core in-place upgrades. Reject invalid or busy-provider submissions on the server and preserve intermediate crafting outputs.
- Add a configurable 32-block resonant input range, validate dimensions and coordinates before routing, and preserve legacy output layouts. Return buffered resources to ME storage before removal and retain virtual-batch remainders after partial insertion.
- Match Botania petal recipes using the actual component variants and available inventory capacity, preserving quantities during partial insertion and recovery.
- Fix a Forge/Netty protocol-switch race that could leave automatic reads enabled without a read subscription and stall multiplayer login until the default 30-second timeout. Restore the subscription after protocol changes while respecting deliberate read pauses.
- Keep long provider and core-order confirmation titles inside their panels, clear of title-bar buttons; hover over a shortened title to read it in full. Handle the confirmation menu's initial empty destination state safely.

## 1.2.2-beta2 — Forge 1.20.1

对比版本：1.20.1-1.2.2-beta（2026-10-08 发布）

- 新增谐振封包核心及可复制、旋转的输入输出布局，支持水晶科技样板；新增拥有 144 个样板槽、四页界面的扩展供应器。
- 新增供应器核心订单、首个核心制作、EC／Avaritia 核心升级和 JEI 配方入口，通过实际 ME 合成任务确认产物。
- 新增自然灵气自然祭坛、森林仪式和供奉台自动化，完善拆除、保存与重载恢复。
- 新增女仆祭坛、P 点 ME 存取和经验球流体回收；补齐 FA 原生精华转换、ME 精华存储及黑洞经验流体转换。
- 新生魔艺注魔室支持使用预先放置的常驻催化剂；改善机器识别、无线连接提示及小尺寸界面适配。
- 调整 20 种专用核心制作成本，完善高级核心对前一级核心的升级要求；改善 DE 注入器刷新、雕刻、灵火及 Mekanism More Machines 批量加工。
- 修复 EC 实时配方判断、隐藏分页槽位编辑、供应器拆除掉落、批量回执持久化及超大数量产物和退款的回存问题。
- 配套依赖更新为 AE2 Lightning Tech 2.1.2、Thunderbolt Core 2.0.3，均使用 Forge 1.20.1 版本。

## 1.2.2-beta — Forge 1.20.1 backports

- Ars Nouveau Imbuement Chamber patterns containing only the reagent now use matching preplaced catalysts. The catalysts stay on their pedestals across repeated crafts and machine reloads. Patterns declaring catalysts retain the borrow-and-return workflow.
- Removed “Reborn” / “重生” from this addon's displayed name, creative tab and guide titles. Updated the English and Chinese imbuement guides to explain both catalyst workflows.
- Backported chiseling conversions, pure-workbench batch crafting, MekMM continuous dispatch, DE injector refresh, fuzzy input matching and empty core-slot hints. See [UPSTREAM_BACKPORTS.zh-CN.md](UPSTREAM_BACKPORTS.zh-CN.md) for source commits and Forge-specific coverage.
- Removed unused adapter helpers, reflection caches and test fields; shared Ender/Flux grid planning and harvest limits, simplified menu wiring, and corrected outdated cache and version comments.
- Third cleanup removed duplicated Ender/Flux reflection and grid helpers, unused reload and batch overload paths, and stale comments; production sources are 409 lines shorter. Added three EC crafter boundary tests.
