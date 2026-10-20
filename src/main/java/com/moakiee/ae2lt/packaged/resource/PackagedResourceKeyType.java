package com.moakiee.ae2lt.packaged.resource;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class PackagedResourceKeyType extends AEKeyType {
    public static final PackagedResourceKeyType INSTANCE = new PackagedResourceKeyType();

    private PackagedResourceKeyType() {
        super(PackagedResourceKey.TYPE_ID, PackagedResourceKey.class,
                Component.translatable("key_type.ae2ltpp.packaged_resource"));
    }

    @Override public MapCodec<? extends AEKey> codec() { return PackagedResourceKey.CODEC; }
    @Override public int getAmountPerByte() { return 8; }
    @Override public int getAmountPerOperation() { return 1; }
    @Override public int getAmountPerUnit() { return 1; }
    @Override public AEKey readFromPacket(RegistryFriendlyByteBuf in) {
        return PackagedResourceKey.of(in.readResourceLocation());
    }
}
