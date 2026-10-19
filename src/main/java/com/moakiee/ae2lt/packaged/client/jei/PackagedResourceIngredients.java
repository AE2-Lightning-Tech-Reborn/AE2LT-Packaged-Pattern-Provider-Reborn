package com.moakiee.ae2lt.packaged.client.jei;

import java.util.List;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import com.moakiee.ae2lt.packaged.resource.PackagedResourceKey;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;

/** Real quantities for ghost slots; these are never obtainable inventory items. */
public final class PackagedResourceIngredients {
    public record Resource(PackagedResourceKey key, long amount) {}
    public static final IIngredientType<Resource> TYPE = () -> Resource.class;
    private PackagedResourceIngredients() {}
    public static void register(IModIngredientRegistration registration) {
        var values = ModList.get().isLoaded("forbidden_arcanus")
                ? ForbiddenEssenceResources.ALL.stream().map(key -> new Resource(key, 1)).toList() : List.<Resource>of();
        registration.register(TYPE, values, new Helper(), new Renderer());
        if (ModList.get().isLoaded("ae2jeiintegration")) Bridge.register();
    }
    private static final class Helper implements IIngredientHelper<Resource> {
        @Override public IIngredientType<Resource> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(Resource value) { return value.key().getDisplayName().getString(); }
        @Override public String getUniqueId(Resource value, UidContext context) { return value.key().getId().toString(); }
        @Override public ResourceLocation getResourceLocation(Resource value) { return value.key().getId(); }
        @Override public Resource copyIngredient(Resource value) { return value; }
        @Override public Resource normalizeIngredient(Resource value) { return new Resource(value.key(), 1); }
        @Override public long getAmount(Resource value) { return value.amount(); }
        @Override public Resource copyWithAmount(Resource value, long amount) { return new Resource(value.key(), amount); }
        @Override public boolean isValidIngredient(Resource value) { return value != null && value.key() != null && value.amount() > 0; }
        @Override public String getErrorInfo(Resource value) { return String.valueOf(value); }
    }
    private static final class Renderer implements IIngredientRenderer<Resource> {
        @Override public void render(GuiGraphics graphics, Resource value) {
            var mc = Minecraft.getInstance();
            AEKeyRendering.drawInGui(mc, graphics, 0, 0, value.key());
            if (value.amount() > 1) {
                String amount = value.amount() < 10000 ? Long.toString(value.amount()) : value.amount() / 1000 + "k";
                graphics.pose().pushPose();
                graphics.pose().scale(0.75F, 0.75F, 1);
                graphics.drawString(mc.font, amount, 22 - mc.font.width(amount), 13, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }
        @Override public List<Component> getTooltip(Resource value, TooltipFlag flags) {
            return List.of(value.key().getDisplayName(), Component.literal(Long.toString(value.amount())),
                    Component.translatable("ae2ltpp.resource.help"));
        }
    }
    /** Loaded only when the optional AE2 JEI bridge is present. */
    private static final class Bridge {
        static void register() {
            tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters.register(
                    new tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter<Resource>() {
                        @Override public IIngredientType<Resource> getIngredientType() { return TYPE; }
                        @Override public Resource getIngredientFromStack(GenericStack stack) {
                            return stack.what() instanceof PackagedResourceKey key ? new Resource(key, stack.amount()) : null;
                        }
                        @Override public GenericStack getStackFromIngredient(Resource value) {
                            return new GenericStack(value.key(), value.amount());
                        }
                    });
        }
    }
}
