package com.moakiee.ae2lt.packaged.client;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.resource.PackagedResourceKey;
import com.moakiee.ae2lt.packaged.resource.PackagedResourceKeyType;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class PackagedResourceRendering implements AEKeyRenderHandler<PackagedResourceKey> {
    public static void register() {
        if (ModList.get().isLoaded("touhou_little_maid")) {
            MaidPowerRendering.register();
        }
        AEKeyRendering.register(PackagedResourceKeyType.INSTANCE, PackagedResourceKey.class, new PackagedResourceRendering());
    }
    @Override public void drawInGui(Minecraft client, GuiGraphics graphics, int x, int y, PackagedResourceKey key) {
        if (ModList.get().isLoaded("forbidden_arcanus") && ForbiddenEssenceResources.isEssence(key)) {
            graphics.blitSprite(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "icon/" + key.getId().getPath()), x, y, 16, 16);
        } else graphics.renderItem(Items.AMETHYST_SHARD.getDefaultInstance(), x, y);
    }
    @Override public void drawOnBlockFace(PoseStack pose, MultiBufferSource buffers, PackagedResourceKey key,
                                         float scale, int light, Level level) {
        AEKeyRendering.drawOnBlockFace(pose, buffers, AEItemKey.of(switch (key.getId().getPath()) {
            case "aureal" -> Items.GLOWSTONE_DUST;
            case "souls" -> Items.SOUL_LANTERN;
            case "blood" -> Items.REDSTONE;
            case "experience" -> Items.EXPERIENCE_BOTTLE;
            default -> Items.AMETHYST_SHARD;
        }), scale, light, level);
    }
    @Override public Component getDisplayName(PackagedResourceKey key) { return key.getDisplayName(); }
}
