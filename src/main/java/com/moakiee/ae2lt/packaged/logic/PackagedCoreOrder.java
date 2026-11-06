package com.moakiee.ae2lt.packaged.logic;

import java.util.concurrent.Future;
import java.util.function.Function;
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

/** Owns the final core receipt for a terminal order or a restored legacy order. */
public final class PackagedCoreOrder implements ICraftingRequester {
    private final PackagedPatternProviderBlockEntity host;
    private Future<ICraftingPlan> calculation;
    private IGrid calculationGrid;
    private ICraftingLink link;
    private AEItemKey target;
    private boolean received;
    private boolean returnToNetwork;
    private long remaining;
    private ItemStack delivered = ItemStack.EMPTY;
    private long deliveredAmount;
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
        remaining = 1;
        startCalculation();
    }

    public boolean canAcceptTerminalOrder() {
        return target == null && calculation == null && link == null && delivered.isEmpty()
                && host.getMainNode().isActive();
    }

    /** Adopt the terminal's already-confirmed plan, retaining its CPU and player action source. */
    public ICraftingSubmitResult submitFromTerminal(ICraftingPlan plan,
            Function<ICraftingRequester, ICraftingSubmitResult> submit) {
        if (!canAcceptTerminalOrder()) return appeng.crafting.execution.CraftingSubmitResult.CPU_BUSY;
        target = (AEItemKey) plan.finalOutput().what();
        remaining = plan.finalOutput().amount();
        received = false;
        returnToNetwork = false;
        try {
            var result = submit.apply(this);
            if (result.successful() && result.link() != null) {
                link = result.link();
                state("crafting");
            } else {
                target = null;
                remaining = 0;
                state("failed");
            }
            return result;
        } catch (RuntimeException failure) {
            target = null;
            remaining = 0;
            throw failure;
        }
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
                    deliveredAmount, Actionable.MODULATE, source());
            if (inserted > 0) {
                deliveredAmount -= inserted;
                if (deliveredAmount == 0) delivered = ItemStack.EMPTY;
                state(delivered.isEmpty() ? "returned" : "storage_full");
            }
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
                || target == null || !target.equals(what) || amount <= 0 || remaining <= 0) return 0;
        boolean installOne = !received && !returnToNetwork && host.getInstalledAdapterStack().isEmpty();
        long accepted = Math.min(amount, remaining);
        if (mode == Actionable.MODULATE && accepted > 0) {
            long toBuffer = accepted;
            if (installOne) {
                install(target);
                toBuffer--;
            }
            if (toBuffer > 0) {
                if (delivered.isEmpty()) delivered = target.toStack(1);
                deliveredAmount += toBuffer;
                returnToNetwork = true;
            }
            received = true;
            remaining -= accepted;
            host.saveChanges();
        }
        return accepted;
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
        remaining = 0;
        state("canceled");
    }
    public void addDrops(java.util.List<ItemStack> drops) {
        cancel();
        if (!delivered.isEmpty()) {
            AEItemKey.of(delivered).addDrops(deliveredAmount, drops, host.getLevel(), host.getBlockPos());
            delivered = ItemStack.EMPTY;
            deliveredAmount = 0;
        }
    }
    public CompoundTag save() {
        var data = new CompoundTag();
        if (target != null) data.put("target", target.toTag());
        if (link != null) { var tag = new CompoundTag(); link.writeToNBT(tag); data.put("link", tag); }
        if (!delivered.isEmpty()) {
            data.put("delivered", delivered.save(new CompoundTag()));
            data.putLong("delivered_amount", deliveredAmount);
        }
        data.putBoolean("received", received);
        data.putLong("remaining", remaining);
        data.putBoolean("return_to_network", returnToNetwork);
        data.putString("status", status);
        return data;
    }
    public void load(CompoundTag data) {
        suspendCalculation();
        target = data.contains("target") ? AEItemKey.fromTag(data.getCompound("target")) : null;
        link = data.contains("link") ? StorageHelper.loadCraftingLink(data.getCompound("link"), this) : null;
        delivered = ItemStack.of(data.getCompound("delivered"));
        deliveredAmount = data.contains("delivered_amount") ? Math.max(0, data.getLong("delivered_amount")) : delivered.getCount();
        if (delivered.isEmpty() || deliveredAmount == 0) {
            delivered = ItemStack.EMPTY;
            deliveredAmount = 0;
        } else delivered.setCount(1);
        received = data.getBoolean("received");
        remaining = data.contains("remaining") ? Math.max(0, data.getLong("remaining")) : received ? 0 : 1;
        returnToNetwork = data.getBoolean("return_to_network");
        status = data.contains("status") ? data.getString("status") : "idle";
    }
}
