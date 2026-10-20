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
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class MekmmMachineDiscoveryGameTests {
    @GameTest(template = "mek_routing_test", timeoutTicks = 100)
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
                    if (be instanceof mekanism.common.tile.TileEntityBoundingBlock) {
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
                            case ITEM -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                            case FLUID -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                            case CHEMICAL -> MekReflection.getChemicalHandler(level, pos, side);
                        };
                        helper.assertTrue(cap != null, "native port capability exposed: " + label + " mode=" + m + " port=" + port);
                        checks++;
                    }
                }
                level.setBlockAndUpdate(main, Blocks.AIR.defaultBlockState());
            }
        }
        System.out.println("PP_MEKMM_NATURAL_PLACEMENT_PASS machines=6 directions=4 ports=" + checks);
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 150)
    public static void normalProviderDispatchesThroughNaturalBounding(GameTestHelper helper) {
        providerDispatch(helper, false);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 150)
    public static void wirelessProviderDispatchesThroughNaturalBounding(GameTestHelper helper) {
        providerDispatch(helper, true);
    }

    private static void providerDispatch(GameTestHelper helper, boolean wireless) {
        var level = helper.getLevel();
        var main = helper.absolutePos(new BlockPos(7, 2, 7));
        var block = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("mekmm:large_chemical_infuser"));
        place(helper, main, block, Direction.NORTH);
        var providerPos = main.north(wireless ? 4 : 2);
        Block providerBlock = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(providerPos, providerBlock.defaultBlockState());
        level.setBlockAndUpdate(providerPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, PPItems.MEKMM_ADAPTER.toStack());
        if (wireless) helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), main.north(), Direction.NORTH), "wireless target is native bounding block");
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var oxygen = new GenericStack(MekanismKey.of(MekanismChemicals.OXYGEN.asStack(32)), 32);
        var chlorine = new GenericStack(MekanismKey.of(MekanismChemicals.CHLORINE.asStack(32)), 32);
        var acid = new GenericStack(MekanismKey.of(MekanismChemicals.SULFURIC_ACID.asStack(64)), 64);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(List.of(chlorine, oxygen), List.of(acid)));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(provider.getMainNode().isActive(), "provider is active");
            var recognition = logic.getMachineRecognition();
            helper.assertTrue(recognition.count() == 1, "natural bounding block recognizes one machine, wireless=" + wireless);
            helper.assertTrue(recognition.names().getString().equals(block.getName().getString()),
                    "recognition shows the controller name rather than the bounding block name");
            var menu = new PackagedPatternProviderMenu(0,
                    net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level).getInventory(), provider);
            menu.broadcastChanges();
            helper.assertTrue(menu.recognizedMachineCount == 1
                            && menu.recognizedMachineNames.equals(recognition.names()),
                    "server menu publishes machine recognition before any dispatch");
            var pattern = logic.getAvailablePatterns().getFirst();
            var inputs = new KeyCounter[] { new KeyCounter(), new KeyCounter() };
            inputs[0].add(chlorine.what(), chlorine.amount());
            inputs[1].add(oxygen.what(), oxygen.amount());
            helper.assertTrue(logic.pushPattern(pattern, inputs), "provider dispatch succeeds through naturally placed machine bounding block, wireless=" + wireless);
            try {
                var machine = level.getBlockEntity(main);
                var left = (mekanism.api.chemical.IChemicalTank) machine.getClass().getField("leftTank").get(machine);
                var right = (mekanism.api.chemical.IChemicalTank) machine.getClass().getField("rightTank").get(machine);
                helper.assertTrue(left.getStored() == 32 && right.getStored() == 32, "each real tank receives its chemical, wireless=" + wireless);
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

    private static void place(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        var state = block.defaultBlockState();
        for (var property : state.getProperties()) {
            if (property instanceof DirectionProperty direction && property.getName().equals("facing")) state = state.setValue(direction, facing);
        }
        helper.getLevel().setBlockAndUpdate(pos, state);
        block.setPlacedBy(helper.getLevel(), pos, state, null, new ItemStack(block));
    }
}
