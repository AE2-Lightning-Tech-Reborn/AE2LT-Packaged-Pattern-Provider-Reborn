package com.moakiee.ae2lt.packaged.blockentity;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEItemKey;
import appeng.api.networking.IGridNodeListener;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.api.pattern.PatternProviderUiProfile;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.item.ResonantPackagedCoreItem;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.PackagedCoreBootstrap;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity;

public class PackagedPatternProviderBlockEntity extends StablePatternProviderBlockEntity
        implements FrequencyBindingHost, PatternProviderUiProfile, AdapterPersistentScope {

    private static final String TAG_ADAPTER_INV = "ae2ltpp_adapter_inv";
    private static final String TAG_ADAPTER_FLAGS = "ae2ltpp_adapter_flags";

    /**
     * Per-(key, target-position) boolean flag store backing
     * {@link AdapterPersistentScope}. We keep it as a flat {@code key -> set of
     * packed BlockPos longs} map so the on-disk shape is the cheapest possible
     * (one {@code long[]} per key, mostly empty in practice), and so the same
     * provider can host flags from several adapters concurrently without
     * clashing &mdash; namespacing is the adapter's responsibility, which is
     * spelled out in {@link AdapterPersistentScope}.
     */
    private final java.util.HashMap<String, java.util.HashSet<Long>> adapterFlags = new java.util.HashMap<>();
    // Keep recorded anchor types with their links, so temporarily removing a core never erases those links.
    private final java.util.Map<Long, net.minecraft.resources.ResourceLocation> resonantAnchorBlocks = new java.util.HashMap<>();
    private static final String TAG_RESONANT_ANCHORS = "ae2ltpp_resonant_anchors";

    private final com.moakiee.ae2lt.packaged.logic.PackagedCoreOrder coreOrder =
            new com.moakiee.ae2lt.packaged.logic.PackagedCoreOrder(this);

    public com.moakiee.ae2lt.packaged.logic.PackagedCoreOrder getCoreOrder() { return coreOrder; }

    private final ProviderMode fixedProviderMode;
    private final FrequencyBindingAccess frequencyBinding = FrequencyApi.createBinding(this);

    @Override
    public boolean acceptsWirelessTarget(Level targetLevel, BlockPos pos) {
        if (!isWirelessProvider() || !(targetLevel instanceof ServerLevel serverLevel)
                || !targetLevel.isLoaded(pos)) return false;
        var core = adapterInv.getStackInSlot(0);
        if (core.getItem() instanceof ResonantPackagedCoreItem) {
            var layout = ResonantPackagedCoreItem.layout(core);
            return layout != null && !pos.equals(getBlockPos()) && layout.matchesAnchor(targetLevel.getBlockState(pos));
        }
        var adapter = MultiblockAdapterRegistry.find(serverLevel, pos, serverLevel.getBlockEntity(pos));
        if (adapter == null) return false;
        var required = adapter.requiredAdapterId(serverLevel, pos);
        if (MultiblockAdapterItem.stackCovers(core, required)) return true;
        // Put the core-manufacturing pattern in first, then bind its machine.
        // Binding grants no general access: every dispatch rechecks the core gate.
        return getLogic().getAvailablePatterns().stream().anyMatch(pattern ->
                PackagedCoreBootstrap.allows(pattern, required)
                        && adapter.bind(serverLevel, pos, pattern) != null);
    }

    @Override
    public Component getWirelessTargetRejectionMessage() {
        return Component.translatable(adapterInv.getStackInSlot(0).isEmpty()
                ? "ae2ltpp.connector.core_required" : "ae2ltpp.connector.core_mismatch");
    }

    @Override
    public boolean addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
        if (level == null || !level.dimension().equals(dimension) || !acceptsWirelessTarget(level, pos)
                || !super.addOrUpdateConnection(dimension, pos, boundFace)) return false;
        if (ResonantPackagedCoreItem.layout(adapterInv.getStackInSlot(0)) != null) {
            resonantAnchorBlocks.put(pos.asLong(), net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock()));
        } else resonantAnchorBlocks.remove(pos.asLong());
        pruneResonantAnchorRecords();
        saveChanges();
        return true;
    }

    @Override
    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        if (!super.removeConnection(dimension, pos)) return false;
        resonantAnchorBlocks.remove(pos.asLong());
        saveChanges();
        return true;
    }

    private void pruneResonantAnchorRecords() {
        var positions = new java.util.HashSet<Long>();
        for (var connection : getConnections()) positions.add(connection.pos().asLong());
        resonantAnchorBlocks.keySet().retainAll(positions);
    }

    @Override
    protected boolean isWirelessTargetPresent(ServerLevel targetLevel, BlockPos pos) {
        var recorded = resonantAnchorBlocks.get(pos.asLong());
        if (recorded != null) return recorded.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(targetLevel.getBlockState(pos).getBlock()));
        return MultiblockAdapterRegistry.find(targetLevel, pos, targetLevel.getBlockEntity(pos)) != null;
    }

    /**
     * Single-slot inventory holding the {@link MultiblockAdapterItem} packaged core
     * that unlocks this provider's machine family. Empty slot = no adapter
     * installed = nothing matches in {@link PackagedPatternProviderLogic}'s
     * binding stage.
     *
     * <p>Mirrors AE2LT's {@code OverloadedInterface.filterInv} pattern: a
     * dedicated {@link AppEngInternalInventory} with a per-slot type filter
     * and an inventory listener that re-runs the binding pipeline on change.
     */
    private final InternalInventoryHost adapterInvHost = new InternalInventoryHost() {
        @Override
        public void saveChangedInventory(AppEngInternalInventory inv) {
            saveChanges();
            markForUpdate();
        }

        @Override
        public void onChangeInventory(AppEngInternalInventory inv, int slot) {
            onAdapterInventoryChanged();
        }

        @Override
        public boolean isClientSide() {
            return level != null && level.isClientSide();
        }
    };

    private final AppEngInternalInventory adapterInv = new AppEngInternalInventory(adapterInvHost, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof MultiblockAdapterItem;
        }
    };

    public PackagedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        this(PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get(), pos, blockState, ProviderMode.NORMAL);
    }

    protected PackagedPatternProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
                                                ProviderMode fixedProviderMode) {
        super(type, pos, blockState);
        this.fixedProviderMode = fixedProviderMode;
        getMainNode().addService(appeng.api.networking.crafting.ICraftingRequester.class, coreOrder);
        sanitizePackagedModes();
    }

    public AppEngInternalInventory getAdapterInv() {
        return adapterInv;
    }

    /**
     * Convenience: the single packaged core currently installed, or
     * {@link ItemStack#EMPTY} when the slot is empty.
     */
    public ItemStack getInstalledAdapterStack() {
        return adapterInv.getStackInSlot(0);
    }

    /**
     * Notify the dispatch logic that bindings must be re-evaluated because
     * the player swapped the packaged core. Safely called from the inventory host
     * before logic is fully ready (early load phase).
     */
    private void onAdapterInventoryChanged() {
        var logic = getLogic();
        if (logic instanceof PackagedPatternProviderLogic packaged) {
            packaged.onAdapterSlotChanged();
        }
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new PackagedPatternProviderLogic(this.getMainNode(), this);
    }

    @Override
    protected void serverTickAdditional(ServerLevel level) {
        frequencyBinding.serverTick();
        coreOrder.tick();
    }

    @Override
    public FrequencyBindingAccess getFrequencyBindingAccess() {
        return frequencyBinding;
    }

    @Override
    public AENetworkedBlockEntity getFrequencyBindingBlockEntity() {
        return this;
    }

    @Override
    public void saveFrequencyBindingChanges() {
        saveChanges();
    }

    @Override
    public void markFrequencyBindingForUpdate() {
        markForUpdate();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        frequencyBinding.onMainNodeStateChanged(reason);
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
    }

    @Override
    public ProviderMode getProviderMode() {
        return isProviderModeSwitchable() ? super.getProviderMode() : fixedProviderMode();
    }

    @Override
    public void setProviderMode(ProviderMode providerMode) {
        super.setProviderMode(isProviderModeSwitchable() ? providerMode : fixedProviderMode());
    }

    @Override
    public void setReturnMode(ReturnMode mode) {
        super.setReturnMode(mode == ReturnMode.EJECT ? ReturnMode.OFF : mode);
    }

    @Override
    public ReturnMode getReturnMode() {
        var mode = super.getReturnMode();
        return mode == ReturnMode.EJECT ? ReturnMode.OFF : mode;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        if (getProviderMode() == ProviderMode.WIRELESS) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void setWirelessSpeedMode(WirelessSpeedMode wirelessSpeedMode) {
        super.setWirelessSpeedMode(WirelessSpeedMode.NORMAL);
    }

    @Override
    public WirelessSpeedMode getWirelessSpeedMode() {
        return WirelessSpeedMode.NORMAL;
    }

    @Override
    public void setFilteredImport(boolean filteredImport) {
        super.setFilteredImport(false);
    }

    @Override
    public boolean isFilteredImport() {
        return false;
    }

    @Override
    public boolean ae2lt$isPackagedProviderUi() {
        return true;
    }

    public boolean isProviderModeSwitchable() {
        return false;
    }

    @Override
    public boolean ae2lt$isModeSwitchVisible() {
        return isProviderModeSwitchable();
    }

    @Override
    public boolean ae2lt$isFilteredImportVisible() {
        return false;
    }

    @Override
    public boolean ae2lt$isWirelessTuningVisible() {
        return false;
    }

    @Override
    public boolean ae2lt$isBlockingModeVisible() {
        return false;
    }

    @Override
    public String ae2lt$titleTranslationKey() {
        return "ae2ltpp.gui.title.packaged_pattern_provider";
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        frequencyBinding.load(data);
        coreOrder.load(data.getCompound("ae2ltpp_core_order"), registries);
        sanitizePackagedModes();
        if (data.contains(TAG_ADAPTER_INV)) {
            adapterInv.readFromNBT(data, TAG_ADAPTER_INV, registries);
        }
        resonantAnchorBlocks.clear();
        for (var entry : data.getList(TAG_RESONANT_ANCHORS, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof CompoundTag anchor)) continue;
            var id = net.minecraft.resources.ResourceLocation.tryParse(anchor.getString("block"));
            if (id != null) resonantAnchorBlocks.put(anchor.getLong("pos"), id);
        }
        pruneResonantAnchorRecords();
        adapterFlags.clear();
        if (data.contains(TAG_ADAPTER_FLAGS, Tag.TAG_COMPOUND)) {
            var flagsTag = data.getCompound(TAG_ADAPTER_FLAGS);
            for (var key : flagsTag.getAllKeys()) {
                long[] arr = flagsTag.getLongArray(key);
                if (arr.length == 0) {
                    continue;
                }
                var set = new java.util.HashSet<Long>(arr.length);
                for (long packed : arr) {
                    set.add(packed);
                }
                adapterFlags.put(key, set);
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        frequencyBinding.save(data);
        data.put("ae2ltpp_core_order", coreOrder.save(registries));
        adapterInv.writeToNBT(data, TAG_ADAPTER_INV, registries);
        pruneResonantAnchorRecords();
        var anchors = new net.minecraft.nbt.ListTag();
        resonantAnchorBlocks.forEach((pos, block) -> {
            var anchor = new CompoundTag();
            anchor.putLong("pos", pos);
            anchor.putString("block", block.toString());
            anchors.add(anchor);
        });
        data.put(TAG_RESONANT_ANCHORS, anchors);
        if (!adapterFlags.isEmpty()) {
            var flagsTag = new CompoundTag();
            for (var entry : adapterFlags.entrySet()) {
                var set = entry.getValue();
                if (set.isEmpty()) {
                    continue;
                }
                long[] arr = new long[set.size()];
                int i = 0;
                for (long packed : set) {
                    arr[i++] = packed;
                }
                flagsTag.putLongArray(entry.getKey(), arr);
            }
            if (!flagsTag.isEmpty()) {
                data.put(TAG_ADAPTER_FLAGS, flagsTag);
            }
        }
    }

    @Override
    public void setFlag(BlockPos targetPos, String key) {
        var set = adapterFlags.computeIfAbsent(key, k -> new java.util.HashSet<>());
        if (set.add(targetPos.asLong())) {
            saveChanges();
        }
    }

    @Override
    public boolean hasFlag(BlockPos targetPos, String key) {
        var set = adapterFlags.get(key);
        return set != null && set.contains(targetPos.asLong());
    }

    @Override
    public void clearFlag(BlockPos targetPos, String key) {
        var set = adapterFlags.get(key);
        if (set == null) {
            return;
        }
        if (set.remove(targetPos.asLong())) {
            if (set.isEmpty()) {
                adapterFlags.remove(key);
            }
            saveChanges();
        }
    }

    @Override
    public void addAdditionalDrops(net.minecraft.world.level.Level level,
                                    BlockPos pos,
                                    java.util.List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        coreOrder.addDrops(drops);
        var card = adapterInv.getStackInSlot(0);
        if (!card.isEmpty()) {
            drops.add(card.copy());
            adapterInv.setItemDirect(0, ItemStack.EMPTY);
        }
    }

    @Override
    public void exportSettings(
            SettingsFrom mode,
            DataComponentMap.Builder builder,
            @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        frequencyBinding.exportMemorySettings(
                mode, builder, this::writeStableMemoryCardSettings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        frequencyBinding.importMemorySettings(
                mode, input, this::readStableMemoryCardSettings);
        sanitizePackagedModes();
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        coreOrder.suspendCalculation();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        frequencyBinding.clearRemoved();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getItemFromBlockEntity());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(getItemFromBlockEntity());
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        if (level instanceof ServerLevel) {
            clearInvalidConnections();
        }
        MenuOpener.open(PackagedPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(PackagedPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    protected ProviderMode fixedProviderMode() {
        return fixedProviderMode == null ? ProviderMode.NORMAL : fixedProviderMode;
    }

    private void sanitizePackagedModes() {
        super.setProviderMode(getProviderMode());
        if (super.getReturnMode() == ReturnMode.EJECT) {
            super.setReturnMode(ReturnMode.OFF);
        }
        super.setWirelessSpeedMode(WirelessSpeedMode.NORMAL);
        super.setFilteredImport(false);
    }
}
