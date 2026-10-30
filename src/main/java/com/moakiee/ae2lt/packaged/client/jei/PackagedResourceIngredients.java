package com.moakiee.ae2lt.packaged.client.jei;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import com.moakiee.ae2lt.packaged.resource.PackagedResourceKey;
import com.moakiee.ae2lt.packaged.resource.MaidPowerKey;
import appeng.api.stacks.AEKey;
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
    public record Resource(AEKey key, long amount) {}
    public static final IIngredientType<Resource> TYPE = new IIngredientType<>() {
        @Override public Class<? extends Resource> getIngredientClass() { return Resource.class; }
        @Override public String getUid() { return "ae2ltpp:resource"; }
    };
    public static final Codec<Resource> CODEC = GenericStack.CODEC.flatXmap(
            stack -> supported(stack.what()) && stack.amount() > 0
                    ? DataResult.success(new Resource(stack.what(), stack.amount()))
                    : DataResult.error(() -> "Invalid packaged JEI resource"),
            value -> value != null && supported(value.key()) && value.amount() > 0
                    ? DataResult.success(new GenericStack(value.key(), value.amount()))
                    : DataResult.error(() -> "Invalid packaged JEI resource"));
    private PackagedResourceIngredients() {}
    private static boolean supported(AEKey key) { return key instanceof PackagedResourceKey || key instanceof MaidPowerKey; }
    public static void register(IModIngredientRegistration registration) {
        var values = new java.util.ArrayList<Resource>();
        if (ModList.get().isLoaded("forbidden_arcanus")) ForbiddenEssenceResources.ALL.forEach(key -> values.add(new Resource(key, 1)));
        if (ModList.get().isLoaded("touhou_little_maid")) values.add(new Resource(MaidPowerKey.INSTANCE, 100));
        registration.register(TYPE, values, new Helper(), new Renderer(), CODEC);
        if (ModList.get().isLoaded("ae2jeiintegration")) Bridge.register();
    }
    private static final class Helper implements IIngredientHelper<Resource> {
        @Override public IIngredientType<Resource> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(Resource value) { return value.key().getDisplayName().getString(); }
        // Still required by older JEI 19.x; newer versions use the object UID below.
        @SuppressWarnings("removal")
        @Override public String getUniqueId(Resource value, UidContext context) { return value.key().getId().toString(); }
        @Override public Object getUid(Resource value, UidContext context) { return value.key(); }
        @Override public ResourceLocation getResourceLocation(Resource value) { return value.key().getId(); }
        @Override public Resource copyIngredient(Resource value) { return value; }
        @Override public Resource normalizeIngredient(Resource value) { return new Resource(value.key(), 1); }
        @Override public long getAmount(Resource value) { return value.amount(); }
        @Override public Resource copyWithAmount(Resource value, long amount) { return new Resource(value.key(), amount); }
        @Override public boolean isValidIngredient(Resource value) { return value != null && supported(value.key()) && value.amount() > 0; }
        @Override public String getErrorInfo(Resource value) { return String.valueOf(value); }
    }
    private static final class Renderer implements IIngredientRenderer<Resource> {
        @Override public void render(GuiGraphics graphics, Resource value) {
            var mc = Minecraft.getInstance();
            AEKeyRendering.drawInGui(mc, graphics, 0, 0, value.key());
            if (value.amount() > 1) {
                String amount = value.key() == MaidPowerKey.INSTANCE ? java.math.BigDecimal.valueOf(value.amount(), 2).stripTrailingZeros().toPlainString()
                        : value.amount() < 10000 ? Long.toString(value.amount()) : value.amount() / 1000 + "k";
                graphics.pose().pushPose();
                try {
                    // Item icons render above z=0. Match vanilla item decorations so
                    // buffered icons cannot cover the quantity, including in scroll grids.
                    graphics.pose().translate(0, 0, 200);
                    graphics.drawString(mc.font, amount, 17 - mc.font.width(amount), 9, 0xFFFFFF, true);
                } finally {
                    graphics.pose().popPose();
                }
            }
        }
        @SuppressWarnings("removal")
        @Override public List<Component> getTooltip(Resource value, TooltipFlag flags) {
            return List.of(value.key().getDisplayName(), Component.literal(value.key() == MaidPowerKey.INSTANCE
                    ? java.math.BigDecimal.valueOf(value.amount(), 2).stripTrailingZeros().toPlainString() + " P" : Long.toString(value.amount())),
                    Component.translatable("ae2ltpp.resource.help"));
        }
    }
    /** Loaded only when the optional AE2 JEI bridge is present. */
    private static final class Bridge {
        private static boolean registered;
        static synchronized void register() {
            if (registered) return;
            tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters.register(
                    new tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter<Resource>() {
                        @Override public IIngredientType<Resource> getIngredientType() { return TYPE; }
                        @Override public Resource getIngredientFromStack(GenericStack stack) {
                            return stack.what() instanceof PackagedResourceKey || stack.what() instanceof MaidPowerKey
                                    ? new Resource(stack.what(), stack.amount()) : null;
                        }
                        @Override public GenericStack getStackFromIngredient(Resource value) {
                            return new GenericStack(value.key(), value.amount());
                        }
                    });
            registered = true;
        }
    }
}
