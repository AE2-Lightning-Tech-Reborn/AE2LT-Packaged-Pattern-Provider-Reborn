package com.moakiee.ae2lt.packaged.logic;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemHandlerHelper;

/** Native provider/CPU/capability tests with controlled acknowledgement failures. */
@GameTestHolder("ae2ltpp_batch")
@PrefixGameTestTemplate(false)
public final class PhysicalBatchGameTests {
    private static int sequence;
    private static final String CLAIM = "physical_test:harvest";
    private static GenericStack stack(net.minecraft.world.item.Item item, long n) { return new GenericStack(AEItemKey.of(item), n); }
    private static List<GenericStack> inputs() { return List.of(stack(Items.IRON_INGOT,1), stack(Items.GOLD_INGOT,2)); }
    private static KeyCounter[] template() {
        return inputs().stream().map(s -> {var c = new KeyCounter(); c.add(s.what(),s.amount()); return c;}).toArray(KeyCounter[]::new);
    }

    @GameTest(templateNamespace="ae2ltpp_batch", template="adapter_conn", timeoutTicks=160)
    public static void partialInputsPersistWithoutResettingHarvestAndDropOnce(GameTestHelper h) {
        fixture(h, f -> {
            f.adapter.ironLimit = 5;
            f.adapter.goldLimit = 0;
            h.assertTrue(f.logic.pushBatch(f.pattern(), template(), 20) == 0, "provider owns all 20 paid copies");
            h.assertTrue(f.count(Items.IRON_INGOT) == 5 && f.count(Items.GOLD_INGOT) == 0, "native barrel received partial first port only");
            h.assertTrue(f.logic.isBusy() && !f.logic.pushPattern(f.pattern(),template()), "pending physical inputs block fresh dispatch");
            var claim = ExpectedStackHarvestOwnership.load(f.level, f.adapter.main, f.be, CLAIM);
            h.assertTrue(claim != null && claim.remaining().get(0).amount() == 20, "scaled harvest is owned before first input");
            // Simulate three outputs already harvested before saving the partial input queue.
            ExpectedStackHarvestOwnership.store(f.level, f.adapter.main, f.be, CLAIM, List.of(stack(Items.DIAMOND,17)));
            var saved = f.be.saveWithFullMetadata();
            f.be.load(saved);
            h.assertTrue(f.adapter.initializations == 1, "loading does not initialize a new harvest");
            f.adapter.ironLimit = Long.MAX_VALUE;
            f.level.setBlockAndUpdate(f.adapter.iron, Blocks.STONE.defaultBlockState());
            f.logic.tickAutoReturn();
            h.assertTrue(f.logic.isBusy(), "a replaced input port cannot absorb paid inputs");
            f.level.setBlockAndUpdate(f.adapter.iron, Blocks.BARREL.defaultBlockState());
            h.runAfterDelay(1, () -> {
                f.logic.tickAutoReturn();
                h.assertTrue(f.count(Items.IRON_INGOT) == 15, "only the undelivered 15 iron resume after reload and port restoration");
                var restored = ExpectedStackHarvestOwnership.load(f.level, f.adapter.main, f.be, CLAIM);
                h.assertTrue(f.adapter.initializations == 1 && restored.remaining().get(0).amount() == 17,
                        "partial harvest attribution is preserved, never reset to 20");
                f.logic.clearContent();
                var drops = new ArrayList<ItemStack>();
                f.be.addAdditionalDrops(f.level, f.pos, drops);
                h.assertTrue(count(drops,Items.GOLD_INGOT) == 40 && count(drops,Items.IRON_INGOT) == 0,
                        "breaking refunds exactly the known undelivered inputs after clearContent");
                var again = new ArrayList<ItemStack>();
                f.be.addAdditionalDrops(f.level, f.pos, again);
                h.assertTrue(count(again,Items.GOLD_INGOT) == 0, "paid inputs cannot drop twice");
                h.succeed();
            });
        });
    }

