package com.moakiee.ae2lt.packaged.client;

import appeng.api.client.*;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ResourceRendering {
    private ResourceRendering() {}
    public static ItemStack icon(PackagedResourceKey key) {
        String item = switch (key.getId().getPath()) {
            case "aureal" -> "aureal_bottle";
            case "souls" -> "soul";
            case "blood" -> "blood_test_tube";
            default -> "xpetrified_orb";
        };
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("forbidden_arcanus", item)));
    }
    public static void register() {
        MaidPowerRendering.register();
        AEKeyRendering.register(PackagedResourceKeyType.INSTANCE, PackagedResourceKey.class, new AEKeyRenderHandler<>() {
            @Override public void drawInGui(Minecraft mc, GuiGraphics graphics, int x, int y, PackagedResourceKey key) {
                graphics.renderItem(icon(key), x, y);
            }
            @Override public void drawOnBlockFace(com.mojang.blaze3d.vertex.PoseStack pose,
                    net.minecraft.client.renderer.MultiBufferSource buffers, PackagedResourceKey key, float scale, int light,
                    net.minecraft.world.level.Level level) {
                AEKeyRendering.drawOnBlockFace(pose, buffers, appeng.api.stacks.AEItemKey.of(icon(key)), scale, light, level);
            }
            @Override public Component getDisplayName(PackagedResourceKey key) { return key.getDisplayName(); }
        });
    }
}
