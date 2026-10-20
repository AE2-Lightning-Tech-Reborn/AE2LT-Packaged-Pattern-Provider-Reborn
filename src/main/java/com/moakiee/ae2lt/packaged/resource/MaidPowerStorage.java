package com.moakiee.ae2lt.packaged.resource;

import java.lang.reflect.Method;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** A storage bus can use the native maid beacon as a P reservoir. No shadow balance. */
public final class MaidPowerStorage implements MEStorage {
    private final BlockEntity host;
    private final Access access;
    private record Access(Class<?> type, Method get, Method set, Method max) {}
    private MaidPowerStorage(BlockEntity host, Access access) { this.host = host; this.access = access; }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!ModList.get().isLoaded("touhou_little_maid")) return;
        try {
            var type = Class.forName("com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon");
            var access = new Access(type, type.getMethod("getStoragePower"),
                    type.getMethod("setStoragePower", float.class), type.getMethod("getMaxStorage"));
            var beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("touhou_little_maid:maid_beacon"));
            if (beType != null) register(event, beType, access);
        } catch (ReflectiveOperationException | LinkageError e) { failure(e); }
    }
    private static <T extends BlockEntity> void register(RegisterCapabilitiesEvent event, BlockEntityType<T> type, Access access) {
        event.registerBlockEntity(PackagedResourceStorage.CAPABILITY, type,
                (host, side) -> access.type().isInstance(host) ? new MaidPowerStorage(host, access) : null);
    }
    private boolean live() {
        var level = host.getLevel();
        return !host.isRemoved() && level != null && !level.isClientSide()
                && level.isLoaded(host.getBlockPos()) && level.getBlockEntity(host.getBlockPos()) == host;
    }
    @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) { return transfer(key, amount, mode, true); }
    @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) { return transfer(key, amount, mode, false); }
    private long transfer(AEKey key, long amount, Actionable mode, boolean inserting) {
        if (key != MaidPowerKey.INSTANCE || amount <= 0 || !live()) return 0;
        try {
            float before = (float) access.get().invoke(host);
            var transfer = MaidPowerAmounts.transfer(before, (float) access.max().invoke(host), amount, inserting);
            if (mode == Actionable.SIMULATE || transfer.moved() == 0) return transfer.moved();
            try { access.set().invoke(host, transfer.after()); }
            catch (ReflectiveOperationException e) { failure(e); }
            long after = MaidPowerAmounts.stored((float) access.get().invoke(host));
            return Math.min(transfer.moved(), Math.max(0, inserting
                    ? after - MaidPowerAmounts.stored(before) : MaidPowerAmounts.stored(before) - after));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(e); return 0; }
    }
    @Override public void getAvailableStacks(KeyCounter out) {
        if (!live()) return;
        try {
            long amount = MaidPowerAmounts.stored((float) access.get().invoke(host));
            if (amount > 0) out.add(MaidPowerKey.INSTANCE, amount);
        } catch (ReflectiveOperationException | RuntimeException e) { failure(e); }
    }
    @Override public Component getDescription() { return host.getBlockState().getBlock().getName(); }
    private static void failure(Throwable e) { CompatibilityDiagnostics.failure(MaidPowerStorage.class, "beacon", e); }
}
