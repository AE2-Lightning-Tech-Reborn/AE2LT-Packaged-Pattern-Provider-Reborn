package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ReflectionSupportTest {
    public static class Parent {
        public String field = "parent";
        public String value() { return "parent"; }
        public String value(int n) { return "number" + n; }
    }
    public static final class Child extends Parent {
        private String privateField = "private";
        @Override public String value() { return "child"; }
        private String hidden() { return "hidden"; }
    }

    @Test
    void cachedGettersAreIsolatedByOwnerAndLookupMode() throws Exception {
        var parent = ReflectionSupport.findMethodCached(Parent.class, "value").orElseThrow();
        var child = ReflectionSupport.findMethodCached(Child.class, "value").orElseThrow();
        assertEquals("parent", parent.invoke(new Parent()));
        assertEquals("child", child.invoke(new Child()));
        assertSame(child, ReflectionSupport.findMethodCached(Child.class, "value", new Class<?>[0]).orElseThrow());
        assertTrue(ReflectionSupport.findMethodCached(Child.class, "hidden").isEmpty());
        assertEquals("hidden", ReflectionSupport.findDeclaredMethodCached(Child.class, "hidden").orElseThrow().invoke(new Child()));
        assertEquals("parent", ReflectionSupport.findFieldCached(Child.class, "field").orElseThrow().get(new Child()));
        assertTrue(ReflectionSupport.findDeclaredFieldCached(Child.class, "field").isEmpty());
        assertEquals("private", ReflectionSupport.findDeclaredFieldCached(Child.class, "privateField").orElseThrow().get(new Child()));
    }

    @Test
    void missingGetterAndMutableParameterArraysCannotPoisonOtherSignatures() throws Exception {
        var missing = ReflectionSupport.findMethodCached(Child.class, "missing");
        assertTrue(missing.isEmpty());
        assertSame(missing, ReflectionSupport.findMethodCached(Child.class, "missing"));
        Class<?>[] parameters = {int.class};
        var number = ReflectionSupport.findMethodCached(Child.class, "value", parameters).orElseThrow();
        parameters[0] = String.class;
        assertTrue(ReflectionSupport.findMethodCached(Child.class, "value", parameters).isEmpty());
        assertEquals("number3", number.invoke(new Child(), 3));
        assertSame(number, ReflectionSupport.findMethodCached(Child.class, "value", int.class).orElseThrow());
    }

    @Test
    void shouldReturnEmptyWhenClassMissing() {
        assertTrue(ReflectionSupport.findClass("missing.fake.ClassName").isEmpty());
    }

    @Test
    void shouldFindExistingClass() {
        var found = ReflectionSupport.findClass("java.lang.String");

        assertTrue(found.isPresent());
        assertEquals(String.class, found.orElseThrow());
    }

    @Test
    void shouldFindAndInvokeExistingMethod() {
        var method = ReflectionSupport.findMethod(String.class, "substring", int.class);
        var result = ReflectionSupport.invoke(method.orElseThrow(), "value", 1);

        assertTrue(result.isPresent());
        assertEquals("alue", result.orElseThrow());
    }

    @Test
    void shouldReturnEmptyForMissingOptionalModClass() {
        assertTrue(ReflectionSupport.findClass("missing.fake.optional.ModClass").isEmpty());
    }

    @Test
    void shouldReturnEmptyForMissingMethod() {
        var result = ReflectionSupport.findMethod(String.class, "missingMethodName");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenInvocationFails() {
        var method = ReflectionSupport.findMethod(String.class, "substring", int.class, int.class);
        var result = ReflectionSupport.invoke(method.orElseThrow(), "value", 4, 1);

        assertTrue(result.isEmpty());
    }
}
