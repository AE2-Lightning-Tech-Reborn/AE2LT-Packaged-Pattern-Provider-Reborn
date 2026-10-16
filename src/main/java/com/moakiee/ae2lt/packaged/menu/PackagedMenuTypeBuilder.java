package com.moakiee.ae2lt.packaged.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.packaged.mixin.MenuTypeBuilderAccessor;

/**
 * Builds an addon-owned menu type from AE2's network factory and opener.
 * AE2 1.20.1 build(String) forces the ae2 namespace and its own registration list.
 */
public final class PackagedMenuTypeBuilder {

    private PackagedMenuTypeBuilder() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <M extends AEBaseMenu, I> MenuType<M> buildUnregistered(
            MenuTypeBuilder<M, I> builder, ResourceLocation id) {
        MenuTypeBuilderAccessor<M, I> accessor = (MenuTypeBuilderAccessor) (Object) builder;
        if (accessor.ae2ltpp$getMenuType() != null) {
            throw new IllegalStateException("buildUnregistered already called for " + id);
        }
        accessor.ae2ltpp$setId(id);
        MenuType<M> menuType = IForgeMenuType.create(accessor::ae2ltpp$invokeFromNetwork);
        accessor.ae2ltpp$setMenuType(menuType);
        MenuOpener.addOpener(menuType, accessor::ae2ltpp$invokeOpen);
        return menuType;
    }
}
