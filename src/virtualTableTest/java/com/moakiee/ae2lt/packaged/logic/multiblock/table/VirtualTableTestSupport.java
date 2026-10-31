package com.moakiee.ae2lt.packaged.logic.multiblock.table;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.blakebr0.extendedcrafting.api.TableCraftingInput;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapelessTableRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;
import com.google.gson.Gson;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class VirtualTableTestSupport {
    public static final IActionSource SOURCE = IActionSource.empty();
    private VirtualTableTestSupport() {}

    public enum Family {
        AVARITIA("avaritia", "crafting_table_recipe", List.of("sculk_crafting_table", "nether_crafting_table", "end_crafting_table", "extreme_crafting_table")),
        EXTENDED("extendedcrafting", "table", List.of("basic_table", "advanced_table", "elite_table", "ultimate_table"));
        public final String mod;
        public final String type;
        final List<String> blocks;
        Family(String mod, String type, List<String> blocks) { this.mod = mod; this.type = type; this.blocks = blocks; }
        public VirtualCraftingAdapter adapter() { return this == AVARITIA ? new AvaritiaTableAdapter() : new ExtendedCraftingTableAdapter(); }
        public ResourceLocation block(int tier) { return id(mod + ":" + blocks.get(tier - 1)); }
        public Recipe<?> shaped(ShapedRecipePattern shape, ItemStack output, int tier, boolean exact) {
            // Both native APIs use a positive tier as an exact requirement; zero is automatic.
            return this == AVARITIA ? new ShapedTableCraftingRecipe(shape, output, exact ? tier : 0, false)
                    : new ShapedTableRecipe(shape, output, exact ? tier : 0);
        }
        public Recipe<?> shapeless(NonNullList<Ingredient> ingredients, ItemStack output, int tier) {
            return this == AVARITIA ? new ShapelessTableCraftingRecipe(ingredients, output, tier)
                    : new ShapelessTableRecipe(ingredients, output, tier);
        }
        public CraftingInput input(int size, List<ItemStack> stacks, int tier) {
            return this == AVARITIA ? TierInput.of(size, size, stacks, tier) : TableCraftingInput.of(size, size, stacks, tier);
        }
    }

    public record Fixture(GameTestHelper helper, Family family, int tier, BlockPos pos,
                          VirtualCraftingAdapter adapter, IItemHandlerModifiable inventory) {
        public VirtualCraftingResult plan(IPatternDetails pattern, KeyCounter[] inputs) {
            var binding = adapter.bind(helper.getLevel(), pos, pattern);
            probe(helper, "bind", Map.of("family", family.mod, "tier", tier, "accepted", binding != null));
            if (binding == null) return null;
            var result = adapter.planVirtualWithBinding(helper.getLevel(), pos, pattern, inputs, binding.handle(), SOURCE);
            probe(helper, "plan", Map.of("family", family.mod, "tier", tier, "accepted", result != null,
                    "outputs", result == null ? "" : result.outputs().toString()));
            return result;
        }
    }

    public static Fixture table(GameTestHelper h, Family family, int tier) {
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var block = BuiltInRegistries.BLOCK.get(family.block(tier));
        h.assertTrue(block != Blocks.AIR, "required native table registered: " + family.block(tier));
        h.getLevel().removeBlock(pos, false);
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        var entity = h.getLevel().getBlockEntity(pos);
        h.assertTrue(entity != null, "native table entity exists");
        try {
            var inventory = entity.getClass().getMethod("getInventory").invoke(entity);
            h.assertTrue(inventory instanceof IItemHandlerModifiable, "native mutable table inventory");
            return new Fixture(h, family, tier, pos, family.adapter(), (IItemHandlerModifiable) inventory);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    public static final class RecipeScope implements AutoCloseable {
        final GameTestHelper helper;
        final List<RecipeHolder<?>> previous;
        public RecipeScope(GameTestHelper helper, Family family, Recipe<?>... recipes) {
            this.helper = helper;
            previous = List.copyOf(helper.getLevel().getRecipeManager().getRecipes());
            var replacements = new ArrayList<>(previous);
            var type = BuiltInRegistries.RECIPE_TYPE.get(id(family.mod + ":" + family.type));
            replacements.removeIf(holder -> holder.value().getType() == type);
            for (int i = 0; i < recipes.length; i++) replacements.add(new RecipeHolder<>(id("ae2ltpp:table_probe_" + i), recipes[i]));
            helper.getLevel().getRecipeManager().replaceRecipes(replacements);
        }
        public void close() { helper.getLevel().getRecipeManager().replaceRecipes(previous); }
    }

    public static Recipe<?> simple(Family family) {
        return family.shaped(ShapedRecipePattern.of(Map.of('I', Ingredient.of(Items.IRON_INGOT)), "III"), new ItemStack(Items.DIAMOND), 1, false);
    }
    public static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    public static GenericStack item(Item item, long amount) { return new GenericStack(AEItemKey.of(item), amount); }
    public static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, List<GenericStack> outputs) {
        var value = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs), h.getLevel());
        h.assertTrue(value != null, "processing pattern decoded");
        return value;
    }
    public static KeyCounter[] counters(List<GenericStack> stacks) {
        var counter = new KeyCounter();
        stacks.forEach(stack -> counter.add(stack.what(), stack.amount()));
        return new KeyCounter[]{counter};
    }
    public static void probe(GameTestHelper h, String stage, Map<String, ?> values) {
        var event = new LinkedHashMap<String, Object>();
        event.put("origin", h.absolutePos(BlockPos.ZERO).toShortString());
        event.put("tick", h.getLevel().getGameTime());
        event.put("stage", stage);
        event.putAll(values);
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("virtual-table-probe.jsonl"), new Gson().toJson(event) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
}
