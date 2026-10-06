---
navigation:
  title: AE2LT Packaged Pattern Provider Reborn
  position: 67
item_ids:
- ae2ltpp:packaged_pattern_provider
- ae2ltpp:wireless_packaged_pattern_provider
---

# AE2LT Packaged Pattern Provider Reborn

`Congratulations on making it this far! This is the pinnacle of the lightning power you have mastered!`

The **Packaged Pattern Provider** (and its wireless version) lets AE2 autocrafting drive crafting machines from **other mods**, greatly simplifying automation setups that would otherwise be complex and inefficient.
~~`Although it seems you still need a thorough understanding of these crafting structures...`~~
Insert the corresponding **Packaged Core** into the provider to select the type of machine it will control.

## Making specialized cores

Craft a **Basic Packaged Core**, then process it in the target mod's corresponding machine to make a specialized core. The shared Chiseling core uses a crafting-grid recipe with any supported chisel. This second step mainly uses materials readily available at that machine's progression stage. Check your recipe viewer for the exact layout and energy requirements.

* The AA Empowerer core uses regular crystals. The Draconic Evolution Fusion core uses Draconium-tier fusion crafting, a Draconium Core, and Draconium Ingots.
* EC table cores use components of their own tier, Black Iron Ingots, and Luminessence. Ender, Flux, and Combination cores also use common materials.
* Avaritia table cores use materials available at their respective stages. Although EC and Avaritia table recipes occupy only a 3×3 area, they still require the corresponding table tier.
* The Avaritia Extreme Smithing core uses an Engineering Processor as its template and one Diamond Lattice in each of the three addition slots.
* The Mystical Agriculture Infusion core uses Prosperity Shards and Inferium Essence. The Occultism Ritual core uses a Foliot crafting ritual.

## General usage

1. Put the matching Packaged Core into the provider's **Packaged Core slot** first.
2. Place a regular provider against the target machine's **main block / core**, or use the wireless connector
   to link a wireless provider to it. The installed core must support the target; missing or
   mismatched cores are rejected. Portable chiseling conversions do not need a target.
3. As with a normal Pattern Provider, encode the recipe as a **processing pattern** and insert it.
4. When AE2 requests the output, the provider reads the machine recipe (including custom recipes), sends the ingredients to the machine, and automatically returns the completed result to your ME network.

> The Packaged Pattern Provider behaves much like a simulated player. Multiblock machines must still be built
> according to **their own structure**. A regular provider must touch the **main block / core**, while a wireless
> provider must target that block through its wireless connection. You must also supply the machine with enough
> power, mana, Source, or other required resources.
>
> If a craft cannot be dispatched correctly, check that the machine structure is complete, the machine contains no leftover items, and the correct Packaged Core is installed.
>
> Block-only targets such as Occultism Spirit Fire and Chipped workbenches support wireless connections.
> Removing a core stops its recipes without erasing saved links; removing the target invalidates the link.
>
> If materials are not returned correctly, check that automatic return is enabled on the provider.
>
> Machines marked **⚠** have additional requirements—read their individual pages before using them.

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

### Astral Sorcery
* [Astral Sorcery Altars and Infuser](astral-sorcery.md) ⚠

### Chiseling
* [Chipped, Rechiseled and Chisel](chiseling.md)
