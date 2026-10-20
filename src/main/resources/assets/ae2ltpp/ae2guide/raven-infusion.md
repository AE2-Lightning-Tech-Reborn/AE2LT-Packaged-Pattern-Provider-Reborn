---
navigation:
  parent: index.md
  title: Raven Infusion
  icon: apothic_infusion_packaged_core
  position: 177
item_ids:
- ae2ltpp:apothic_infusion_packaged_core
---

# Raven Infusion

<ItemLink id="ae2ltpp:apothic_infusion_packaged_core" />

Both core manufacture and subsequent infusion require a **Raven Enchanting Table**. Ordinary enchanting tables are excluded.

The first core costs a basic packaged core, 3 lapis and **30,970 XP (619,400 mB)**: the complete cost from level 0 to 100. Later infusion uses the native per-infusion XP cost and 3 lapis. The core waives Eterna, Quanta and Arcana environment bounds while preserving native ingredient, component and special output logic.

XP fluid and lapis are paid from explicit pattern inputs, then table stores, then ME reserves. Patterns may omit these auxiliary ingredients. Native Raven tables store lapis but have no XP tank; fluid capabilities supplied by other mods are supported. Insufficient supplies reject work and failed payment rolls back.

Annihilation planes convert XP orbs into the registered XP fluid with the largest network stock, at 20 mB per XP. Fluids use the `ae2ltpp:experience` tag. Without a matching fluid or enough storage the orb remains. Merged orbs are redeemed one member at a time.
