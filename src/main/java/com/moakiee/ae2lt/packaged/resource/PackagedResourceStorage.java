package com.moakiee.ae2lt.packaged.resource;

import appeng.api.behaviors.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.registries.RegisterEvent;

/** Forge capability and AE2 transfer strategies for native non-item resources. */
public final class PackagedResourceStorage {
    public static final Capability<PackagedResourceInventory> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private PackagedResourceStorage() {}
    public static void registerKeyType(RegisterEvent event) {
        if (event.getRegistryKey().location().equals(new ResourceLocation("ae2", "keytypes"))) {
            AEKeyTypes.register(PackagedResourceKeyType.INSTANCE);
            AEKeyTypes.register(MaidPowerKeyType.INSTANCE);
        }
    }
    public static void registerBehaviors() {
        registerBehaviors(PackagedResourceKeyType.INSTANCE);
        registerBehaviors(MaidPowerKeyType.INSTANCE);
        PickupStrategy.register(PackagedResourceKeyType.INSTANCE,
                (level, pos, side, host, enchantments, owner) -> new ExperienceOrbPickupStrategy(() -> {
                    if (!(host instanceof appeng.api.networking.IInWorldGridNodeHost gridHost)) return new KeyCounter();
                    var node = gridHost.getGridNode(side.getOpposite());
                    return node == null ? new KeyCounter() : node.getGrid().getStorageService().getCachedInventory();
                }));
        if (net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid")) {
            PickupStrategy.register(MaidPowerKeyType.INSTANCE,
                    (level, pos, side, host, enchantments, owner) -> new MaidPowerPickupStrategy());
        }
    }
    private static void registerBehaviors(AEKeyType type) {
        var handler = new ResourceHandlerStrategy(type);
        StackExportStrategy.register(type, (level, pos, face) ->
                new appeng.parts.automation.StorageExportStrategy<>(CAPABILITY, handler, level, pos, face) {});
        StackImportStrategy.register(type, (level, pos, face) ->
                new appeng.parts.automation.StorageImportStrategy<>(CAPABILITY, handler, level, pos, face));
        appeng.api.behaviors.GenericSlotCapacities.register(type, (long) Integer.MAX_VALUE);
        ExternalStorageStrategy.register(type, (level, pos, side) -> (extractableOnly, changed) -> {
            BlockEntity host = level.getBlockEntity(pos);
            if (host == null || !host.getCapability(CAPABILITY, side).isPresent()) return null;
            return new MEStorage() {
                private MEStorage storage() {
                    return host.isRemoved() || !level.isLoaded(pos) || level.getBlockEntity(pos) != host
                            ? null : host.getCapability(CAPABILITY, side).orElse(null);
                }
                @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    var storage = storage();
                    long moved = storage == null || !type.contains(key) || amount <= 0
                            ? 0 : storage.insert(key, amount, mode, source);
                    if (moved > 0 && mode == Actionable.MODULATE) changed.run();
                    return moved;
                }
                @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                    var storage = storage();
                    long moved = storage == null || !type.contains(key) || amount <= 0
                            ? 0 : storage.extract(key, amount, mode, source);
                    if (moved > 0 && mode == Actionable.MODULATE) changed.run();
                    return moved;
                }
                @Override public void getAvailableStacks(KeyCounter out) {
                    var storage = storage();
                    if (storage == null) return;
                    for (var entry : storage.getAvailableStacks()) {
                        if (!type.contains(entry.getKey()) || entry.getLongValue() <= 0) continue;
                        long amount = extractableOnly ? storage.extract(entry.getKey(), entry.getLongValue(),
                                Actionable.SIMULATE, IActionSource.empty()) : entry.getLongValue();
                        if (amount > 0) out.add(entry.getKey(), amount);
                    }
                }
                @Override public Component getDescription() { return type.getDescription(); }
            };
        });
    }
}
