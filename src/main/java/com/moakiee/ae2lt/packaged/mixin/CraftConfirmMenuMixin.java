package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.menu.CoreOrderConfirmation;
import appeng.api.networking.IGrid;
import appeng.menu.guisync.GuiSync;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.packaged.logic.TerminalCoreOrders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuMixin implements CoreOrderConfirmation {
    @Shadow private ICraftingPlan result;
    @Shadow private IGrid getGrid() { throw new AssertionError(); }
    @Unique @GuiSync(22010)
    private String ae2ltpp$coreMode;
    @Unique @GuiSync(22011)
    private Component ae2ltpp$coreDescription;
    @Unique @GuiSync(22012)
    private boolean ae2ltpp$coreBlocked;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2ltpp$initializeCoreDestination(CallbackInfo ci) {
        // Mixin's line-number-based initializer extraction can omit the first of these
        // annotated field initializers. Initialize explicitly before the first client render
        // or server GUI sync; the client may render before receiving its first sync packet.
        ae2ltpp$coreMode = "";
        ae2ltpp$coreDescription = Component.empty();
    }

    // Rendering must also be safe before the client has usable synchronized state.
    @Override public String ae2ltpp$coreMode() {
        return ae2ltpp$coreMode == null ? "" : ae2ltpp$coreMode;
    }
    @Override public Component ae2ltpp$coreDescription() {
        return ae2ltpp$coreDescription == null ? Component.empty() : ae2ltpp$coreDescription;
    }
    @Override public boolean ae2ltpp$coreBlocked() { return ae2ltpp$coreBlocked; }

    @Unique
    private void ae2ltpp$refreshCoreDestination() {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide()) return;
        var grid = getGrid();
        var decision = TerminalCoreOrders.inspect(grid == null ? null : grid.getCraftingService(), result);
        ae2ltpp$coreMode = decision.mode();
        ae2ltpp$coreDescription = decision.description();
        ae2ltpp$coreBlocked = decision.blocked();
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void ae2ltpp$syncCoreDestination(CallbackInfo ci) {
        ae2ltpp$refreshCoreDestination();
    }

    @Inject(method = "startJob", at = @At("HEAD"), cancellable = true)
    private void ae2ltpp$validateCoreDestination(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide()) return;
        ae2ltpp$refreshCoreDestination();
        if (ae2ltpp$coreBlocked) {
            menu.setAutoStart(false);
            menu.clearError();
            menu.getPlayer().displayClientMessage(ae2ltpp$coreDescription, false);
            ci.cancel();
        }
    }

    @WrapOperation(method = "startJob", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingService;submitJob("
                    + "Lappeng/api/networking/crafting/ICraftingPlan;"
                    + "Lappeng/api/networking/crafting/ICraftingRequester;"
                    + "Lappeng/api/networking/crafting/ICraftingCPU;Z"
                    + "Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/networking/crafting/ICraftingSubmitResult;"))
    private ICraftingSubmitResult ae2ltpp$installTerminalCore(ICraftingService service, ICraftingPlan plan,
            ICraftingRequester requester, ICraftingCPU cpu, boolean prioritizePower, IActionSource source,
            Operation<ICraftingSubmitResult> original) {
        return TerminalCoreOrders.submit(service, plan, requester,
                target -> original.call(service, plan, target, cpu, prioritizePower, source));
    }
}
