package com.moakiee.ae2lt.packaged.client;

import java.util.List;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

/** Also keeps core ordering usable without JEI. */
public final class CoreOrderScreen extends appeng.client.gui.AESubScreen<PackagedPatternProviderMenu, PackagedPatternProviderScreen> {
    private final List<PackagedCoreDefinition> cores = PackagedCoreDefinition.visibleWhen(ModList.get()::isLoaded);
    private int page;
    public CoreOrderScreen(PackagedPatternProviderScreen parent) {
        super(parent, "/screens/core_order.json");
    }
    @Override protected void init() {
        super.init();
        clearWidgets();
        int x = width / 2 - 120, y = height / 2 - 100;
        for (int i = page * 6; i < Math.min(cores.size(), page * 6 + 6); i++) {
            var item = cores.get(i).runtimeItem().get();
            addRenderableWidget(Button.builder(item.getName(item.getDefaultInstance()), b -> menu.clientOrderCore(BuiltInRegistries.ITEM.getKey(item)))
                    .bounds(x, y + 24 + (i % 6) * 22, 240, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(x, y + 160, 25, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = Math.max(0, Math.min((cores.size() - 1) / 6, page + 1)); rebuildWidgets(); }).bounds(x + 215, y + 160, 25, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ae2ltpp.core_order.cancel"), b -> menu.clientCancelCore()).bounds(x + 30, y + 160, 85, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(x + 120, y + 160, 85, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        super.render(graphics, mx, my, partialTick);
        graphics.drawCenteredString(font, Component.translatable("ae2ltpp.jei.core_title"), width / 2, height / 2 - 100, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("ae2ltpp.core_order." + menu.coreOrderStatus), width / 2, height / 2 + 88, 0xFFFFFF);
    }
    @Override public void onClose() { returnToParent(); }
    @Override public boolean isPauseScreen() { return false; }
}