    @GameTest(templateNamespace="ae2ltpp_batch", template="adapter_conn", timeoutTicks=160)
    public static void cpuDoesNotRefundUnknownPhysicalInsertion(GameTestHelper h) {
        fixture(h, f -> {
            f.adapter.ironLimit = 5; f.adapter.goldLimit = 0; f.adapter.throwAfterIron = true;
            long[] remaining = {70};
            var tasks = new java.util.HashMap<IPatternDetails,long[]>(); tasks.put(f.pattern(), remaining);
            var waiting = new appeng.crafting.inv.ListCraftingInventory(key -> {});
            var inventory = new appeng.crafting.inv.ListCraftingInventory(key -> {});
            inventory.insert(AEItemKey.of(Items.IRON_INGOT),70,Actionable.MODULATE);
            inventory.insert(AEItemKey.of(Items.GOLD_INGOT),140,Actionable.MODULATE);
            var job = new com.moakiee.thunderbolt.core.crafting.batch.DefaultBatchJobView(f.level,
                    java.util.UUID.randomUUID(),tasks,waiting,t -> ((long[])t)[0],(t,n) -> ((long[])t)[0]=n,
                    new Object(),(tracker,n,type) -> {});
            var grid = f.be.getMainNode().getGrid();
            var result = com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor.runBatchOnly(1,
                    com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                    (appeng.me.service.CraftingService)grid.getCraftingService(),grid.getEnergyService(),job,inventory,
                    new java.util.HashMap<>(),f.be::saveChanges);
            h.assertTrue(result.dispatchedCopies() == 64 && remaining[0] == 6, "CPU accepts exactly 64 copies even when a port throws after mutation");
            h.assertTrue(inventory.list.get(AEItemKey.of(Items.IRON_INGOT)) == 6
                    && inventory.list.get(AEItemKey.of(Items.GOLD_INGOT)) == 12
                    && waiting.list.get(AEItemKey.of(Items.DIAMOND)) == 64, "CPU material and waiting totals never refund accepted inputs");
            h.assertTrue(f.count(Items.IRON_INGOT) == 5, "throwing handler really inserted five iron before the error");
            var saved = f.be.saveWithFullMetadata(); f.be.load(saved);
            f.adapter.throwAfterIron = false; f.adapter.ironLimit = Long.MAX_VALUE;
            h.runAfterDelay(1, () -> {
                f.logic.tickAutoReturn();
                h.assertTrue(f.count(Items.IRON_INGOT) == 5 && f.logic.isBusy(), "unknown current transfer is quarantined across reload, not replayed");
                var known = f.logic.drainPendingBatchInputs();
                h.assertTrue(known.size() == 1 && known.get(0).what().equals(AEItemKey.of(Items.GOLD_INGOT))
                        && known.get(0).amount() == 128, "only unvisited known inputs may be recovered");
                h.assertTrue(f.logic.drainPendingBatchInputs().isEmpty(), "second recovery is empty");
                h.succeed();
            });
        });
    }

