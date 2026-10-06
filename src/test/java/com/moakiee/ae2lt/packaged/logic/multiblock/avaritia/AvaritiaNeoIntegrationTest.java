package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

class AvaritiaNeoIntegrationTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
        assumeTrue(AvaritiaNeoSupport.isAvailable(), "Run with -Penable_avaritia_tests=true");
    }

    @Test
    void realTileInventoryFieldsUseContainerInterface() throws ReflectiveOperationException {
        var type = Class.forName("net.byAqua3.avaritia.tile.TileExtremeCraftingTable");
        assertTrue(Container.class.isAssignableFrom(type.getField("matrix").getType()));
        assertTrue(Container.class.isAssignableFrom(type.getField("result").getType()));
    }

    @Test
    void rejectsUnknownTiles() {
        assertFalse(AvaritiaNeoSupport.isSupportedTable(new Object()));
        assertNull(AvaritiaNeoSupport.getHandler(new Object()));
        assertFalse(AvaritiaNeoSupport.resultIsEmpty(new Object()));
    }

    @Test
    void shapedRecipeUsesWidthAndNineWideGridWithHoles() throws ReflectiveOperationException {
        var ingredients = NonNullList.withSize(4, Ingredient.EMPTY);
        ingredients.set(0, Ingredient.of(Items.DIAMOND));
        ingredients.set(3, Ingredient.of(Items.IRON_INGOT));
        var recipe = shaped(2, 2, ingredients, new ItemStack(Items.EMERALD));
        assertEquals(2, AvaritiaNeoSupport.recipeDimension(recipe, "getWidth"));
        assertEquals(2, AvaritiaNeoSupport.recipeDimension(recipe, "getHeight"));

        var layout = layout(recipe, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT)));
        var grid = gridFromLayout(layout);
        assertTrue(grid.getItem(0).is(Items.DIAMOND));
        assertTrue(grid.getItem(10).is(Items.IRON_INGOT));
        assertTrue(grid.getItem(1).isEmpty());
        assertTrue(recipe.matches(grid, null));
        grid.setItem(1, new ItemStack(Items.COBBLESTONE));
        assertFalse(recipe.matches(grid, null));
    }

    @Test
    void shapelessRecipeWorksWithoutShapedDimensions() throws ReflectiveOperationException {
        var ingredients = NonNullList.withSize(2, Ingredient.EMPTY);
        ingredients.set(0, Ingredient.of(Items.DIAMOND));
        ingredients.set(1, Ingredient.of(Items.IRON_INGOT));
        var recipe = shapeless(ingredients, new ItemStack(Items.EMERALD, 2));
        assertNull(AvaritiaNeoSupport.recipeDimension(recipe, "getWidth"));
        var layout = layout(recipe, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND)));
        var grid = gridFromLayout(layout);
        assertTrue(recipe.matches(grid, null));
        assertEquals(2, recipe.assemble(grid, RegistryAccess.EMPTY).getCount());
        grid.setItem(2, new ItemStack(Items.COBBLESTONE));
        assertFalse(recipe.matches(grid, null));
    }

    @Test
    void virtualBatchPreservesOutputTagsAndBucketRemainders() throws ReflectiveOperationException {
        var ingredients = NonNullList.withSize(1, Ingredient.of(Items.WATER_BUCKET));
        var output = new ItemStack(Items.DIAMOND, 2);
        var tag = new CompoundTag();
        tag.putString("test", "preserved");
        output.setTag(tag);
        var recipe = shaped(1, 1, ingredients, output);
        var grid = gridFromLayout(layout(recipe, List.of(new ItemStack(Items.WATER_BUCKET))));
        assertTrue(recipe.matches(grid, null));
        var remainders = (NonNullList<?>) adapterMethod("remainingItems", Object.class, CraftingContainer.class)
                .invoke(null, recipe, grid);
        assertEquals(81, remainders.size());
        assertTrue(((ItemStack) remainders.get(0)).is(Items.BUCKET));
        var outputs = (List<?>) adapterMethod("virtualOutputs", ItemStack.class, NonNullList.class, int.class)
                .invoke(null, recipe.assemble(grid, RegistryAccess.EMPTY), remainders, 3);
        var result = (GenericStack) outputs.get(0);
        var bucket = (GenericStack) outputs.get(1);
        assertEquals(AEItemKey.of(output), result.what());
        assertEquals(6, result.amount());
        assertEquals(AEItemKey.of(Items.BUCKET), bucket.what());
        assertEquals(3, bucket.amount());
        assertTrue(grid.getItem(0).is(Items.WATER_BUCKET));
    }

    @Test
    void selectsNeoRecipeTypeAndLeavesTierMetadataUntiered() throws ReflectiveOperationException {
        assertEquals(new ResourceLocation("avaritia", "extreme_crafting"),
                adapterMethod("recipeTypeId").invoke(null));
        var recipe = shaped(1, 1, NonNullList.withSize(1, Ingredient.of(Items.DIAMOND)),
                new ItemStack(Items.EMERALD));
        var reflection = Class.forName(AvaritiaTableAdapter.class.getName() + "$AvaritiaReflection");
        var tier = reflection.getDeclaredMethod("recipeTier", Object.class);
        tier.setAccessible(true);
        assertEquals(AvaritiaTableSpecs.RecipeTier.untiered(), tier.invoke(null, recipe));
    }

    @SuppressWarnings("unchecked")
    private static Recipe<CraftingContainer> shaped(int width, int height,
                                                    NonNullList<Ingredient> ingredients, ItemStack result)
            throws ReflectiveOperationException {
        return (Recipe<CraftingContainer>) Class.forName("net.byAqua3.avaritia.recipe.RecipeExtremeShaped")
                .getConstructor(ResourceLocation.class, String.class, int.class, int.class,
                        NonNullList.class, ItemStack.class)
                .newInstance(new ResourceLocation("test", "shaped"), "", width, height, ingredients, result);
    }

    @SuppressWarnings("unchecked")
    private static Recipe<CraftingContainer> shapeless(NonNullList<Ingredient> ingredients, ItemStack result)
            throws ReflectiveOperationException {
        return (Recipe<CraftingContainer>) Class.forName("net.byAqua3.avaritia.recipe.RecipeExtremeShapeless")
                .getConstructor(ResourceLocation.class, String.class, ItemStack.class, NonNullList.class, boolean.class)
                .newInstance(new ResourceLocation("test", "shapeless"), "", result, ingredients, false);
    }

    private static List<?> layout(Object recipe, List<ItemStack> stacks) throws ReflectiveOperationException {
        var specClass = Class.forName(AvaritiaTableAdapter.class.getName() + "$TableSpec");
        var constructor = specClass.getDeclaredConstructor(ResourceLocation.class,
                AvaritiaTableSpecs.TableSpec.class, int.class);
        constructor.setAccessible(true);
        var spec = constructor.newInstance(new ResourceLocation("avaritia", "extreme_crafting_table"),
                AvaritiaTableSpecs.find("avaritia", "extreme_crafting_table"), 9);
        var result = (List<?>) adapterMethod("layoutGridFromIngredients", Object.class, List.class, specClass)
                .invoke(null, recipe, stacks, spec);
        assertNotNull(result);
        return result;
    }

    private static CraftingContainer gridFromLayout(List<?> layout) throws ReflectiveOperationException {
        var stacks = new ArrayList<ItemStack>();
        for (int index = 0; index < 81; index++) {
            stacks.add(ItemStack.EMPTY);
        }
        for (var unit : layout) {
            var slot = unit.getClass().getDeclaredMethod("slot");
            var stack = unit.getClass().getDeclaredMethod("stack");
            slot.setAccessible(true);
            stack.setAccessible(true);
            stacks.set((int) slot.invoke(unit), (ItemStack) stack.invoke(unit));
        }
        var reflection = Class.forName(AvaritiaTableAdapter.class.getName() + "$AvaritiaReflection");
        var create = reflection.getDeclaredMethod("createGridInput", int.class, List.class);
        create.setAccessible(true);
        var grid = (CraftingContainer) create.invoke(null, 9, stacks);
        assertNotNull(grid);
        return grid;
    }

    private static Method adapterMethod(String name, Class<?>... parameters) throws ReflectiveOperationException {
        var method = AvaritiaTableAdapter.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
