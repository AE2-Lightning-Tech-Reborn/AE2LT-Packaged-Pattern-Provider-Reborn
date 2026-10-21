package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

/** Optional FA API boundary. All writes use native inventory/essence/ritual methods. */
public final class FAAccess {
    static final String BASE = "com.stal111.forbidden_arcanus.";
    private record MethodKey(Class<?> owner, String name, List<Class<?>> arguments) {}
    private static final Map<MethodKey, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private FAAccess() {}
    public static ResourceLocation id(String path) { return new ResourceLocation("forbidden_arcanus", path); }
    public static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("FA API class: " + name, e); }
    }
    public static boolean is(Object value, String name) { return value != null && value.getClass().getName().equals(name); }
    public static Object field(Object owner, String name) {
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
    public static Object call(Object owner, String name, Object... args) {
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
    public static boolean live(BlockEntity be) {
        return be != null && !be.isRemoved() && be.getLevel() instanceof ServerLevel level
                && level.isLoaded(be.getBlockPos()) && level.getBlockEntity(be.getBlockPos()) == be;
    }
    public static IItemHandler inventory(BlockEntity be) { return (IItemHandler) call(be, "getItemStackHandler"); }
    public static ItemStack stack(BlockEntity be, int slot) { return inventory(be).getStackInSlot(slot); }
    public static void setStack(BlockEntity be, int slot, ItemStack stack) { call(be, "setStack", slot, stack); be.setChanged(); }
    @SuppressWarnings("unchecked")
    public static Registry<Object> registry(net.minecraft.world.level.Level level, String path) {
        return level.registryAccess().registryOrThrow(ResourceKey.createRegistryKey(id(path)));
    }
    public static Object essence(String name) {
        var enumType = type(BASE + "common.block.entity.forge.essence.EssenceType");
        return Arrays.stream(enumType.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }
    public static int amount(BlockEntity forge, String name) { return (int) call(call(forge, "getEssenceManager"), "getEssence", essence(name)); }
    public static int capacity(BlockEntity forge, String name) { return (int) call(field(call(forge, "getEssenceManager"), "maxEssences"), "get", essence(name)); }
    public static void amount(BlockEntity forge, String name, int value) {
        if (value < 0 || value > capacity(forge, name)) throw new IllegalArgumentException("Essence capacity");
        call(call(forge, "getEssenceManager"), "setEssence", essence(name), value); forge.setChanged();
    }
    public static boolean active(BlockEntity forge) { return (boolean) call(call(forge, "getRitualManager"), "isRitualActive"); }
}
