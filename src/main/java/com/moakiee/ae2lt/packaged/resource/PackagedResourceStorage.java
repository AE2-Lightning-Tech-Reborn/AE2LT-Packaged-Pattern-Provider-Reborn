package com.moakiee.ae2lt.packaged.resource;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Resource-only capability. Integrations expose their native storage here; the
 * standard item/fluid capabilities on the same block remain independent.
 */
public final class PackagedResourceStorage {
    public static final BlockCapability<MEStorage, Direction> CAPABILITY = BlockCapability.createSided(
            ResourceLocation.parse("ae2ltpp:packaged_resources"), MEStorage.class);

    private PackagedResourceStorage() {}

    public static void registerKeyType(RegisterEvent event) {
        if (event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
            AEKeyTypes.register(PackagedResourceKeyType.INSTANCE);
        }
    }

    public static void registerBehaviors() {
        var handler = new ResourceHandlerStrategy();
        appeng.api.behaviors.StackExportStrategy.register(PackagedResourceKeyType.INSTANCE,
                (level, pos, face) -> new appeng.parts.automation.StorageExportStrategy<>(CAPABILITY, handler, level, pos, face));
        appeng.api.behaviors.StackImportStrategy.register(PackagedResourceKeyType.INSTANCE,
                (level, pos, face) -> new appeng.parts.automation.StorageImportStrategy<>(CAPABILITY, handler, level, pos, face));
        ExternalStorageStrategy.register(PackagedResourceKeyType.INSTANCE, (level, pos, side) -> {
            var cache = BlockCapabilityCache.create(CAPABILITY, level, pos, side);
            return (extractableOnly, changed) -> cache.getCapability() == null ? null : new MEStorage() {
                @Override
                public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    var storage = cache.getCapability();
                    long inserted = storage == null || !(key instanceof PackagedResourceKey) || amount <= 0
                            ? 0 : storage.insert(key, amount, mode, source);
                    if (inserted > 0 && mode == Actionable.MODULATE) changed.run();
                    return inserted;
                }

                @Override
                public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                    var storage = cache.getCapability();
                    long extracted = storage == null || !(key instanceof PackagedResourceKey) || amount <= 0
                            ? 0 : storage.extract(key, amount, mode, source);
                    if (extracted > 0 && mode == Actionable.MODULATE) changed.run();
                    return extracted;
                }

                @Override
                public void getAvailableStacks(KeyCounter out) {
                    var storage = cache.getCapability();
                    if (storage == null) return;
                    for (var entry : storage.getAvailableStacks()) {
                        if (!(entry.getKey() instanceof PackagedResourceKey) || entry.getLongValue() <= 0) continue;
                        long amount = extractableOnly ? storage.extract(entry.getKey(), entry.getLongValue(),
                                Actionable.SIMULATE, IActionSource.empty()) : entry.getLongValue();
                        if (amount > 0) out.add(entry.getKey(), amount);
                    }
                }

                @Override public Component getDescription() { return PackagedResourceKeyType.INSTANCE.getDescription(); }
            };
        });
    }
}
