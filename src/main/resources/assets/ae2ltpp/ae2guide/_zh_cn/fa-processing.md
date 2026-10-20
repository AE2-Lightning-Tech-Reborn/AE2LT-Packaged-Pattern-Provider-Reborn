---
navigation:
  parent: index.md
  title: FA 机器封包
  icon: fa_hephaestus_forge_packaged_core
  position: 175
item_ids:
- ae2ltpp:fa_hephaestus_forge_packaged_core
- ae2ltpp:fa_clibano_packaged_core
- ae2ltpp:fa_black_hole_packaged_core
---

# FA 机器封包

请将供应器回收模式设为 **自动回收（AUTO）**，以收取原生机器的产物。

* <ItemLink id="ae2ltpp:fa_hephaestus_forge_packaged_core" /> 同时支持原生仪式和四槽资源转化。仪式物品由样板完整提供；四种点数按 **样板 → 锻炉已有 → ME 内部** 的顺序补齐，合计不足拒绝发配。仍需正确的锻炉底座、等级、强化物品及空基座，仪式由 FA 原生计时完成。
* 资源转化样板将物品放入对应的四个原生槽位，等待 FA 转化后回收点数和空容器，保留炉内原有点数。必须为完整产量预留容量；去附魔配方按最低保证经验编码，实际多出的经验一并返回。正在执行封包任务的锻炉暂不向 ME 总线开放点数，防止同一份资源被重复使用。
* <ItemLink id="ae2ltpp:fa_clibano_packaged_core" /> 将材料发到冶炼炉输入槽，自动收回输出槽产物。原生燃料、火焰等级、强化物品和烧炼时间仍然生效；可连接已成型结构的外壳。
* <ItemLink id="ae2ltpp:fa_black_hole_packaged_core" /> 连接原生黑洞后执行虚拟转化：默认每 **1200 mB** `c:experience` 经验流体生成一个石化经验球。数量及流体标签可通过 `ae2ltpp:fa/black_hole` 数据包配方修改；没有匹配的经验流体时不提供这条配方。

JEI 的「FA 封包处理」页面会列出仪式点数、资源转化和黑洞配方。通过 AE2 JEI 集成传入处理样板，确保容器副产物和点数数量完整。三个核心的制作入口也收录在「封包核心制作与升级」页面中。
