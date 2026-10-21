package com.moakiee.ae2lt.packaged.logic;

import java.util.concurrent.Future;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One explicit player order. Only the requesting provider receives and installs its result. */
public final class PackagedCoreOrder implements ICraftingRequester {
    private final PackagedPatternProviderBlockEntity host;
    private Future<ICraftingPlan> calculation;
    private IGrid calculationGrid;
    private ICraftingLink link;
    private AEItemKey target;
    private boolean received;
    private boolean returnToNetwork;
    private ItemStack delivered = ItemStack.EMPTY;
    private String status = "idle";

    public PackagedCoreOrder(PackagedPatternProviderBlockEntity host) { this.host = host; }
    public String status() { return status; }
    private void state(String value) { status = value; host.saveChanges(); }
    private IActionSource source() { return IActionSource.ofMachine(host); }
    private IGrid grid() { return host.getMainNode().getGrid(); }

    public void request(Item item) {
        if (!(item instanceof MultiblockAdapterItem) || calculation != null || link != null) return;
        var grid = grid();
        if (host.getLevel() == null || host.getLevel().isClientSide() || grid == null
                || !host.getMainNode().isActive()) { state("offline"); return; }
        if (!delivered.isEmpty()) { state("storage_full"); return; }
        var key = AEItemKey.of(item);
        target = key;
        received = false;
        startCalculation();
    }

    private void startCalculation() {
        var node = getActionableNode();
        if (node == null || !node.isActive() || target == null) return;
        calculationGrid = node.getGrid();
        var actionSource = source();
        try {
            calculation = calculationGrid.getCraftingService().beginCraftingCalculation(host.getLevel(),
                    new ICraftingSimulationRequester() {
                        @Override public IActionSource getActionSource() { return actionSource; }
                        @Override public IGridNode getGridNode() { return node; }
                    }, target, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
            state("calculating");
        } catch (RuntimeException failure) {
            target = null;
            state("failed");
        }
    }

    public void tick() {
        var grid = grid();
        if (grid == null || !host.getMainNode().isActive()) return;
        // Buffer a real requester receipt before reinserting it. Inserting into ME
        // from inside insertCraftedItems can reenter the CPU's output delivery.
        if (!delivered.isEmpty() && (link == null || link.isDone() || link.isCanceled())) {
            long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(delivered),
                    delivered.getCount(), Actionable.MODULATE, source());
            if (inserted > 0) { delivered.shrink((int) inserted); state(delivered.isEmpty() ? "returned" : "storage_full"); }
        }
        if (link != null) {
            if (link.isCanceled() || link.isDone()) {
                link = null;
                target = null;
                state(received ? (returnToNetwork ? (delivered.isEmpty() ? "returned" : "storage_full") : "installed") : "canceled");
            }
            return;
        }
        if (target == null) return;
        if (calculation != null && calculationGrid != grid) suspendCalculation();
        if (calculation == null) { startCalculation(); return; }
        if (!calculation.isDone()) return;
        try {
            var plan = calculation.get();
            calculation = null;
            if (plan.simulation() || !plan.missingItems().isEmpty()) {
                target = null;
                state("missing_ingredients");
                return;
            }
            returnToNetwork = plan.patternTimes().keySet().stream()
                    .filter(pattern -> target.equals(pattern.getPrimaryOutput().what()))
                    .flatMap(pattern -> java.util.Arrays.stream(pattern.getInputs()))
                    .flatMap(input -> java.util.Arrays.stream(input.getPossibleInputs()))
                    .anyMatch(input -> input.what() instanceof AEItemKey key && key.getItem() instanceof MultiblockAdapterItem);
            var result = grid.getCraftingService().submitJob(plan, this, null, false, source());
            if (result.successful() && result.link() != null) {
                link = result.link();
                state("crafting");
            } else {
                target = null;
                state("no_cpu");
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            calculation = null;
            target = null;
            state("failed");
        }
    }

    public boolean awaitsInstallation(AEItemKey output) {
        return link != null && !link.isCanceled() && !link.isDone() && !returnToNetwork
                && !received && output.equals(target);
    }

    private void install(AEItemKey key) {
        host.getAdapterInv().setItemDirect(0, key.toStack(1));
        received = true;
        host.saveChanges();
    }

    @Override public IGridNode getActionableNode() { return host.getActionableNode(); }
    @Override public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return link == null ? ImmutableSet.of() : ImmutableSet.of(link);
    }
    @Override public long insertCraftedItems(ICraftingLink incoming, AEKey what, long amount, Actionable mode) {
        if (link == null || incoming == null || !link.getCraftingID().equals(incoming.getCraftingID())
                || received || target == null || !target.equals(what) || amount <= 0) return 0;
        if (returnToNetwork || !host.getInstalledAdapterStack().isEmpty()) {
            if (!delivered.isEmpty()) return 0;
            if (mode == Actionable.MODULATE) {
                delivered = target.toStack(1);
                received = true;
                returnToNetwork = true;
                host.saveChanges();
            }
            return 1;
        }
        if (mode == Actionable.MODULATE) install(target);
        return 1;
    }
    @Override public void jobStateChange(ICraftingLink changed) {
        if (link == null || !link.getCraftingID().equals(changed.getCraftingID())
                || (!changed.isDone() && !changed.isCanceled())) return;
        // MEStorage insertion may synchronously complete the CPU while its receipt
        // callback is still on the stack. Finalize on tick after the receipt is recorded.
        host.saveChanges();
    }

    public void suspendCalculation() {
        if (calculation != null) calculation.cancel(false);
        calculation = null;
        calculationGrid = null;
    }
    public void cancel() {
        suspendCalculation();
        if (link != null) link.cancel();
        link = null;
        target = null;
        state("canceled");
    }
    public void addDrops(java.util.List<ItemStack> drops) {
        cancel();
        if (!delivered.isEmpty()) { drops.add(delivered.copy()); delivered = ItemStack.EMPTY; }
    }
    public CompoundTag save() {
        var data = new CompoundTag();
        if (target != null) data.put("target", target.toTag());
        if (link != null) { var tag = new CompoundTag(); link.writeToNBT(tag); data.put("link", tag); }
        if (!delivered.isEmpty()) data.put("delivered", delivered.save(new CompoundTag()));
        data.putBoolean("received", received);
        data.putBoolean("return_to_network", returnToNetwork);
        data.putString("status", status);
        return data;
    }
    public void load(CompoundTag data) {
        suspendCalculation();
        target = data.contains("target") ? AEItemKey.fromTag(data.getCompound("target")) : null;
        link = data.contains("link") ? StorageHelper.loadCraftingLink(data.getCompound("link"), this) : null;
        delivered = ItemStack.of(data.getCompound("delivered"));
        received = data.getBoolean("received");
        returnToNetwork = data.getBoolean("return_to_network");
        status = data.contains("status") ? data.getString("status") : "idle";
    }
}
