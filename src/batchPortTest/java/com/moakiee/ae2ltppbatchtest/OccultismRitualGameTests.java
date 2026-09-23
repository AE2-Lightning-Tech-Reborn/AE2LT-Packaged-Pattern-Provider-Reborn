package com.moakiee.ae2ltppbatchtest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.occultism.OccultismRitualAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Development-only check against Occultism's real ritual recipe and pentacle. */
public final class OccultismRitualGameTests {
    private OccultismRitualGameTests() {}

    public static void ritualDistributesNativeRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var bowlPos = helper.absolutePos(new BlockPos(6, 2, 6));
        int duration = preparePentacle(helper, bowlPos);
        var inputs = inputs();
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(item("occultism:fragile_soul_gem")));
        var pattern = PatternDetailsHelper.decodePattern(encoded, level);
        var adapter = new OccultismRitualAdapter();
        var binding = adapter.bind(level, bowlPos, pattern);
        helper.assertTrue(binding != null, "Native Occultism ritual did not bind");
        helper.assertTrue(adapter.canDispatch(level, bowlPos, binding.handle()),
                "Occultism ritual rejected a valid empty pentacle");
        var plan = adapter.planWithBinding(level, bowlPos, pattern, counters(inputs), binding.handle(), null);
        helper.assertTrue(plan != null && plan.targets().size() == 4,
                "Occultism ritual did not assign three sacrificial bowls and activation bowl");
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(plan, null, retained::addAll);
        helper.assertTrue(result.success() && retained.isEmpty(),
                "Occultism ritual dispatch lost input ownership");
        assertRitualStarted(helper, bowlPos);
        awaitNativeOutput(helper, bowlPos, duration, inputs.size());
    }

    public static void providerRoutesNativeRitual(GameTestHelper helper) {
        var level = helper.getLevel();
        var bowlPos = helper.absolutePos(new BlockPos(6, 2, 6));
        int duration = preparePentacle(helper, bowlPos);
        var providerPos = bowlPos.west();
        level.setBlockAndUpdate(providerPos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(providerPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, PPItems.OCCULTISM_RITUAL_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var inputs = inputs();
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(item("occultism:fragile_soul_gem"))));
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            helper.assertTrue(!logic.getAvailablePatterns().isEmpty(),
                    "Occultism ritual pattern was not advertised by PP");
            helper.assertTrue(logic.pushPattern(logic.getAvailablePatterns().getFirst(), counters(inputs)),
                    "PP did not dispatch the native Occultism ritual");
            assertRitualStarted(helper, bowlPos);
            awaitNativeOutput(helper, bowlPos, duration, inputs.size());
        });
    }

    public static void itemUseProxyCompletesNativeRitual(GameTestHelper helper) {
        var level = helper.getLevel();
        var bowlPos = helper.absolutePos(new BlockPos(6, 2, 6));
        preparePentacle(helper, bowlPos, "possess_endermite", 4);
        var inputs = List.of(item("occultism:book_of_binding_bound_foliot"),
                item("minecraft:dirt"), item("minecraft:stone"),
                item("minecraft:dirt"), item("minecraft:stone"),
                item("minecraft:egg"));
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                inputs, List.of(item("occultism:spawn_egg/possessed_endermite"))), level);
        var adapter = new OccultismRitualAdapter();
        var binding = adapter.bind(level, bowlPos, pattern);
        helper.assertTrue(binding != null && adapter.canDispatch(level, bowlPos, binding.handle()),
                "Occultism item-use ritual did not bind to its real pentacle");
        var plan = adapter.planWithBinding(level, bowlPos, pattern, counters(inputs), binding.handle(), null);
        helper.assertTrue(plan != null && plan.targets().size() == 6,
                "Occultism item-use ritual did not plan four bowls, activation and proxy cost");
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(plan, null, retained::addAll);
        helper.assertTrue(result.success() && retained.isEmpty(),
                "Occultism item-use ritual did not consume its proxy item exactly once");
        assertRitualStarted(helper, bowlPos);
        try {
            var goldenBowl = level.getBlockEntity(bowlPos);
            helper.assertTrue(goldenBowl.getClass().getField("itemUseProvided").getBoolean(goldenBowl),
                    "Occultism item-use proxy was not credited after ritual activation");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read Occultism's item-use gate", e);
        }
        helper.runAfterDelay(500, () -> {
            var expected = Identifier.parse("occultism:possessed_endermite");
            var nearby = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new AABB(bowlPos).inflate(20)).stream()
                    .map(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).toList();
            boolean active;
            boolean itemUseProvided;
            int currentTime;
            int remaining;
            try {
                var goldenBowl = level.getBlockEntity(bowlPos);
                active = goldenBowl.getClass().getMethod("getCurrentRitualRecipe")
                        .invoke(goldenBowl) != null;
                itemUseProvided = goldenBowl.getClass().getField("itemUseProvided").getBoolean(goldenBowl);
                currentTime = goldenBowl.getClass().getField("currentTime").getInt(goldenBowl);
                var ingredients = (List<?>) goldenBowl.getClass().getField("remainingAdditionalIngredients")
                        .get(goldenBowl);
                remaining = ingredients == null ? -1 : ingredients.size();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Could not inspect Occultism's item-use ritual state", e);
            }
            helper.assertTrue(nearby.contains(expected),
                    "Occultism item-use proxy ritual did not summon its native entity"
                            + " (active=" + active + ", itemUseProvided=" + itemUseProvided
                            + ", currentTime=" + currentTime + ", remaining=" + remaining
                            + ", nearby=" + nearby + ")");
            helper.succeed();
        });
    }

    public static void sacrificeProxyCompletesNativeRitual(GameTestHelper helper) {
        var level = helper.getLevel();
        var bowlPos = helper.absolutePos(new BlockPos(6, 2, 6));
        int duration = preparePentacle(helper, bowlPos, "misc_bee_nest", 4);
        var inputs = List.of(item("minecraft:beehive"),
                item("minecraft:honeycomb_block"), item("minecraft:honeycomb_block"),
                item("minecraft:honeycomb_block"), item("minecraft:honeycomb_block"),
                item("minecraft:bee_spawn_egg"));
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                inputs, List.of(item("minecraft:bee_nest"))), level);
        var adapter = new OccultismRitualAdapter();
        var binding = adapter.bind(level, bowlPos, pattern);
        helper.assertTrue(binding != null && adapter.canDispatch(level, bowlPos, binding.handle()),
                "Occultism sacrifice ritual did not bind to its real pentacle");
        var plan = adapter.planWithBinding(level, bowlPos, pattern, counters(inputs), binding.handle(), null);
        helper.assertTrue(plan != null && plan.targets().size() == 6,
                "Occultism sacrifice ritual did not plan four bowls, activation and proxy cost");
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(plan, null, retained::addAll);
        helper.assertTrue(result.success() && retained.isEmpty(),
                "Occultism sacrifice ritual did not consume its proxy spawn egg exactly once");
        assertRitualStarted(helper, bowlPos);
        try {
            var goldenBowl = level.getBlockEntity(bowlPos);
            helper.assertTrue(goldenBowl.getClass().getField("sacrificeProvided").getBoolean(goldenBowl),
                    "Occultism sacrifice proxy was not credited after ritual activation");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read Occultism's sacrifice gate", e);
        }
        helper.runAfterDelay(duration * 20 + 100, () -> {
            var expected = BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:bee_nest"))
                    .orElseThrow().value();
            var dropped = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(bowlPos).inflate(10)).stream().map(ItemEntity::getItem).toList();
            boolean active;
            boolean sacrificeProvided;
            int currentTime;
            int remaining;
            try {
                var goldenBowl = level.getBlockEntity(bowlPos);
                active = goldenBowl.getClass().getMethod("getCurrentRitualRecipe")
                        .invoke(goldenBowl) != null;
                sacrificeProvided = goldenBowl.getClass().getField("sacrificeProvided")
                        .getBoolean(goldenBowl);
                currentTime = goldenBowl.getClass().getField("currentTime").getInt(goldenBowl);
                var ingredients = (List<?>) goldenBowl.getClass().getField("remainingAdditionalIngredients")
                        .get(goldenBowl);
                remaining = ingredients == null ? -1 : ingredients.size();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Could not inspect Occultism's sacrifice ritual state", e);
            }
            helper.assertTrue(dropped.stream().anyMatch(stack -> stack.is(expected)),
                    "Occultism sacrifice proxy ritual did not produce its native bee nest"
                            + " (active=" + active + ", sacrificeProvided=" + sacrificeProvided
                            + ", currentTime=" + currentTime + ", remaining=" + remaining
                            + ", dropped=" + dropped + ")");
            helper.succeed();
        });
    }

    public static void ritualCollectsNativeOutput(GameTestHelper helper) {
        var level = helper.getLevel();
        var bowlPos = helper.absolutePos(new BlockPos(6, 2, 6));
        preparePentacle(helper, bowlPos);
        var sacrificialBowl = BuiltInRegistries.BLOCK.get(Identifier.parse("occultism:sacrificial_bowl"))
                .orElseThrow().value();
        level.setBlockAndUpdate(bowlPos.above(), sacrificialBowl.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        var inputs = inputs();
        var expected = item("occultism:fragile_soul_gem");
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                inputs, List.of(expected)), level);
        var adapter = new OccultismRitualAdapter();
        var binding = adapter.bind(level, bowlPos, pattern);
        helper.assertTrue(binding != null && adapter.canDispatch(level, bowlPos, binding.handle()),
                "Occultism output catcher invalidated the native pentacle");
        var plan = adapter.planWithBinding(level, bowlPos, pattern, counters(inputs), binding.handle(), null);
        helper.assertTrue(plan != null, "Occultism output catcher ritual had no dispatch plan");
        var retained = new ArrayList<GenericStack>();
        helper.assertTrue(DispatchExecutor.execute(plan, null, retained::addAll).success()
                        && retained.isEmpty(),
                "Occultism output catcher ritual did not dispatch without losing inputs");
        helper.runAfterDelay(600, () -> {
            var filter = new AllowedOutputFilter();
            filter.allowStrict(expected.what());
            var extracted = adapter.extractOutputs(level, bowlPos, filter, null);
            helper.assertTrue(extracted.size() == 1 && extracted.getFirst().what().equals(expected.what())
                            && extracted.getFirst().amount() == 1,
                    "PP could not extract the native Occultism result from its inverted bowl");
            helper.assertTrue(adapter.extractOutputs(level, bowlPos, filter, null).isEmpty(),
                    "PP extracted the same Occultism result twice");
            helper.succeed();
        });
    }

    private static int preparePentacle(GameTestHelper helper, BlockPos bowlPos) {
        return preparePentacle(helper, bowlPos, "craft_fragile_soul_gem", 3);
    }

    private static int preparePentacle(GameTestHelper helper, BlockPos bowlPos,
                                       String recipeName, int bowlCount) {
        var level = helper.getLevel();
        int duration;
        try {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(Identifier.parse("occultism:ritual"))
                    .orElseThrow().value();
            var recipes = RecipeCompat26.byType(level, type);
            var recipe = recipes.stream()
                    .filter(holder -> holder.id().identifier()
                            .equals(Identifier.parse("occultism:ritual/" + recipeName)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Native ritual absent: "
                            + recipes.stream().limit(12).map(holder -> holder.id().toString()).toList()
                            + " (count=" + recipes.size() + ")"));
            duration = ((Number) recipe.value().getClass().getMethod("getDuration")
                    .invoke(recipe.value())).intValue();
            Object pentacle = recipe.value().getClass().getMethod("getPentacle").invoke(recipe.value());
            Method place = pentacle.getClass().getMethod("place", net.minecraft.world.level.Level.class,
                    BlockPos.class, Rotation.class);
            place.invoke(pentacle, level, bowlPos, Rotation.NONE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("Could not place Occultism's native craft_foliot pentacle", e);
        }
        level.setBlockAndUpdate(bowlPos,
                BuiltInRegistries.BLOCK.get(Identifier.parse("occultism:golden_sacrificial_bowl"))
                        .orElseThrow().value().defaultBlockState());
        var bowlOffsets = List.of(new BlockPos(1, 1, 0), new BlockPos(0, 1, 1),
                new BlockPos(-1, 1, 0), new BlockPos(0, 1, -1));
        for (var offset : bowlOffsets.subList(0, bowlCount)) {
            level.setBlockAndUpdate(bowlPos.offset(offset),
                    BuiltInRegistries.BLOCK.get(Identifier.parse("occultism:sacrificial_bowl"))
                            .orElseThrow().value().defaultBlockState());
        }
        return duration;
    }

    private static List<GenericStack> inputs() {
        return List.of(
                item("occultism:book_of_binding_bound_foliot"),
                item("minecraft:iron_ingot"),
                item("minecraft:egg"),
                item("minecraft:glass"));
    }

    private static KeyCounter[] counters(List<GenericStack> inputs) {
        var counters = new KeyCounter[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            counters[i] = new KeyCounter();
            counters[i].add(inputs.get(i).what(), inputs.get(i).amount());
        }
        return counters;
    }

    private static void assertRitualStarted(GameTestHelper helper, BlockPos bowlPos) {
        var level = helper.getLevel();
        try {
            var goldenBowl = level.getBlockEntity(bowlPos);
            helper.assertTrue(goldenBowl != null
                            && goldenBowl.getClass().getMethod("getCurrentRitualRecipe").invoke(goldenBowl) != null,
                    "Occultism did not start the native ritual after activation insertion");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read Occultism's active ritual", e);
        }
    }

    private static void awaitNativeOutput(GameTestHelper helper, BlockPos bowlPos,
                                          int duration, int ingredientCount) {
        var level = helper.getLevel();
        helper.runAfterDelay(Math.max(600, duration * (ingredientCount + 1) + 100), () -> {
            var resultItem = BuiltInRegistries.ITEM.get(Identifier.parse("occultism:fragile_soul_gem"))
                    .orElseThrow().value();
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(bowlPos).inflate(6))
                            .stream().anyMatch(entity -> entity.getItem().is(resultItem)),
                    "Occultism ritual did not produce its native fragile soul gem after "
                            + duration + " recipe ticks");
            helper.succeed();
        });
    }

    private static GenericStack item(String id) {
        return new GenericStack(AEItemKey.of(BuiltInRegistries.ITEM.get(Identifier.parse(id))
                .orElseThrow().value()), 1);
    }
}