    @GameTest(templateNamespace="ae2ltpp_batch", template="adapter_conn", timeoutTicks=160)
    public static void completePhysicalBatchHonorsLaneLimit(GameTestHelper h) {
        fixture(h, f -> {
            h.assertTrue(f.logic.getBatchCapacity(f.pattern()) == 64, "physical adapter advertises a bounded batch");
            h.assertTrue(f.logic.pushBatch(f.pattern(),template(),70) == 6, "physical batch accepts 64 copies");
            h.assertTrue(f.count(Items.IRON_INGOT) == 64 && f.count(Items.GOLD_INGOT) == 128,
                    "both native capabilities receive exactly the scaled inputs");
            h.assertTrue(!f.logic.isBusy() && f.logic.getBatchCapacity(f.pattern()) == 0,
                    "acknowledged queue empties without refreshing this tick's lane budget");
            h.assertTrue(f.adapter.initializations == 1, "harvest initialized once for 64 copies");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="ae2ltpp_batch", template="adapter_conn")
    public static void receiptsAndAliasedPortsAreConservative(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        var adapter = new TestAdapter(level,pos,AdapterPersistentScope.NOOP);
        var target = new TargetSlot(level,pos,Direction.NORTH,List.of(stack(Items.IRON_INGOT,1)),InsertionStrategy.CUSTOM);
        var alias = new TargetSlot(level,pos,Direction.SOUTH,List.of(stack(Items.IRON_INGOT,1)),InsertionStrategy.CUSTOM);
        h.assertTrue(PendingBatchInputs.prepare(new DispatchPlan(List.of(target,alias),null),adapter).isEmpty(),
                "two faces of one inventory cannot inflate simulation capacity");
        var deliveries = PendingBatchInputs.prepare(new DispatchPlan(List.of(target),null),adapter);
        for (long receipt : new long[]{-1,2}) {
            var queue = new PendingBatchInputs();
            queue.adopt(deliveries,new PendingBatchInputs.Ownership(adapter.id,level.dimension().location(),pos,
                    BuiltInRegistries.BLOCK.getKey(Blocks.BARREL),List.of(stack(Items.DIAMOND,1))));
            queue.markInitialized();
            boolean rejected=false;
            try {queue.deliver(d -> receipt);} catch (IllegalStateException expected) {rejected=true;}
            h.assertTrue(rejected && queue.snapshot().isEmpty() && queue.uncertainSnapshot().size()==1,
                    "illegal receipt is quarantined, not converted into a retry");
            var restored = new PendingBatchInputs(); restored.readFromNBT(queue.writeToNBT());
            final int[] calls={0}; restored.deliver(d -> {calls[0]++; return 0;});
            h.assertTrue(calls[0]==0 && restored.drainKnownInputs().isEmpty(), "unknown receipt survives NBT with no replay/refund");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="ae2ltpp_batch", template="adapter_conn", timeoutTicks=160)
    public static void restoredInputsUseTheirSavedDimensionAndPreserveMissingTargets(GameTestHelper h) {
        fixture(h, f -> {
            var remote=f.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            var main=new BlockPos(f.pos.getX(),80,f.pos.getZ());
            remote.getChunkAt(main);
            remote.getChunkAt(main.north()); remote.getChunkAt(main.south());
            remote.setBlockAndUpdate(main,Blocks.DIAMOND_BLOCK.defaultBlockState());
            remote.setBlockAndUpdate(main.north(),Blocks.AIR.defaultBlockState());
            remote.setBlockAndUpdate(main.south(),Blocks.AIR.defaultBlockState());
            remote.setBlockAndUpdate(main.north(),Blocks.BARREL.defaultBlockState());
            remote.setBlockAndUpdate(main.south(),Blocks.BARREL.defaultBlockState());
            var adapter=new TestAdapter(remote,main,f.be);
            MultiblockAdapterRegistry.register(new AdapterRegistration(adapter.id,-1000,() -> true,adapter));
            var plan=adapter.planWithBinding(remote,main,f.pattern(),template(),adapter,new appeng.me.helpers.MachineSource(f.be));
            var queue=new PendingBatchInputs();
            queue.adopt(PendingBatchInputs.scale(PendingBatchInputs.prepare(plan,adapter),8),
                    new PendingBatchInputs.Ownership(adapter.id,remote.dimension().location(),main,
                            BuiltInRegistries.BLOCK.getKey(Blocks.DIAMOND_BLOCK),List.of(stack(Items.DIAMOND,8))));
            var saved=new net.minecraft.nbt.CompoundTag(); f.logic.writeToNBT(saved);
            saved.put("batch_inputs",queue.writeToNBT()); f.logic.readFromNBT(saved);
            f.logic.tickAutoReturn();
            var remoteFixture=new Fixture(remote,main,f.be,f.logic,adapter);
            h.assertTrue(!f.logic.isBusy()&&remoteFixture.count(Items.IRON_INGOT)==8
                    &&remoteFixture.count(Items.GOLD_INGOT)==16&&f.count(Items.IRON_INGOT)==0,
                    "restored deliveries resolve saved dimension, never the provider's matching coordinates");
            queue.clear();
            var missing=new ResourceLocation("ae2ltpp_batch:missing_dimension");
            var delivery=new PendingBatchInputs.Delivery(adapter.id,missing,main,Direction.UP,
                    BuiltInRegistries.BLOCK.getKey(Blocks.BARREL),stack(Items.IRON_INGOT,7));
            queue.adopt(List.of(delivery),new PendingBatchInputs.Ownership(adapter.id,missing,main,
                    BuiltInRegistries.BLOCK.getKey(Blocks.DIAMOND_BLOCK),List.of(stack(Items.DIAMOND,7))));
            saved.put("batch_inputs",queue.writeToNBT()); f.logic.readFromNBT(saved);f.logic.tickAutoReturn();
            h.assertTrue(f.logic.isBusy()&&f.logic.drainPendingBatchInputs().get(0).amount()==7,
                    "unavailable dimensions preserve paid inputs for recovery");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="ae2ltpp_batch", template="adapter_conn", timeoutTicks=160)
    public static void retiredHarvestStopsQueuedInputsWithoutInventingOwnership(GameTestHelper h) {
        fixture(h, f -> {
            f.adapter.ironLimit=5; f.adapter.goldLimit=0;
            h.assertTrue(f.logic.pushBatch(f.pattern(),template(),20)==0,"batch adopted");
            f.be.clearFlagsForTarget(f.adapter.main);
            f.adapter.ironLimit=Long.MAX_VALUE; f.adapter.goldLimit=Long.MAX_VALUE;
            h.runAfterDelay(1,() -> {
                f.logic.tickAutoReturn();
                h.assertTrue(f.count(Items.IRON_INGOT)==5&&f.count(Items.GOLD_INGOT)==0&&f.logic.isBusy(),
                        "a removed output claim cannot silently resume physical input delivery");
                var known=f.logic.drainPendingBatchInputs();
                h.assertTrue(known.stream().mapToLong(GenericStack::amount).sum()==55,
                        "15 iron and 40 gold stay recoverable after retiring the target");
                h.succeed();
            });
        });
    }

    private record Fixture(ServerLevel level, BlockPos pos, PackagedPatternProviderBlockEntity be,
                           PackagedPatternProviderLogic logic, TestAdapter adapter) {
        IPatternDetails pattern() { return logic.getAvailablePatterns().get(0); }
        long count(net.minecraft.world.item.Item item) {
            long total=0;
            for (var p : List.of(adapter.iron,adapter.gold)) {
                var block = level.getBlockEntity(p);
                if(block==null) continue;
                var handler=block.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(() -> new AssertionError("missing barrel capability"));
                for(int i=0;i<handler.getSlots();i++) if(handler.getStackInSlot(i).is(item)) total+=handler.getStackInSlot(i).getCount();
            }
            return total;
        }
    }
    private static void fixture(GameTestHelper h, java.util.function.Consumer<Fixture> action) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.west(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos.east().north(),Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(pos.east().south(),Blocks.BARREL.defaultBlockState());
        var be=(PackagedPatternProviderBlockEntity)level.getBlockEntity(pos);
        be.setReturnMode(ReturnMode.OFF);
        var logic=(PackagedPatternProviderLogic)be.getLogic();
        var adapter=new TestAdapter(level,pos.east(),be);
        MultiblockAdapterRegistry.register(new AdapterRegistration(adapter.id,-1000,() -> true,adapter));
        logic.onNeighborChanged();
        logic.getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(
                inputs().toArray(GenericStack[]::new),new GenericStack[]{stack(Items.DIAMOND,1)}));
        h.runAfterDelay(50,() -> {logic.updatePatterns(); action.accept(new Fixture(level,pos,be,logic,adapter));});
    }
    private static long count(List<ItemStack> stacks,net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    private static final class TestAdapter implements InputBatchCraftingAdapter {
        final ResourceLocation id=new ResourceLocation("ae2ltpp_batch","physical_"+(sequence++));
        final ServerLevel level; final BlockPos main,iron,gold; final AdapterPersistentScope scope;
        long ironLimit=Long.MAX_VALUE,goldLimit=Long.MAX_VALUE;
        boolean throwAfterIron; int initializations;
        TestAdapter(ServerLevel level,BlockPos main,AdapterPersistentScope scope) {
            this.level=level;this.main=main;this.iron=main.north();this.gold=main.south();this.scope=scope;
        }
        public int priority(){return -1000;}
        public ResourceLocation batchAdapterId(){return id;}
        public boolean recognizesMain(ServerLevel l,BlockPos p,BlockEntity be){return l==level&&p.equals(main)&&l.getBlockState(p).is(Blocks.DIAMOND_BLOCK);}
        public BindingResult bind(ServerLevel l,BlockPos p,IPatternDetails pattern){return new BindingResult(this,BindingMode.REAL);}
        public boolean canDispatch(ServerLevel l,BlockPos p,Object handle){return true;}
        public boolean canDispatch(ServerLevel l,BlockPos p,Object handle,AdapterPersistentScope s){return ExpectedStackHarvestOwnership.load(l,p,s,CLAIM)==null;}
        public DispatchPlan planWithBinding(ServerLevel l,BlockPos p,IPatternDetails pattern,KeyCounter[] input,Object handle,IActionSource source){
            return new DispatchPlan(List.of(
                    new TargetSlot(l,iron,Direction.UP,List.of(stack(Items.IRON_INGOT,1)),InsertionStrategy.CUSTOM),
                    new TargetSlot(l,gold,Direction.UP,List.of(stack(Items.GOLD_INGOT,2)),InsertionStrategy.CUSTOM)),
                    () -> {throw new AssertionError("single-craft callback must be replaced by batch ownership");});
        }
        public List<GenericStack> extractOutputs(ServerLevel l,BlockPos p,AllowedOutputFilter f,IActionSource s){return List.of();}
        public boolean refreshBatchOwnership(ServerLevel l,BlockPos p,AdapterPersistentScope s){
            return ExpectedStackHarvestOwnership.load(l,p,s,CLAIM)!=null;
        }
        public boolean initializeBatchOwnership(ServerLevel l,BlockPos p,List<GenericStack> outputs,AdapterPersistentScope s){
            initializations++; ExpectedStackHarvestOwnership.store(l,p,s,CLAIM,outputs);return true;
        }
        public long insertBatchInput(ServerLevel l,BlockPos p,Direction face,GenericStack stack,Actionable mode,IActionSource source){
            boolean simulate=mode==Actionable.SIMULATE;
            if(!simulate && ExpectedStackHarvestOwnership.load(level,main,scope,CLAIM)==null) throw new AssertionError("input arrived before harvest ownership");
            long requested=stack.amount();
            long limit=simulate?requested:Math.min(requested,p.equals(iron)?ironLimit:goldLimit);
            var handler=l.getBlockEntity(p).getCapability(ForgeCapabilities.ITEM_HANDLER,face).orElseThrow(() -> new AssertionError("missing native capability"));
            var left=ItemHandlerHelper.insertItemStacked(handler,((AEItemKey)stack.what()).toStack((int)limit),simulate);
            long inserted=limit-left.getCount();
            if(!simulate&&p.equals(iron)&&throwAfterIron) throw new IllegalStateException("injected error after native insertion");
            return inserted;
        }
    }
}
