package com.moakiee.ae2lt.packaged.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.TextOverride;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.Text;
import com.moakiee.ae2lt.packaged.client.BoundedScreenTitle;
import com.moakiee.ae2lt.packaged.client.CoreOrderConfirmationUi;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class CoreConfirmationRenderMixin {
    @Unique
    private final BoundedScreenTitle ae2ltpp$boundedTitle = new BoundedScreenTitle();

    // After the virtual update (including subclasses), but before drawing or handling button tooltips.
    @Inject(method = "render", remap = true, at = @At(value = "INVOKE", remap = false,
            target = "Lappeng/client/gui/AEBaseScreen;updateBeforeRender()V", shift = At.Shift.AFTER))
    private void ae2ltpp$coreConfirmation(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ae2ltpp$boundedTitle.reset();
        if ((Object) this instanceof CraftConfirmScreen screen) CoreOrderConfirmationUi.update(screen);
    }

    @Inject(method = "drawText", at = @At("HEAD"), cancellable = true)
    private void ae2ltpp$drawBoundedTitle(GuiGraphics graphics, Text text, TextOverride override, CallbackInfo ci) {
        if (ae2ltpp$boundedTitle.draw((AEBaseScreen<?>) (Object) this, graphics, text, override)) ci.cancel();
    }

    @Inject(method = "render", remap = true, at = @At("TAIL"))
    private void ae2ltpp$titleTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ae2ltpp$boundedTitle.renderTooltip((AEBaseScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }
}
