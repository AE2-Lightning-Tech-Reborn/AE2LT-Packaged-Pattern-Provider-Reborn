package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RecipeMethodAccessTest {
    public static class Chemical {}
    public static class Gas extends Chemical {}
    public static class Ingredient {
        public boolean testType(Chemical gas) { return gas instanceof Gas; }
        public boolean testType(Object value) { return false; }
    }
    public static class Ambiguous {
        public boolean testType(java.io.Serializable x) { return false; }
        public boolean testType(CharSequence x) { return true; }
    }
    @Test void resolvesErasedChemicalBaseAndKeepsMostSpecificOverload() throws Exception {
        var method = RecipeMethodAccess.find(Ingredient.class,"testType",Gas.class).orElseThrow();
        assertEquals(Chemical.class,method.getParameterTypes()[0]);
        assertEquals(true,method.invoke(new Ingredient(),new Gas()));
        assertSame(method,RecipeMethodAccess.find(Ingredient.class,"testType",Gas.class).orElseThrow());
    }
    @Test void rejectsAmbiguousAndIncompatiblePredicates() {
        assertTrue(RecipeMethodAccess.find(Ambiguous.class,"testType",String.class).isEmpty());
        assertTrue(RecipeMethodAccess.find(Ingredient.class,"missing",Gas.class).isEmpty());
    }
}
