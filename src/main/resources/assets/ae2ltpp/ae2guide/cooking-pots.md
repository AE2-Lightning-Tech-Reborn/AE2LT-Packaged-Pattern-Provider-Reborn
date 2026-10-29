---
navigation:
  parent: index.md
  title: Cooking Pots
  icon: fd_cooking_pot_packaged_core
  position: 178
item_ids:
- ae2ltpp:fd_cooking_pot_packaged_core
- ae2ltpp:ad_extreme_cooking_pot_packaged_core
---

# Cooking Pots

<ItemLink id="ae2ltpp:fd_cooking_pot_packaged_core" />
<ItemLink id="ae2ltpp:ad_extreme_cooking_pot_packaged_core" />

Connect Farmer's Delight's Cooking Pot or Avaritia's Delight's Extreme Cooking Pot. Keep a valid heat source beneath it and set the provider's return mode to **AUTO**. The extreme core also covers the ordinary pot.

Encode one native recipe as a processing pattern. Include **one bowl or bottle per output serving** when the recipe requires a container, in addition to all food ingredients. For example, a recipe yielding two servings requires two bowls. Use an overloaded pattern for large ingredient lists.

The provider distributes repeated ingredients into separate recipe slots, puts vessels into the container slot, waits for native cooking, and returns only finished items from the output slot. It starts the next recipe when the pot is empty, preventing mixed meals. Existing ingredients, vessels or unserved meals block new dispatch; clear them before dedicating the pot to ME automation. Keep other input pipes off the same pot.

The ordinary core is cooked from a basic core and four materials. The extreme core upgrades the ordinary core; JEI includes both native manufacture and provider in-place upgrade recipes. Core manufacture needs no serving vessel.

Native ingredient remainders (such as milk buckets) still eject beside the pot; use a hopper or collection device for those. They are not treated as paid meal outputs. Heat, native recipe precedence, cooking time and native remainder rules are preserved. Recipe datapacks and addons using these same cooking recipe types work without extra cores; independent addon machines require separate support.
