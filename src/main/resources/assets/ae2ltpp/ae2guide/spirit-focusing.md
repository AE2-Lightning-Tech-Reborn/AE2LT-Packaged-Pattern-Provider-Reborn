---
navigation:
  parent: index.md
  title: Spirit Focusing
  icon: malum_spirit_focusing_packaged_core
  position: 140
item_ids:
- ae2ltpp:malum_spirit_focusing_packaged_core
---

# Spirit Focusing (Spirit Crucible)

**Packaged Core:** <ItemLink id="ae2ltpp:malum_spirit_focusing_packaged_core" />
**Mod:** Malum

Automatically performs Spirit Focusing in the Spirit Crucible.
Place the provider against the **Spirit Crucible** and encode the corresponding **processing pattern**.
For each craft, the provider performs Spirit Focusing automatically, then retrieves the result and the augments when focusing finishes.
The provider starts only when the Crucible is idle and its slots are empty.

## ⚠ Augments, ingredients, and patterns

* When encoding the pattern, include every **augment** and **spirit shard** used by the recipe as pattern inputs. The only pattern output should be the focusing result.
* The provider retrieves the catalyst left in the crucible after focusing. Some recipes damage a catalyst or can break it, so it is not always returned unchanged. Spirit Focusing currently accepts product-only outputs; do not add an unchanged catalyst as a byproduct, because that return is not guaranteed. Large requests can therefore still require a catalyst for every craft.
