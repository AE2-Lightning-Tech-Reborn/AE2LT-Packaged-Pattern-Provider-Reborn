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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Native FA 2.6.x quantities, limits and change callbacks; no shadow balance or NBT writes. */
public final class ForbiddenEssenceStorage implements MEStorage {
    private final BlockEntity host;
    private final Access access;
    private final boolean jar;

    private ForbiddenEssenceStorage(BlockEntity host, Access access, boolean jar) {
        this.host = host;
        this.access = access;
        this.jar = jar;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) return;
        var access = Access.load();
        if (access == null) return;
        register(event, "hephaestus_forge", access, false);
        register(event, "essence_utrem_jar", access, true);
    }

    private static void register(RegisterCapabilitiesEvent event, String id, Access access, boolean jar) {
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", id));
        if (type != null) registerType(event, type, access, jar);
    }

    private static <T extends BlockEntity> void registerType(RegisterCapabilitiesEvent event,
                                                            BlockEntityType<T> type, Access access, boolean jar) {
        event.registerBlockEntity(PackagedResourceStorage.CAPABILITY, type,
                (host, side) -> (jar ? access.jarClass : access.forgeClass).isInstance(host)
                        ? new ForbiddenEssenceStorage(host, access, jar) : null);
    }

    private boolean live() {
        var level = host.getLevel();
        return !host.isRemoved() && !host.getPersistentData().contains("ae2ltpp_forge_job")
                && level != null && !level.isClientSide()
                && level.isLoaded(host.getBlockPos()) && level.getBlockEntity(host.getBlockPos()) == host;
    }

    @Nullable
    private Object essenceType(AEKey key) {
        if (!(key instanceof PackagedResourceKey resource) || !ForbiddenEssenceResources.isEssence(resource)) return null;
        var type = access.types.get(resource.getId().getPath());
        if (!jar) return type;
        // A jar's state determines its resource even when it is empty.
        for (var property : host.getBlockState().getProperties()) {
            if (property.getName().equals("essence_type")) {
                return host.getBlockState().getValue(property).equals(type) ? type : null;
            }
        }
        return null;
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
            var owner = jar ? host : access.manager.invoke(host);
            int before = access.amount(owner, type, jar);
            int capacity = access.capacity(owner, type, jar);
            long available = inserting ? Math.max(0L, (long) capacity - before) : Math.max(0, before);
            int moved = (int) Math.min(amount, available);
            if (moved <= 0 || mode == Actionable.SIMULATE) return moved;
            try {
                if (jar) access.jarAdd.invoke(host, inserting ? moved : -moved);
                else access.setEssence.invoke(owner, type, inserting ? before + moved : before - moved);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // Native callbacks may fail after the balance changed. Measure the
                // receipt below so the caller never refunds a confirmed transfer.
                CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "changeEssence", failure);
            }
            int after = access.amount(owner, type, jar);
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
            var owner = jar ? host : access.manager.invoke(host);
            for (var key : ForbiddenEssenceResources.ALL) {
                var type = essenceType(key);
                if (type == null) continue;
                int amount = access.amount(owner, type, jar);
                if (amount > 0) out.add(key, amount);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ForbiddenEssenceStorage.class, "listEssences", failure);
        }
    }

    @Override public Component getDescription() { return host.getBlockState().getBlock().getName(); }

    private static final class Access {
        final Class<?> forgeClass, jarClass;
        final Map<String, Object> types = new HashMap<>();
        final Method manager, getEssence, setEssence, definitionGet, jarAmount, jarLimit, jarAdd;
        final Field maximum;

        private Access() throws ReflectiveOperationException {
            String base = "com.stal111.forbidden_arcanus.common.block.entity.";
            forgeClass = Class.forName(base + "forge.HephaestusForgeBlockEntity");
            jarClass = Class.forName(base + "EssenceUtremJarBlockEntity");
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
            jarAmount = jarClass.getMethod("getAmount");
            jarLimit = jarClass.getMethod("getLimit");
            jarAdd = jarClass.getMethod("addEssence", int.class);
        }

        int amount(Object owner, Object type, boolean jar) throws ReflectiveOperationException {
            return ((Number) (jar ? jarAmount.invoke(owner) : getEssence.invoke(owner, type))).intValue();
        }
        int capacity(Object owner, Object type, boolean jar) throws ReflectiveOperationException {
            return Math.max(0, ((Number) (jar ? jarLimit.invoke(owner)
                    : definitionGet.invoke(maximum.get(owner), type))).intValue());
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
