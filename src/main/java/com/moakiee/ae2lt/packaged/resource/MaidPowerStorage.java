package com.moakiee.ae2lt.packaged.resource;

import java.lang.reflect.Method;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

/** Exposes the native upper maid beacon's P balance without a second stored balance. */
public final class MaidPowerStorage implements PackagedResourceInventory {
    private static final String BEACON = "com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon";
    private final BlockEntity host;
    private final Access access;
    private record Access(Method get, Method set, Method max) {}
    private static final class Api {
        static final Access ACCESS = load();
        private static Access load() {
            try {
                var type = Class.forName(BEACON);
                return new Access(type.getMethod("getStoragePower"), type.getMethod("setStoragePower", float.class),
                        type.getMethod("getMaxStorage"));
            } catch (ReflectiveOperationException | LinkageError e) { failure(e); return null; }
        }
    }
    private MaidPowerStorage(BlockEntity host, Access access) { this.host = host; this.access = access; }

    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        var host = event.getObject();
        if (!host.getClass().getName().equals(BEACON) || Api.ACCESS == null) return;
        LazyOptional<PackagedResourceInventory> storage = LazyOptional.of(() -> new MaidPowerStorage(host, Api.ACCESS));
        event.addCapability(new ResourceLocation("ae2ltpp", "maid_power"), new ICapabilityProvider() {
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, net.minecraft.core.Direction side) {
                return capability == PackagedResourceStorage.CAPABILITY ? storage.cast() : LazyOptional.empty();
            }
        });
        event.addListener(storage::invalidate);
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(e); }
    }
    @Override public Component getDescription() { return host.getBlockState().getBlock().getName(); }
    private static void failure(Throwable e) { CompatibilityDiagnostics.failure(MaidPowerStorage.class, "beacon", e); }
}
