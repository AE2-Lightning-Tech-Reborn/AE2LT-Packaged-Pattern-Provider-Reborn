package com.moakiee.ae2lt.packaged.gametest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekanismMoreMachinesAdapter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/** Uses actual bounding blocks, port capabilities and native MekMM recipe tickers. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class MekContinuousBackportTests {
    private MekContinuousBackportTests() {}

    private static BlockEntity machine(GameTestHelper h, String name) {
        var main = h.absolutePos(new BlockPos(7, 2, 7));
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mekmm", name));
        h.assertTrue(block != null, "Missing native MekMM controller");
        var state = block.defaultBlockState();
        h.getLevel().setBlockAndUpdate(main, state);
        block.setPlacedBy(h.getLevel(), main, state, null, new ItemStack(block));
        return h.getLevel().getBlockEntity(main);
    }

    private static PackagedPatternProviderBlockEntity scope(GameTestHelper h) {
        var relative = new BlockPos(2, 2, 2);
        h.setBlock(relative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        return (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
    }

    private static GenericStack gas(String id, long amount) throws ReflectiveOperationException {
        var registry = (IForgeRegistry<?>) Class.forName("mekanism.api.MekanismAPI").getMethod("gasRegistry").invoke(null);
        var gas = registry.getValue(new ResourceLocation("mekanism", id));
        var stack = Class.forName("mekanism.api.chemical.gas.GasStack")
                .getConstructor(Class.forName("mekanism.api.providers.IGasProvider"), long.class).newInstance(gas, amount);
        var key = (AEKey) Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey")
                .getMethod("of", Class.forName("mekanism.api.chemical.ChemicalStack")).invoke(null, stack);
        return new GenericStack(key, amount);
    }

    private static IPatternDetails pattern(GameTestHelper h, GenericStack input, List<GenericStack> outputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{input}, outputs.toArray(GenericStack[]::new)), h.getLevel());
    }

    private static KeyCounter[] counters(GenericStack input) {
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return new KeyCounter[]{counter};
    }

    private static boolean available() {
        return ModList.get().isLoaded("mekmm") && ModList.get().isLoaded("appmek");
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void queuedBatchRenewsOnlyRemainingOutputOwnership(GameTestHelper h) throws ReflectiveOperationException {
        h.assertTrue(available(), "MekMM and Applied Mekanistics are required");
        var tile = machine(h, "large_electrolytic_separator");
        var provider = scope(h);
        var adapter = new MekanismMoreMachinesAdapter();
        var remaining = List.of(gas("hydrogen", 30), gas("oxygen", 10));
        var key = "mekmm:harvest";
        com.moakiee.ae2lt.packaged.logic.multiblock.ExpectedStackHarvestOwnership.store(
                h.getLevel(), tile.getBlockPos(), provider, key,
                Math.max(0, h.getLevel().getGameTime() - 100), remaining);
        provider.setState(tile.getBlockPos(), "mekmm:mode@" + h.getLevel().dimension().location(),
                adapter.dispatchMode(h.getLevel(), tile.getBlockPos()).toString());
        h.assertTrue(adapter.refreshBatchOwnership(h.getLevel(), tile.getBlockPos(), provider),
                "Queued inputs could not renew their remaining output claim");
        var claim = com.moakiee.ae2lt.packaged.logic.multiblock.ExpectedStackHarvestOwnership.load(
                h.getLevel(), tile.getBlockPos(), provider, key);
        h.assertTrue(claim != null && claim.remaining().equals(remaining)
                && claim.createdTick() == h.getLevel().getGameTime(),
                "Renewal restored harvested amounts or kept an expired clock");
        com.moakiee.ae2lt.packaged.logic.multiblock.ExpectedStackHarvestOwnership.clear(
                h.getLevel(), tile.getBlockPos(), provider, key);
        h.assertTrue(!adapter.refreshBatchOwnership(h.getLevel(), tile.getBlockPos(), provider),
                "Deleted output ownership was recreated");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 160)
    public static void providerSends64PhysicalCopiesWithoutChangingBorrowedInputs(GameTestHelper h)
            throws ReflectiveOperationException {
        h.assertTrue(available(), "MekMM and Applied Mekanistics are required for physical batch testing");
        var tile = machine(h, "large_electrolytic_separator");
        var providerPos = tile.getBlockPos().north(2);
        h.getLevel().setBlockAndUpdate(providerPos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(providerPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.MEKMM_ADAPTER.get()));
        var input = new GenericStack(AEFluidKey.of(Fluids.WATER), 20);
        var outputs = List.of(gas("hydrogen", 20), gas("oxygen", 10));
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{input}, outputs.toArray(GenericStack[]::new)));
        provider.getLogic().updatePatterns();
        h.runAfterDelay(40, () -> {
            try {
                var logic = (PackagedPatternProviderLogic) provider.getLogic();
                var pattern = logic.getAvailablePatterns().get(0);
                var template = counters(input);
                h.assertTrue(logic.getBatchCapacity(pattern) == 64,
                        "Native input lane capacity=" + logic.getBatchCapacity(pattern));
                h.assertTrue(logic.pushBatch(pattern, template, 130) == 66, "Native input lane did not accept exactly 64 copies");
                h.assertTrue(template[0].get(input.what()) == 20, "Physical batch changed borrowed inputs");
                var fluid = tile.getClass().getField("fluidTank").get(tile);
                h.assertTrue(((Number) fluid.getClass().getMethod("getFluidAmount").invoke(fluid)).longValue() == 1280,
                        "Physical batch inserted the wrong fluid amount");
                provider.load(provider.saveWithFullMetadata());
                h.assertTrue(((Number) fluid.getClass().getMethod("getFluidAmount").invoke(fluid)).longValue() == 1280,
                        "Provider reload replayed completed batch inputs");
                h.succeed();
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void nativeRecipesRejectWrongOutputsAndArrangeReversedChemicalInputs(GameTestHelper h)
            throws ReflectiveOperationException {
        h.assertTrue(available(), "MekMM and Applied Mekanistics are required for native recipe testing");
        var tile = machine(h, "large_chemical_infuser");
        var hydrogen = gas("hydrogen", 1);
        var chlorine = gas("chlorine", 1);
        var output = gas("hydrogen_chloride", 1);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{chlorine, hydrogen}, new GenericStack[]{output}), h.getLevel());
        var adapter = new MekanismMoreMachinesAdapter();
        var binding = adapter.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.assertTrue(binding != null, "Reversed native chemical recipe inputs did not bind");
        var inputs = new KeyCounter[]{counters(chlorine)[0], counters(hydrogen)[0]};
        var plan = adapter.planBatchWithBinding(h.getLevel(), tile.getBlockPos(), pattern, inputs,
                binding.handle(), IActionSource.empty(), scope(h));
        h.assertTrue(plan != null && plan.targets().size() == 2 && plan.onCommit() == null,
                "Native two-port batch plan was rejected");
        h.assertTrue(plan.targets().get(0).stacks().equals(List.of(hydrogen))
                        && plan.targets().get(1).stacks().equals(List.of(chlorine)),
                "Native chemical inputs were routed in pattern order instead of recipe order");
        var wrong = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{chlorine, hydrogen}, new GenericStack[]{gas("oxygen", 1)}), h.getLevel());
        h.assertTrue(adapter.bind(h.getLevel(), tile.getBlockPos(), wrong) == null,
                "Native recipe binding accepted an unrelated declared output");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 800)
    public static void separatorAcceptsRunningOrderAndReturnsBothReceiptsExactlyOnce(GameTestHelper h)
            throws ReflectiveOperationException {
        if (!available()) { h.succeed(); return; }
        var tile = machine(h, "large_electrolytic_separator");
        var scope = scope(h);
        var input = new GenericStack(AEFluidKey.of(Fluids.WATER), 100);
        var outputs = List.of(gas("hydrogen", 100), gas("oxygen", 50));
        var pattern = pattern(h, input, outputs);
        var adapter = new MekanismMoreMachinesAdapter();
        var binding = adapter.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.assertTrue(binding != null, "Native water-electrolysis pattern did not bind");
        var energy = tile.getClass().getMethod("getEnergyContainer").invoke(tile);
        var energyApi = Class.forName("mekanism.api.energy.IEnergyContainer");
        Method fill = energyApi.getMethod("setEnergy", Class.forName("mekanism.api.math.FloatingLong"));
        var maximum = energyApi.getMethod("getMaxEnergy").invoke(energy);
        var fluid = tile.getClass().getField("fluidTank").get(tile);
        var left = tile.getClass().getField("leftTank").get(tile);
        var tankApi = Class.forName("mekanism.api.chemical.IChemicalTank");
        Method stored = tankApi.getMethod("getStored");
        var started = new boolean[1];
        var extended = new boolean[1];
        var collected = new KeyCounter();
        var filter = new AllowedOutputFilter();
        outputs.forEach(output -> filter.allowStrict(output.what()));
        var returnInv = new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {});
        h.runAfterDelay(790, () -> {
            try {
                h.fail("Continuous native state: started=" + started[0] + ", extended=" + extended[0]
                        + ", input=" + fluid.getClass().getMethod("getFluidAmount").invoke(fluid)
                        + ", left=" + stored.invoke(left) + ", collected=" + collected
                        + ", active=" + tile.getClass().getMethod("getActive").invoke(tile));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        });
        h.runAfterDelay(10, () -> {
            var plan = adapter.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(input),
                    binding.handle(), IActionSource.empty(), scope);
            h.assertTrue(plan != null && DispatchExecutor.execute(plan, IActionSource.empty(), returnInv).success(),
                    "First native water order failed through bounding-block capabilities");
            started[0] = true;
        });
        h.onEachTick(() -> {
            try {
                fill.invoke(energy, maximum);
                if (!started[0]) return;
                if (!extended[0] && ((Number) stored.invoke(left)).longValue() > 0) {
                    h.assertTrue((int) fluid.getClass().getMethod("getFluidAmount").invoke(fluid) > 0
                            && Boolean.TRUE.equals(tile.getClass().getMethod("getActive").invoke(tile)),
                            "Continuous fixture did not retain a running native recipe");
                    h.assertTrue(adapter.containsPatternInput(h.getLevel(), tile.getBlockPos(), Set.of(input.what())),
                            "Blocking inspection missed fluid in native input port");
                    var plan = adapter.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(input),
                            binding.handle(), IActionSource.empty(), scope);
                    h.assertTrue(plan != null && DispatchExecutor.execute(plan, IActionSource.empty(), returnInv).success(),
                            "Running controller with owned outputs rejected the next order");
                    scope.load(scope.saveWithFullMetadata());
                    extended[0] = true;
                }
                if (!extended[0]) return;
                adapter.extractOutputs(h.getLevel(), tile.getBlockPos(), filter, IActionSource.empty(), scope)
                        .forEach(stack -> collected.add(stack.what(), stack.amount()));
                for (var output : outputs) {
                    h.assertTrue(collected.get(output.what()) <= output.amount() * 2, "Continuous receipt duplicated outputs");
                    if (collected.get(output.what()) != output.amount() * 2) return;
                }
                h.assertTrue(adapter.extractOutputs(h.getLevel(), tile.getBlockPos(), filter, IActionSource.empty(), scope).isEmpty(),
                        "Completed receipt was harvested twice");
                h.succeed();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("MekMM native fixture API failed", failure);
            }
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 3000)
    public static void separatorMerges128RunningOrdersAcross16Reloads(GameTestHelper h)
            throws ReflectiveOperationException {
        if (!available()) { h.succeed(); return; }
        var tile = machine(h, "large_electrolytic_separator");
        var scope = scope(h);
        var input = new GenericStack(AEFluidKey.of(Fluids.WATER), 20);
        var outputs = List.of(gas("hydrogen", 20), gas("oxygen", 10));
        var pattern = pattern(h, input, outputs);
        var adapter = new MekanismMoreMachinesAdapter();
        var binding = adapter.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.assertTrue(binding != null, "Native stress electrolysis pattern did not bind");
        var energy = tile.getClass().getMethod("getEnergyContainer").invoke(tile);
        var energyApi = Class.forName("mekanism.api.energy.IEnergyContainer");
        var fill = energyApi.getMethod("setEnergy", Class.forName("mekanism.api.math.FloatingLong"));
        var maximum = energyApi.getMethod("getMaxEnergy").invoke(energy);
        var left = tile.getClass().getField("leftTank").get(tile);
        var stored = Class.forName("mekanism.api.chemical.IChemicalTank").getMethod("getStored");
        var collected = new KeyCounter();
        var filter = new AllowedOutputFilter();
        outputs.forEach(output -> filter.allowStrict(output.what()));
        var returnInv = new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {});
        int[] orders = {0};
        int[] reloads = {0};
        boolean[] extendedRunning = {false};
        long start = h.getLevel().getGameTime();
        h.runAfterDelay(2900, () -> h.fail("Native order stress timed out: orders=" + orders[0]
                + ", reloads=" + reloads[0] + ", collected=" + collected));
        h.onEachTick(() -> {
            try {
                fill.invoke(energy, maximum);
                if (h.getLevel().getGameTime() - start < 10) return;
                if (orders[0] < 128) {
                    boolean running = orders[0] > 0 && ((Number) stored.invoke(left)).longValue() > 0
                            && Boolean.TRUE.equals(tile.getClass().getMethod("getActive").invoke(tile));
                    var plan = adapter.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(input),
                            binding.handle(), IActionSource.empty(), scope);
                    h.assertTrue(plan != null && DispatchExecutor.execute(plan, IActionSource.empty(), returnInv).success(),
                            "Continuous stress order rejected at order " + orders[0]);
                    orders[0]++;
                    extendedRunning[0] |= running;
                    if (orders[0] % 8 == 0) {
                        scope.load(scope.saveWithFullMetadata());
                        reloads[0]++;
                    }
                    // Keep every earlier output unharvested while extending its receipt.
                    if (orders[0] < 128) return;
                    h.assertTrue(extendedRunning[0], "Stress fixture never appended to a running machine with outputs");
                }
                adapter.extractOutputs(h.getLevel(), tile.getBlockPos(), filter, IActionSource.empty(), scope)
                        .forEach(stack -> collected.add(stack.what(), stack.amount()));
                for (var output : outputs) {
                    long expected = output.amount() * 128;
                    h.assertTrue(collected.get(output.what()) <= expected, "Stress receipt duplicated native output");
                    if (collected.get(output.what()) != expected) return;
                }
                h.assertTrue(returnInv.isEmpty(), "Stress dispatch needed unexpected input compensation");
                h.assertTrue(adapter.extractOutputs(h.getLevel(), tile.getBlockPos(), filter, IActionSource.empty(), scope).isEmpty(),
                        "Stress receipts returned a completed order twice");
                org.slf4j.LoggerFactory.getLogger("ae2ltpp-stress").info(
                        "STRESS PASS native MekMM orders={} reloads={} hydrogen=2560 oxygen=1280 elapsedServerTicks={}",
                        orders[0], reloads[0], h.getLevel().getGameTime() - start);
                h.succeed();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("MekMM native stress fixture API failed", failure);
            }
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void rotaryModeChangeCannotExtendPreviousOrder(GameTestHelper h) throws ReflectiveOperationException {
        if (!available()) { h.succeed(); return; }
        var tile = machine(h, "large_rotary_condensentrator");
        var scope = scope(h);
        var mode = tile.getClass().getField("mode");
        mode.setBoolean(tile, true);
        var input = new GenericStack(AEFluidKey.of(Fluids.WATER), 1000);
        var pattern = pattern(h, input, List.of(gas("water_vapor", 1000)));
        var adapter = new MekanismMoreMachinesAdapter();
        var binding = adapter.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.assertTrue(binding != null, "Native decondensentrating mode did not bind");
        var plan = adapter.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(input),
                binding.handle(), IActionSource.empty(), scope);
        h.assertTrue(plan != null && DispatchExecutor.execute(plan, IActionSource.empty(),
                new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {})).success(), "Native rotary order failed");
        mode.setBoolean(tile, false);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), tile.getBlockPos(), binding.handle(), scope),
                "Mode switch silently extended the earlier receipt");
        h.succeed();
    }
}
