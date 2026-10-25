---
navigation:
  parent: index.md
  title: Ritual Brazier
  icon: ars_ritual_brazier_packaged_core
  position: 45
item_ids:
- ae2ltpp:ars_ritual_brazier_packaged_core
---

# Ritual Brazier

**Packaged Core:** <ItemLink id="ae2ltpp:ars_ritual_brazier_packaged_core" />
**Mod:** Ars Nouveau

Place the provider against a Ritual Brazier and enable automatic return. Encode one ritual in a processing pattern: exactly one ritual tablet plus its native augment items. The complete input must pass the ritual's successive item-acceptance and start checks, including item components and quantity limits. A pattern supports up to 4,096 input items. Fluids and multiple tablets are not accepted.

The provider installs the tablet, feeds each augment through the brazier's native API, then starts the ritual. It waits for an empty, unpowered brazier and never replaces a pending or running ritual. Source, timing, redstone pauses, relays, and world effects remain native. Supply the source and surroundings required by that ritual.

Returned items are unrestricted: items directly emitted by the paid ritual return even if absent from the pattern's output list. Nearby manual drops and manually started rituals are not collected. Saved progress and buffered results survive reloads; breaking the brazier drops its buffered results. With return disabled or blocked, a full result buffer falls back to native world drops.

For example, one Awakening tablet plus two Books and Quills produces two Bookwyrm Charms. Declare those charms as the pattern output for AE2 to complete its order. Output declarations do not create items: summoning or environmental rituals may have no item result, and a request for an item they never produce will remain waiting. Results placed directly into native adjacent inventories, or created later by independent projectiles/entities, retain their native collection route.

Craft this core in an Enchanting Apparatus: Basic Packaged Core as the reagent, one Ritual Brazier and three Source Gems on pedestals, and 1,000 source.
