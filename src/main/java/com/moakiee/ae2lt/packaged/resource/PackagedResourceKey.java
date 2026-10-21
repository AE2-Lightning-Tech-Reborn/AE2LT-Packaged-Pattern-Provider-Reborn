package com.moakiee.ae2lt.packaged.resource;

import java.util.List;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A quantity of a named native resource; the quantity is never part of its identity. */
public final class PackagedResourceKey extends AEKey {
    public static final ResourceLocation TYPE_ID = new ResourceLocation("ae2ltpp:packaged_resource");
    // AE2 indexes primary keys by reference identity. Intern IDs on every decode
    // as well as creation; weak interning does not retain obsolete datapack IDs.
    private static final Interner<ResourceLocation> IDS = Interners.newWeakInterner();
    private final ResourceLocation id;

    private PackagedResourceKey(ResourceLocation id) { this.id = IDS.intern(id); }

    public static PackagedResourceKey of(ResourceLocation id) {
        return new PackagedResourceKey(java.util.Objects.requireNonNull(id));
    }

    @Override public AEKeyType getType() { return PackagedResourceKeyType.INSTANCE; }
    @Override public PackagedResourceKey dropSecondary() { return this; }
    @Override public Object getPrimaryKey() { return id; }
    @Override public ResourceLocation getId() { return id; }
    @Override public void writeToPacket(FriendlyByteBuf out) { out.writeResourceLocation(id); }

    @Override
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putString("resource", id.toString());
        return tag;
    }

    @Override
    protected Component computeDisplayName() {
        return ForbiddenEssenceResources.isEssence(this)
                ? Component.translatable("forbidden_arcanus.essence." + id.getPath())
                : Component.literal(id.toString());
    }

    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    @Override public boolean equals(Object other) { return other instanceof PackagedResourceKey key && id.equals(key.id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "PackagedResource[" + id + "]"; }
}
