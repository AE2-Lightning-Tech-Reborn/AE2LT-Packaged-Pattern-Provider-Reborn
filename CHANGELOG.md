# Changelog

## Unreleased — Forge 1.20.1

- Backported live-server MekMM recipe validation, machine recognition status and persistent wireless connection instructions, with compact-screen layout support.

- Backported the Touhou Little Maid altar, P-point ME resource and experience-orb fluid collection. Six native EC/Avaritia higher-tier core recipes now require the preceding core.
- Backported EC combination live-recipe checks and physical-batch ownership renewal with persisted ambiguous receipts.
- Added provider-owned core orders, first-core bootstrap and EC/Avaritia tier upgrades through actual ME crafting CPU receipts.
- Added optional JEI core recipes and ordering, preserving native ingredient counts and installed-core upgrade requirements. Core manufacturing also works with a resonant core installed and collects only the matching core outputs.
- Backported native Forbidden Arcanus essence conversions, ME essence storage and JEI encoding, plus datapack-driven black-hole XP-fluid conversion. Adapted to FA 2.2.6; the newer essence jar is unavailable on this version.

- Backported resonant cores with portable input/output layouts and Crystal Science pattern support, plus a 144-slot provider with four pages and switchable adjacent/wireless dispatch. See [Forge verification](RESONANT_BACKPORT.zh-CN.md).
- Fixed hidden-page slot editing and ensured undelivered resonant inputs drop exactly once when the provider breaks.
- Backported cheaper recipes for 20 specialized cores while preserving Forge serializers and native machine tiers.
- Added bounded linear batches for Chipped, internal chiseling and Spirit Fire; MekMM failed requests can retry on the next tick.
- Preserve quantities beyond one long across output buffering, refunds and NBT reloads; return inventories report exact accepted quantities at capacity.

## 1.2.2-beta — Forge 1.20.1 backports

- Ars Nouveau Imbuement Chamber patterns containing only the reagent now use matching preplaced catalysts. The catalysts stay on their pedestals across repeated crafts and machine reloads. Patterns declaring catalysts retain the borrow-and-return workflow.
- Removed “Reborn” / “重生” from this addon's displayed name, creative tab and guide titles. Updated the English and Chinese imbuement guides to explain both catalyst workflows.
- Backported chiseling conversions, pure-workbench batch crafting, MekMM continuous dispatch, DE injector refresh, fuzzy input matching and empty core-slot hints. See [UPSTREAM_BACKPORTS.zh-CN.md](UPSTREAM_BACKPORTS.zh-CN.md) for source commits and Forge-specific coverage.
- Removed unused adapter helpers, reflection caches and test fields; shared Ender/Flux grid planning and harvest limits, simplified menu wiring, and corrected outdated cache and version comments.
- Third cleanup removed duplicated Ender/Flux reflection and grid helpers, unused reload and batch overload paths, and stale comments; production sources are 409 lines shorter. Added three EC crafter boundary tests.
