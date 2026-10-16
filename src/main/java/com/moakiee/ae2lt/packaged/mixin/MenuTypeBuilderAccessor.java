package com.moakiee.ae2lt.packaged.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocator;

/**
 * Exposes AE2 menu wiring for addon registration without calling
 * MenuTypeBuilder.build(String), which forces the ae2 namespace.
 */
@Mixin(MenuTypeBuilder.class)
public interface MenuTypeBuilderAccessor<M extends AEBaseMenu, I> {
    @Accessor(value = "id", remap = false)
    void ae2ltpp$setId(ResourceLocation id);

    @Accessor(value = "menuType", remap = false)
    void ae2ltpp$setMenuType(MenuType<M> menuType);

    @Accessor(value = "menuType", remap = false)
    MenuType<M> ae2ltpp$getMenuType();

    @Invoker(value = "fromNetwork", remap = false)
    M ae2ltpp$invokeFromNetwork(int containerId, Inventory inv, FriendlyByteBuf packetBuf);

    @Invoker(value = "open", remap = false)
    boolean ae2ltpp$invokeOpen(Player player, MenuLocator locator, boolean fromSubMenu);
}
