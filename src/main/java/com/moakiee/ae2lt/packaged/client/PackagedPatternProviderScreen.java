package com.moakiee.ae2lt.packaged.client;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import com.moakiee.ae2lt.packaged.client.compat.Button;
import com.moakiee.ae2lt.packaged.client.compat.Tooltip;
import appeng.menu.SlotSemantics;
import static com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.SLOTS_PER_PAGE;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.config.Settings;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;

import com.moakiee.ae2lt.api.client.PatternProviderToolbarButtonHider;
import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;

public class PackagedPatternProviderScreen
        extends PatternProviderScreen<PackagedPatternProviderMenu> {
    private static final Gson STYLE_COPY = new GsonBuilder()
            .registerTypeHierarchyAdapter(Component.class, new Component.Serializer())
            .create();
    private static final net.minecraft.resources.ResourceLocation CORE_SLOT_TEXTURE =
            new net.minecraft.resources.ResourceLocation("ae2ltpp", "textures/gui/packaged_core_slot.png");
    private final PackagedProviderTextureButton autoReturnButton;
    private final PackagedProviderTextureButton modeButton;
    private Button previousPage;
    private Button nextPage;
    private boolean compactLayout;

    public PackagedPatternProviderScreen(PackagedPatternProviderMenu menu, Inventory playerInventory,
                                         Component title, ScreenStyle style) {
        super(menu, playerInventory, title, copyStyle(style));

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
    }

    @Override
    protected void init() {
        compactLayout = height < 255;
        imageHeight = style.getBackground().getSrcHeight() - (compactLayout ? 15 : 0);
        int offset = compactLayout ? 10 : 0;
        style.getSlots().get("ENCODED_PATTERN").setTop(42 - offset);
        style.getSlots().get("STORAGE").setTop(127 - offset);
        style.getSlots().get("AE2LTPP_ADAPTER_CARD").setTop(78 - offset);
        style.getText().get("interface_config").getPosition().setTop(31 - offset);
        style.getText().get("interface_stored_items").getPosition().setTop(116 - offset);
        style.getWidget("adapterCard").setTop(72 - offset);
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
                    .bounds(leftPos + 106, topPos + 28 - offset, 14, 12)
                    .tooltip(Tooltip.create(Component.translatable("ae2ltpp.gui.previous_page"))).build());
            nextPage = addRenderableWidget(Button.builder(Component.literal(">"), b -> menu.clientNextPage())
                    .bounds(leftPos + 148, topPos + 28 - offset, 14, 12)
                    .tooltip(Tooltip.create(Component.translatable("ae2ltpp.gui.next_page"))).build());
        }
    }

    static ScreenStyle copyStyle(ScreenStyle style) {
        return STYLE_COPY.fromJson(STYLE_COPY.toJson(style), ScreenStyle.class);
    }

    @Override
    public void drawBG(PoseStack graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        if (!compactLayout) {
            super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
            return;
        }
        // Remove blank bands while keeping slot textures at their native size.
        var background = style.getBackground();
        int x = background.getSrcX();
        int y = background.getSrcY();
        int width = background.getSrcWidth();
        background.copy().src(x, y, width, 20).dest(offsetX, offsetY).blit(graphics, 0);
        background.copy().src(x, y + 30, width, 115)
                .dest(offsetX, offsetY + 20).blit(graphics, 0);
        background.copy().src(x, y + 150, width, background.getSrcHeight() - 150)
                .dest(offsetX, offsetY + 135).blit(graphics, 0);
    }

    @Override
    public void renderSlot(com.mojang.blaze3d.vertex.PoseStack graphics, net.minecraft.world.inventory.Slot slot) {
        if (slot == menu.getAdapterSlot() && slot.isActive() && !slot.hasItem()) {
            com.moakiee.ae2lt.packaged.client.compat.LegacyGui.blit(graphics, CORE_SLOT_TEXTURE, slot.x, slot.y, 0, 0, 16, 16, 16, 16);
        }
        super.renderSlot(graphics, slot);
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
        if (previousPage != null) previousPage.active = menu.currentPage > 0;
        if (nextPage != null) nextPage.active = menu.currentPage + 1 < menu.getTotalPages();
    }

    @Override
    public void drawFG(PoseStack graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (menu.getTotalPages() > 1) {
            String pageText = (menu.currentPage + 1) + "/" + menu.getTotalPages();
            com.moakiee.ae2lt.packaged.client.compat.LegacyGui.drawString(graphics, font, pageText, 134 - font.width(pageText) / 2,
                    30 - (compactLayout ? 10 : 0), 0x404040, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (menu.getTotalPages() > 1 && mouseX >= leftPos + 8 && mouseX < leftPos + 170
                && mouseY >= topPos + 28 - (compactLayout ? 10 : 0)
                && mouseY < topPos + 114 - (compactLayout ? 10 : 0) && scrollY != 0) {
            if (scrollY > 0) menu.clientPrevPage();
            else menu.clientNextPage();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
}
