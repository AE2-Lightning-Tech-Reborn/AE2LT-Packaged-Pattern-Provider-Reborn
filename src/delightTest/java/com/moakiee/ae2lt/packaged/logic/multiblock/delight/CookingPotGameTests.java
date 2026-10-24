package com.moakiee.ae2lt.packaged.logic.multiblock.delight;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class CookingPotGameTests {
    private static final CookingPotAdapter ADAPTER = new CookingPotAdapter();
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final AllowedOutputFilter FILTER = new AllowedOutputFilter();
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static boolean absent(GameTestHelper h, String mod) {
        if (ModList.get().isLoaded(mod)) return false;
        h.succeed();
        return true;
    }
    private static GenericStack item(Item item, long amount) { return new GenericStack(AEItemKey.of(item), amount); }
    private static List<GenericStack> ordinaryInputs() { return List.of(item(Items.CARROT, 6), item(Items.BOWL, 2)); }
    private static List<GenericStack> ordinaryOutputs() { return List.of(item(Items.GOLDEN_CARROT, 2)); }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, List<GenericStack> outputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs), h.getLevel());
    }
    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var amounts = new KeyCounter();
        stacks.forEach(stack -> amounts.add(stack.what(), stack.amount()));
        return new KeyCounter[]{amounts};
    }
    private static CookingPotAccess.Pot pot(GameTestHelper h, CookingPotAccess.Kind kind) {
        return pot(h, kind, h.absolutePos(new BlockPos(3, 3, 3)));
    }
    private static CookingPotAccess.Pot pot(GameTestHelper h, CookingPotAccess.Kind kind, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(kind.blockId).defaultBlockState());
        var pot = CookingPotAccess.pot(h.getLevel(), pos);
        h.assertTrue(pot != null && pot.heated(), "native heated pot and expected inventory");
        return pot;
    }
    private static void tick(GameTestHelper h, CookingPotAccess.Pot pot) throws Exception {
        var entity = pot.entity();
        entity.getClass().getMethod("cookingTick", Level.class, BlockPos.class, BlockState.class, entity.getClass())
                .invoke(null, h.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity);
    }
    private static List<GenericStack> finish(GameTestHelper h, CookingPotAccess.Pot pot, int ticks) throws Exception {
        var outputs = new ArrayList<GenericStack>();
        for (int i = 0; i < ticks; i++) {
            tick(h, pot);
            outputs.addAll(ADAPTER.extractOutputs(h.getLevel(), pot.entity().getBlockPos(), FILTER, SOURCE));
        }
        return outputs;
    }
    private static Object dispatch(GameTestHelper h, CookingPotAccess.Pot pot, List<GenericStack> inputs, List<GenericStack> outputs) {
        var pos = pot.entity().getBlockPos();
        var pattern = pattern(h, inputs, outputs);
        var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "recipe binds");
        var plan = ADAPTER.planWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), SOURCE);
        h.assertTrue(plan != null, "recipe plans");
        var returned = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, SOURCE, returned::addAll).success(), "real dispatch succeeds");
        h.assertTrue(returned.isEmpty(), "dispatch does not invent instant outputs");
        return binding.handle();
    }
    @GameTest(template = "delight_test")
    public static void optionalRegistrationAndCoreRecipes(GameTestHelper h) {
        boolean fd = ModList.get().isLoaded("farmersdelight");
        boolean ad = ModList.get().isLoaded("avaritia_delight");
        h.assertTrue(MultiblockAdapterRegistry.getAll().stream().anyMatch(a -> a instanceof CookingPotAdapter) == fd, "optional adapter registration");
        var visible = PackagedCoreDefinition.visibleWhen(mod -> ModList.get().isLoaded(mod));
        h.assertTrue(visible.stream().anyMatch(c -> c.itemId().equals("fd_cooking_pot_packaged_core")) == fd, "FD core visibility");
        h.assertTrue(visible.stream().anyMatch(c -> c.itemId().equals("ad_extreme_cooking_pot_packaged_core")) == ad, "AD core visibility");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/fd_cooking_pot_packaged_core")).isPresent() == fd, "FD recipe conditional");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/ad_extreme_cooking_pot_packaged_core")).isPresent() == ad, "AD recipe conditional");
        h.assertTrue(PPItems.AD_COOKING_POT_ADAPTER.get().covers(CookingPotAccess.Kind.FARMERS.coreId), "extreme covers ordinary");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void sixSlotsVesselsNativeTimeAndExactlyOnceReturn(GameTestHelper h) throws Exception {
        if (absent(h, "farmersdelight")) return;
        var pot = pot(h, CookingPotAccess.Kind.FARMERS);
        var pos = pot.entity().getBlockPos();
        var bound = dispatch(h, pot, ordinaryInputs(), ordinaryOutputs());
        for (int i = 0; i < 6; i++) h.assertTrue(pot.inventory().getStackInSlot(i).getCount() == 1, "one repeated ingredient per slot");
        h.assertTrue(pot.inventory().getStackInSlot(7).getCount() == 2, "two paid vessels");
        tick(h, pot);
        h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), pos, FILTER, SOURCE).isEmpty(), "native timer is preserved");
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), pos, bound), "busy pot does not mix another recipe");
        h.assertTrue(finish(h, pot, 12).equals(ordinaryOutputs()), "exact native meal return");
        h.assertTrue(pot.empty() && ADAPTER.extractOutputs(h.getLevel(), pos, FILTER, SOURCE).isEmpty(), "vessels consumed; no duplicate return");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void all81SlotsAndNativeOrdinaryCompatibility(GameTestHelper h) throws Exception {
        if (absent(h, "avaritia_delight")) return;
        var pot = pot(h, CookingPotAccess.Kind.EXTREME);
        var inputs = List.of(item(Items.POTATO, 81), item(Items.GLASS_BOTTLE, 4));
        var outputs = List.of(item(Items.DIAMOND, 4));
        dispatch(h, pot, inputs, outputs);
        for (int i = 0; i < 81; i++) h.assertTrue(pot.inventory().getStackInSlot(i).is(Items.POTATO), "all 81 ingredients routed");
        h.assertTrue(pot.inventory().getStackInSlot(82).getCount() == 4, "extreme vessel slot");
        h.assertTrue(finish(h, pot, 12).equals(outputs) && pot.empty(), "native 81-slot result");
        dispatch(h, pot, ordinaryInputs(), ordinaryOutputs());
        h.assertTrue(finish(h, pot, 12).equals(ordinaryOutputs()) && pot.empty(), "ordinary recipe also cooks in extreme pot");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void missingExcessWrongVesselsAndWrongOutputsRefused(GameTestHelper h) {
        if (absent(h, "farmersdelight")) return;
        var pot = pot(h, CookingPotAccess.Kind.FARMERS); var pos = pot.entity().getBlockPos();
        for (var invalid : List.of(List.of(item(Items.CARROT, 6)),
                List.of(item(Items.CARROT, 6), item(Items.BOWL, 1)), List.of(item(Items.CARROT, 6), item(Items.BOWL, 3)),
                List.of(item(Items.CARROT, 6), item(Items.GLASS_BOTTLE, 2)), List.of(item(Items.CARROT, 7), item(Items.BOWL, 2))))
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, invalid, ordinaryOutputs())) == null, "invalid quantities refused");
        h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, ordinaryInputs(), List.of(item(Items.GOLDEN_CARROT, 3)))) == null, "inflated output refused");
        var tagged = new ItemStack(Items.GOLDEN_CARROT, 2);
        tagged.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("not native"));
        h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, ordinaryInputs(), List.of(new GenericStack(AEItemKey.of(tagged), 2)))) == null, "strict output components");
        var correct = pattern(h, ordinaryInputs(), ordinaryOutputs()); var binding = ADAPTER.bind(h.getLevel(), pos, correct);
        h.assertTrue(binding != null && ADAPTER.planWithBinding(h.getLevel(), pos, correct,
                counters(List.of(item(Items.POTATO, 6), item(Items.BOWL, 2))), binding.handle(), SOURCE) == null, "actual input revalidated");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void noHeatDirtyPotAndUnservedMealCannotBypassVessels(GameTestHelper h) {
        if (absent(h, "farmersdelight")) return;
        var pot = pot(h, CookingPotAccess.Kind.FARMERS); var pos = pot.entity().getBlockPos();
        var pattern = pattern(h, ordinaryInputs(), ordinaryOutputs()); var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "binding independent of transient readiness");
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), pos, binding.handle()), "heat required");
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        var inventory = (net.neoforged.neoforge.items.IItemHandlerModifiable)pot.inventory();
        for (int slot : new int[]{0, 6, 7}) {
            inventory.setStackInSlot(slot, new ItemStack(Items.GOLDEN_CARROT));
            h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), pos, binding.handle()), "occupied native slot blocks dispatch");
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), pos, FILTER, SOURCE).isEmpty(), "only actual output slot may be extracted");
            h.assertTrue(!inventory.getStackInSlot(slot).isEmpty(), "unserved food is never harvested for free");
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        inventory.setStackInSlot(8, new ItemStack(Items.GOLDEN_CARROT));
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), pos, binding.handle()), "full output blocks admission");
        h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), pos, FILTER, SOURCE).equals(List.of(item(Items.GOLDEN_CARROT, 1))), "dedicated native output harvested");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void extremeNormalRecipeShadowDoesNotDestroyExtraIngredients(GameTestHelper h) {
        if (absent(h, "avaritia_delight")) return;
        var pot = pot(h, CookingPotAccess.Kind.EXTREME);
        var shadow = pattern(h, List.of(item(Items.CARROT, 81)), List.of(item(Items.EMERALD, 1)));
        h.assertTrue(ADAPTER.bind(h.getLevel(), pot.entity().getBlockPos(), shadow) == null, "native first-six-slot recipe takes precedence; refuse mismatched output");
        h.assertTrue(pot.empty(), "rejected binding cannot consume 81 ingredients");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void partialDispatchRefundsOnlyInsertedIngredients(GameTestHelper h) {
        if (absent(h, "farmersdelight")) return;
        var pot = pot(h, CookingPotAccess.Kind.FARMERS); var pos = pot.entity().getBlockPos();
        var pattern = pattern(h, ordinaryInputs(), ordinaryOutputs()); var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        var plan = ADAPTER.planWithBinding(h.getLevel(), pos, pattern, counters(ordinaryInputs()), binding.handle(), SOURCE);
        var targets = new ArrayList<>(plan.targets()); var old = targets.getLast();
        targets.set(targets.size() - 1, new TargetSlot(old.level(), old.pos(), old.face(), old.stacks(),
                InsertionStrategy.CUSTOM, (stack, mode) -> mode == Actionable.SIMULATE ? stack.amount() : 0L));
        var returned = new ArrayList<GenericStack>();
        DispatchExecutor.execute(new DispatchPlan(targets, plan.onCommit(), plan.onPartialCommit()), SOURCE, returned::addAll);
        var totals = counters(returned)[0];
        h.assertTrue(totals.get(AEItemKey.of(Items.CARROT)) == 6 && totals.get(AEItemKey.of(Items.BOWL)) == 2 && pot.empty(), "accepted food and rejected vessels retained exactly once");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void nativeSaveReloadResumesCookingOnce(GameTestHelper h) throws Exception {
        if (absent(h, "avaritia_delight")) return;
        var pot = pot(h, CookingPotAccess.Kind.EXTREME); var pos = pot.entity().getBlockPos();
        var inputs = List.of(item(Items.POTATO, 81), item(Items.GLASS_BOTTLE, 4)); var outputs = List.of(item(Items.DIAMOND, 4));
        dispatch(h, pot, inputs, outputs); tick(h, pot);
        var tag = pot.entity().saveWithFullMetadata(h.getLevel().registryAccess()); var state = pot.entity().getBlockState();
        h.getLevel().removeBlockEntity(pos);
        var restored = BlockEntity.loadStatic(pos, state, tag, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored); var loaded = CookingPotAccess.pot(h.getLevel(), pos);
        h.assertTrue(loaded != null && loaded.inventory().getStackInSlot(82).getCount() == 4, "native vessel state persists");
        h.assertTrue(finish(h, loaded, 12).equals(outputs) && loaded.empty(), "one result after native reload");
        h.succeed();
    }
    @GameTest(template = "delight_test")
    public static void ingredientBucketsKeepNativeRemainderBehavior(GameTestHelper h) throws Exception {
        if (absent(h, "farmersdelight")) return;
        var pot = pot(h, CookingPotAccess.Kind.FARMERS); var pos = pot.entity().getBlockPos();
        var unrelated = new ItemEntity(h.getLevel(), pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, new ItemStack(Items.DIRT));
        h.getLevel().addFreshEntity(unrelated);
        dispatch(h, pot, List.of(item(Items.MILK_BUCKET, 1), item(Items.SUGAR, 1)), List.of(item(Items.PAPER, 1)));
        h.assertTrue(finish(h, pot, 12).equals(List.of(item(Items.PAPER, 1))), "native output");
        var buckets = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(Items.BUCKET)).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(buckets == 1 && unrelated.isAlive(), "native bucket ejected once; unrelated drops untouched");
        h.succeed();
    }
    @GameTest(template = "delight_test", timeoutTicks = 500)
    public static void firstCoreTerminalOrderCooksAndInstalls(GameTestHelper h) {
        if (absent(h, "farmersdelight")) return;
        coreOrder(h, false);
    }
    @GameTest(template = "delight_test", timeoutTicks = 500)
    public static void extremeCoreWirelessSpareOrderReturnsToMe(GameTestHelper h) {
        if (absent(h, "avaritia_delight")) return;
        coreOrder(h, true);
    }
    private static void coreOrder(GameTestHelper h, boolean extreme) {
        var pos = h.absolutePos(new BlockPos(3, 3, 5));
        var block = extreme ? "ae2ltpp:wireless_packaged_pattern_provider" : "ae2ltpp:packaged_pattern_provider";
        h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id(block)).defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity)h.getLevel().getBlockEntity(pos);
        provider.setReturnMode(com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode.AUTO);
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.north(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var machine = pot(h, extreme ? CookingPotAccess.Kind.EXTREME : CookingPotAccess.Kind.FARMERS, extreme ? pos.east(3) : pos.east());
        var itemId = extreme ? "ad_extreme_cooking_pot_packaged_core" : "fd_cooking_pot_packaged_core";
        var core = extreme ? PPItems.AD_COOKING_POT_ADAPTER.get() : PPItems.FD_COOKING_POT_ADAPTER.get();
        var recipe = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/"+itemId)).orElseThrow();
        var in = new ArrayList<GenericStack>();
        recipe.value().getIngredients().forEach(ingredient -> in.add(new GenericStack(AEItemKey.of(ingredient.getItems()[0]), 1)));
        var logic = (PackagedPatternProviderLogic)provider.getLogic();
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(in, List.of(item(core, 1))));
        var store = new Store(); in.forEach(stack -> store.stock.add(stack.what(), stack.amount()));
        h.runAfterDelay(60, () -> {
            provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(store, 0));
            logic.updatePatterns();
            if (extreme) h.assertTrue(provider.addOrUpdateConnection(h.getLevel().dimension(), machine.entity().getBlockPos(), Direction.UP), "wireless coreless binding");
            provider.getCoreOrder().request(core);
            h.succeedWhen(() -> {
                h.assertTrue(extreme ? store.stock.get(AEItemKey.of(core)) == 1 && provider.getInstalledAdapterStack().isEmpty()
                                : provider.getInstalledAdapterStack().is(core),
                        "native core destination: "+provider.getCoreOrder().status()+" inventory="+
                        java.util.stream.IntStream.range(0, machine.inventory().getSlots())
                                .filter(i -> !machine.inventory().getStackInSlot(i).isEmpty())
                                .mapToObj(i -> i+":"+machine.inventory().getStackInSlot(i)).toList()+" heated="+machine.heated());
                in.forEach(stack -> h.assertTrue(store.stock.get(stack.what()) == 0, "core materials paid exactly once"));
                h.assertTrue(provider.getMainNode().getGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()), "CPU finished");
            });
        });
    }
    private static final class Store implements MEStorage {
        final KeyCounter stock = new KeyCounter();
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) stock.add(key, amount); return amount;
        }
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, stock.get(key));
            if (mode == Actionable.MODULATE) stock.add(key, -accepted); return accepted;
        }
        public void getAvailableStacks(KeyCounter target) { target.addAll(stock); }
        public Component getDescription() { return Component.literal("Cooking pot test storage"); }
    }
}
