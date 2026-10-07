---
navigation:
  parent: index.md
  title: Re-Avaritia Tables
  icon: avaritia_extreme_table_packaged_core
  position: 120
item_ids:
- ae2ltpp:avaritia_sculk_table_packaged_core
- ae2ltpp:avaritia_nether_table_packaged_core
- ae2ltpp:avaritia_end_table_packaged_core
- ae2ltpp:avaritia_extreme_table_packaged_core
---

# Re-Avaritia Crafting Tables

**Packaged Cores:** <ItemLink id="ae2ltpp:avaritia_sculk_table_packaged_core" />
<ItemLink id="ae2ltpp:avaritia_nether_table_packaged_core" />
<ItemLink id="ae2ltpp:avaritia_end_table_packaged_core" />
<ItemLink id="ae2ltpp:avaritia_extreme_table_packaged_core" />
**Mod:** Re-Avaritia

Automates the Re-Avaritia 1.4.1 runtime layouts: Sculk 3×3 (tier 1), Nether 5×5 (tier 2), End 7×7 (tier 3), and Extreme 9×9 (tier 4). Each block has its own exact handler-slot profile; a table with a different slot count is rejected rather than guessed.
Place the provider against a table and encode the corresponding **processing pattern**. The provider automatically retrieves the crafted result and any crafting remainders, such as empty buckets.

## ⚠ One packaged core covers lower tiers

The cores form a chain: a higher-tier core also unlocks every lower tier. An Extreme Crafting Table core
works on Extreme / End / Nether / Sculk, an End Table core works on End / Nether / Sculk, and so on.

## AvaritiaNeo (Forge 1.20.1)

AvaritiaNeo 1.1.6 is also supported: use the Extreme Crafting Table core with its 9×9 Extreme Crafting Table. Both shaped and shapeless `avaritia:extreme_crafting` recipes use virtual crafting, including batch outputs and crafting remainders. A separate `avaritia:extreme_shaped` recipe makes the same core using AvaritiaNeo's neutronium ingots.

The real crafting grid and result slot must be empty. The provider never takes manually placed ingredients or the table's preview result. Compressed crafting tables, the compressor, and Re-Avaritia-only tiered tables and extreme smithing are not part of this AvaritiaNeo integration.

## Notes

* The table's crafting grid must be empty while the provider is crafting.
* Recipes that inherit the output from the input item still keep their normal Re-Avaritia behavior.
`A crafting table, a big crafting table, a bigger crafting table, and an even bigger crafting table.`
