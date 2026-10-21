package com.moakiee.ae2lt.packaged.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.Settings;
import appeng.menu.SlotSemantics;
import static com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.SLOTS_PER_PAGE;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;

import com.moakiee.ae2lt.api.client.PatternProviderToolbarButtonHider;
import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;

public class PackagedPatternProviderScreen
        extends PatternProviderScreen<PackagedPatternProviderMenu> {
    private static final ResourceLocation CORE_SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LTPackagedProvider.MODID, "textures/gui/packaged_core_slot.png");

    private final PackagedProviderTextureButton autoReturnButton;
    private final PackagedProviderTextureButton modeButton;
    private Button previousPage;
    private Button nextPage;

    public PackagedPatternProviderScreen(PackagedPatternProviderMenu menu, Inventory playerInventory,
                                         Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        var frequencyButton = PackagedProviderTextureButton.frequencyBinding(
                () -> FrequencyApi.openBindingScreen(menu));
        frequencyButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.frequency.bind")));
        frequencyButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.frequency.bind")));
        addToLeftToolbar(frequencyButton);

        autoReturnButton = PackagedProviderTextureButton.autoReturn(
                menu::clientToggleAutoReturn);
        autoReturnButton.setTooltipOn(List.of(
                Component.translatable("ae2ltpp.gui.auto_return.on")));
        autoReturnButton.setTooltipOff(List.of(
                Component.translatable("ae2ltpp.gui.auto_return.off")));
        addToLeftToolbar(autoReturnButton);

        modeButton = PackagedProviderTextureButton.providerMode(menu::clientToggleMode);
        modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        if (menu.isModeSwitchVisible()) {
            addToLeftToolbar(modeButton);
        }

        var adapterSlot = menu.getAdapterSlot();
        if (adapterSlot != null) {
            widgets.add("adapterCard", new UpgradesPanel(List.of(adapterSlot), List::of));
        }
        widgets.add("machineRecognition", new MachineRecognitionWidget(menu));
        widgets.add("wirelessConnectionHint", new WirelessConnectionHintWidget(menu));
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot == menu.getAdapterSlot() && slot.isActive() && !slot.hasItem()) {
            guiGraphics.blit(CORE_SLOT_TEXTURE, slot.x, slot.y, 0, 0, 16, 16, 16, 16);
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected void init() {
        super.init();
        hideUnsupportedToolbarButtons();
        var patternSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        for (int i = SLOTS_PER_PAGE; i < patternSlots.size(); i++) {
            var origin = patternSlots.get(i % SLOTS_PER_PAGE);
            patternSlots.get(i).x = origin.x;
            patternSlots.get(i).y = origin.y;
        }
        if (menu.getTotalPages() > 1) {
            previousPage = addRenderableWidget(Button.builder(Component.literal("<"), b -> menu.clientPrevPage())
                    .bounds(leftPos + 106, topPos + 28, 14, 12)
                    .tooltip(Tooltip.create(Component.translatable("ae2ltpp.gui.previous_page"))).build());
            nextPage = addRenderableWidget(Button.builder(Component.literal(">"), b -> menu.clientNextPage())
                    .bounds(leftPos + 148, topPos + 28, 14, 12)
                    .tooltip(Tooltip.create(Component.translatable("ae2ltpp.gui.next_page"))).build());
        }
    }

    private void hideUnsupportedToolbarButtons() {
        for (var listener : children()) {
            if (!(listener instanceof AbstractWidget widget)) {
                continue;
            }
            boolean hide = PatternProviderToolbarButtonHider
                    .shouldHideToolbarButtonClassName(widget.getClass().getName());
            if (widget instanceof SettingToggleButton<?> settingButton) {
                var setting = settingButton.getSetting();
                hide |= setting == Settings.BLOCKING_MODE
                        || (setting != null
                        && PatternProviderToolbarButtonHider
                                .shouldHideToolbarButtonSettingName(setting.getName()));
            }
            if (hide) {
                widget.visible = false;
                widget.active = false;
            }
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent(
                "dialog_title",
                Component.translatable(menu.getTitleTranslationKey()));
        autoReturnButton.setState(menu.isAutoReturnEnabled());
        modeButton.setState(menu.isWirelessMode());
        menu.showPage(menu.currentPage);
        setTextContent("interface_config", Component.empty());
        setTextContent("interface_stored_items", Component.translatable("gui.ae2.ReturnInventory"));
        if (previousPage != null) previousPage.active = menu.currentPage > 0;
        if (nextPage != null) nextPage.active = menu.currentPage + 1 < menu.getTotalPages();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (menu.getTotalPages() > 1) {
            String pageText = (menu.currentPage + 1) + "/" + menu.getTotalPages();
            graphics.drawString(font, pageText, 134 - font.width(pageText) / 2, 30, 0x404040, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.getTotalPages() > 1 && mouseX >= leftPos + 8 && mouseX < leftPos + 170
                && mouseY >= topPos + 28 && mouseY < topPos + 114 && scrollY != 0) {
            if (scrollY > 0) menu.clientPrevPage();
            else menu.clientNextPage();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
