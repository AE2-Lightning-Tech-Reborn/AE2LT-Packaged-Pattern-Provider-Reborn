---
navigation:
  title: AE2LT Packaged Pattern Provider Reborn
  position: 67
item_ids:
- ae2ltpp:packaged_pattern_provider
- ae2ltpp:wireless_packaged_pattern_provider
- ae2ltpp:extended_packaged_pattern_provider
---

# AE2LT Packaged Pattern Provider Reborn

`Congratulations on making it this far! This is the pinnacle of the lightning power you have mastered!`

The **Packaged Pattern Provider** (and its wireless version) lets AE2 autocrafting drive crafting machines from **other mods**, greatly simplifying automation setups that would otherwise be complex and inefficient.
~~`Although it seems you still need a thorough understanding of these crafting structures...`~~
Insert the corresponding **Packaged Core** into the provider to select the type of machine it will control.

## Extended Packaged Pattern Provider

The extended model has **144 pattern slots across four pages of 36**. Use the arrows or scroll over the pattern area to change pages. Patterns on every page remain available to the ME network. It retains one core slot and supports all packaged cores, including the Resonant Packaged Core.

Use the left toolbar to switch between **normal and wireless mode**. Normal mode uses adjacent machines; wireless mode uses connector-bound machines. New providers start in normal mode. Switching back preserves wireless bindings for later reuse. The mode, patterns, core and bindings survive world reloads. Inputs already accepted before a switch continue to their original ports.

Lightning assembly upgrades one Wireless Packaged Pattern Provider with four Capacity Cards, sixteen Engineering Processors and one 256k ME Storage Component, consuming 128,000,000 FE and 512 extreme-high-voltage lightning strikes. The original normal and wireless providers retain their fixed modes and 36 slots.

## Making specialized cores

Craft a **Basic Packaged Core**, then process it in the target mod's corresponding machine to make a specialized core. The shared Chiseling core uses a crafting-grid recipe with any supported chisel. This second step mainly uses materials readily available at that machine's progression stage. Check your recipe viewer for the exact layout and energy requirements.

* The AA Empowerer core uses regular crystals. The Draconic Evolution Fusion core uses Draconium-tier fusion crafting, a Draconium Core, and Draconium Ingots.
* EC table cores use components of their own tier, Black Iron Ingots, and Luminessence. Ender, Flux, and Combination cores also use common materials.
* Avaritia table cores use materials available at their respective stages. Although EC and Avaritia table recipes occupy only a 3×3 area, they still require the corresponding table tier.
* The Avaritia Extreme Smithing core uses an Engineering Processor as its template and one Diamond Lattice in each of the three addition slots.
* The Mystical Agriculture Infusion core uses Prosperity Shards and Inferium Essence. The Occultism Ritual core uses a Foliot crafting ritual.

## Core orders and upgrades

Click **Core crafting** in a provider to view initial core recipes and upgrades in one JEI category. Encode the processing pattern and make it available on the network, then use `+` in JEI opened from that provider to order one core. Without JEI, use the core selection list. Each provider handles one order at a time and requires an available AE2 crafting CPU. The Basic Packaged Core remains an ordinary crafting ingredient.

A provider without a core can manufacture its first specialized core. Build, connect and power the machine required by the native recipe. For wireless providers, insert the core-making pattern before binding the machine. This exception only admits core outputs that match a real machine recipe.

* **Initial manufacture:** install the delivered core if the slot is empty; otherwise keep the installed core and return the new one to ME.
* **Upgrade pattern includes the old core:** consume the core supplied by the CPU, leave the installed core alone, and return the result to ME.
* **Upgrade pattern omits the old core:** require the matching core already installed in the ordering provider. JEI shows it as a requirement without transferring it into the pattern. Consume it only when the supplied materials complete the upgrade, then install the result. Ordering, planning and missing materials never remove it.

Upgrades that omit the old core must be ordered from that provider. Ordinary terminal orders use AE2's normal item delivery to ME; use a provider order for automatic installation. Upgrade recipes currently cover adjacent EC table, Avaritia table and Astral altar tiers. The provider completes these upgrades directly from their listed materials; native machine recipes remain separately available. Results destined for a full ME inventory stay buffered in the provider until space becomes available.

## Special resource keys

Forbidden Arcanus Aureal, Souls, Blood and Experience are distinct resources with quantities in ME storage and processing patterns. Attach a storage bus to a native Essence Utrem Jar or Hephaestus Forge to access its resources. Import and export buses use the same resource type and respect native capacity limits. A jar accepts only the essence type selected by its block state. With AE2 JEI Integration, resource ingredients can be transferred into patterns.

Mekanism chemicals continue to use Applied Mekanistics Chemical Keys. FA essences can be stored in native jars connected to ME.

[FA Packaged Processing](fa-processing.md)

## General usage

1. Put the matching Packaged Core into the provider's **Packaged Core slot** first.
2. Place a regular provider against the target machine's **main block / core**, or use the wireless connector
   to link a wireless provider to it. The installed core must support the target, except for first-core manufacture with its pattern already inserted as described above. Other missing or mismatched cores are rejected. Portable chiseling conversions do not need a target.
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

### Nature's Aura
* [Natural Altar, Ritual of the Forest and Offering Table](natures-aura.md)

### Crystal Science
* [Resonant layouts for ordinary and resonating patterns](resonant.md)
