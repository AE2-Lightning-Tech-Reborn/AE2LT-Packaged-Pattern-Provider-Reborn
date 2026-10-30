package com.moakiee.ae2lt.packaged.mixin.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public interface CraftConfirmScreenAccessor {
    @Accessor("start") Button ae2ltpp$startButton();
}
