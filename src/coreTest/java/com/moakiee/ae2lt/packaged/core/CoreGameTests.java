package com.moakiee.ae2lt.packaged.core;

import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.registry.*;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class CoreGameTests {
    @GameTest(template="core_test", timeoutTicks=600) public static void firstCoreNormal(GameTestHelper h) { order(h, false, false, false); }
    @GameTest(template="core_test", timeoutTicks=600) public static void firstCoreWireless(GameTestHelper h) { order(h, true, false, false); }
    @GameTest(template="core_test", timeoutTicks=600) public static void omittedOldCoreInstallsUpgrade(GameTestHelper h) { order(h, false, true, false); }
    @GameTest(template="core_test", timeoutTicks=600) public static void explicitOldCoreReturnsToNetwork(GameTestHelper h) { order(h, false, true, true); }

    @GameTest(template="core_test", timeoutTicks=600) public static void firstCoreOccupiedReturnsToNetwork(GameTestHelper h) { order(h, false, false, false, true); }

    private static PackagedPatternProviderBlockEntity provider(GameTestHelper h, boolean wireless) {
        var pos = h.absolutePos(new BlockPos(3, 2, 5));
        var level = h.getLevel();
        net.minecraft.world.level.block.Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        ((DriveBlockEntity)level.getBlockEntity(pos.north())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        return (PackagedPatternProviderBlockEntity)level.getBlockEntity(pos);
    }
    private static void order(GameTestHelper h, boolean wireless, boolean upgrade, boolean explicitOld) {
        order(h,wireless,upgrade,explicitOld,false);
    }
    private static void order(GameTestHelper h, boolean wireless, boolean upgrade, boolean explicitOld, boolean occupied) {
        var provider = provider(h, wireless);
        var level = h.getLevel();
        var logic = (PackagedPatternProviderLogic)provider.getLogic();
        var old = PPItems.EC_BASIC_ADAPTER.get();
        var result = upgrade ? PPItems.EC_ADVANCED_ADAPTER.get() : old;
        if (upgrade) provider.getAdapterInv().setItemDirect(0, old.getDefaultInstance());
        if (occupied) provider.getAdapterInv().setItemDirect(0, PPItems.MA_INFUSION_ADAPTER.toStack());
        var inputs = new java.util.ArrayList<GenericStack>(List.of(
                stack("extendedcrafting:luminessence",4),
                stack("extendedcrafting:" + (upgrade ? "advanced_component" : "basic_component"),2),
                stack("extendedcrafting:black_iron_ingot",2)));
        if (!upgrade) inputs.add(new GenericStack(AEItemKey.of(PPItems.BASIC_PACKAGED_CORE.get()),1));
        if (explicitOld) inputs.add(new GenericStack(AEItemKey.of(old),1));
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(new GenericStack(AEItemKey.of(result),1))));
        var machine = provider.getBlockPos().east(wireless ? 4 : 1);
        if (!upgrade) level.setBlockAndUpdate(machine, BuiltInRegistries.BLOCK.get(id("extendedcrafting:basic_table")).defaultBlockState());
        h.runAfterDelay(60, () -> {
            logic.updatePatterns();
            if (wireless) h.assertTrue(provider.addOrUpdateConnection(level.dimension(), machine, Direction.WEST), "coreless wireless binding");
            var grid = provider.getMainNode().getGrid();
            h.assertTrue(grid != null && provider.getMainNode().isActive(), "active powered grid");
            var storage = grid.getStorageService().getInventory();
            for (var input : inputs) h.assertTrue(storage.insert(input.what(), input.amount(), Actionable.MODULATE, IActionSource.empty()) == input.amount(), "seed exact raw inputs");
            provider.getCoreOrder().request(result);
            if (upgrade) h.assertTrue(provider.getInstalledAdapterStack().is(old), "ordering never takes the installed core");
            var lastStatus = new String[]{""};
            h.succeedWhen(() -> {
                String status = provider.getCoreOrder().status();
                if (!lastStatus[0].equals(status)) {
                    lastStatus[0] = status;
                    System.out.println("CORE_STATUS wireless=" + wireless + " upgrade=" + upgrade + " explicit=" + explicitOld
                            + " state=" + status + " jobs=" + provider.getCoreOrder().getRequestedJobs().size()
                            + " cpus=" + grid.getCraftingService().getCpus().size() + " stock=" + storage.getAvailableStacks());
                }
                h.assertTrue(status.equals(explicitOld || occupied ? "returned" : "installed"), "order status=" + status);
                h.assertTrue(provider.getInstalledAdapterStack().is(occupied ? PPItems.MA_INFUSION_ADAPTER.get() : explicitOld ? old : result), "correct installed core");
                h.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(result)) == (explicitOld || occupied ? 1 : 0), "exact result destination");
                for (var input : inputs) h.assertTrue(storage.getAvailableStacks().get(input.what()) == 0, "all supplied materials consumed exactly");
                h.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()), "CPU completed");
                h.assertTrue(!grid.getCraftingService().isRequestingAny(), "no outstanding outputs");
                System.out.println("CORE_E2E_PASS wireless=" + wireless + " upgrade=" + upgrade + " explicitOld=" + explicitOld + " occupied=" + occupied + " status=" + status);
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=150)
    public static void missingUpgradeMaterialsLeaveInstalledCore(GameTestHelper h) {
        var provider = provider(h, false);
        provider.getAdapterInv().setItemDirect(0, PPItems.EC_BASIC_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic)provider.getLogic();
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(stack("extendedcrafting:luminessence",4),stack("extendedcrafting:advanced_component",2),stack("extendedcrafting:black_iron_ingot",2)),
                List.of(new GenericStack(AEItemKey.of(PPItems.EC_ADVANCED_ADAPTER.get()),1))));
        h.runAfterDelay(60, () -> {
            logic.updatePatterns();
            provider.getCoreOrder().request(PPItems.EC_ADVANCED_ADAPTER.get());
            h.succeedWhen(() -> {
                h.assertTrue(provider.getCoreOrder().status().equals("missing_ingredients"), "missing materials reported");
                h.assertTrue(provider.getInstalledAdapterStack().is(PPItems.EC_BASIC_ADAPTER.get()), "old core retained");
                h.assertTrue(provider.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().isEmpty(), "nothing minted");
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=120)
    public static void receiptSurvivesFullStorageAndReload(GameTestHelper h) {
        var provider = provider(h, false);
        provider.getAdapterInv().setItemDirect(0, PPItems.MA_INFUSION_ADAPTER.toStack());
        var drive = (DriveBlockEntity) h.getLevel().getBlockEntity(provider.getBlockPos().north());
        drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
        h.runAfterDelay(60, () -> {
            var registries = h.getLevel().registryAccess();
            var target = AEItemKey.of(PPItems.EC_BASIC_ADAPTER.get());
            var data = new net.minecraft.nbt.CompoundTag();
            data.put("target", target.toTag(registries));
            var linkTag = new net.minecraft.nbt.CompoundTag();
            linkTag.putUUID("craftId", java.util.UUID.randomUUID());
            linkTag.putBoolean("req", true);
            data.put("link", linkTag);
            var order = provider.getCoreOrder();
            order.load(data, registries);
            var link = order.getRequestedJobs().iterator().next();
            h.assertTrue(order.insertCraftedItems(link, target, 1, Actionable.SIMULATE) == 1, "receipt simulation");
            h.assertTrue(!order.save(registries).contains("delivered"), "simulation does not buffer");
            h.assertTrue(order.insertCraftedItems(link, target, 1, Actionable.MODULATE) == 1, "accept one real receipt");
            h.assertTrue(order.insertCraftedItems(link, target, 1, Actionable.MODULATE) == 0, "duplicate receipt rejected");
            data = order.save(registries);
            data.getCompound("link").putBoolean("done", true);
            order.load(data, registries);
            order.tick();
            h.assertTrue(order.status().equals("storage_full"), "full storage retains receipt");
            h.assertTrue(order.save(registries).contains("delivered"), "buffer persists after completed link clears");
            order.load(order.save(registries), registries);
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
            h.succeedWhen(() -> {
                h.assertTrue(order.status().equals("returned"), "buffer flushed after space appears");
                h.assertTrue(provider.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(target) == 1, "exactly one result survives reload");
                h.assertTrue(provider.getInstalledAdapterStack().is(PPItems.MA_INFUSION_ADAPTER.get()), "occupied slot retained");
                h.assertTrue(!order.save(registries).contains("delivered"), "buffer removed only after insertion receipt");
            });
        });
    }

    @GameTest(template="core_test")
    public static void upgradeRejectsChangedCoreAndWrongQuantities(GameTestHelper h) {
        var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(
                com.moakiee.ae2lt.packaged.recipe.CoreUpgradeRecipes.TYPE.get()).stream()
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .filter(r -> r.result().is(PPItems.EC_ADVANCED_ADAPTER.get())).findFirst().orElseThrow();
        var inputs = List.of(stack("extendedcrafting:luminessence",4), stack("extendedcrafting:advanced_component",2), stack("extendedcrafting:black_iron_ingot",2));
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(new GenericStack(AEItemKey.of(PPItems.EC_ADVANCED_ADAPTER.get()),1))), h.getLevel());
        var counter = new KeyCounter();
        inputs.forEach(input -> counter.add(input.what(), input.amount()));
        var holders = new KeyCounter[]{counter};
        h.assertTrue(recipe.completionMode(PPItems.EC_BASIC_ADAPTER.toStack(), pattern, holders)==0, "exact installed-core upgrade admitted");
        h.assertTrue(recipe.completionMode(ItemStack.EMPTY, pattern, holders)==-1, "removed core rejected at completion");
        h.assertTrue(recipe.completionMode(PPItems.MA_INFUSION_ADAPTER.toStack(), pattern, holders)==-1, "changed core rejected");
        counter.add(AEItemKey.of(Items.DIAMOND),1);
        h.assertTrue(recipe.completionMode(PPItems.EC_BASIC_ADAPTER.toStack(), pattern, holders)==-1, "unrelated extra input rejected");
        counter.remove(AEItemKey.of(Items.DIAMOND),1);
        counter.remove(stack("extendedcrafting:luminessence",1).what(),1);
        counter.removeZeros();
        h.assertTrue(recipe.completionMode(PPItems.EC_BASIC_ADAPTER.toStack(), pattern, holders)==-1, "short material quantity rejected");
        h.succeed();
    }

    @GameTest(template="core_test")
    public static void resourceKeyRoundTrip(GameTestHelper h) {
        var key = ForbiddenEssenceResources.BLOOD;
        var fromTag = AEKey.fromTagGeneric(h.getLevel().registryAccess(), key.toTagGeneric(h.getLevel().registryAccess()));
        h.assertTrue(key.equals(fromTag) && key.getPrimaryKey() == fromTag.getPrimaryKey(), "NBT identity and AE2 primary identity");
        var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        AEKey.writeKey(buf,key);
        var decoded = AEKey.readKey(buf);
        buf.release();
        h.assertTrue(key.equals(decoded) && key.getPrimaryKey() == decoded.getPrimaryKey(), "packet identity");
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(key, 250)), List.of(new GenericStack(AEItemKey.of(Items.DIAMOND),1)));
        var pattern = PatternDetailsHelper.decodePattern(encoded,h.getLevel());
        h.assertTrue(pattern != null && pattern.getInputs()[0].getPossibleInputs()[0].what().equals(key), "actual processing pattern retains resource type");
        h.succeed();
    }

    @GameTest(template="core_test")
    public static void forbiddenNativeJarAndForge(GameTestHelper h) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) { h.succeed(); return; }
        var level = h.getLevel();
        for (String block : List.of("essence_utrem_jar", "hephaestus_forge_tier_1")) {
            var pos = h.absolutePos(new BlockPos(block.equals("essence_utrem_jar") ? 2 : 5,2,3));
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("forbidden_arcanus:" + block)).defaultBlockState());
            var storage = level.getCapability(PackagedResourceStorage.CAPABILITY, pos, Direction.UP);
            h.assertTrue(storage != null, "native capability registered " + block);
            var key = ForbiddenEssenceResources.AUREAL;
            long capacity = storage.insert(key,Long.MAX_VALUE,Actionable.SIMULATE,IActionSource.empty());
            h.assertTrue(capacity > 0, "native capacity " + block);
            h.assertTrue(storage.getAvailableStacks().get(key)==0, "simulation does not mutate");
            h.assertTrue(storage.insert(key,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.empty()) == capacity,"bounded insert");
            h.assertTrue(storage.insert(key,1,Actionable.MODULATE,IActionSource.empty()) == 0,"no overfill");
            h.assertTrue(storage.extract(key,capacity+100,Actionable.SIMULATE,IActionSource.empty()) == capacity,"bounded simulate extract");
            h.assertTrue(storage.getAvailableStacks().get(key)==capacity,"simulation leaves amount");
            h.assertTrue(storage.extract(key,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.empty()) == capacity,"exact extraction receipt");
            h.assertTrue(storage.getAvailableStacks().get(key)==0,"no negative amount");
            h.assertTrue(storage.insert(AEItemKey.of(Items.DIAMOND),3,Actionable.MODULATE,IActionSource.empty())==0,"foreign key rejected");
            level.removeBlock(pos,false);
            h.assertTrue(storage.insert(key,1,Actionable.MODULATE,IActionSource.empty())==0,"stale storage rejected");
        }
        h.succeed();
    }
    private static GenericStack stack(String item,long count) { return new GenericStack(AEItemKey.of(BuiltInRegistries.ITEM.get(id(item))),count); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
