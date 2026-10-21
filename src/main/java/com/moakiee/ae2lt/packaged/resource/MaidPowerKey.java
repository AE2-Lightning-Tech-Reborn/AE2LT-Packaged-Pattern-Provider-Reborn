package com.moakiee.ae2lt.packaged.resource;

import java.util.List;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One raw unit is the native orb's integer value: 0.01 P. */
public final class MaidPowerKey extends AEKey {
    public static final ResourceLocation ID = new ResourceLocation("touhou_little_maid:power");
    public static final MaidPowerKey INSTANCE = new MaidPowerKey();

    private MaidPowerKey() {}
    @Override public AEKeyType getType() { return MaidPowerKeyType.INSTANCE; }
    @Override public MaidPowerKey dropSecondary() { return this; }
    @Override public Object getPrimaryKey() { return INSTANCE; }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void writeToPacket(FriendlyByteBuf out) {}
    @Override public CompoundTag toTag() { return new CompoundTag(); }
    @Override protected Component computeDisplayName() { return Component.translatable("key.ae2ltpp.maid_power"); }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
}
