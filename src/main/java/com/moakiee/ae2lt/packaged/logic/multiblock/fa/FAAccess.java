package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

/** Optional FA API boundary. All writes use native inventory/essence/ritual methods. */
final class FAAccess {
    static final String BASE = "com.stal111.forbidden_arcanus.";
    static final String FORGE = BASE + "common.block.entity.forge.HephaestusForgeBlockEntity";
    static final String CLIBANO = BASE + "common.block.entity.clibano.ClibanoMainBlockEntity";
    static final String FRAME = BASE + "common.block.entity.clibano.ClibanoFrameBlockEntity";
    static final String PEDESTAL = BASE + "common.block.entity.PedestalBlockEntity";
    private record MethodKey(Class<?> owner, String name, List<Class<?>> arguments) {}
    private static final Map<MethodKey, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private FAAccess() {}
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path); }
    static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("FA API class: " + name, e); }
    }
    static boolean is(Object value, String name) { return value != null && type(name).isInstance(value); }
    static Object field(Object owner, String name) {
        var type = owner instanceof Class<?> c ? c : owner.getClass();
        var key = type.getName() + '#' + name;
        try {
            var f = FIELDS.computeIfAbsent(key, ignored -> {
                for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                    try { var result = c.getDeclaredField(name); result.setAccessible(true); return result; }
                    catch (NoSuchFieldException ignoredField) { }
                }
                throw new IllegalStateException("FA API field: " + key);
            });
            return f.get(owner instanceof Class<?> ? null : owner);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("FA API field: " + key, e); }
    }
    static Object call(Object owner, String name, Object... args) {
        var type = owner instanceof Class<?> c ? c : owner.getClass();
        var key = new MethodKey(type, name, Arrays.stream(args).map(a -> a == null ? Void.class : a.getClass()).toList());
        var method = METHODS.computeIfAbsent(key, ignored -> {
            var candidates = new ArrayList<Method>(List.of(type.getMethods()));
            for (Class<?> c = type; c != null; c = c.getSuperclass()) candidates.addAll(List.of(c.getDeclaredMethods()));
            for (var m : candidates) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                var parameters = m.getParameterTypes(); boolean compatible = true;
                for (int i = 0; i < args.length; i++) if (args[i] != null && !boxed(parameters[i]).isInstance(args[i])) { compatible = false; break; }
                if (compatible) { m.setAccessible(true); return m; }
            }
            throw new IllegalStateException("FA API method: " + type.getName() + '#' + name);
        });
        try { return method.invoke(owner instanceof Class<?> ? null : owner, args); }
        catch (InvocationTargetException e) { throw new IllegalStateException("FA native call: " + name, e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("FA API call: " + name, e); }
    }
    private static Class<?> boxed(Class<?> c) {
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return c;
    }
    static Object construct(String name, Object... args) {
        for (var constructor : type(name).getConstructors()) {
            if (constructor.getParameterCount() != args.length) continue;
            try { return constructor.newInstance(args); }
            catch (IllegalArgumentException ignored) { }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("FA constructor: " + name, e); }
        }
        throw new IllegalStateException("FA constructor: " + name);
    }
    static boolean live(BlockEntity be) {
        return be != null && !be.isRemoved() && be.getLevel() instanceof ServerLevel level
                && level.isLoaded(be.getBlockPos()) && level.getBlockEntity(be.getBlockPos()) == be;
    }
    static IItemHandler inventory(BlockEntity be) { return (IItemHandler) call(be, "getItemStackHandler"); }
    static ItemStack stack(BlockEntity be, int slot) { return inventory(be).getStackInSlot(slot); }
    static void setStack(BlockEntity be, int slot, ItemStack stack) { call(be, "setStack", slot, stack); be.setChanged(); }
    static BlockEntity clibano(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (is(be, CLIBANO)) return be;
        if (!is(be, FRAME)) return null;
        var main = (BlockPos) call(call(be, "getFrameData"), "mainPos");
        if (main.distManhattan(pos) > 3 || !level.isLoaded(main)) return null;
        var core = level.getBlockEntity(main);
        return is(core, CLIBANO) ? core : null;
    }
    @SuppressWarnings("unchecked")
    static Registry<Object> registry(net.minecraft.world.level.Level level, String path) {
        return level.registryAccess().registryOrThrow(ResourceKey.createRegistryKey(id(path)));
    }
    static Object essence(String name) {
        var enumType = type(BASE + "common.block.entity.forge.essence.EssenceType");
        return Arrays.stream(enumType.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }
    static int amount(BlockEntity forge, String name) { return (int) call(call(forge, "getEssenceManager"), "getEssence", essence(name)); }
    static int capacity(BlockEntity forge, String name) { return (int) call(field(call(forge, "getEssenceManager"), "maxEssences"), "get", essence(name)); }
    static void amount(BlockEntity forge, String name, int value) {
        if (value < 0 || value > capacity(forge, name)) throw new IllegalArgumentException("Essence capacity");
        call(call(forge, "getEssenceManager"), "setEssence", essence(name), value); forge.setChanged();
    }
    static boolean active(BlockEntity forge) { return (boolean) call(call(forge, "getRitualManager"), "isRitualActive"); }
    static boolean structure(ServerLevel level, BlockPos pos) {
        var pattern = field(type(BASE + "common.block.ModBlockPatterns"), "BASE_HEPHAESTUS_PATTERN");
        return call(pattern, "find", level, pos.below()) != null;
    }
}
