---
navigation:
  parent: index.md
  title: Imbuement Chamber
  icon: ars_imbuement_packaged_core
  position: 40
item_ids:
- ae2ltpp:ars_imbuement_packaged_core
---

# Imbuement Chamber

**Packaged Core:** <ItemLink id="ae2ltpp:ars_imbuement_packaged_core" />
**Mod:** Ars Nouveau

Automatically performs Imbuement Chamber recipes. Place the provider against the Chamber, then place **enough** Arcane Pedestals and/or Arcane Platforms anywhere within the one-block-radius cube around it.
The reagent goes into the Chamber, and the result is retrieved when imbuing finishes. Ars checks pedestal catalysts without consuming them. Choose one of the following setups.

## Resident catalysts

For a dedicated recipe, place its catalysts on the pedestals yourself. Encode a processing pattern with **only the reagent as input and the finished product as output**. The provider inserts the reagent and collects the product; it leaves the catalysts on the pedestals for subsequent crafts, including after a save/reload.

The Chamber must be empty before dispatch. The occupied pedestals must match the recipe exactly; missing, wrong, or extra catalyst items on separate pedestals block dispatch. Resident catalysts do not belong in the pattern's inputs or byproducts.

## Borrow and return catalysts

To switch recipes on one Chamber, include **the reagent and all catalysts as inputs**, and include **the product and those same catalysts as byproducts**. The provider places the catalysts, then returns them to the ME network with the finished product. Returned catalyst keys must preserve the input items' NBT and quantities.

Before starting this mode, the Chamber and every Arcane Pedestal or Arcane Platform within 1 block must be empty. Keep the catalysts in ME stock so the craft can borrow them. Clear resident catalysts before switching to this mode.
