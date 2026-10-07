---
navigation:
  parent: index.md
  title: Runic Altar
  icon: botania_runic_altar_packaged_core
  position: 230
item_ids:
- ae2ltpp:botania_runic_altar_packaged_core
---

# Runic Altar

**Packaged Core:** <ItemLink id="ae2ltpp:botania_runic_altar_packaged_core" />
**Mod:** Botania

Automatically performs Runic Altar recipes. Place the provider against the Runic Altar and encode the corresponding **processing pattern**. The provider sends the ingredients into the altar, and once it has accumulated enough mana, the result is produced and returned (you do not need to right-click it with a wand).

## ⚠ Mana & recycled rune catalysts

* The altar needs **mana** from your mana spreaders — keep it supplied.
* Put the recipe's returned catalyst runes in both the inputs and the byproduct outputs, with the product first. Each catalyst output slot must contain 1 rune matching the corresponding input's item and components; consumed ingredients and livingrock must not be declared as returns. The provider validates the complete outputs, so missing or extra returns prevent binding. You still need the initial catalyst runes in stock before they can cycle through subsequent crafts.
