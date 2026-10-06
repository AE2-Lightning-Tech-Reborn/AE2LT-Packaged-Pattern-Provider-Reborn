package com.moakiee.ae2lt.packaged.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class HephaestusUpgradeReceiptItem extends Item {
    public HephaestusUpgradeReceiptItem(Properties properties) {
        super(properties);
    }

    public static ItemStack forTier(Item item, int tier) {
        if (tier < 2 || tier > 5) throw new IllegalArgumentException("Unsupported forge tier: " + tier);
        var stack = new ItemStack(item);
        stack.getOrCreateTag().putInt("Tier", tier);
        return stack;
    }

    public static int tier(ItemStack stack, Item item) {
        if (!stack.is(item) || stack.getCount() != 1 || stack.getTag() == null
                || !stack.getTag().contains("Tier", 3) || stack.getTag().getAllKeys().size() != 1) return 0;
        int tier = stack.getTag().getInt("Tier");
        return tier >= 2 && tier <= 5 ? tier : 0;
    }

    @Override public Component getName(ItemStack stack) {
        int tier = tier(stack, this);
        return tier == 0 ? super.getName(stack) : Component.translatable(getDescriptionId() + ".tier", tier);
    }
}
