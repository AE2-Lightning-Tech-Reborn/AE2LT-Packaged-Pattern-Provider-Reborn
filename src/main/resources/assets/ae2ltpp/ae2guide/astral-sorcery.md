---
navigation:
  parent: index.md
  title: Astral Sorcery Altars and Infuser
  icon: astral_radiance_packaged_core
  position: 220
item_ids:
- ae2ltpp:astral_illumination_packaged_core
- ae2ltpp:astral_resonance_packaged_core
- ae2ltpp:astral_luminance_packaged_core
- ae2ltpp:astral_radiance_packaged_core
- ae2ltpp:astral_infuser_packaged_core
---

# Astral Sorcery Altars and Infuser

* <ItemLink id="ae2ltpp:astral_illumination_packaged_core" />
* <ItemLink id="ae2ltpp:astral_resonance_packaged_core" />
* <ItemLink id="ae2ltpp:astral_luminance_packaged_core" />
* <ItemLink id="ae2ltpp:astral_radiance_packaged_core" />
* <ItemLink id="ae2ltpp:astral_infuser_packaged_core" />

For Astral Sorcery 1.21.1 / 2.0.2. Higher altar cores cover lower altar tiers; the infuser has a separate core. Craft each core in its corresponding altar tier, and the infuser core in a Resonance Altar.

## Setup

* Touch the main altar/infuser block with the provider, or target it with a wireless provider. Encode a processing pattern for exactly one recipe execution.
* Keep the 3×3 altar grid and the 5×5 relay grid empty before dispatch. Place all required relays at their native recipe positions.
* Include grid, relay and additional dropped item ingredients in the pattern. Additional ingredients are physically dropped for native capture. Encode filled containers as items; remaining containers are returned after completion.
* Supply the native structure, night, focus constellation, starlight, lumen and fluids externally. Focus break chance and crafting duration are unchanged. Additional fluid requirements use native chalices rather than ME fluid inputs.
* Prepare the infuser's surrounding fluids and structure, with native chalice replenishment as needed. Fluid consumption remains probabilistic when the recipe specifies it.
* Enable automatic return. The next craft waits for collection of the current output. Avoid mixing manual inputs with automation. Interrupted crafts retain their physical inputs for manual recovery or restart.

## Outputs

Crystal component copying, merging and size-dependent amounts are checked against the actual inputs. Encode the corresponding output count. Random attributes, enchantments, unique identifiers and loot components require an overloaded pattern with **ID-only output matching**; native crafting generates the actual components.

Recipes that replace the altar block, unlock player research or use unknown output modifiers remain manual. Lightwells, attunement altars and focal world transmutation are outside this integration's scope.
