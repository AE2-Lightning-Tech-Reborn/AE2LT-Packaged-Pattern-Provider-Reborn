package com.moakiee.ae2lt.packaged.client.jei;

import java.util.List;
import appeng.api.stacks.GenericStack;
import appeng.api.integrations.jei.*;
import com.moakiee.ae2lt.packaged.client.ResourceRendering;
import com.moakiee.ae2lt.packaged.resource.*;
import mezz.jei.api.*;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;

/** AE2 15's public converter enables JEI drag/drop and processing-pattern encoding. */
@JeiPlugin
public final class ResourceJeiPlugin implements IModPlugin {
    public static final IIngredientType<GenericStack> TYPE = () -> GenericStack.class;
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation("ae2ltpp", "resources"); }
    @Override public void registerIngredients(IModIngredientRegistration registration) {
        var resources = new java.util.ArrayList<GenericStack>();
        if (ModList.get().isLoaded("forbidden_arcanus")) resources.addAll(
                ForbiddenEssenceResources.ALL.stream().map(key -> new GenericStack(key, 1)).toList());
        if (ModList.get().isLoaded("touhou_little_maid")) resources.add(new GenericStack(MaidPowerKey.INSTANCE, 100));
        if (resources.isEmpty()) return;
        IngredientConverters.register(new IngredientConverter<GenericStack>() {
            @Override public IIngredientType<GenericStack> getIngredientType() { return TYPE; }
            @Override public GenericStack getIngredientFromStack(GenericStack stack) {
                return (stack.what() instanceof PackagedResourceKey || stack.what() == MaidPowerKey.INSTANCE) ? stack : null;
            }
            @Override public GenericStack getStackFromIngredient(GenericStack stack) { return stack; }
        });
        registration.register(TYPE, resources,
                new IIngredientHelper<GenericStack>() {
                    @Override public IIngredientType<GenericStack> getIngredientType() { return TYPE; }
                    @Override public String getDisplayName(GenericStack stack) { return stack.what().getDisplayName().getString(); }
                    @Override public String getUniqueId(GenericStack stack, UidContext context) { return stack.what().getId().toString(); }
                    @Override public ResourceLocation getResourceLocation(GenericStack stack) { return stack.what().getId(); }
                    @Override public GenericStack copyIngredient(GenericStack stack) { return stack; }
                    @Override public long getAmount(GenericStack stack) { return stack.amount(); }
                    @Override public GenericStack copyWithAmount(GenericStack stack, long amount) { return new GenericStack(stack.what(), amount); }
                    @Override public boolean isValidIngredient(GenericStack stack) { return stack != null && (stack.what() instanceof PackagedResourceKey || stack.what() == MaidPowerKey.INSTANCE) && stack.amount() > 0; }
                    @Override public String getErrorInfo(GenericStack stack) { return String.valueOf(stack); }
                }, new IIngredientRenderer<GenericStack>() {
                    @Override public void render(GuiGraphics graphics, GenericStack stack) {
                        graphics.renderItem(stack.what() == MaidPowerKey.INSTANCE
                                ? com.moakiee.ae2lt.packaged.client.MaidPowerRendering.icon()
                                : ResourceRendering.icon((PackagedResourceKey) stack.what()), 0, 0);
                        if (stack.what() == MaidPowerKey.INSTANCE || stack.amount() > 1) {
                            String amount = stack.what() == MaidPowerKey.INSTANCE
                                    ? java.math.BigDecimal.valueOf(stack.amount(), 2).stripTrailingZeros().toPlainString()
                                    : stack.amount() < 10000 ? Long.toString(stack.amount()) : stack.amount() / 1000 + "k";
                            var font = Minecraft.getInstance().font;
                            graphics.pose().pushPose();
                            graphics.pose().translate(0, 0, 200);
                            graphics.pose().scale(0.75F, 0.75F, 1);
                            graphics.drawString(font, amount, 22 - font.width(amount), 13, 0xFFFFFF, true);
                            graphics.pose().popPose();
                        }
                    }
                    @Override public List<Component> getTooltip(GenericStack stack, TooltipFlag flag) {
                        return List.of(stack.what().getDisplayName(), Component.literal(stack.what() == MaidPowerKey.INSTANCE
                                ? java.math.BigDecimal.valueOf(stack.amount(), 2).stripTrailingZeros().toPlainString() + " P"
                                : Long.toString(stack.amount())));
                    }
                });
    }
}
