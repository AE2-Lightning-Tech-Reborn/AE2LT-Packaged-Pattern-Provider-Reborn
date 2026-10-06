package com.moakiee.ae2lt.packaged.logic;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BatchCopies;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingOutputBuffer;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting;
import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import com.moakiee.thunderbolt.core.crafting.batch.DefaultBatchJobView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real target recipes through Thunderbolt's batch executor and wired/wireless providers. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class UnboundedWorkbenchGameTests {
    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_basic(GameTestHelper helper) {
        table(helper, "extendedcrafting:basic_table", Items.DIAMOND, true, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_basic_wireless(GameTestHelper helper) {
        table(helper, "extendedcrafting:basic_table", Items.DIAMOND, true, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_advanced(GameTestHelper helper) {
        table(helper, "extendedcrafting:advanced_table", Items.LAPIS_LAZULI, true, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_advanced_wireless(GameTestHelper helper) {
        table(helper, "extendedcrafting:advanced_table", Items.LAPIS_LAZULI, true, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_elite(GameTestHelper helper) {
        table(helper, "extendedcrafting:elite_table", Items.REDSTONE, true, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_elite_wireless(GameTestHelper helper) {
        table(helper, "extendedcrafting:elite_table", Items.REDSTONE, true, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_ultimate(GameTestHelper helper) {
        table(helper, "extendedcrafting:ultimate_table", Items.QUARTZ, true, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void ec_ultimate_wireless(GameTestHelper helper) {
        table(helper, "extendedcrafting:ultimate_table", Items.QUARTZ, true, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_sculk(GameTestHelper helper) {
        table(helper, "avaritia:sculk_crafting_table", Items.EMERALD, false, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_sculk_wireless(GameTestHelper helper) {
        table(helper, "avaritia:sculk_crafting_table", Items.EMERALD, false, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_nether(GameTestHelper helper) {
        table(helper, "avaritia:nether_crafting_table", Items.COAL, false, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_nether_wireless(GameTestHelper helper) {
        table(helper, "avaritia:nether_crafting_table", Items.COAL, false, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_end(GameTestHelper helper) {
        table(helper, "avaritia:end_crafting_table", Items.AMETHYST_SHARD, false, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_end_wireless(GameTestHelper helper) {
        table(helper, "avaritia:end_crafting_table", Items.AMETHYST_SHARD, false, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_extreme(GameTestHelper helper) {
        table(helper, "avaritia:extreme_crafting_table", Items.PRISMARINE_CRYSTALS, false, false, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void av_extreme_wireless(GameTestHelper helper) {
        table(helper, "avaritia:extreme_crafting_table", Items.PRISMARINE_CRYSTALS, false, true, 257);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void longBatch(GameTestHelper helper) {
        table(helper, "extendedcrafting:basic_table", Items.DIAMOND, true, false, 3_000_000_000L);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void smithing(GameTestHelper helper) {
        smithing(helper, false);
    }

    @GameTest(template = "workbench_test", timeoutTicks = 180)
    public static void smithingWireless(GameTestHelper helper) {
        smithing(helper, true);
    }

    private static void table(GameTestHelper helper, String block, Item output,
            boolean extendedCrafting, boolean wireless, long requested) {
        var adapter = extendedCrafting ? PPItems.EC_ULTIMATE_ADAPTER.toStack()
                : PPItems.AVARITIA_EXTREME_TABLE_ADAPTER.toStack();
        run(helper, block, adapter, List.of(stack(Items.MILK_BUCKET), stack(Items.IRON_INGOT)),
                output, wireless, requested, true);
    }

    private static void smithing(GameTestHelper helper, boolean wireless) {
        // Use Re-Avaritia's own five-input recipe, including its reusable template.
        run(helper, "avaritia:extreme_smithing_table", PPItems.AVARITIA_EXTREME_SMITHING_ADAPTER.toStack(),
                List.of(stack(item("avaritia:upgrade_smithing_template")), stack(Items.CLOCK),
                        stack(Items.ENCHANTED_GOLDEN_APPLE), stack(item("avaritia:enhancement_core")),
                        stack(item("avaritia:eternal_singularity"))),
                item("avaritia:infinity_clock"), wireless, 257, false);
    }

    private static void run(GameTestHelper helper, String block, ItemStack adapter,
            List<GenericStack> inputs, Item output, boolean wireless, long requested, boolean buckets) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var target = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block));
        helper.assertTrue(target != Blocks.AIR, "target registered: " + block);
        Block providerBlock = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get()
                : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(pos, providerBlock.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var targetPos = wireless ? pos.east(3) : pos.east();
        level.setBlockAndUpdate(targetPos, target.defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.getAdapterInv().setItemDirect(0, adapter);
        if (wireless) {
            helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), targetPos, Direction.WEST),
                    "wireless target bound");
        }
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        logic.getPatternInv().setItemDirect(0,
                PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(stack(output))));
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            var pattern = logic.getAvailablePatterns().getFirst();
            long capacity = logic.getBatchCapacity(pattern);
            helper.assertTrue(capacity == Long.MAX_VALUE, block + " expected uncapped capacity actual=" + capacity);
            var tasks = new HashMap<IPatternDetails, long[]>();
            var remaining = new long[] {requested};
            tasks.put(pattern, remaining);
            var waiting = new ListCraftingInventory(key -> {});
            var inventory = new ListCraftingInventory(key -> {});
            inputs.forEach(input -> inventory.insert(input.what(), requested * input.amount(), Actionable.MODULATE));
            var job = new DefaultBatchJobView(level, UUID.randomUUID(), tasks, waiting,
                    task -> ((long[]) task)[0], (task, count) -> ((long[]) task)[0] = count,
                    new Object(), (tracker, count, type) -> {});
            var grid = logic.getGrid();
            var result = BatchExecutor.runBatchOnly(1, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                    (CraftingService) grid.getCraftingService(), grid.getEnergyService(), job, inventory,
                    new HashMap<>(), provider::saveChanges);
            helper.assertTrue(result.dispatchedCopies() == requested && result.consumedCpuOps() == 1,
                    block + " one CPU op must batch the requested copies: " + result);
            helper.assertTrue(remaining[0] == 0 && inputs.stream().allMatch(input -> inventory.list.get(input.what()) == 0),
                    "exact remaining tasks/materials");
            helper.assertTrue(waiting.list.get(AEItemKey.of(output)) == requested, "expected exact outputs");
            assertOutputs(helper, logic, output, requested, buckets);
            helper.assertTrue(logic.getBatchCapacity(pattern) == Long.MAX_VALUE,
                    "batch does not exhaust same-tick capacity");
            var template = inputs.stream().map(input -> {
                var counter = new KeyCounter();
                counter.add(input.what(), input.amount());
                return counter;
            }).toArray(KeyCounter[]::new);
            for (int i = 0; i < 65; i++) {
                helper.assertTrue(logic.pushPattern(pattern, BatchCopies.copy(template)),
                        "ordinary dispatch crosses 64 in same tick");
            }
            assertOutputs(helper, logic, output, requested + 65, buckets);
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            helper.assertTrue(logic.getBatchCapacity(pattern) == 1, "blocking mode forces single path");
            helper.assertTrue(logic.pushBatch(pattern, template, 8) == 8, "blocking mode rejects batch");
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.NO);
            logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
            helper.assertTrue(logic.getBatchCapacity(pattern) <= 1 && logic.pushBatch(pattern, template, 8) == 8,
                    "lock mode rejects batch");
            var saved = new CompoundTag();
            logic.writeToNBT(saved, level.registryAccess());
            logic.clearContent();
            logic.readFromNBT(saved, level.registryAccess());
            assertOutputs(helper, logic, output, requested + 65, buckets);
            System.out.println("PP_UNBOUNDED_TABLE_PASS target=" + block + " wireless=" + wireless
                    + " copies=" + requested + " cpuOps=1 remaining=0 ordinaryExtra=65 reload=exact"
                    + " buckets=" + buckets + " blocking=single lock=single");
            helper.succeed();
        });
    }

    private static void assertOutputs(GameTestHelper helper, PackagedPatternProviderLogic logic,
            Item output, long expected, boolean buckets) {
        var pending = pending(logic);
        helper.assertTrue(amount(pending, output) == expected, "exact long outputs, including after NBT reload");
        if (buckets) {
            helper.assertTrue(amount(pending, Items.BUCKET) == expected, "exact bucket remainders");
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static GenericStack stack(Item item) {
        return new GenericStack(AEItemKey.of(item), 1);
    }

    private static List<GenericStack> pending(PackagedPatternProviderLogic logic) {
        try {
            var field = PackagedPatternProviderLogic.class.getDeclaredField("pendingOutputs");
            field.setAccessible(true);
            return ((PendingOutputBuffer) field.get(logic)).snapshot();
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static long amount(List<GenericStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.what().equals(AEItemKey.of(item)))
                .mapToLong(GenericStack::amount).sum();
    }
}
