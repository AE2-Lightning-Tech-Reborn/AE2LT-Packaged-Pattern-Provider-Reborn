---
navigation:
  parent: index.md
  title: 渡鸦灌注
  icon: apothic_infusion_packaged_core
  position: 177
item_ids:
- ae2ltpp:apothic_infusion_packaged_core
---

# 渡鸦灌注

<ItemLink id="ae2ltpp:apothic_infusion_packaged_core" />

制作核心及后续灌注都只接受**渡鸦附魔台**，普通附魔台不能参与封包。

首个核心消耗基础封包核心、3 个青金石与 **30,970 点经验（619,400 mB）**，即从 0 级升到 100 级的总经验。日常灌注沿用原模组每次灌注的经验费用与 3 个青金石；核心免除永恒、量子、奥秘的环境限制，仍保留原配方的物品匹配、组件和特殊成品逻辑。

经验流体与青金石依次使用样板发配量、附魔台内部存量、ME 网络存量。样板可以只写待灌注物品，由其余来源补足费用。原版渡鸦台保存青金石但没有经验槽；若其他模组提供流体接口，同样可用。供料不足不开始制作，扣款失败会回退。

破坏面板将经验球转换为 ME 网络中存量最多的已注册经验流体，每点经验 20 mB。流体由 `ae2ltpp:experience` 标签识别；没有匹配流体或存储不足时保留经验球。合并经验球逐份收取，不重复支付。
