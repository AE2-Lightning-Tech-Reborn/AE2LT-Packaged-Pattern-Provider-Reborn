package com.moakiee.ae2lt.packaged.item;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import com.moakiee.ae2lt.packaged.client.PackagedCoreClientExtensions;

/**
 * Packaged core that unlocks a machine family through its covered adapter ids.
 * Higher tiers may also cover lower tiers; each provider holds one core.
 */
public class MultiblockAdapterItem extends Item {

    private final Set<ResourceLocation> coveredIds;

    /** @param coveredIds unlocked ids, including the canonical primaryId */
    public MultiblockAdapterItem(Properties props,
                                 ResourceLocation primaryId,
                                 Set<ResourceLocation> coveredIds) {
        super(props);
        this.coveredIds = Set.copyOf(coveredIds);
        if (!this.coveredIds.contains(primaryId)) {
            throw new IllegalArgumentException(
                    "coveredIds must contain primaryId: " + primaryId + " not in " + coveredIds);
        }
    }

    /** @return true if this item unlocks the given required adapter id. */
    public boolean covers(@Nullable ResourceLocation requiredId) {
        return requiredId != null && coveredIds.contains(requiredId);
    }

    /** Static helper: check an ItemStack against a required id. */
    public static boolean stackCovers(ItemStack stack, @Nullable ResourceLocation requiredId) {
        return requiredId != null
                && stack.getItem() instanceof MultiblockAdapterItem item
                && item.covers(requiredId);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (coveredIds.size() > 1) {
            tooltip.add(Component.translatable("tooltip.ae2ltpp.packaged_core.covers",
                    Component.literal(String.valueOf(coveredIds.size())).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        PackagedCoreClientExtensions.accept(consumer);
    }
}
