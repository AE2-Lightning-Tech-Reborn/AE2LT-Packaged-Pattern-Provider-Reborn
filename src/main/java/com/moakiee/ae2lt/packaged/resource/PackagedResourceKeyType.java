package com.moakiee.ae2lt.packaged.resource;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class PackagedResourceKeyType extends AEKeyType {
    public static final PackagedResourceKeyType INSTANCE = new PackagedResourceKeyType();

    private PackagedResourceKeyType() {
        super(PackagedResourceKey.TYPE_ID, PackagedResourceKey.class,
                Component.translatable("key_type.ae2ltpp.packaged_resource"));
    }

    @Override public AEKey loadKeyFromTag(net.minecraft.nbt.CompoundTag tag) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("resource"));
        return id == null ? null : PackagedResourceKey.of(id);
    }
    @Override public int getAmountPerByte() { return 8; }
    @Override public int getAmountPerOperation() { return 1; }
    @Override public int getAmountPerUnit() { return 1; }
    @Override public AEKey readFromPacket(FriendlyByteBuf in) {
        return PackagedResourceKey.of(in.readResourceLocation());
    }
}
