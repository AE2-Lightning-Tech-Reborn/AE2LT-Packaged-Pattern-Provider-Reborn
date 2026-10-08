# Changelog

## 1.2.2-beta — Forge 1.20.1 backports

- Ars Nouveau Imbuement Chamber patterns containing only the reagent now use matching preplaced catalysts. The catalysts stay on their pedestals across repeated crafts and machine reloads. Patterns declaring catalysts retain the borrow-and-return workflow.
- Removed “Reborn” / “重生” from this addon's displayed name, creative tab and guide titles. Updated the English and Chinese imbuement guides to explain both catalyst workflows.
- Backported chiseling conversions, pure-workbench batch crafting, MekMM continuous dispatch, DE injector refresh, fuzzy input matching and empty core-slot hints. See [UPSTREAM_BACKPORTS.zh-CN.md](UPSTREAM_BACKPORTS.zh-CN.md) for source commits and Forge-specific coverage.
- Removed unused adapter helpers, reflection caches and test fields; shared Ender/Flux grid planning and harvest limits, simplified menu wiring, and corrected outdated cache and version comments.
- Third cleanup removed duplicated Ender/Flux reflection and grid helpers, unused reload and batch overload paths, and stale comments; production sources are 409 lines shorter. Added three EC crafter boundary tests.
