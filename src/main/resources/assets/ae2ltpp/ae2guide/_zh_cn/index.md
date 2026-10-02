---
navigation:
  title: AE2 闪电科技封包：重生
  position: 67
item_ids:
- ae2ltpp:packaged_pattern_provider
- ae2ltpp:wireless_packaged_pattern_provider
---

# AE2 闪电科技封包：重生

`恭喜你走到了这一步！这将是你所掌握的闪电力量最巅峰之作！`

**封包样板供应器**(以及它的无线版本)可以让 AE2 自动合成去驱动**其它模组**的合成机器。大大简化较为复杂、低效的自动化搭建。
~~`但似乎你仍需对这些合成结构有足够深入的了解…`~~
只需在供应器里放入一张对应的**封包核心**，即可选择要驱动哪种机器。

## 制作专用核心

先制作**基础封包核心**，再放进目标模组的对应机器中加工为专用核心。二次加工主要使用该机器所在阶段的常见材料；具体摆放和能量需求请查看配方查看器。

* AA 充能台核心使用普通晶体；龙研聚合核心使用基础级聚合、龙芯和龙锭。
* EC 工作台核心使用本级组件、黑铁锭和流明精华；末影、通量与组合核心也使用常规材料。
* 无尽工作台核心使用该阶段的常见材料。EC 与无尽的工作台配方虽然只占用 3×3 区域，仍必须在对应等级的工作台中制作。
* 无尽终焉锻造核心使用工程处理器作为模板，并在三个添加物槽中各放一个钻石晶格。
* 神秘农业注魔核心使用繁荣碎片和下级精华；神秘学仪式核心在魔精灌注仪式「埃兹维斯的灵体驱策」中制作。

## 通用用法

1. **先插入封包核心**：把对应核心放入供应器的「封包核心」槽。
2. 普通供应器贴着目标机器的**主方块／核心**放置；无线供应器用连接器绑定该方块。
   目标必须受到当前核心支持，未插核心或核心不匹配时拒绝连接。手持凿子转换无需目标。
3. 像普通样板供应器一样，把相应配方做成**处理样板**放进去。
4. 当 AE2 请求该产物时，供应器会自动读取机器配方(包括自定义配方)，并把材料发送到机器，完成后自动将产物回收到你的 ME 网络中。

> 封包样板供应器的行为类似于模拟玩家合成，因此多方块机器仍需按它**本身的结构**搭好。普通供应器需要贴在
> **主方块／核心**上，无线供应器则需要把无线连接目标设为该方块。你也仍需要为这些机器供应充足的能量/魔力/魔源等。
>
> 如果无法正常发配合成，请检查机器结构是否完整，机器内是否有残留，是否放上正确的封包核心。
>
> 神秘学灵火、Chipped 工作台等没有方块实体的目标也支持无线连接。
> 临时取下核心会停工，但保留已有连接；目标被拆除后连接失效。
>
> 如果无法正常回收材料，请检查供应器是否开启自动回收。
>
> 带 **⚠** 标记的机器有额外注意事项——使用前请先看它的单独页面。

## 已支持的机器

### Actually Additions（实用拓展）
* [原子再构机](atomic-reconstructor.md)
* [充能台](empowerer.md)

### Ars Nouveau（新生魔艺）
* [附魔装置](enchanting-apparatus.md)
* [灌注室](imbuement-chamber.md) ⚠

### Draconic Evolution（龙之进化）
* [聚合合成](fusion-crafting.md)

### Extended Crafting（扩展合成）
* [工作台 — 基础 / 高级 / 精英 / 终极](ec-tables.md)
* [末影合成器](ender-crafter.md)
* [通量合成器](flux-crafter.md)
* [组合合成](combination-crafter.md)

### Mystical Agriculture（神秘农业）
* [注魔祭坛](infusion-altar.md)
* [觉醒祭坛](awakening-altar.md) ⚠

### Malum（恶意）
* [精魂聚焦 — 灵魂坩埚](spirit-focusing.md) ⚠
* [精魂灌注 — 灵魂祭坛](spirit-infusion.md)

### Occultism（神秘学）
* [神秘仪式](occultism-ritual.md) ⚠
* [灵火](spirit-fire.md)

### Mekanism: More Machines（通用机械：更多机器）
* [大型机器 — 一个封包核心通用](mekanism-more-machines.md)

### Botania（植物魔法）
* [花药台](petal-apothecary.md) ⚠
* [魔力池](mana-pool.md) ⚠
* [精灵传送门](alfheim-portal.md) ⚠
* [泰拉凝聚板](terra-plate.md)
* [符文祭坛](runic-altar.md) ⚠

### Re-Avaritia（无尽贪婪）
* [工作台 — 幽匿 / 炼狱 / 终末 / 终焉](avaritia-tables.md)
* [终焉锻造台](avaritia-extreme-smithing.md)

### Astral Sorcery
* [星辉魔法祭坛与灌注器](astral-sorcery.md) ⚠

### 凿子与装饰方块
* [Chipped、Rechiseled 与 Chisel](chiseling.md)
