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


## Covered machines

* Large Chemical Infuser
* Large Electrolytic Separator
* Large Antiprotonic Nucleosynthesizer
* Large Rotary Condensentrator
* Large Solar Neutron Activator
* Large Pigment Mixer

## Notes

* The machine still needs its own **energy**. Keep it adequately powered.

Failed requests can retry on the next tick; duplicate failures within one tick are suppressed without blocking other patterns. Both chemical inputs to the Large Chemical Infuser are consumed according to the recipe. They are not resident catalysts; auto-return reads only output ports.

Adjacent providers can touch the controller or a bounding block created by the machine itself. Wireless providers can link either with the Overloaded Wireless Connector; keep the target chunk loaded. Multiple links to one machine count only once.

The green **Found** or gray **None** status shows machine discovery. Hover for machine names and count. Dispatch still requires a matching core, processing pattern and machine operating conditions. Wireless mode keeps its connection-tool instructions visible.
