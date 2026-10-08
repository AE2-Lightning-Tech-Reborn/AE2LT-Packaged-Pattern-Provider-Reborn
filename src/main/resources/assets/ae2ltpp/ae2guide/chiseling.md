---
navigation:
  parent: index.md
  title: Chiseling
  icon: chiseling_packaged_core
  position: 160
item_ids:
- ae2ltpp:chiseling_packaged_core
---

# Chiseling

Combine a Basic Packaged Core with a supported chisel or any Chipped workbench to make the Chiseling Packaged Core.
Install it in either packaged provider and encode a processing pattern for the source material
and the desired variant. The core supplies the tool; only the material goes in the pattern.

Rechiseled conversions run inside the provider without a workbench or wireless target.
Use the real conversion ratio, including block/slab ratios; a conversion must consume the
entire input without leftovers. Unrelated variants and invented output NBT are rejected.

Chipped 3.x requires a complete workbench. Place the provider next to the main half or
connect the wireless provider to that half. Each workbench uses its own native recipe type;
only variants offered by that workbench can be exchanged, at 1:1. Removing either half stops crafting.
The provider does not access a player's inventory.

The installed mod's live recipes determine valid conversions, including registered addon recipes.
Normal provider power costs and the per-tick limit apply.
