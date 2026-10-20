package com.moakiee.ae2lt.packaged.resource;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MaidPowerKeyType extends AEKeyType {
    public static final MaidPowerKeyType INSTANCE = new MaidPowerKeyType();
    private MaidPowerKeyType() {
        super(ResourceLocation.parse("ae2ltpp:maid_power"), MaidPowerKey.class,
                Component.translatable("key.ae2ltpp.maid_power"));
    }
    @Override public MapCodec<? extends AEKey> codec() { return MaidPowerKey.CODEC; }
    @Override public AEKey readFromPacket(RegistryFriendlyByteBuf in) { return MaidPowerKey.INSTANCE; }
    @Override public int getAmountPerUnit() { return 100; }
    @Override public String getUnitSymbol() { return "P"; }
    @Override public int getAmountPerOperation() { return 100; }
    @Override public int getAmountPerByte() { return 800; }
}
