package com.moakiee.ae2lt.packaged.registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraftforge.fml.ModList;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.item.HephaestusUpgradeReceiptItem;
public final class PPCreativeTabs {
    public static final CreativeModeTab MAIN_TAB = new CreativeModeTab("ae2ltpp") {
        @Override public ItemStack makeIcon() { return PPBlocks.PACKAGED_PATTERN_PROVIDER.get().asItem().getDefaultInstance(); }
        @Override public void fillItemList(NonNullList<ItemStack> output) {
                        output.add(new net.minecraft.world.item.ItemStack(PPBlocks.PACKAGED_PATTERN_PROVIDER.get()));
                        output.add(new net.minecraft.world.item.ItemStack(PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get()));
                        output.add(new net.minecraft.world.item.ItemStack(PPBlocks.EXTENDED_PACKAGED_PATTERN_PROVIDER.get()));
                        output.add(new net.minecraft.world.item.ItemStack(PPItems.BASIC_PACKAGED_CORE.get()));
                        for (var core : PackagedCoreDefinition.visibleWhen(PPCreativeTabs::isLoaded)) {
                            output.add(new net.minecraft.world.item.ItemStack(core.runtimeItem().get()));
                        }
                        if (isLoaded("forbidden_arcanus")) {
                            for (int tier = 2; tier <= 5; tier++) {
                                output.add(HephaestusUpgradeReceiptItem.forTier(PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get(), tier));
                            }
                        }
        }
    };
    private static boolean isLoaded(String id) { return ModList.get().isLoaded(id); }
    private PPCreativeTabs() {}
}
