package com.moakiee.ae2lt.packaged.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.BlockCapabilities;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.compat.BloodMagicAltarAdapter;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class BloodMagicAltarTests {
    private static final String HARVEST_STATE = "bloodmagic_altar:harvest";

    private BloodMagicAltarTests() {
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 1000)
    public static void poweredCpuOrdersAltarCraftAndRejectsMissingStone(GameTestHelper helper) {
        var level = helper.getLevel();
        var altarRelative = new BlockPos(4, 2, 4);
        var altarPos = helper.absolutePos(altarRelative);
        var altarBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("bloodmagic", "altar"));
        var slate = ForgeRegistries.ITEMS.getValue(new ResourceLocation("bloodmagic", "blankslate"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(altarBlock != null && slate != null, "Blood Magic CPU recipe resources missing");
        helper.setBlock(altarRelative, altarBlock.defaultBlockState());
        var altar = level.getBlockEntity(altarPos);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(altar != null, "Blood altar CPU fixture is missing its block entity");
        var fill = ReflectionSupport.findMethodCached(altar.getClass(), "fillMainTank", int.class).orElseThrow();
        var filled = ReflectionSupport.invokeMutation(fill, altar, 4000).orElse(null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(filled instanceof Integer && (Integer) filled >= 4000, "Blood altar CPU fixture could not fill LP");

        var providerRelative = altarRelative.south();
        helper.setBlock(providerRelative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(providerRelative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(providerRelative.east(2), AEBlocks.CHEST.block().defaultBlockState());
        helper.setBlock(providerRelative.east().above(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(providerRelative));
        var chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(providerRelative.east(2)));
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.BLOOD_MAGIC_ALTAR_ADAPTER.get()));
        provider.setReturnMode(ReturnMode.AUTO);

        var input = AEItemKey.of(Items.STONE);
        var output = AEItemKey.of(slate);
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(input, 1)},
                new GenericStack[]{new GenericStack(output, 1)}));
        provider.getLogic().updatePatterns();
        var fixture = new AltarCraftingFixture(provider, chest, input, output);
        helper.runAfterDelay(60, () -> {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.getMainNode().isActive(), "Blood altar CPU provider is not active");
            var grid = provider.getMainNode().getGrid();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid == chest.getMainNode().getGrid(), "Blood altar CPU storage is on another grid");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getCraftingService().getCpus().size() == 1, "Blood altar CPU fixture has no active CPU");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getCraftingService().isCraftable(output), "Blood altar pattern is not registered in ME");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getStorageService().getInventory().insert(input, 2, Actionable.MODULATE,
                    IActionSource.empty()) == 2, "Blood altar CPU fixture could not store stone");
            helper.runAfterDelay(5, () -> beginAltarCpuOrder(helper, fixture, 2, 0));
        });
    }

    private static void beginAltarCpuOrder(GameTestHelper helper, AltarCraftingFixture fixture,
                                           long amount, long completed) {
        var requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return fixture.provider().getMainNode().getNode();
            }
        };
        var service = fixture.provider().getMainNode().getGrid().getCraftingService();
        var future = service.beginCraftingCalculation(helper.getLevel(), requester, fixture.output(), amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        pollAltarPlan(helper, fixture, amount, completed, future, 100);
    }

    private static void pollAltarPlan(GameTestHelper helper, AltarCraftingFixture fixture, long amount,
                                      long completed, Future<ICraftingPlan> future, int remainingTicks) {
        if (!future.isDone()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(remainingTicks > 0, "Blood altar ME calculation timed out");
            helper.runAfterDelay(1, () -> pollAltarPlan(helper, fixture, amount, completed, future, remainingTicks - 1));
            return;
        }
        ICraftingPlan plan;
        try {
            plan = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Blood altar ME calculation interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Blood altar ME calculation failed", failed.getCause());
        }
        var service = fixture.provider().getMainNode().getGrid().getCraftingService();
        if (completed == 2) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.simulation() && plan.missingItems().get(fixture.input()) == 1,
                    "Blood altar calculation did not report depleted stone");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!service.submitJob(plan, null, null, false, IActionSource.empty()).successful(),
                    "Blood altar CPU accepted an order with missing stone");
            helper.runAfterDelay(40, () -> {
                assertAltarCpuResult(helper, fixture, 2);
                helper.succeed();
            });
            return;
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(),
                "Blood altar ME order has missing ingredients");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.finalOutput().what().equals(fixture.output()) && plan.finalOutput().amount() == amount
                && plan.usedItems().get(fixture.input()) == amount, "Blood altar order quantities are incorrect");
        var cpu = (CraftingCPUCluster) service.getCpus().iterator().next();
        var submitted = service.submitJob(plan, null, cpu, false, IActionSource.empty());
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(submitted.successful(), "Blood altar CPU rejected order: " + submitted.errorCode());
        pollAltarCpuOrder(helper, fixture, completed + amount, cpu, 800);
    }

    private static void pollAltarCpuOrder(GameTestHelper helper, AltarCraftingFixture fixture, long completed,
                                          CraftingCPUCluster cpu, int remainingTicks) {
        if (cpu.isBusy()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(remainingTicks > 0, "Blood altar CPU order timed out: "
                    + fixture.provider().saveWithoutMetadata() + " altar="
                    + fixture.provider().getLevel().getBlockEntity(fixture.provider().getBlockPos().north())
                    .saveWithoutMetadata());
            helper.runAfterDelay(1, () -> pollAltarCpuOrder(helper, fixture, completed, cpu, remainingTicks - 1));
            return;
        }
        helper.runAfterDelay(40, () -> {
            assertAltarCpuResult(helper, fixture, completed);
            var saved = fixture.provider().saveWithoutMetadata();
            fixture.provider().loadTag(saved);
            helper.runAfterDelay(40, () -> {
                assertAltarCpuResult(helper, fixture, completed);
                beginAltarCpuOrder(helper, fixture, 1, completed);
            });
        });
    }

    private static void assertAltarCpuResult(GameTestHelper helper, AltarCraftingFixture fixture, long completed) {
        var stored = fixture.provider().getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(stored.get(fixture.input()) == 2 - completed && stored.get(fixture.output()) == completed,
                "Blood altar CPU lost or duplicated stone or slate");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(stored.size() == (completed == 2 ? 1 : 2), "Blood altar ME storage has unexpected keys");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.provider().getMainNode().getGrid().getCraftingService().getCpus().stream()
                .noneMatch(cpu -> cpu.isBusy()), "Blood altar CPU remained busy after finishing order");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.provider().getLogic().getReturnInv().isEmpty(), "Blood altar output remains in return inventory");
        var altarHandler = BlockCapabilities.find(fixture.provider().getLevel(), fixture.provider().getBlockPos().north(),
                ForgeCapabilities.ITEM_HANDLER, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(altarHandler != null && altarHandler.getSlots() > 0 && altarHandler.getStackInSlot(0).isEmpty(),
                "Blood altar retained a completed input");
    }

    private record AltarCraftingFixture(PackagedPatternProviderBlockEntity provider, ChestBlockEntity chest,
                                        AEItemKey input, AEItemKey output) {}

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 600)
    public static void noBloodRollbackAndRepeatedCraft(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(2, 1, 2);
        var pos = helper.absolutePos(relative);
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("bloodmagic", "altar"));
        var slate = ForgeRegistries.ITEMS.getValue(new ResourceLocation("bloodmagic", "blankslate"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(block != null && slate != null, "Blood Magic altar or slate missing");
        helper.setBlock(relative, block.defaultBlockState());
        var entity = level.getBlockEntity(pos);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(entity != null, "Blood altar block entity missing");
        var adapter = new BloodMagicAltarAdapter();
        var input = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var output = new GenericStack(AEItemKey.of(slate), 1);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{input}, new GenericStack[]{output}), level);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(pattern != null, "Blood altar pattern did not decode");
        var binding = adapter.bind(level, pos, pattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Tier-one slate recipe did not bind");
        var reinforced = ForgeRegistries.ITEMS.getValue(new ResourceLocation("bloodmagic", "reinforcedslate"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(reinforced != null, "Reinforced slate missing");
        var tierTwoPattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{output}, new GenericStack[]{new GenericStack(AEItemKey.of(reinforced), 1)}), level);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(tierTwoPattern != null, "Tier-two slate pattern did not decode");
        var tierTwoBinding = adapter.bind(level, pos, tierTwoPattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(tierTwoBinding != null, "Tier-two slate recipe did not bind");
        var scope = new MemoryScope();
        var counter = new KeyCounter();
        counter.add(input.what(), 1);
        var tierTwoCounter = new KeyCounter();
        tierTwoCounter.add(output.what(), 1);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.planWithBinding(level, pos, tierTwoPattern, new KeyCounter[]{tierTwoCounter},
                tierTwoBinding.handle(), IActionSource.empty(), scope) == null,
                "Tier-one altar planned a tier-two slate recipe");
        var plan = adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter}, binding.handle(),
                IActionSource.empty(), scope);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Empty altar did not plan");
        var target = plan.targets().get(0);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(input, Actionable.MODULATE) == 1, "Altar rejected stone");
        boolean refused = false;
        try {
            plan.onCommit().run();
        } catch (IllegalStateException expected) {
            refused = true;
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(refused, "Altar without blood accepted a craft");
        var recovered = new java.util.ArrayList<GenericStack>();
        plan.onPartialCommit().recover(List.of(new AcceptedInsertion(target, input)), recovered::add);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(recovered.size() == 1 && recovered.get(0).what().equals(input.what())
                && recovered.get(0).amount() == 1, "Rejected craft did not refund exactly one stone");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(SlotHarvestOwnership.load(level, pos, scope, HARVEST_STATE) == null,
                "Rejected craft left output ownership");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, pos, binding.handle(), scope), "Rollback blocked retry");

        var fill = com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport.findMethodCached(
                entity.getClass(), "fillMainTank", int.class).orElseThrow();
        var filled = com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport.invokeMutation(
                fill, entity, 2000).orElse(null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(filled instanceof Integer && (Integer) filled >= 2000, "Altar could not receive blood");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(currentBlood(entity) >= 2000, "Altar lost blood immediately after filling: " + currentBlood(entity));
        var filter = new AllowedOutputFilter();
        filter.allowStrict(AEItemKey.of(slate));
        dispatch(helper, adapter, pos, pattern, counter, binding.handle(), scope, input);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(currentBlood(entity) >= 1000, "Altar lost blood when starting: " + currentBlood(entity));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!adapter.canDispatch(level, pos, binding.handle(), scope), "Active altar accepted a second job");
        helper.runAfterDelay(240, () -> {
            assertHarvest(helper, adapter, pos, scope, filter, output, "first");
            var refilled = ReflectionSupport.invokeMutation(fill, entity, 2000).orElse(null);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(refilled instanceof Integer && (Integer) refilled >= 1000,
                    "Second craft could not receive blood");
            dispatch(helper, adapter, pos, pattern, counter, binding.handle(), scope, input);
            helper.runAfterDelay(240, () -> {
                assertHarvest(helper, adapter, pos, scope, filter, output, "second");
                helper.succeed();
            });
        });
    }

    private static void dispatch(GameTestHelper helper, BloodMagicAltarAdapter adapter, BlockPos pos,
                                 appeng.api.crafting.IPatternDetails pattern, KeyCounter counter, Object handle,
                                 AdapterPersistentScope scope, GenericStack input) {
        var plan = adapter.planWithBinding(helper.getLevel(), pos, pattern, new KeyCounter[]{counter}, handle,
                IActionSource.empty(), scope);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Altar did not plan next craft");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.targets().get(0).customInserter().apply(input, Actionable.MODULATE) == 1,
                "Altar rejected next input");
        plan.onCommit().run();
    }

    private static void assertHarvest(GameTestHelper helper, BloodMagicAltarAdapter adapter, BlockPos pos,
                                      AdapterPersistentScope scope, AllowedOutputFilter filter, GenericStack expected,
                                      String round) {
        var outputs = adapter.extractOutputs(helper.getLevel(), pos, filter, IActionSource.empty(), scope);
        var entity = helper.getLevel().getBlockEntity(pos);
        var handler = BlockCapabilities.find(helper.getLevel(), pos, ForgeCapabilities.ITEM_HANDLER, null);
        var current = handler == null ? ItemStack.EMPTY : handler.getStackInSlot(0);
        var active = ReflectionSupport.findMethodCached(entity.getClass(), "isActive")
                .flatMap(method -> ReflectionSupport.invoke(method, entity)).orElse(null);
        var progress = ReflectionSupport.findMethodCached(entity.getClass(), "getProgress")
                .flatMap(method -> ReflectionSupport.invoke(method, entity)).orElse(null);
        var blood = ReflectionSupport.findMethodCached(entity.getClass(), "getCurrentBlood")
                .flatMap(method -> ReflectionSupport.invoke(method, entity)).orElse(null);
        var required = ReflectionSupport.findMethodCached(entity.getClass(), "getLiquidRequired")
                .flatMap(method -> ReflectionSupport.invoke(method, entity)).orElse(null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(outputs.size() == 1 && outputs.get(0).what().equals(expected.what())
                && outputs.get(0).amount() == 1, round + " slate was not harvested exactly once: slot=" + current
                        + " active=" + active + " progress=" + progress + " blood=" + blood
                        + " required=" + required + " claim="
                        + SlotHarvestOwnership.load(helper.getLevel(), pos, scope, HARVEST_STATE));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.extractOutputs(helper.getLevel(), pos, filter, IActionSource.empty(), scope).isEmpty(),
                "Altar harvested the same slate twice");
    }

    private static int currentBlood(Object entity) {
        return ReflectionSupport.findMethodCached(entity.getClass(), "getCurrentBlood")
                .flatMap(method -> ReflectionSupport.invoke(method, entity))
                .filter(Integer.class::isInstance).map(Integer.class::cast).orElse(-1);
    }

    private static final class MemoryScope implements AdapterPersistentScope {
        private final Map<String, String> states = new HashMap<>();

        @Override public void setFlag(BlockPos pos, String key) { states.put(key, "true"); }
        @Override public boolean hasFlag(BlockPos pos, String key) { return states.containsKey(key); }
        @Override public void clearFlag(BlockPos pos, String key) { states.remove(key); }
        @Override public void setState(BlockPos pos, String key, String value) { states.put(key, value); }
        @Override public String getState(BlockPos pos, String key) { return states.get(key); }
        @Override public void clearState(BlockPos pos, String key) { states.remove(key); }
    }
}
