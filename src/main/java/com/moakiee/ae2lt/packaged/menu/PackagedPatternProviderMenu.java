package com.moakiee.ae2lt.packaged.menu;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingMenuHost;
import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ProviderMode;
import static com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.SLOTS_PER_PAGE;

public class PackagedPatternProviderMenu extends PatternProviderMenu
        implements FrequencyBindingMenuHost {
    public static final MenuType<PackagedPatternProviderMenu> TYPE = MenuTypeBuilder
            .create(PackagedPatternProviderMenu::new, PatternProviderLogicHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LTPackagedProvider.MODID, "packaged_pattern_provider"));

    @GuiSync(22000)
    public int returnMode;

    @GuiSync(22001)
    public String titleTranslationKey =
            "ae2ltpp.gui.title.packaged_pattern_provider";

    @GuiSync(22002)
    public int providerMode;

    @GuiSync(22003)
    public int currentPage;

    @GuiSync(22005)
    public int recognizedMachineCount;

    @GuiSync(22006)
    public Component recognizedMachineNames = Component.empty();

    private final PackagedPatternProviderBlockEntity host;
    private final Slot adapterSlot;
    private int recognitionRefreshTicks;

    public PackagedPatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);

        if (!(host instanceof PackagedPatternProviderBlockEntity packagedHost)) {
            throw new IllegalArgumentException("Packaged provider menu opened for non-packaged host: " + host);
        }
        this.host = packagedHost;

        var adapterSlot = new AdapterCardSlot(packagedHost.getAdapterInv(), 0);
        adapterSlot.setNotDraggable();
        adapterSlot.setEmptyTooltip(() -> List.of(Component.translatable("ae2ltpp.gui.adapter_slot"),
                Component.translatable("ae2ltpp.gui.adapter_slot.help")));
        this.adapterSlot = addSlot(adapterSlot, PackagedPatternProviderSlotSemantics.ADAPTER_CARD);

        registerClientAction("toggleAutoReturn", this::toggleAutoReturn);
        registerClientAction("toggleMode", this::toggleMode);
        registerClientAction("nextPage", () -> changePage(1));
        registerClientAction("prevPage", () -> changePage(-1));
        showPage(0);
    }

    public Slot getAdapterSlot() {
        return adapterSlot;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            returnMode = host.getReturnMode().ordinal();
            providerMode = host.getProviderMode().ordinal();
            titleTranslationKey = host.ae2lt$titleTranslationKey();
            if (recognitionRefreshTicks-- <= 0 && host.getLogic() instanceof PackagedPatternProviderLogic logic) {
                var recognition = logic.getMachineRecognition();
                recognizedMachineCount = recognition.count();
                recognizedMachineNames = recognition.names();
                recognitionRefreshTicks = 9;
            }
        }
        showPage(currentPage);
        super.broadcastChanges();
    }

    public int getTotalPages() {
        return Math.max(1, (host.getPatternSlotCount() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    public void showPage(int page) {
        currentPage = Math.clamp(page, 0, getTotalPages() - 1);
        var patternSlots = getSlots(SlotSemantics.ENCODED_PATTERN);
        int start = currentPage * SLOTS_PER_PAGE;
        for (int i = 0; i < patternSlots.size(); i++) {
            if (patternSlots.get(i) instanceof AppEngSlot slot) {
                slot.setActive(i >= start && i < start + SLOTS_PER_PAGE);
            }
        }
    }

    private void changePage(int delta) {
        if (isServerSide()) {
            showPage(currentPage + delta);
        }
    }

    private void toggleMode() {
        if (isServerSide() && host.isProviderModeSwitchable()) {
            host.setProviderMode(host.isWirelessProvider() ? ProviderMode.NORMAL : ProviderMode.WIRELESS);
        }
    }

    public boolean isModeSwitchVisible() {
        return host.isProviderModeSwitchable();
    }

    public boolean isWirelessMode() {
        return providerMode == ProviderMode.WIRELESS.ordinal();
    }

    public void clientToggleMode() {
        sendClientAction("toggleMode");
    }

    public void clientNextPage() {
        sendClientAction("nextPage");
    }

    public void clientPrevPage() {
        sendClientAction("prevPage");
    }

    private void toggleAutoReturn() {
        if (isServerSide()) {
            host.setReturnMode(
                    host.getReturnMode() == ReturnMode.AUTO
                            ? ReturnMode.OFF
                            : ReturnMode.AUTO);
        }
    }

    public void clientToggleAutoReturn() {
        sendClientAction("toggleAutoReturn");
    }

    public boolean isAutoReturnEnabled() {
        return returnMode == ReturnMode.AUTO.ordinal();
    }

    public String getTitleTranslationKey() {
        return titleTranslationKey;
    }

    private static final class AdapterCardSlot extends AppEngSlot {
        private AdapterCardSlot(InternalInventory inv, int invSlot) {
            super(inv, invSlot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof MultiblockAdapterItem && super.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
