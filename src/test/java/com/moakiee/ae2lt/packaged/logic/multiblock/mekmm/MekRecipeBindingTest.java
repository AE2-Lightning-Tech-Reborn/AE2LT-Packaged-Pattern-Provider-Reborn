package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Proxy;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MekRecipeBindingTest {
    private static final ResourceLocation ID = new ResourceLocation("mekmm:recipe_copy");

    @BeforeAll
    static void bootstrap() {
        com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap.initialize();
    }

    @Test
    void equivalentRecipeCopyCanStillDispatch() {
        var fixture = new Fixture();
        var serverRecipe = fixture.recipe(1, Items.GOLD_INGOT);
        assertNotSame(fixture.cachedRecipe, serverRecipe);
        fixture.live(serverRecipe);
        assertEquals(List.of(fixture.input), fixture.arrange());
    }

    @Test
    void reloadedInputAmountIsValidatedAgainstTheServerRecipe() {
        var fixture = new Fixture();
        fixture.live(fixture.recipe(2, Items.GOLD_INGOT));
        assertNull(fixture.arrange());
    }

    @Test
    void reloadedOutputIsValidatedAgainstTheServerRecipe() {
        var fixture = new Fixture();
        fixture.live(fixture.recipe(1, Items.DIAMOND));
        assertNull(fixture.arrange());
    }

    @Test
    void removedRecipeCannotUseTheCachedCopy() {
        var fixture = new Fixture();
        fixture.manager.replaceRecipes(List.of());
        assertNull(fixture.arrange());
    }

    @Test
    void recipeTypeChangeCannotReuseTheMachineBinding() {
        var fixture = new Fixture();
        var serverRecipe = fixture.recipe(1, Items.GOLD_INGOT, new RecipeType<>() {});
        fixture.live(serverRecipe);
        assertNull(fixture.arrange());
    }

    public interface NativeItemRecipe {
        NativeInput getInput();
        ItemStack getOutput(ItemStack input);
    }

    public record NativeInput(long amount) {
        public boolean testType(ItemStack stack) { return stack.is(Items.IRON_INGOT); }
        public long getNeededAmount(ItemStack stack) { return testType(stack) ? amount : 0; }
    }

    private static final class Fixture {
        final TestLevel level = allocateLevel();
        final RecipeManager manager = new RecipeManager();
        final RecipeType<?> type = new RecipeType<>() {};
        final GenericStack input = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        final IPatternDetails pattern = (IPatternDetails) Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(), new Class<?>[] {IPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOutputs" -> new GenericStack[] {new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)};
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final Recipe<?> cachedRecipe = recipe(1, Items.GOLD_INGOT);
        final MekmmMachineSpec.PortGroup mode = new MekmmMachineSpec.PortGroup(
                List.of(new MekmmMachineSpec.PortDef(new MekPortSpec(1, 0, 0, MekPortSpec.RelativeAccess.BACK),
                        MekmmMachineSpec.PortType.ITEM)),
                List.of(new MekmmMachineSpec.PortDef(new MekPortSpec(1, 0, 2, MekPortSpec.RelativeAccess.BACK),
                        MekmmMachineSpec.PortType.ITEM)));
        final MekRecipeBinding binding = new MekRecipeBinding(
                new MekmmMachineSpec(ID, "test.Machine", false, List.of(mode), null),
                Map.of(mode, List.of(cachedRecipe)));

        Fixture() { level.manager = manager; }

        Recipe<?> recipe(long amount, Item output) { return recipe(amount, output, type); }

        Recipe<?> recipe(long amount, Item output, RecipeType<?> recipeType) {
            return (Recipe<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Recipe.class, NativeItemRecipe.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getType" -> recipeType;
                        case "getId" -> ID;
                        case "getInput" -> new NativeInput(amount);
                        case "getOutput" -> new ItemStack(output);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        void live(Recipe<?> recipe) { manager.replaceRecipes(List.of(recipe)); }

        List<GenericStack> arrange() {
            var inputs = new KeyCounter();
            inputs.add(input.what(), input.amount());
            return binding.arrange(level, mode, pattern, new KeyCounter[] {inputs});
        }
    }

    /** Only the overridden recipe lookup is used; no world or server is constructed. */
    private static final class TestLevel extends ServerLevel {
        RecipeManager manager;
        private TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public RecipeManager getRecipeManager() { return manager; }
    }

    private static TestLevel allocateLevel() {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (TestLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TestLevel.class);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
