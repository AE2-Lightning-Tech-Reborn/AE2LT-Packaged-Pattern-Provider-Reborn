package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReflectionSupport {
    private static final ConcurrentMap<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MethodLookupKey, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MethodLookupKey, Optional<Method>> DECLARED_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ClassValue<MemberCache> MEMBERS = new ClassValue<>() {
        @Override protected MemberCache computeValue(Class<?> type) { return new MemberCache(); }
    };

    private static final class MemberCache {
        final ConcurrentMap<String, Optional<Method>> methods = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Optional<Method>> declaredMethods = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Optional<Field>> fields = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Optional<Field>> declaredFields = new ConcurrentHashMap<>();
    }

    private ReflectionSupport() {
    }

    public static Optional<Class<?>> findClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    public static Optional<Class<?>> findClassCached(String className) {
        return CLASS_CACHE.computeIfAbsent(className, ReflectionSupport::findClass);
    }

    public static Optional<Method> findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return Optional.of(type.getMethod(name, parameterTypes));
        } catch (NoSuchMethodException | SecurityException e) {
            return Optional.empty();
        }
    }

    public static Optional<Method> findMethodCached(Class<?> type, String name, Class<?>... parameterTypes) {
        if (parameterTypes.length == 0) return findMethodCached(type, name);
        return METHOD_CACHE.computeIfAbsent(MethodLookupKey.of(type, name, parameterTypes),
                key -> accessible(findMethod(key.type(), key.name(), key.parameterTypes().toArray(Class<?>[]::new)).orElse(null)));
    }

    /** Frequent optional-mod getters need no signature object or argument-array copy on a cache hit. */
    public static Optional<Method> findMethodCached(Class<?> type, String name) {
        var cache = MEMBERS.get(type).methods;
        var found = cache.get(name);
        return found != null ? found : cache.computeIfAbsent(name, key -> accessible(findMethod(type, key).orElse(null)));
    }

    public static Optional<Method> findDeclaredMethodCached(Class<?> type, String name, Class<?>... parameterTypes) {
        if (parameterTypes.length == 0) {
            var cache = MEMBERS.get(type).declaredMethods;
            var found = cache.get(name);
            return found != null ? found : cache.computeIfAbsent(name,
                    key -> accessible(findDeclaredMethod(type, key).orElse(null)));
        }
        return DECLARED_METHOD_CACHE.computeIfAbsent(MethodLookupKey.of(type, name, parameterTypes),
                key -> accessible(findDeclaredMethod(key.type(), key.name(), key.parameterTypes().toArray(Class<?>[]::new)).orElse(null)));
    }

    public static Optional<Field> findFieldCached(Class<?> type, String name) {
        var cache = MEMBERS.get(type).fields;
        var found = cache.get(name);
        return found != null ? found : cache.computeIfAbsent(name, key -> accessible(findField(type, key).orElse(null)));
    }

    public static Optional<Field> findDeclaredFieldCached(Class<?> type, String name) {
        var cache = MEMBERS.get(type).declaredFields;
        var found = cache.get(name);
        return found != null ? found : cache.computeIfAbsent(name,
                key -> accessible(findDeclaredField(type, key).orElse(null)));
    }

    public static Optional<Object> invoke(Method method, Object target, Object... args) {
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
            CompatibilityDiagnostics.failure(method.getDeclaringClass(), method.toGenericString(), e);
            return Optional.empty();
        }
    }

    private static Optional<Method> findDeclaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return Optional.of(type.getDeclaredMethod(name, parameterTypes));
        } catch (NoSuchMethodException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static Optional<Field> findField(Class<?> type, String name) {
        try {
            return Optional.of(type.getField(name));
        } catch (NoSuchFieldException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static Optional<Field> findDeclaredField(Class<?> type, String name) {
        try {
            return Optional.of(type.getDeclaredField(name));
        } catch (NoSuchFieldException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static Optional<Method> accessible(Method method) {
        if (method == null) {
            return Optional.empty();
        }
        try {
            method.setAccessible(true);
            return Optional.of(method);
        } catch (RuntimeException e) {
            CompatibilityDiagnostics.failure(method.getDeclaringClass(), method.toGenericString(), e);
            return Optional.empty();
        }
    }

    private static Optional<Field> accessible(Field field) {
        if (field == null) {
            return Optional.empty();
        }
        try {
            field.setAccessible(true);
            return Optional.of(field);
        } catch (RuntimeException e) {
            CompatibilityDiagnostics.failure(field.getDeclaringClass(), field.getName(), e);
            return Optional.empty();
        }
    }

    private record MethodLookupKey(Class<?> type, String name, List<Class<?>> parameterTypes) {
        private MethodLookupKey {
            parameterTypes = List.copyOf(parameterTypes);
        }

        private static MethodLookupKey of(Class<?> type, String name, Class<?>... parameterTypes) {
            return new MethodLookupKey(type, name, Arrays.asList((Class<?>[]) parameterTypes.clone()));
        }
    }

}
