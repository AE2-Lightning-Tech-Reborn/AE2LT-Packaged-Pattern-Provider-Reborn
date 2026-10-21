package com.moakiee.ae2lt.packaged.gametest;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.compat.ForbiddenArcanusHephaestusForgeAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.fa.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.*;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ForbiddenResourceBackportTests {
    private static BlockEntity forge(GameTestHelper h) {
        var pos = new BlockPos(7,2,7);
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(new ResourceLocation("forbidden_arcanus:hephaestus_forge")).defaultBlockState());
        return h.getBlockEntity(pos);
    }
    private static PackagedPatternProviderBlockEntity provider(GameTestHelper h, int x) {
        var pos = new BlockPos(x,2,2); h.setBlock(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        return (PackagedPatternProviderBlockEntity) h.getBlockEntity(pos);
    }
    private static IPatternDetails pattern(GameTestHelper h, GenericStack input, List<GenericStack> outputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{input}, outputs.toArray(GenericStack[]::new)),h.getLevel());
    }
    @GameTest(template="adapterconnectiontests.adapter_conn")
    public static void resourceIdentityAndNativeStorage(GameTestHelper h) {
        for (var key : ForbiddenEssenceResources.ALL) {
            var decoded = AEKey.fromTagGeneric(key.toTagGeneric());
            h.assertTrue(key.equals(decoded) && key.getPrimaryKey() == decoded.getPrimaryKey(),"NBT resource identity");
            var buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            AEKey.writeKey(buf,key); decoded = AEKey.readKey(buf); buf.release();
            h.assertTrue(key.equals(decoded) && key.getPrimaryKey() == decoded.getPrimaryKey(),"packet resource identity");
            var pat = pattern(h,new GenericStack(key,250),List.of(new GenericStack(AEItemKey.of(Items.DIAMOND),1)));
            h.assertTrue(pat != null && pat.getInputs()[0].getPossibleInputs()[0].what().equals(key),"processing pattern retains resource type");
        }
        var forge = forge(h);
        var storage = forge.getCapability(PackagedResourceStorage.CAPABILITY).orElseThrow(IllegalStateException::new);
        for (var key : ForbiddenEssenceResources.ALL) {
            long cap = storage.insert(key,Long.MAX_VALUE,Actionable.SIMULATE,IActionSource.empty());
            h.assertTrue(cap > 0 && storage.getAvailableStacks().get(key)==0,"native capacity / simulate insert");
            h.assertTrue(storage.insert(key,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.empty())==cap,"bounded insert receipt");
            h.assertTrue(storage.insert(key,1,Actionable.MODULATE,IActionSource.empty())==0,"no overfill");
            h.assertTrue(storage.extract(key,Long.MAX_VALUE,Actionable.SIMULATE,IActionSource.empty())==cap && storage.getAvailableStacks().get(key)==cap,"simulate extract unchanged");
            h.assertTrue(storage.extract(key,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.empty())==cap && storage.getAvailableStacks().get(key)==0,"bounded extraction receipt");
        }
        h.assertTrue(storage.insert(AEItemKey.of(Items.DIAMOND),1,Actionable.MODULATE,IActionSource.empty())==0,"foreign resource rejected");
        h.getLevel().removeBlock(forge.getBlockPos(),false);
        h.assertTrue(storage.insert(ForbiddenEssenceResources.BLOOD,1,Actionable.MODULATE,IActionSource.empty())==0,"stale storage rejected");
        h.succeed();
    }
    @GameTest(template="adapterconnectiontests.adapter_conn",timeoutTicks=180)
    public static void deterministicConversionUsesNativeTick(GameTestHelper h) { conversion(h, new ItemStack(Items.EXPERIENCE_BOTTLE,3)); }
    @GameTest(template="adapterconnectiontests.adapter_conn",timeoutTicks=180)
    public static void nativeBloodTubeReturnsExactRemainder(GameTestHelper h) {
        var tube = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("forbidden_arcanus:blood_test_tube")));
        tube = (ItemStack) FAAccess.call(FAAccess.type("com.stal111.forbidden_arcanus.common.item.BloodTestTubeItem"),"setBlood",tube,37);
        conversion(h,tube);
    }
    @GameTest(template="adapterconnectiontests.adapter_conn",timeoutTicks=180)
    public static void randomEnchantmentConversionReturnsNativeAmount(GameTestHelper h) {
        var tool = new ItemStack(Items.DIAMOND_SWORD); tool.enchant(Enchantments.SHARPNESS,3); conversion(h,tool);
    }
    @GameTest(template="adapterconnectiontests.adapter_conn")
    public static void removedConversionOwnerReleasesOnlyNativeInventory(GameTestHelper h) {
        var forge = forge(h); var owner = provider(h,2);
        var pattern = pattern(h,new GenericStack(AEItemKey.of(Items.EXPERIENCE_BOTTLE),1),
                List.of(new GenericStack(ForbiddenEssenceResources.EXPERIENCE,15)));
        var adapter = new ForbiddenArcanusHephaestusForgeAdapter();
        var binding = adapter.bind(h.getLevel(),forge.getBlockPos(),pattern);
        var counter = new KeyCounter();counter.add(AEItemKey.of(Items.EXPERIENCE_BOTTLE),1);
        var plan = adapter.planWithBinding(h.getLevel(),forge.getBlockPos(),pattern,new KeyCounter[]{counter},binding.handle(),IActionSource.empty(),owner);
        h.assertTrue(plan != null && DispatchExecutor.execute(plan,IActionSource.empty(),new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {})).success(),"owned dispatch");
        FAForgeConversion.releaseAbandoned(forge);
        h.assertTrue(forge.getPersistentData().contains(FAForgeConversion.JOB),"live owner retained");
        h.getLevel().removeBlock(owner.getBlockPos(),false);
        FAForgeConversion.releaseAbandoned(forge);
        h.assertTrue(!forge.getPersistentData().contains(FAForgeConversion.JOB),"removed owner released");
        h.assertTrue(FAAccess.stack(forge,8).getCount()==1 && FAAccess.amount(forge,"experience")==0,"native inputs retained without refund or minted output");
        h.succeed();
    }
    private static void conversion(GameTestHelper h, ItemStack stack) {
        var forge = forge(h); var owner = provider(h,2); var stranger = provider(h,4);
        var input = FAForgeConversion.describe(h.getLevel(),AEItemKey.of(stack),stack.getCount());
        h.assertTrue(input != null,"native conversion contract");
        String name = ForbiddenEssenceResources.ALL.get(input.index()).getId().getPath();
        FAAccess.amount(forge,name,7);
        var outputs = new ArrayList<GenericStack>(); outputs.add(new GenericStack(ForbiddenEssenceResources.ALL.get(input.index()),input.min()));
        if (!input.remainder().isEmpty()) outputs.add(new GenericStack(AEItemKey.of(input.remainder()),input.remainder().getCount()));
        var supplied = new GenericStack(AEItemKey.of(stack),stack.getCount());
        var pattern = pattern(h,supplied,outputs); var adapter = new ForbiddenArcanusHephaestusForgeAdapter();
        var binding = adapter.bind(h.getLevel(),forge.getBlockPos(),pattern);
        h.assertTrue(binding != null,"real conversion binding");
        var counter = new KeyCounter(); counter.add(supplied.what(),supplied.amount());
        var plan = adapter.planWithBinding(h.getLevel(),forge.getBlockPos(),pattern,new KeyCounter[]{counter},binding.handle(),IActionSource.empty(),owner);
        h.assertTrue(plan != null && DispatchExecutor.execute(plan,IActionSource.empty(),new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {})).success(),"exact native conversion dispatch");
        var nativeStorage = forge.getCapability(PackagedResourceStorage.CAPABILITY).orElseThrow(IllegalStateException::new);
        h.assertTrue(nativeStorage.extract(outputs.get(0).what(),1,Actionable.MODULATE,IActionSource.empty())==0,"inflight resource cannot be extracted twice");
        h.assertTrue(!adapter.canDispatch(h.getLevel(),forge.getBlockPos(),binding.handle(),stranger),"another provider cannot replace job");
        var filter = new AllowedOutputFilter(); outputs.forEach(output -> filter.allowStrict(output.what()));
        owner.loadTag(owner.saveWithoutMetadata());
        forge.load(forge.saveWithoutMetadata());
        var collected = new KeyCounter();
        h.succeedWhen(() -> {
            h.assertTrue(adapter.extractOutputs(h.getLevel(),forge.getBlockPos(),filter,IActionSource.empty(),stranger).isEmpty(),"another provider cannot steal conversion");
            for (var output : adapter.extractOutputs(h.getLevel(),forge.getBlockPos(),filter,IActionSource.empty(),owner)) collected.add(output.what(),output.amount());
            h.assertTrue(!forge.getPersistentData().contains(FAForgeConversion.JOB),"conversion completed and all receipts collected");
            long amount = collected.get(outputs.get(0).what());
            h.assertTrue(amount >= input.min() && amount <= input.max(),"native bounded resource receipt");
            h.assertTrue(FAAccess.amount(forge,name)==7,"preexisting essences retained");
            if (!input.remainder().isEmpty()) h.assertTrue(collected.get(AEItemKey.of(input.remainder()))==input.remainder().getCount(),"native container returned exactly once");
            h.assertTrue(adapter.extractOutputs(h.getLevel(),forge.getBlockPos(),filter,IActionSource.empty(),owner).isEmpty(),"conversion cannot replay");
        });
    }
}
