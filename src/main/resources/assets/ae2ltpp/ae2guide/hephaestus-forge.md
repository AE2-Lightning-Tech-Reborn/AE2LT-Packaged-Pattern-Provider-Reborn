---
navigation:
  parent: index.md
  title: Hephaestus Forge
  icon: forbidden_arcanus_hephaestus_forge_packaged_core
  position: 132
item_ids:
- ae2ltpp:forbidden_arcanus_hephaestus_forge_packaged_core
---

# Hephaestus Forge

Supports Create Item and Upgrade Tier rituals with one main ingredient and up to eight pedestal ingredients. The complete forge and eight empty pedestals must be present. Fill the required essences and install any enhancers separately; the packaged core does not supply these resources. The ritual must finish before its exact output is harvested.

For Upgrade Tier rituals (tiers 2–5), use the matching tier's **Hephaestus Forge Upgrade Receipt** from the creative tab as the processing pattern's single output. This receipt is supplied by this addon as proof that the forge itself upgraded; Forbidden Arcanus does not produce an item. Only the bound ritual's successful tier change issues one receipt. The forge remains upgraded after the receipt is harvested. Other non-item ritual results are not automated.

## Essence conversion and ME storage

Processing patterns can convert native forge inputs into Aureal, Souls, Blood and Experience resources. Drag the four essences from JEI into a processing pattern and set their amounts. The native forge performs the conversion. Blood tubes are fully drained and their container is returned. Enchanted items declare the native minimum yield; any extra randomly produced XP is also returned.

ME storage, import and export buses can access native forge essence storage, with the native capacity limits. Use a separate idle forge for storage so conversion outputs have a destination. External ME transfers pause during conversions and rituals to prevent duplicate accounting. Forbidden Arcanus for 1.20.1 has no Essence Utrem Jar.
