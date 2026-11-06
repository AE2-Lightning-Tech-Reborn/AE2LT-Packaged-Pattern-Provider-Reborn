package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.lang.reflect.Method;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Mekanism 10.4 generic chemical predicates erase parameters to ChemicalStack. */
final class RecipeMethodAccess {
    private record Lookup(Class<?> type, String name, List<Class<?>> arguments) {}
    private static final ConcurrentHashMap<Lookup, Optional<Method>> CACHE = new ConcurrentHashMap<>();

    static Optional<Method> find(Class<?> type, String name, Class<?>... arguments) {
        return CACHE.computeIfAbsent(new Lookup(type, name, List.of(arguments)), RecipeMethodAccess::resolve);
    }

    private static Optional<Method> resolve(Lookup key) {
        var arguments = key.arguments().toArray(Class<?>[]::new);
        var exact = ReflectionSupport.findMethodCached(key.type(), key.name(), arguments);
        if (exact.isPresent()) return exact;
        try {
            // Resolve a unique most-specific overload, then reuse the accessible-method cache.
            var candidates = Arrays.stream(key.type().getMethods())
                    .filter(m -> m.getName().equals(key.name()) && m.getParameterCount() == key.arguments().size())
                    .filter(m -> compatible(m.getParameterTypes(), key.arguments()))
                    .filter(m -> !m.isBridge() && !m.isSynthetic()).toList();
            var best = candidates.stream().filter(m -> candidates.stream().allMatch(other ->
                    compatible(other.getParameterTypes(), List.of(m.getParameterTypes())))).toList();
            return best.size() == 1
                    ? ReflectionSupport.findMethodCached(key.type(), key.name(), best.get(0).getParameterTypes())
                    : Optional.empty();
        } catch (SecurityException failure) {
            return Optional.empty();
        }
    }

    private static boolean compatible(Class<?>[] parameters, List<Class<?>> arguments) {
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].isAssignableFrom(arguments.get(i))) return false;
        }
        return true;
    }
}
