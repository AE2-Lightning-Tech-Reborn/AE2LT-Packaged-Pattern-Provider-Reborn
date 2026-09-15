package com.moakiee.ae2lt.packaged.probe;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Actual optional-mod APIs and table recipes; no unit-test AE2 stand-ins. */
final class AdapterRefactorProbe {
    static void run(ServerLevel level) throws Exception {
        checkApiLookups();
        checkCapabilities(level);
        checkPublicCapabilitiesWithoutReflectionHandles(level);
        checkTableSemantics();
        checkTableAdapter(level, "extendedcrafting:ultimate_table", "extendedcrafting:table", new ExtendedCraftingTableAdapter());
        checkTableAdapter(level, "avaritia:extreme_crafting_table", "avaritia:crafting_table_recipe", new AvaritiaTableAdapter());
        System.out.println("ADAPTER_REFACTOR_PROBE PASS access, capability identity, components, remainders and real table recipes");
    }

    private static void checkApiLookups() throws Exception {
        var required = Map.ofEntries(
                Map.entry("ma.InfusionAltarAccess", "getAltarIngredientMethod"),
                Map.entry("ma.AwakeningAltarAccess", "getEssencesMethod"),
                Map.entry("ec.ExtendedCraftingTableAccess", "tableInputOfMethod"),
                Map.entry("ec.ExtendedCraftingCombinationAccess", "getInputMethod"),
                Map.entry("ec.ExtendedCraftingEnderCrafterAccess", "shapedWidthMethod"),
                Map.entry("ec.ExtendedCraftingFluxCrafterAccess", "shapedWidthMethod"),
                Map.entry("avaritia.AvaritiaTableAccess", "tierInputOfMethod"),
                Map.entry("avaritia.AvaritiaExtremeSmithingAccess", "inputConstructor"),
                Map.entry("aa.ActuallyAdditionsEmpowererAccess", "recipeMatchesMethod"),
                Map.entry("aa.ActuallyAdditionsAtomicReconstructorAccess", "invokeReconstructorMethod"),
                Map.entry("ars.ArsNouveauImbuementChamberAccess", "getRecipesMethod"),
                Map.entry("de.DraconicFusionCraftingAccess", "fusionIngredientsMethod"),
                Map.entry("occultism.OccultismRitualAccess", "getActivationItemMethod"));
        for (var entry : required.entrySet()) {
            var type = access(entry.getKey());
            var lookup = type.getDeclaredMethod("ensureLookup");
            lookup.setAccessible(true);
            lookup.invoke(null);
            var field = type.getDeclaredField(entry.getValue());
            field.setAccessible(true);
            check(field.get(null) != null, "required API " + entry);
        }
        var ars = access("ars.ArsNouveauEnchantingApparatusAccess");
        for (String name : List.of("apparatusInputConstructor", "getRecipesMethod", "hasSourceNearbyMethod")) {
            var method = ars.getDeclaredMethod(name);
            method.setAccessible(true);
            check(method.invoke(null) != null, "Ars apparatus API " + name);
        }
    }

