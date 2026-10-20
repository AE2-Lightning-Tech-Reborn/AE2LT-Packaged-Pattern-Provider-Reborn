package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MekRecipeBindingTest {
    private static final ResourceLocation ID = ResourceLocation.parse("mekmm:recipe_copy");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var loadingList = mock(net.neoforged.fml.loading.LoadingModList.class);
            loading.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(loadingList);
            when(loadingList.getModFiles()).thenReturn(List.of());
            Bootstrap.bootStrap();
        }
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
        doReturn(Optional.empty()).when(fixture.manager).byKey(ID);
        assertNull(fixture.arrange());
    }

    @Test
    void recipeTypeChangeCannotReuseTheMachineBinding() {
        var fixture = new Fixture();
        var serverRecipe = fixture.recipe(1, Items.GOLD_INGOT);
        doReturn(mock(RecipeType.class)).when(serverRecipe).getType();
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
        final ServerLevel level = mock(ServerLevel.class);
        final RecipeManager manager = mock(RecipeManager.class);
        final RecipeType<?> type = mock(RecipeType.class);
        final GenericStack input = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        final IPatternDetails pattern = mock(IPatternDetails.class);
        final Recipe<?> cachedRecipe = recipe(1, Items.GOLD_INGOT);
        final MekmmMachineSpec.PortGroup mode = new MekmmMachineSpec.PortGroup(
                List.of(new MekmmMachineSpec.PortDef(new MekPortSpec(1, 0, 0, MekPortSpec.RelativeAccess.BACK),
                        MekmmMachineSpec.PortType.ITEM)),
                List.of(new MekmmMachineSpec.PortDef(new MekPortSpec(1, 0, 2, MekPortSpec.RelativeAccess.BACK),
                        MekmmMachineSpec.PortType.ITEM)));
        final MekRecipeBinding binding = new MekRecipeBinding(
                new MekmmMachineSpec(ID, "test.Machine", List.of(mode), null),
                Map.of(mode, List.of(new RecipeHolder<>(ID, cachedRecipe))));

        Fixture() {
            when(level.getRecipeManager()).thenReturn(manager);
            when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        }

        Recipe<?> recipe(long amount, Item output) {
            var recipe = mock(Recipe.class, withSettings().extraInterfaces(NativeItemRecipe.class));
            doReturn(type).when(recipe).getType();
            when(((NativeItemRecipe) recipe).getInput()).thenReturn(new NativeInput(amount));
            when(((NativeItemRecipe) recipe).getOutput(any(ItemStack.class))).thenAnswer(call -> new ItemStack(output));
            return recipe;
        }

        void live(Recipe<?> recipe) {
            doReturn(Optional.of(new RecipeHolder<>(ID, recipe))).when(manager).byKey(ID);
        }

        List<GenericStack> arrange() {
            var inputs = new KeyCounter();
            inputs.add(input.what(), input.amount());
            return binding.arrange(level, mode, pattern, new KeyCounter[] {inputs});
        }
    }
}
