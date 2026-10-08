---
navigation:
  parent: index.md
  title: Imbuement Chamber
  icon: ars_imbuement_packaged_core
  position: 40
item_ids:
- ae2ltpp:ars_imbuement_packaged_core
---

# Imbuement Chamber

**Packaged Core:** <ItemLink id="ae2ltpp:ars_imbuement_packaged_core" />
**Mod:** Ars Nouveau

Automatically performs Imbuement Chamber recipes. Place the provider against the Chamber, then place **enough** Arcane Pedestals and/or Arcane Platforms anywhere within the one-block-radius cube around it.
The core ingredient goes into the Chamber, and the result is retrieved when imbuing finishes.

## Resident catalysts

Ars does **not** consume the pedestal items for an Imbuement recipe — it only checks that they are
present. For repeated production with one setup, place one complete set on the pedestals first.
Encode a **processing pattern containing only the Chamber ingredient as input and the finished
product as output**, with the quantities for one craft. Leave the pedestal catalysts out of both
the inputs and outputs.

The provider checks that the resident catalysts match the recipe before every dispatch, inserts
only the Chamber ingredient, and retrieves only the product. Pedestal quantities and components
stay unchanged, including across machine saves and reloads. AE2 therefore does not request or
craft additional catalyst sets, even when the ME network has none in stock.

For example, if one craft consumes A × 1 and requires B × 2 and C × 1 on separate pedestals,
leave those catalysts on the pedestals and encode A × 1 → product × 1. An order for 64 products
needs A × 64 and keeps the original catalysts in place.

The Chamber must be empty, and the occupied pedestals must match the complete recipe. Missing,
wrong, or extra pedestal items block dispatch. Change the pedestal setup when switching recipes.

## Borrowed catalysts for switching recipes

Patterns that include the pedestal catalysts as inputs retain the dynamic workflow: the provider
places them for each craft, then retrieves them together with the product. This lets one Chamber
run recipes requiring different pedestal setups.

Encode one craft in a **processing pattern**:

* **Inputs:** the Chamber ingredient and every pedestal catalyst, with the quantities needed for one craft.
* **First output:** the recipe's finished product, with its actual quantity.
* **Other outputs:** all pedestal catalysts, with exactly the same quantities and components as their inputs. Repeated catalysts may share an output slot with their total quantity. If the product and a catalyst have the same item and components, combine their quantities in the first output.

For example, if one craft consumes A × 1 and needs B × 2 and C × 1 on the pedestals, encode inputs A × 1, B × 2, C × 1 and outputs product × 1, B × 2, C × 1. A request for 64 products can then reuse B × 2 and C × 1 as they return; A is still consumed 64 times. You need the initial catalyst set in stock or a separate recipe to produce it. More simultaneous crafts require more physical sets.

When declaring catalyst returns, include the complete set. Missing, extra, or mismatched returns prevent binding. Existing product-only patterns still work, but do not tell AE2 to reuse catalysts, so large requests can require many sets. The provider does not edit your patterns; manually re-encode them to declare catalyst returns. Each craft still places and retrieves the real catalysts.

Before starting, the Chamber and every Arcane Pedestal within 1 block must be empty; any occupied pedestal in that range blocks the craft.
