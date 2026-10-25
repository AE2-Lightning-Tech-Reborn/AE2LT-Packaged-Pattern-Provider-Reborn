---
navigation:
  parent: index.md
  title: Scribe's Table
  icon: ars_scribes_packaged_core
  position: 42
item_ids:
- ae2ltpp:ars_scribes_packaged_core
---

# Scribe's Table

**Packaged Core:** <ItemLink id="ae2ltpp:ars_scribes_packaged_core" />
**Mod:** Ars Nouveau

Automates native glyph recipes. Connect the provider to either half of a complete Scribe's Table, install this core, and enable automatic return. Encode one craft's exact ingredients and actual glyph output in a processing pattern. The table must have no existing recipe or displayed item.

Experience uses the same fluids as Raven infusion: **1 experience point = 20 mB**, selected by the `ae2ltpp:experience` fluid tag. Use the recipe's experience points, not player levels. For example, a 37-point recipe costs 740 mB. Supply experience fluid in the pattern; any missing amount is taken from the table's fluid capability, when one is provided by another mod, then from the provider's ME network. An item-only pattern is also supported when the network has sufficient experience fluid. Supplying more fluid than the recipe costs is rejected.

Items and experience must both be available before the table starts. Insufficient supplies do not start a partial recipe. The table keeps its native 120-tick scribing process; occupied tables cannot be overwritten, and completed glyphs return once. In-progress recipes and captured results survive saves and reloads. Nearby manual drops are not collected.

Unfinished dismantling and cancellation keep Ars Nouveau's own item and experience behavior; the packaged core adds no experience refund. Completed glyphs waiting for automatic return are dropped if either table half is dismantled.

This core is made in an Enchanting Apparatus using a Basic Packaged Core as reagent, one Scribe's Table and three Source Gems on pedestals, and 1,000 source. This integration covers glyph recipes; copying player-authored spells through the table remains a manual action.
