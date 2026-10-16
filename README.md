# AE2LT Packaged Pattern Provider Reborn

## Overview

AE2LT Packaged Pattern Provider Reborn is an AE2 Lightning Tech Reborn addon for Minecraft 1.20.1 on Forge. It adds `Packaged Pattern Provider` and `Wireless Packaged Pattern Provider` blocks and extends AE2 / AE2LT pattern dispatch into selected multiblock crafting integrations.

## Supported Versions

- Minecraft: 1.20.1
- Java: 17
- Forge: 47.4.10 (loader range `[47,)`)
- Applied Energistics 2: 15.4.10 (range `[15.4.10,16)`)
- AE2 Lightning Tech Reborn: 2.1.0
- Thunderbolt Core Reborn: 2.0.1
- AE2WTLib: 15.3.3-forge
- Curios: 5.14.1+1.20.1
- Gradle Wrapper: 8.8

This branch is the Forge 1.20.1 port of the addon. The `main` branch targets
Minecraft 1.21.1 on NeoForge; both consume the same three-layer-clean refactor of
AE2 Lightning Tech Reborn and Thunderbolt Core Reborn, so the overload-pattern contract
(`OverloadedPatternDetails`) and the wireless support types are shared, while the
platform layer (registries, capabilities, recipe and NBT APIs) differs.

## Required Dependencies

- Applied Energistics 2
- AE2 Lightning Tech Reborn
- Thunderbolt Core Reborn
- AE2WTLib
- Curios
- Forge

The current `build.gradle` also uses:

- GuideME
- JEI runtime dependency for local development
- Jade runtime dependency for local development

## Development Setup

You need:

- JDK 17
- Git
- The included Gradle Wrapper
- A Minecraft 1.20.1 Forge mod development environment

Both AE2 Lightning Tech Reborn and Thunderbolt Core Reborn must be available
through Maven Local or the local upstream build option. Build matching 1.20.1 artifacts first:

```powershell
# Thunderbolt Core Reborn publishes itself
.\gradlew.bat publishToMavenLocal

# AE2 Lightning Tech Reborn has no publishing block; build the jar and install it as
# com.moakiee.ae2lt:ae2lt:2.1.0
.\gradlew.bat jarJar
```

Then place `build/libs/ae2lt-forge-1.20.1-2.1.0.jar` in
`~/.m2/repository/com/moakiee/ae2lt/ae2lt/2.1.0/` as
`ae2lt-2.1.0.jar` alongside a minimal POM. Alternatively, place both upstream
checkouts next to this repository and pass `-Puse_local_upstream=true` to Gradle.
Use `-Pae2lt_build_libs=<path>` and `-Pthunderbolt_build_libs=<path>` if their
`build/libs` directories are elsewhere.

Forge only applies access transformers declared by the project itself, so AE2's
transformer is vendored at `gradle/dev/ae2-dev-accesstransformer.cfg` purely so
the `runClient` / `runServer` / `runGameTestServer` tasks can load AE2. It is not
part of the published jar; in production FML applies AE2's own copy.

Windows PowerShell example:

```powershell
java -version
javac -version
```

## Optional Upstream Projects

For source-level debugging or cross-project integration work, clone:

- [AE2 Lightning Tech Reborn](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn)
- [Thunderbolt Core Reborn](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn)
- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)

## Building Applied Energistics 2 Locally

```powershell
git checkout 1.20.1
.\gradlew.bat clean build --no-daemon
```

This branch resolves AE2 `15.4.10` from `modmaven.dev`, so a local AE2 build is
only needed for source-level debugging.

## Building AE2 Lightning Tech Reborn and Thunderbolt Core Reborn Locally

```powershell
cd <path>\Thunderbolt-Core
.\gradlew.bat clean publishToMavenLocal --no-daemon

cd <path>\AE2-Lightning-Tech
.\gradlew.bat clean jarJar --no-daemon
```

Thunderbolt publishes itself as `com.moakiee.thunderbolt:thunderbolt:2.0.1`.

AE2 Lightning Tech Reborn has no `maven-publish` block on this line, so its reobfuscated
`jarJar` output has to be installed by hand as
`com.moakiee.ae2lt:ae2lt:2.1.0`:

```
~/.m2/repository/com/moakiee/ae2lt/ae2lt/2.1.0/
    ae2lt-2.1.0.jar   <- build/libs/ae2lt-forge-1.20.1-2.1.0.jar
    ae2lt-2.1.0.pom   <- minimal POM with the same coordinates
```

Use the reobfuscated `jarJar` artifact, not the `-slim` one: this branch consumes
it through `fg.deobf`, which expects production (SRG) names.

## Configuring the AE2LT and Thunderbolt Dependencies

Both are plain Maven coordinates resolved from `mavenLocal()`, pinned by
`gradle.properties`:

```properties
ae2lt_version=2.1.0
thunderbolt_version=2.0.1
```

AE2LT is resolved non-transitively, because this addon declares its own
development runtime rather than inheriting AE2LT's full local mod set.

## Build

```powershell
.\gradlew.bat clean build --no-daemon
```

## Test

```powershell
.\gradlew.bat clean test --no-daemon
```

## Current Project Architecture

- `AE2LTPackagedProvider.java`: mod entry point
- `registry`: block, item, block entity, and creative tab registration
- `block`: block definitions
- `blockentity`: packaged provider block entities
- `logic`: core packaged dispatch logic
- `logic/multiblock`: multiblock adapter framework
- `resources`: language files, models, blockstates, loot tables, and the `mods.toml` template
- `test`: pure logic unit tests

