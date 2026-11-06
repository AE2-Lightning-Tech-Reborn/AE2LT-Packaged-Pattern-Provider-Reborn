---
navigation:
  title: AE2LT Packaged Pattern Provider
  position: 67
item_ids:
- ae2ltpp:packaged_pattern_provider
- ae2ltpp:wireless_packaged_pattern_provider
- ae2ltpp:extended_packaged_pattern_provider
---

# AE2LT Packaged Pattern Provider

`Congratulations on making it this far! This is the pinnacle of the lightning power you have mastered!`

The **Packaged Pattern Provider** (and its wireless version) lets AE2 autocrafting drive crafting machines from **other mods**, greatly simplifying automation setups that would otherwise be complex and inefficient.
~~`Although it seems you still need a thorough understanding of these crafting structures...`~~
Insert the corresponding **Packaged Core** into the provider to select the type of machine it will control.

## General usage

1. For a regular Packaged Pattern Provider, place it **touching the target machine's main block / core**.
   For a Wireless Packaged Pattern Provider, set its wireless connection target to that block; it does not
   need to touch the machine.
2. Put the matching Packaged Core into the provider's **Packaged Core slot**. Each provider can serve only one type of machine at a time.
3. As with a normal Pattern Provider, encode the recipe as a **processing pattern** and insert it.
4. When AE2 requests the output, the provider reads the machine recipe (including custom recipes), sends the ingredients to the machine, and automatically returns the completed result to your ME network.

> The Packaged Pattern Provider behaves much like a simulated player. Multiblock machines must still be built
> according to **their own structure**. A regular provider must touch the **main block / core**, while a wireless
> provider must target that block through its wireless connection. You must also supply the machine with enough
> power, mana, Source, or other required resources.
>
> If a craft cannot be dispatched correctly, check that the machine structure is complete, the machine contains no leftover items, and the correct Packaged Core is installed.
>
> A very small number of recipes, such as Occultism Spirit Fire crafting, cannot use the Wireless Packaged Pattern Provider. If a recipe does not work, try a regular Packaged Pattern Provider.
>
> If materials are not returned correctly, check that automatic return is enabled on the provider.
>
> Machines marked **⚠** have additional requirements—read their individual pages before using them.

## Specialized core recipes

Craft specialized cores from a Basic Packaged Core and a small amount of machine materials. EC table cores use the matching tier's components and still require that tier of table. Higher cores continue to cover lower tiers. The fusion core uses basic injectors and 32,000 RF.

### Automatic crafting and upgrades

Use the provider's core crafting button to request a core. The network needs a crafting CPU, ingredients, and encoded processing patterns. A finished first core installs into an empty core slot; an occupied slot sends the result back to ME. A separate selector is available without JEI.

A provider without a core may run its machine's real core-manufacturing pattern. Place a normal provider beside that machine; for a wireless provider, insert the core pattern before binding the machine. Native recipe, structure, and energy requirements still apply. This admission rule unlocks only the matching core recipe.

EC and Re-Avaritia table cores support tier upgrades. Encode the upgrade materials as a processing pattern, omitting the installed core, and place the order from that provider. The installed core is checked and consumed only when the materials complete. Missing materials or a changed core prevent completion. If the pattern explicitly includes the old core, ME supplies it and receives the upgrade output. Cancellation, full storage, and reloads preserve any core already delivered to the provider's output buffer.

## Resonant layouts and extended provider

[Resonant layouts](resonant.md) route materials through configured machine ports. The Extended Packaged Pattern Provider stores 144 patterns across four pages and switches between adjacent and wireless targets.

## Supported machines

### Actually Additions
* [Atomic Reconstructor](atomic-reconstructor.md)
* [Empowerer](empowerer.md)

### Ars Nouveau
* [Enchanting Apparatus](enchanting-apparatus.md)
* [Imbuement Chamber](imbuement-chamber.md) ⚠

### Draconic Evolution
* [Fusion Crafting](fusion-crafting.md)

### Extended Crafting
* [Crafting Tables — Basic / Advanced / Elite / Ultimate](ec-tables.md)
* [Ender Crafter](ender-crafter.md)
* [Flux Crafter](flux-crafter.md)
* [Combination Crafter](combination-crafter.md)

### Mystical Agriculture
* [Infusion Altar](infusion-altar.md)
* [Awakening Altar](awakening-altar.md) ⚠

### Malum
* [Spirit Focusing — Spirit Crucible](spirit-focusing.md) ⚠
* [Spirit Infusion — Spirit Altar](spirit-infusion.md)

### Occultism
* [Ritual](occultism-ritual.md) ⚠
* [Spirit Fire](spirit-fire.md)

### Mekanism: More Machines
* [Large Machines — one packaged core for all](mekanism-more-machines.md)

### Botania
* [Petal Apothecary](petal-apothecary.md) ⚠
* [Mana Pool](mana-pool.md) ⚠
* [Alfheim Portal](alfheim-portal.md) ⚠
* [Terra Plate](terra-plate.md)
* [Runic Altar](runic-altar.md) ⚠

### Re-Avaritia
* [Crafting Tables — Sculk / Nether / End / Extreme](avaritia-tables.md)
* [Extreme Smithing Table](avaritia-extreme-smithing.md)

## Core ordering and upgrades

Choose a manufacturing or in-place upgrade recipe in JEI, encode a processing pattern, put it in a packaged provider, then order from an ME terminal. The network needs materials, power and a crafting CPU; patterns can also be encoded manually without JEI. The confirmation button shows **Make & install**, **Upgrade** or **Make to ME**; hover for the target provider location. A corresponding real machine can manufacture its first core with an empty core slot, including wireless connections. An empty slot receives one core, with extras stored in ME. If the slot is occupied, all results go to ME. Cancel orders in terminal crafting status.

EC and Avaritia in-place upgrades require one core per order. Omit the old core from the pattern to consume the target provider's installed core when the upgrade step executes. To manufacture spare cores in bulk, include the old core as a pattern input: materials come from ME and all results return to ME. Missing materials or a changed installed core prevent the upgrade. Intermediate cores needed by another crafting job stay in its CPU instead of being installed.

* [Black Hole](black-hole.md)

For an installed-core upgrade, encode the target core recipe materials without its predecessor core. For example, EC Advanced requires four Luminessence, two Advanced Components and two Black Iron Ingots → one Advanced Table Packaged Core. The provider consumes its installed Basic Table core on completion. To consume an old core from ME instead, explicitly add that specialized core to the pattern.

### Touhou Little Maid

* [Maid altar and P power](maid-altar.md)

The six higher-tier EC / Avaritia native core recipes also consume their predecessor core.
