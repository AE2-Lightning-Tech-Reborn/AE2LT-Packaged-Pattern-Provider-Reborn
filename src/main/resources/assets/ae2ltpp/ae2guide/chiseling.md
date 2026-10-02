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

Combine a Basic Packaged Core with the corresponding mod's chisel in a crafting grid,
then install the resulting core in a Packaged Pattern Provider or Wireless Packaged Pattern Provider.
Encode a **processing pattern** with one source material and one chosen variant.
The core supplies the tool; the pattern only needs the material being converted.

* **Chipped 4.x:** use the shared Chiseling Packaged Core and place the provider against the visible
  main half of a complete workbench, or connect a wireless target to that half. Any of the seven
  workbenches works: this version shares conversion recipes between all workbench types.
  Removing either half stops crafting. Player inventory is never accessed.
* **Rechiseled:** use the same Chiseling Packaged Core. No workbench or wireless target is required.
  Both 1.1 and 1.2 recipe APIs are supported, including connected variants and registered addon recipes.
  In 1.2, encode the actual block/slab ratio. A conversion must use the entire declared input without leftovers.
* **Chisel Reborn / Chisel Modern:** use the same Chiseling Packaged Core. No workbench or wireless target
  is required. Reborn's chisel or Modern's iron, diamond or hitech chisel can make the core.
  Only variants in the installed mod's live carving group can be exchanged, at 1:1.
  These are alternative mods sharing the `chisel` id; install one of them.

Quantity and output components must match the real conversion. Extra inputs, unrelated variants,
invented output components and inflated output quantities are rejected. Processing patterns can
request multiple items per copy; the provider's normal power cost and batch limit still apply.
