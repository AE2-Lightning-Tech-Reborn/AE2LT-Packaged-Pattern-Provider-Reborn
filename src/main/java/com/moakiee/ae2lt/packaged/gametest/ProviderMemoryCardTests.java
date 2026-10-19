package com.moakiee.ae2lt.packaged.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.util.SettingsFrom;
import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp_core")
@PrefixGameTestTemplate(false)
public final class ProviderMemoryCardTests {
    private static PackagedPatternProviderBlockEntity place(GameTestHelper helper, int x) {
        var position = new BlockPos(x, 1, 2);
        helper.setBlock(position, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        return (PackagedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void copiesProviderSettingsAndFrequencyWithoutCopyingItems(GameTestHelper helper) {
        var source = place(helper, 2);
        var target = place(helper, 4);
        source.setReturnMode(ReturnMode.AUTO);
        source.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.MEKMM_ADAPTER.get()));
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        source.getLogic().getPatternInv().setItemDirect(0, pattern);
        var frequency = new CompoundTag();
        frequency.putInt("FrequencyId", 42);
        source.getFrequencyBindingAccess().load(frequency);
        var card = new CompoundTag();
        source.exportSettings(SettingsFrom.MEMORY_CARD, card, null);
        var settings = card.getCompound("AE2LTMachineConfig");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(settings.getString("ReturnMode").equals("AUTO"), "Card omitted return mode");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(settings.getInt("Frequency") == 42, "Card omitted frequency");
        target.importSettings(SettingsFrom.MEMORY_CARD, card, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getReturnMode() == ReturnMode.AUTO, "Return mode was not imported");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getFrequencyBindingAccess().getFrequencyId() == 42, "Frequency was not imported");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getAdapterInv().getStackInSlot(0).isEmpty(), "Memory card copied a core");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getLogic().getPatternInv().getStackInSlot(0).isEmpty(), "Memory card copied a pattern");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(source.getAdapterInv().getStackInSlot(0).getCount() == 1, "Source core changed");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(ItemStack.matches(source.getLogic().getPatternInv().getStackInSlot(0), pattern),
                "Source pattern changed");
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void importsLegacyAutoReturnAndPrefersTheEnum(GameTestHelper helper) {
        var target = place(helper, 2);
        var settings = new CompoundTag();
        settings.putBoolean("AutoReturn", true);
        settings.putInt("Frequency", 42);
        var card = new CompoundTag();
        card.put("AE2LTMachineConfig", settings);
        target.importSettings(SettingsFrom.MEMORY_CARD, card, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getReturnMode() == ReturnMode.AUTO, "Legacy AutoReturn was not imported");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getFrequencyBindingAccess().getFrequencyId() == 42, "Legacy frequency was not imported");
        settings.putString("ReturnMode", "OFF");
        target.importSettings(SettingsFrom.MEMORY_CARD, card, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getReturnMode() == ReturnMode.OFF, "Legacy flag overrode explicit enum");
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void ignoresMissingAndMalformedCustomSettingsAndOtherExportModes(GameTestHelper helper) {
        var target = place(helper, 2);
        target.setReturnMode(ReturnMode.AUTO);
        target.importSettings(SettingsFrom.MEMORY_CARD, new CompoundTag(), null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getReturnMode() == ReturnMode.AUTO, "Empty card reset provider state");
        var settings = new CompoundTag();
        settings.putString("ReturnMode", "invalid");
        settings.putString("Frequency", "invalid");
        var card = new CompoundTag();
        card.put("AE2LTMachineConfig", settings);
        target.importSettings(SettingsFrom.MEMORY_CARD, card, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getReturnMode() == ReturnMode.AUTO, "Malformed enum reset provider state");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getFrequencyBindingAccess().getFrequencyId() == -1, "Malformed frequency was imported");
        settings.putString("ReturnMode", "OFF");
        target.importSettings(SettingsFrom.DISMANTLE_ITEM, card, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.getReturnMode() == ReturnMode.AUTO, "Item dismantling imported card-only state");
        var dismantled = new CompoundTag();
        target.exportSettings(SettingsFrom.DISMANTLE_ITEM, dismantled, null);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!dismantled.contains("AE2LTMachineConfig"), "Item dismantling exported card-only state");
        helper.succeed();
    }
}
