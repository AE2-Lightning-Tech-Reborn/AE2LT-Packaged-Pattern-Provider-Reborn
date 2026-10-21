package com.moakiee.ae2lt.packaged.resource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Native FA 2.2.6 quantities, limits and change callbacks; no shadow balance or NBT writes. */
public final class ForbiddenEssenceStorage implements PackagedResourceInventory {
    private final BlockEntity host;
    private final Access access;

    private ForbiddenEssenceStorage(BlockEntity host, Access access) {
        this.host = host;
        this.access = access;
    }

    public static void attach(net.minecraftforge.event.AttachCapabilitiesEvent<BlockEntity> event) {
        var host = event.getObject();
        if (!host.getClass().getName().equals("com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity")) return;
        var access = Access.load();
        if (access == null) return;
        var optional = net.minecraftforge.common.util.LazyOptional.<PackagedResourceInventory>of(() -> new ForbiddenEssenceStorage(host, access));
        event.addCapability(new ResourceLocation("ae2ltpp", "forbidden_essences"), new net.minecraftforge.common.capabilities.ICapabilityProvider() {
            @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
                return capability == PackagedResourceStorage.CAPABILITY ? optional.cast() : net.minecraftforge.common.util.LazyOptional.empty();
            }
        });
        event.addListener(optional::invalidate);
    }

    private boolean live() {
        com.moakiee.ae2lt.packaged.logic.multiblock.fa.FAForgeConversion.releaseAbandoned(host);
        var level = host.getLevel();
        return !host.isRemoved() && !host.getPersistentData().contains("ae2ltpp_forge_job")
                && level != null && !level.isClientSide()
                && !com.moakiee.ae2lt.packaged.logic.multiblock.fa.FAAccess.active(host)
                && !host.getPersistentData().contains("ae2ltpp_hephaestus_pending_nonce")
                && level.isLoaded(host.getBlockPos()) && level.getBlockEntity(host.getBlockPos()) == host;
    }

    @Nullable
    private Object essenceType(AEKey key) {
        if (!(key instanceof PackagedResourceKey resource) || !ForbiddenEssenceResources.isEssence(resource)) return null;
        var type = access.types.get(resource.getId().getPath());
        return type;
    }

    @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return transfer(key, amount, mode, true);
    }
    @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return transfer(key, amount, mode, false);
    }

    private long transfer(AEKey key, long amount, Actionable mode, boolean inserting) {
        if (amount <= 0 || !live()) return 0;
        var type = essenceType(key);
        if (type == null) return 0;
        try {
            var owner = access.manager.invoke(host);
            int before = access.amount(owner, type);
            int capacity = access.capacity(owner, type);
            long available = inserting ? Math.max(0L, (long) capacity - before) : Math.max(0, before);
            int moved = (int) Math.min(amount, available);
            if (moved <= 0 || mode == Actionable.SIMULATE) return moved;
            try {
                access.setEssence.invoke(owner, type, inserting ? before + moved : before - moved);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // Native callbacks may fail after the balance changed. Measure the
                // receipt below so the caller never refunds a confirmed transfer.
                CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "changeEssence", failure);
            }
            int after = access.amount(owner, type);
            long receipt = Math.min(moved, Math.max(0L, inserting ? (long) after - before : (long) before - after));
            if (receipt > 0) {
                try {
                    host.setChanged();
                    var level = host.getLevel();
                    level.sendBlockUpdated(host.getBlockPos(), host.getBlockState(), host.getBlockState(), 3);
                } catch (RuntimeException | LinkageError failure) {
                    CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "notifyEssence", failure);
                }
            }
            return receipt;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "transferEssence", failure);
            return 0;
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!live()) return;
        try {
            var owner = access.manager.invoke(host);
            for (var key : ForbiddenEssenceResources.ALL) {
                var type = essenceType(key);
                if (type == null) continue;
                int amount = access.amount(owner, type);
                if (amount > 0) out.add(key, amount);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "listEssences", failure);
        }
    }

    @Override public Component getDescription() { return host.getBlockState().getBlock().getName(); }

    private static final class Access {
        final Class<?> forgeClass;
        final Map<String, Object> types = new HashMap<>();
        final Method manager, getEssence, setEssence, definitionGet;
        final Field maximum;

        private Access() throws ReflectiveOperationException {
            String base = "com.stal111.forbidden_arcanus.common.block.entity.";
            forgeClass = Class.forName(base + "forge.HephaestusForgeBlockEntity");
            var typeClass = Class.forName(base + "forge.essence.EssenceType");
            for (var type : typeClass.getEnumConstants()) types.put(((StringRepresentable) type).getSerializedName(), type);
            var managerClass = Class.forName(base + "forge.essence.EssenceManager");
            var definitionClass = Class.forName(base + "forge.essence.EssencesDefinition");
            manager = forgeClass.getMethod("getEssenceManager");
            getEssence = managerClass.getMethod("getEssence", typeClass);
            setEssence = managerClass.getMethod("setEssence", typeClass, int.class);
            maximum = managerClass.getDeclaredField("maxEssences");
            maximum.setAccessible(true);
            definitionGet = definitionClass.getMethod("get", typeClass);
        }

        int amount(Object owner, Object type) throws ReflectiveOperationException {
            return ((Number) getEssence.invoke(owner, type)).intValue();
        }
        int capacity(Object owner, Object type) throws ReflectiveOperationException {
            return Math.max(0, ((Number) definitionGet.invoke(maximum.get(owner), type)).intValue());
        }

        @Nullable static Access load() {
            try { return new Access(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "resolveEssenceAPI", failure);
                return null;
            }
        }
    }
}
