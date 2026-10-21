package com.moakiee.ae2lt.packaged.client;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.resource.MaidPowerKey;
import com.moakiee.ae2lt.packaged.resource.MaidPowerKeyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MaidPowerRendering implements AEKeyRenderHandler<MaidPowerKey> {
    static void register() { AEKeyRendering.register(MaidPowerKeyType.INSTANCE, MaidPowerKey.class, new MaidPowerRendering()); }
    public static ItemStack icon() {
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("touhou_little_maid:power_point"));
        return (item == Items.AIR ? Items.REDSTONE : item).getDefaultInstance();
    }
    @Override public void drawInGui(Minecraft client, GuiGraphics graphics, int x, int y, MaidPowerKey key) {
        graphics.renderItem(icon(), x, y);
    }
    @Override public void drawOnBlockFace(PoseStack pose, MultiBufferSource buffers, MaidPowerKey key, float scale, int light, Level level) {
        AEKeyRendering.drawOnBlockFace(pose, buffers, AEItemKey.of(icon()), scale, light, level);
    }
    @Override public Component getDisplayName(MaidPowerKey key) { return key.getDisplayName(); }
}
