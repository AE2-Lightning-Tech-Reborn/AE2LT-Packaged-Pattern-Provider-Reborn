---
navigation:
  parent: index.md
  title: FA Packaged Processing
  icon: fa_hephaestus_forge_packaged_core
  position: 175
item_ids:
- ae2ltpp:fa_hephaestus_forge_packaged_core
- ae2ltpp:fa_clibano_packaged_core
- ae2ltpp:fa_black_hole_packaged_core
---

# FA machine processing

Set the provider return mode to **AUTO** to collect native machine outputs.

* <ItemLink id="ae2ltpp:fa_hephaestus_forge_packaged_core" /> handles native rituals and four-slot essence conversion. Patterns provide all physical ritual items. Essence costs use **pattern inputs, then existing forge stock, then ME reserves**; insufficient combined stock rejects dispatch. The native base, tier, enhancers, empty pedestals and ritual duration remain required.
* Conversion patterns place items into the four native conversion slots, wait for FA to process them, then return the resulting essences and empty containers while preserving existing forge stock. Leave enough capacity for the full output. Disenchanting patterns declare guaranteed minimum XP; additional actual XP is also returned. A forge executing packaged work temporarily locks essence access from ME buses to prevent double use.
* <ItemLink id="ae2ltpp:fa_clibano_packaged_core" /> dispatches inputs and collects the native output slots. Fuel, fire tier, enhancers and cooking time remain native. A formed furnace frame can be used as the connection target.
* <ItemLink id="ae2ltpp:fa_black_hole_packaged_core" /> performs a virtual conversion while connected to a native black hole: **1200 mB** of fluid tagged `c:experience` produces one Xpetrified Orb by default. Change the amount or tag through the `ae2ltpp:fa/black_hole` datapack recipe. No recipe is offered when no matching XP fluid exists.

JEI's **FA Packaged Processing** category includes ritual essence costs, conversion inputs and black-hole recipes. AE2 JEI Integration transfers these quantities and container byproducts into processing patterns. The three core recipes also appear in the combined core manufacture and upgrade category.
