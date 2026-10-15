---
navigation:
  parent: index.md
  title: Resonant Layouts
  icon: resonant_packaged_core
  position: 170
item_ids:
- ae2ltpp:resonant_packaged_core
---

# Resonant Layouts

Combine a Basic Packaged Core with a Crystal Science Resonating Pattern Provider to make a
<ItemLink id="ae2ltpp:resonant_packaged_core" />. It records the input and output ports of a machine
layout and sends materials through the machines' normal inventories. The machines still perform
their own recipes and consume their own power.

1. Hold the core and use it on the machine's main block to select the **source anchor**.
2. Sneak-scroll to choose an input slot, then use the core on that slot's destination face.
   Slot numbers refer to the original processing-pattern grid, including empty slots and duplicate ingredients.
   There are 144 input slots. Using the same face again clears its binding.
3. Use the core in air to switch to the output-port group. Bind up to 36 output ports in the same way.
   Switch back by using it in air again. Output ports are only read when the provider's auto-return is enabled;
   only outputs declared by its patterns are collected. Use the machine's dedicated output face where possible.
4. Install the core. An ordinary Packaged Pattern Provider uses an adjacent matching anchor on its output side.
   A Wireless Packaged Pattern Provider uses the anchors bound with the wireless connector. Each anchor must
   be the same block type as the source; every ingredient of a craft is delivered to one complete layout.

**Normal processing patterns** use the core's per-slot bindings. Unbound inputs go to the anchor's original
clicked face. Crafting patterns are not handled by this core.

**Crystal Science resonating patterns** keep their explicit per-ingredient targets. These absolute positions
are converted relative to the core's recorded source anchor, then applied at the current machine anchor.
Unmarked inputs use the core's defaults. All explicit targets must be in the source dimension. Bind the source
anchor in the layout where those patterns were marked; an arbitrary new source would describe a different layout.
The original source does not need to stay loaded or exist after binding.

Offsets and port faces rotate horizontally with the anchor's facing. For blocks without a horizontal facing,
the clicked/bound horizontal face defines the orientation; a vertical face uses north as the reference.
Bind copies from the corresponding face. The current anchor's dimension is used for the whole layout.

Use another resonant core on a configured provider to copy its layout, including the source reference.
Sneak-use on a machine deliberately replaces that reference and clears the old bindings. Moving a core between
providers preserves its layout. Targets outside loaded chunks wait; the core never loads chunks itself.

Before accepting a craft, all ports are checked. If a machine accepts only part of an input afterward, the
provider keeps the remaining materials with their original positions and faces, and resumes delivery when
possible. Pending inputs survive reloads and core changes; breaking the provider drops only undelivered inputs.
New crafts wait until this delivery finishes. Replacing a destination with a different block type pauses delivery.
