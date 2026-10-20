---
navigation:
  parent: index.md
  title: 星辉魔法祭坛与灌注器
  icon: astral_radiance_packaged_core
  position: 220
item_ids:
- ae2ltpp:astral_illumination_packaged_core
- ae2ltpp:astral_resonance_packaged_core
- ae2ltpp:astral_luminance_packaged_core
- ae2ltpp:astral_radiance_packaged_core
- ae2ltpp:astral_infuser_packaged_core
---

# 星辉魔法祭坛与灌注器

* <ItemLink id="ae2ltpp:astral_illumination_packaged_core" />
* <ItemLink id="ae2ltpp:astral_resonance_packaged_core" />
* <ItemLink id="ae2ltpp:astral_luminance_packaged_core" />
* <ItemLink id="ae2ltpp:astral_radiance_packaged_core" />
* <ItemLink id="ae2ltpp:astral_infuser_packaged_core" />

适用于星辉魔法 1.21.1 / 2.0.2。四级祭坛核心向下兼容，灌注器使用独立核心。核心在对应级别的祭坛中制作，灌注器核心在共鸣祭坛制作。

## 使用

* 提供器贴住祭坛或灌注器主方块；无线提供器指定该主方块。编码单次加工样板，每次执行一份配方。
* 祭坛的 3×3 网格和 5×5 中继网格按原配方布置；配方需要的中继器必须已放好。开始前网格、中继器和灌注槽须为空。
* 将网格、中继器及额外掉落物需求都写入样板输入。额外物品会实际投放，由祭坛原生捕获。桶装流体写成物品输入，空桶等容器在加工完成后回收。
* 原生结构、夜晚、焦点星座、焦点破碎概率、星辉、流明、加工时间均保留。焦点和环境资源需自行提供；额外流体通过原生圣杯等方式供应，不写成 ME 流体输入。
* 灌注器周围的液体、结构和圣杯补液按星辉魔法要求准备。原生流体消耗概率保持不变。
* 开启自动回收。等待当前配方产物收回后才会开始下一份；请勿与手工投料混用。若中途破坏结构或夜间结束，材料保留在机器内，可手动恢复/取出。

## 产物与限制

* 晶体属性复制、合并和按尺寸决定的数量使用实际投入材料校验；样板数量必须与原配方吻合。
* 随机属性、随机附魔、唯一标识或战利品组件需要超载样板的“仅 ID 匹配”输出。实际属性由原生加工生成。
* 升级祭坛方块、解锁玩家研究及未知产物修改规则的配方不作为加工样板执行；请手动操作。
* 聚星缸、调谐祭坛、聚焦透镜世界转化等其它机器不在本页支持范围内。
