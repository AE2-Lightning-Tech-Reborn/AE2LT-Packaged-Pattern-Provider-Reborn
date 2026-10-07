package com.moakiee.ae2lt.packaged.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Strict fixtures: missing blocks, recipes, capabilities and adapters always fail. */
final class CompatTestSupport {
    static final BlockPos CENTER = new BlockPos(5, 2, 5);

    static ResourceLocation id(String name) { return ResourceLocation.parse(name); }

    static GenericStack item(String name, long count) {
        var item = BuiltInRegistries.ITEM.getOptional(id(name)).orElseThrow(() ->
                new AssertionError("Missing test item: " + name));
        if (item == Items.AIR) throw new AssertionError("Air is not a test item: " + name);
        return new GenericStack(AEItemKey.of(item), count);
    }

    static BlockEntity place(GameTestHelper h, BlockPos pos, String name) {
        var block = BuiltInRegistries.BLOCK.getOptional(id(name)).orElseThrow(() ->
                new AssertionError("Missing test block: " + name));
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return h.getLevel().getBlockEntity(pos);
    }

    static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, GenericStack output) {
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output));
        var pattern = PatternDetailsHelper.decodePattern(encoded, h.getLevel());
        h.assertTrue(pattern != null, "AE2 decodes the processing pattern");
        return pattern;
    }

    static KeyCounter[] counters(List<GenericStack> inputs) {
        var counter = new KeyCounter();
        inputs.forEach(s -> counter.add(s.what(), s.amount()));
        return new KeyCounter[]{counter};
    }

    static BindingResult bind(GameTestHelper h, BlockPos pos, MultiblockAdapter adapter, IPatternDetails pattern) {
        var registered = MultiblockAdapterRegistry.find(h.getLevel(), pos, h.getLevel().getBlockEntity(pos));
        h.assertTrue(registered != null && registered.getClass() == adapter.getClass(),
                "production registry recognizes " + adapter.getClass().getSimpleName());
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "real recipe binds at " + pos);
        return binding;
    }

    static void dispatch(GameTestHelper h, BlockPos pos, MultiblockAdapter adapter,
                         IPatternDetails pattern, BindingResult binding, List<GenericStack> inputs) {
        h.assertTrue(adapter.canDispatch(h.getLevel(), pos, binding.handle()), "native machine is ready");
        var wrong = adapter.planWithBinding(h.getLevel(), pos, pattern,
                counters(List.of(item("minecraft:bedrock", 1))), binding.handle(), null);
        h.assertTrue(wrong == null, "wrong ingredients cannot generate a dispatch plan");
        var supplied = counters(inputs);
        var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, supplied, binding.handle(), null);
        h.assertTrue(plan != null, "valid ingredients generate a dispatch plan");
        var retained = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, null, retained::addAll).success() && retained.isEmpty(),
                "every input is accepted by real native inventories");
        var filter = new AllowedOutputFilter();
        pattern.getOutputs().forEach(s -> filter.allowStrict(s.what()));
        h.assertTrue(adapter.extractOutputs(h.getLevel(), pos, filter, null).isEmpty(),
                "before the first native tick, neither ingredients nor catalysts may be harvested");
        var expected = counters(inputs)[0];
        for (var entry : expected) h.assertTrue(supplied[0].get(entry.getKey()) == entry.getLongValue(),
                "the borrowed input template is unchanged");
    }

    static IEnergyStorage energy(GameTestHelper h, BlockPos pos) {
        for (Direction face : Direction.values()) {
            var energy = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
            if (energy != null) return energy;
        }
        var energy = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energy == null) throw new AssertionError("Missing native FE capability at " + pos);
        return energy;
    }

    static void charge(GameTestHelper h, BlockPos pos) {
        var energy = energy(h, pos);
        for (int i = 0; i < 100 && energy.receiveEnergy(Integer.MAX_VALUE, false) > 0; i++) { }
    }

    static void awaitOutput(GameTestHelper h, BlockPos pos, MultiblockAdapter adapter,
                            GenericStack expected) {
        awaitOutput(h, pos, adapter, expected, received -> { });
    }

    static void awaitOutput(GameTestHelper h, BlockPos pos, MultiblockAdapter adapter,
                            GenericStack expected, java.util.function.Consumer<KeyCounter> verify) {
        var filter = new AllowedOutputFilter();
        filter.allowStrict(expected.what());
        var received = new KeyCounter();
        h.succeedWhen(() -> {
            adapter.extractOutputs(h.getLevel(), pos, filter, null)
                    .forEach(s -> received.add(s.what(), s.amount()));
            h.assertTrue(received.get(expected.what()) == expected.amount(),
                    "native ticker produces exactly " + expected + "; received=" + received.get(expected.what()));
            verify.accept(received);
            h.assertTrue(adapter.extractOutputs(h.getLevel(), pos, filter, null).isEmpty(),
                    "a second extraction cannot duplicate the native output");
            System.out.println("PP_COMPAT_NATIVE_PASS adapter=" + adapter.getClass().getSimpleName()
                    + " output=" + expected + " tick=" + h.getTick());
        });
    }
}
