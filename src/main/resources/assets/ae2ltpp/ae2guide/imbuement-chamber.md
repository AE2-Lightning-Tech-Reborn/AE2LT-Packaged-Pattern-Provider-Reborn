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
The core ingredient goes into the Chamber, catalysts go on the pedestals, and the result is retrieved when imbuing finishes.

## ⚠ Catalysts are recycled

Ars does **not** consume the pedestal items for an Imbuement recipe — it only checks that they are
present. So this provider treats them as **reusable catalysts**: it places them on the pedestals for
the craft, then **pulls them back into your ME network together with the finished product**.

Encode one craft in a **processing pattern**:

* **Inputs:** the Chamber ingredient and every pedestal catalyst, with the quantities needed for one craft.
* **First output:** the recipe's finished product, with its actual quantity.
* **Other outputs:** all pedestal catalysts, with exactly the same quantities and components as their inputs. Repeated catalysts may share an output slot with their total quantity. If the product and a catalyst have the same item and components, combine their quantities in the first output.

For example, if one craft consumes A × 1 and needs B × 2 and C × 1 on the pedestals, encode inputs A × 1, B × 2, C × 1 and outputs product × 1, B × 2, C × 1. A request for 64 products can then reuse B × 2 and C × 1 as they return; A is still consumed 64 times. You need the initial catalyst set in stock or a separate recipe to produce it. More simultaneous crafts require more physical sets.

When declaring catalyst returns, include the complete set. Missing, extra, or mismatched returns prevent binding. Existing product-only patterns still work, but do not tell AE2 to reuse catalysts, so large requests can require many sets. The provider does not edit your patterns; manually re-encode them to declare catalyst returns. Each craft still places and retrieves the real catalysts.

Before starting, the Chamber and every Arcane Pedestal within 1 block must be empty; any occupied pedestal in that range blocks the craft.
`Like a clam, a delicate imbuement ritual cannot tolerate even a grain of sand.`