    private static void checkCapabilities(ServerLevel level) throws Exception {
        var pos = new BlockPos(5, 80, 5);
        for (var id : List.of("mysticalagriculture:infusion_altar", "mysticalagriculture:infusion_pedestal",
                "mysticalagriculture:awakening_altar", "mysticalagriculture:awakening_pedestal", "mysticalagriculture:essence_vessel",
                "extendedcrafting:basic_table", "extendedcrafting:advanced_table", "extendedcrafting:elite_table", "extendedcrafting:ultimate_table",
                "extendedcrafting:crafting_core", "extendedcrafting:pedestal", "extendedcrafting:ender_crafter", "extendedcrafting:flux_crafter")) {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            check(block != Blocks.AIR, "registered block " + id);
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            var be = level.getBlockEntity(pos);
            check(be != null, "block entity " + id);
            var exposed = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            var legacy = be.getClass().getMethod("getInventory").invoke(be);
            check(exposed == legacy && exposed instanceof IItemHandlerModifiable, "full capability semantics " + id);
            check(CucumberInventoryAccess.get(be) == exposed, "public capability chosen " + id);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void checkTableSemantics() {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("component-preserved"));
        var ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), Ingredient.EMPTY,
                Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT));
        var amounts = new LinkedHashMap<AEItemKey, Long>();
        amounts.put(AEItemKey.of(named), 3L);
        amounts.put(AEItemKey.of(Items.GOLD_INGOT), 3L);
        var assigned = TableCraftingSupport.assignIngredientsStrict(ingredients, amounts, 3);
        check(assigned != null && ItemStack.isSameItemSameComponents(assigned.getFirst(), named), "component-aware assignment");
        var grid = TableCraftingSupport.layoutGrid(ingredients, assigned, 5, 2);
        check(grid != null && grid.get(1).isEmpty() && grid.get(5).isEmpty() && grid.get(6).is(Items.GOLD_INGOT), "shaped empty cells and stride");
        grid.get(0).setCount(9);
        check(assigned.getFirst().getCount() == 1, "layout copies source stacks");
        var shapeless = TableCraftingSupport.layoutGrid(ingredients, assigned, 5, null);
        check(shapeless.get(1).is(Items.GOLD_INGOT), "shapeless row-major layout");
        var remainder = NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.BUCKET));
        var outputs = TableCraftingSupport.virtualOutputs(new ItemStack(Items.DIAMOND, 2), remainder, 3);
        check(outputs.equals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 6), new GenericStack(AEItemKey.of(Items.BUCKET), 3))), "batch output and container quantities");
        var handler = new ItemStackHandler(25);
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.BUCKET)));
        handler.setStackInSlot(17, new ItemStack(Items.BUCKET, 2));
        handler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 4));
        check(TableCraftingSupport.canApplyRemaining(handler, 5, input, remainder, 3, 2), "remainder fits trimmed origin");
        TableCraftingSupport.applyCraftRemainders(handler, 5, input, remainder, 3, 2);
        check(handler.getStackInSlot(17).getCount() == 2 && handler.getStackInSlot(0).getCount() == 4, "only intended slot consumed and replenished");
        handler.setStackInSlot(17, new ItemStack(Items.STONE, 2));
        check(!TableCraftingSupport.canApplyRemaining(handler, 5, input, remainder, 3, 2), "incompatible remainder rejected without mutation");
        check(handler.getStackInSlot(17).getCount() == 2, "rejected grid unchanged");
    }

    private static void checkPublicCapabilitiesWithoutReflectionHandles(ServerLevel level) throws Exception {
        var pos = new BlockPos(5, 80, 5);
        var type = access("aa.ActuallyAdditionsEmpowererAccess");
        for (String id : List.of("actuallyadditions:empowerer", "actuallyadditions:display_stand")) {
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState());
            var be = level.getBlockEntity(pos);
            check(be != null, "AA block " + id);
            var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            var slotField = type.getDeclaredField("invField");
            slotField.setAccessible(true);
            var legacyField = (java.lang.reflect.Field) slotField.get(null);
            check(legacyField.get(be) == inventory && inventory instanceof IItemHandlerModifiable, "AA full inventory capability");
            var getter = type.getDeclaredMethod("getHandler", net.minecraft.world.level.block.entity.BlockEntity.class);
            getter.setAccessible(true);
            slotField.set(null, null);
            try { check(getter.invoke(null, be) == inventory, "AA inventory independent of internal field handle"); }
            finally { slotField.set(null, legacyField); }
            if (id.endsWith("display_stand")) {
                var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
                check(energy != null && energy.receiveEnergy(123, false) == 123, "AA public energy charge");
                var storageField = type.getDeclaredField("storageField");
                storageField.setAccessible(true);
                var original = storageField.get(null);
                storageField.set(null, null);
                try {
                    var energyGetter = type.getDeclaredMethod("getEnergyStored", net.minecraft.world.level.block.entity.BlockEntity.class);
                    energyGetter.setAccessible(true);
                    check(energyGetter.invoke(null, be).equals(123), "AA energy independent of internal field handle");
                } finally { storageField.set(null, original); }
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkTableAdapter(ServerLevel level, String blockId, String typeId, VirtualCraftingAdapter adapter) throws Exception {
        var pos = new BlockPos(6, 80, 5);
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId)).defaultBlockState());
        var be = level.getBlockEntity(pos);
        check(adapter.recognizesMain(level, pos, be), "table recognition " + blockId);
        var type = (RecipeType) BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(typeId));
        int passed = 0;
        for (Object raw : level.getRecipeManager().getAllRecipesFor(type)) {
            var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
            var recipe = holder.value();
            var output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || recipe.getIngredients().isEmpty()) continue;
            var available = new KeyCounter();
            boolean supported = true;
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                var choices = ingredient.getItems();
                if (choices.length == 0) { supported = false; break; }
                available.add(AEItemKey.of(choices[0]), 1);
            }
            if (!supported) continue;
            var supplied = new java.util.ArrayList<GenericStack>();
            for (var entry : available) supplied.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            if (supplied.size() > 9 || supplied.isEmpty()) continue;
            var encoded = PatternDetailsHelper.encodeProcessingPattern(supplied, List.of(new GenericStack(AEItemKey.of(output), output.getCount())));
            var pattern = PatternDetailsHelper.decodePattern(AEItemKey.of(encoded), level);
            check(pattern != null, "real AE2 processing pattern");
            var binding = adapter.bind(level, pos, pattern);
            if (binding == null) continue;
            var result = adapter.planVirtualWithBinding(level, pos, pattern, new KeyCounter[]{available}, binding.handle(), null);
            if (result == null) continue;
            check(result.outputs().contains(new GenericStack(AEItemKey.of(output), output.getCount())), "actual recipe product " + holder.id());
            var handler = (IItemHandlerModifiable) be.getClass().getMethod("getInventory").invoke(be);
            handler.setStackInSlot(0, new ItemStack(Items.STONE));
            check(adapter.planVirtualWithBinding(level, pos, pattern, new KeyCounter[]{available}, binding.handle(), null) == null, "occupied grid blocks virtual craft");
            handler.setStackInSlot(0, ItemStack.EMPTY);
            System.out.println("ADAPTER_REFACTOR_PROBE real recipe " + holder.id());
            if (++passed == 2) break;
        }
        check(passed > 0, "at least one real recipe crafted for " + blockId);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static Class<?> access(String name) throws ClassNotFoundException {
        return Class.forName("com.moakiee.ae2lt.packaged.logic.multiblock." + name);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
