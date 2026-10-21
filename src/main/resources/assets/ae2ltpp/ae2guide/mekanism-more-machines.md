---
navigation:
  parent: index.md
  title: Large Machines
  icon: mekmm_packaged_core
  position: 180
item_ids:
- ae2ltpp:mekmm_packaged_core
---

# Mekanism: More Machines — Large Machines

**Packaged Core:** <ItemLink id="ae2ltpp:mekmm_packaged_core" />
**Mod:** Mekanism: More Machines

A single Packaged Core can drive **all** large multiblock machines from this mod.
Build the machine, place the provider against it, install the Packaged Core, and encode the corresponding **processing pattern**. The provider automatically detects the type of machine it faces and retrieves the crafting result.

* Item, fluid, and chemical inputs in the pattern are automatically sent to the corresponding ports.

A regular provider can touch the controller or any of the machine's bounding blocks; it does not need to touch an input or output port. Wireless providers can also link to either the controller or a bounding block.

The green **Found** badge at the top right of the screen means a supported machine was recognized. Hover over it for machine names and the count; multiple wireless links to the same large machine count once. A gray **None** badge means no machine is currently recognized. Recognition is independent of recipe matching: dispatch still requires the correct core, recipe quantities and machine operating conditions.

## Covered machines

* Large Chemical Infuser
* Large Electrolytic Separator
* Large Antiprotonic Nucleosynthesizer
* Large Rotary Condensentrator
* Large Solar Neutron Activator
* Large Pigment Mixer

## Notes

* The Large Chemical Infuser consumes both chemical inputs according to the recipe. They are not resident catalysts like an Ars Imbuement Chamber's pedestal items. Encode the actual amounts consumed in both inputs, and do not declare unchanged chemical returns as byproducts. Stored input chemicals are a consumable buffer; auto-return drains only the output ports and leaves the input buffer in place.
* The machine still needs its own **energy**. Keep it adequately powered.
* With blocking mode disabled, inputs can be supplied while the machine is running, and blocked requests can retry on the next tick. Auto-return polls output ports every 1–20 ticks, returning to fast polling after collecting output or accepting new input.
