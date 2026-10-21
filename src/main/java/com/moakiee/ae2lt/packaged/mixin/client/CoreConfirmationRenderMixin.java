package com.moakiee.ae2lt.packaged.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import com.moakiee.ae2lt.packaged.client.CoreOrderConfirmationUi;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class CoreConfirmationRenderMixin {
    // After the virtual update (including subclasses), but before drawing or handling button tooltips.
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/AEBaseScreen;updateBeforeRender()V", shift = At.Shift.AFTER))
    private void ae2ltpp$coreConfirmation(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if ((Object) this instanceof CraftConfirmScreen screen) CoreOrderConfirmationUi.update(screen);
    }
}
