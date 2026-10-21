package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.ArrayList;
import java.util.List;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import appeng.api.stacks.AEKey;
import net.minecraftforge.registries.IForgeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import com.moakiee.ae2lt.packaged.logic.multiblock.BlockCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp_discovery")
@PrefixGameTestTemplate(false)
/** Upstream discovery cases adapted to Forge gas capabilities and the 1.20.1 HCl recipe. */
public final class MekmmMachineDiscoveryGameTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 100)
    public static void naturalBoundingPortsAllDirections(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var main = helper.absolutePos(new BlockPos(6, 2, 6));
        var adapter = new MekanismMoreMachinesAdapter();
        int checks = 0;
        for (var spec : MekmmMachines.ALL) {
            for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                place(helper, main, BuiltInRegistries.BLOCK.get(spec.block()), facing);
                var machine = level.getBlockEntity(main);
                var label = spec.block() + " " + facing;
                helper.assertTrue(adapter.recognizesMain(level, main, machine), "controller recognized: " + label);
                int bounds = 0;
                for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) {
                    var pos = main.offset(x, y, z);
                    var be = level.getBlockEntity(pos);
                    if (MekReflection.isBoundingBlock(be)) {
                        bounds++;
                        helper.assertTrue(adapter.recognizesMain(level, pos, be), "natural bounding recognized: " + label + " " + pos);
                        helper.assertTrue(MekanismMoreMachinesAdapter.resolveMainPos(level, pos).equals(main), "natural bounding resolves controller: " + label);
                    }
                }
                helper.assertTrue(bounds > 0, "native placement generated bounding blocks: " + label);
                for (int m = 0; m < spec.modes().size(); m++) {
                    if (spec.modes().size() > 1) {
                        var field = machine.getClass().getDeclaredField("mode");
                        field.setAccessible(true);
                        field.setBoolean(machine, m == 1);
                    }
                    var mode = spec.activeMode(machine);
                    var ports = new ArrayList<>(mode.inputs());
                    ports.addAll(mode.outputs());
                    for (var port : ports) {
                        var p = port.spec();
                        var pos = main.relative(facing.getOpposite(), p.backSteps()).relative(facing.getClockWise(), p.leftSteps()).above(p.y());
                        var side = switch (p.access()) {
                            case FRONT -> facing;
                            case BACK -> facing.getOpposite();
                            case LEFT -> facing.getClockWise();
                            case RIGHT -> facing.getCounterClockWise();
                            case TOP -> Direction.UP;
                            case BOTTOM -> Direction.DOWN;
                        };
                        Object cap = switch (port.type()) {
                            case ITEM -> BlockCapabilities.find(level, pos, ForgeCapabilities.ITEM_HANDLER, side);
                            case FLUID -> BlockCapabilities.find(level, pos, ForgeCapabilities.FLUID_HANDLER, side);
                            case CHEMICAL -> MekReflection.getChemicalHandlers(level, pos, side);
                        };
                        helper.assertTrue(cap != null && (!(cap instanceof List<?> list) || !list.isEmpty()), "native port capability exposed: " + label + " mode=" + m + " port=" + port);
                        checks++;
                    }
                }
                level.setBlockAndUpdate(main, Blocks.AIR.defaultBlockState());
            }
        }
        System.out.println("PP_MEKMM_NATURAL_PLACEMENT_PASS machines=6 directions=4 ports=" + checks);
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 150)
    public static void normalProviderDispatchesThroughNaturalBounding(GameTestHelper helper) {
        providerDispatch(helper, false);
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 150)
    public static void wirelessProviderDispatchesThroughNaturalBounding(GameTestHelper helper) {
        providerDispatch(helper, true);
    }

    private static void providerDispatch(GameTestHelper helper, boolean wireless) {
        var level = helper.getLevel();
        var main = helper.absolutePos(new BlockPos(7, 2, 7));
        var block = BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("mekmm:large_chemical_infuser"));
        place(helper, main, block, Direction.NORTH);
        var providerPos = main.north(wireless ? 4 : 2);
        Block providerBlock = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(providerPos, providerBlock.defaultBlockState());
        level.setBlockAndUpdate(providerPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.MEKMM_ADAPTER.get()));
        if (wireless) helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), main.north(), Direction.NORTH), "wireless target is native bounding block");
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var hydrogen = new GenericStack(gasKey("hydrogen", 32), 32);
        var chlorine = new GenericStack(gasKey("chlorine", 32), 32);
        var hydrogenChloride = new GenericStack(gasKey("hydrogen_chloride", 32), 32);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(new GenericStack[] {chlorine, hydrogen}, new GenericStack[] {hydrogenChloride}));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(provider.getMainNode().isActive(), "provider is active");
            var recognition = logic.getMachineRecognition();
            helper.assertTrue(recognition.count() == 1, "natural bounding block recognizes one machine, wireless=" + wireless);
            helper.assertTrue(recognition.names().getString().equals(block.getName().getString()),
                    "recognition shows the controller name rather than the bounding block name");
            var menu = new PackagedPatternProviderMenu(0,
                    net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level).getInventory(), provider);
            menu.broadcastChanges();
            helper.assertTrue(menu.recognizedMachineCount == 1
                            && menu.recognizedMachineNames.equals(recognition.names()),
                    "server menu publishes machine recognition before any dispatch");
            var pattern = logic.getAvailablePatterns().get(0);
            var inputs = new KeyCounter[] { new KeyCounter(), new KeyCounter() };
            inputs[0].add(chlorine.what(), chlorine.amount());
            inputs[1].add(hydrogen.what(), hydrogen.amount());
            helper.assertTrue(logic.pushPattern(pattern, inputs), "provider dispatch succeeds through naturally placed machine bounding block, wireless=" + wireless);
            try {
                var machine = level.getBlockEntity(main);
                var left = (Object) machine.getClass().getField("leftTank").get(machine);
                var right = (Object) machine.getClass().getField("rightTank").get(machine);
                helper.assertTrue(stored(left) == 32 && stored(right) == 32, "each real tank receives its chemical, wireless=" + wireless);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            if (wireless) {
                helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), main, Direction.NORTH),
                        "controller and bounding block may both be linked");
                helper.assertTrue(logic.getMachineRecognition().count() == 1,
                        "multiple links to one MekMM controller do not inflate the machine count");
                provider.removeConnection(level.dimension(), main);
                provider.removeConnection(level.dimension(), main.north());
            } else {
                provider.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
                helper.assertTrue(logic.getMachineRecognition().count() == 1,
                        "machine recognition is separate from the installed core and recipe eligibility");
                level.setBlockAndUpdate(main, Blocks.AIR.defaultBlockState());
            }
            helper.assertTrue(logic.getMachineRecognition().count() == 0, "removed target clears recognition");
            for (int refresh = 0; refresh < 10; refresh++) menu.broadcastChanges();
            helper.assertTrue(menu.recognizedMachineCount == 0 && menu.recognizedMachineNames.getString().isEmpty(),
                    "open menu clears its green status after target removal");
            System.out.println("PP_MEKMM_PROVIDER_DISPATCH_PASS naturalBounding=true wireless=" + wireless);
            helper.succeed();
        });
    }

    private static AEKey gasKey(String id, long amount) {
        try {
            var registry = (IForgeRegistry<?>) Class.forName("mekanism.api.MekanismAPI").getMethod("gasRegistry").invoke(null);
            var gas = registry.getValue(new net.minecraft.resources.ResourceLocation("mekanism", id));
            var stack = Class.forName("mekanism.api.chemical.gas.GasStack")
                    .getConstructor(Class.forName("mekanism.api.providers.IGasProvider"), long.class).newInstance(gas, amount);
            return (AEKey) Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey")
                    .getMethod("of", Class.forName("mekanism.api.chemical.ChemicalStack")).invoke(null, stack);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static long stored(Object tank) throws ReflectiveOperationException {
        return ((Number) tank.getClass().getMethod("getStored").invoke(tank)).longValue();
    }

    private static void place(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        var state = block.defaultBlockState();
        for (var property : state.getProperties()) {
            if (property instanceof DirectionProperty direction && property.getName().equals("facing")) state = state.setValue(direction, facing);
        }
        helper.getLevel().setBlockAndUpdate(pos, state);
        block.setPlacedBy(helper.getLevel(), pos, state, null, new ItemStack(block));
    }
}