## Supported Multiblock Integrations

Integration code currently exists for:

- Actually Additions
- Ars Nouveau
- Draconic Evolution
- Extended Crafting
- Mystical Agriculture
- Occultism
- Mekanism More Machines
- Malum
- Botania
- AvaritiaNeo (virtual extreme crafting table)
- Blood Magic (altar)
- Forbidden Arcanus (Clibano and Hephaestus Forge)

These entries reflect source code present in the repository, not a blanket statement that every target has already been runtime-validated in every environment.

## How to Add a New Multiblock Adapter

1. Add a new adapter package under `logic/multiblock`.
2. Implement the existing `MultiblockAdapter` interface, returning a binding result that identifies whether the target should run virtually or through real dispatch.
3. Register the adapter during common setup or registry initialization.
4. Use reflection or equivalent safe detection for optional mod access.
5. Add matching and failure-path tests.
6. Update this README support list.

## Known Limitations

- This branch targets AE2LT `2.1.0` and Thunderbolt `2.0.1`; earlier releases are outside the declared dependency range.
- AE2LT must include `WirelessPatternProviderHost.acceptsWirelessTarget` and `getWirelessTargetRejectionMessage` (the updated 2.1.0 build). Older 2.1.0 jars with the same version number do not provide this connector integration. Wireless targets require a matching installed packaged core; existing connections are retained while a core is swapped or removed.
- Optional-mod access is reflection-based and fail-closed. If a target class,
  method, field, capability, or recipe type cannot be verified, that adapter
  remains inactive instead of consuming inputs.
- Avaritia implementations are intentionally split: AvaritiaNeo is supported
  as a virtual extreme crafting table, while the Re-Avaritia-style tiered table
  adapter is only active when its own block-entity classes are present.
- The automated connection suite checks recognition and idle harvest behavior;
  it does not replace a real ME-network dispatch test for every recipe family.
- Optional-mod reflection handling is still mixed, but the cached lookup helper now covers several hot-path adapters. New optional-mod hot paths should prefer cached lookup helpers first, and only move to `MethodHandle` / `VarHandle` when profiling shows a real win.
- A clean machine or CI runner must provide matching AE2LT and Thunderbolt refactor artifacts through the documented local-jar or Maven overrides.

## Troubleshooting

- `JAVA_HOME` points to a non-17 JDK: ForgeGradle 6 needs to run on JDK 17 for
  this branch; switch the shell before invoking Gradle.
- `Could not find com.moakiee.ae2lt:ae2lt` or `com.moakiee.thunderbolt:thunderbolt`:
  build both dependencies from their `-1.20.1` refactor branches into Maven Local
  as described under Development Setup.
- `IllegalAccessError` mentioning `net.minecraft.world.level.block.Blocks` during a
  run task: `gradle/dev/ae2-dev-accesstransformer.cfg` is missing or stale for the
  configured `ae2_version`; refresh it from the AE2 jar's
  `META-INF/accesstransformer.cfg`.
- `Invalid AccessTransformer line`: Forge's parser rejects a bare `#`, so every
  comment line in that file needs text after the marker.
- Gradle is using the wrong JVM: run `.\gradlew.bat --version` and check the JVM line.

## Port Verification Status

Verified locally on this branch:

- `gradlew build -Puse_local_upstream=true -Penable_avaritia_tests=true
  --no-daemon --max-workers=1` runs the full build and optional AvaritiaNeo tests
  on JDK 17; the verified local run passed all 271 unit tests. The migration test
  requiring a deobfuscated AE2LT development jar is excluded from this suite.
  Add `--offline` only after all dependencies are cached.
- `gradlew runGameTestServer -Penable_integration_runtime=true --offline --no-daemon`
  passes all 66 required GameTests with the checked-in integration runtime set.
- Powered ME-network recovery tests cover both provider variants: output amounts
  remain unchanged while storage is unavailable, then reach a real ME chest
  exactly once after installing its cell, including reloads before and after delivery.
- A powered ME CPU executes real Clibano orders for one and then three items,
  with exact ingredient consumption and product delivery, provider reloads, and
  rejection when ingredients run out. Extra residue products stay outside ME
  and require separate extraction to keep subsequent crafts moving.
- A powered ME CPU executes two Blood Magic tier-one altar crafts, verifies exact
  LP-backed input and output delivery across a provider reload, and rejects a
  follow-up order after the stone supply is exhausted.
- A powered ME CPU executes two Hephaestus Forge ferrognetic-mixture rituals,
  waits for missing essences, and checks all five ingredient amounts, essence
  consumption, pedestal cleanup, and exact output delivery across provider
  reloads during and after each ritual. A depleted-ingredient order is rejected.
- The full integration-runtime client reaches the main menu with 36 loaded mods
  and exits normally. This verifies startup; it does not verify in-world dispatch.
- A separate client with fresh configuration also reaches the main menu with
  all 36 mods. The earlier GuideME config-loading failure did not recur in this
  run; one successful fresh start does not establish that the race is fixed.
- Repeated packaging preserves the Mixin refmap in the production jar.
- `git diff --check` passes for the verified worktree.

Not yet verified:

- In-game behaviour of the provider blocks (a `runClient` session).
- The cause and permanent resolution of the intermittent GuideME 20.1.15
  config-loading failure.
- Full interactive ME-network dispatch for every optional-mod recipe family;
  use `MANUAL_TESTING.md` for that release check.
