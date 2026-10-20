package com.moakiee.ae2lt.packaged.logic.multiblock.apothic;

import java.util.*;
import java.util.function.Consumer;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.packaged.resource.ExperienceOrbPickupStrategy;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Explicit pattern inputs pay first, then native table stores, then the provider's network. */
final class RavenResources {
    private record Receipt(GenericStack stack, java.util.function.LongSupplier refund) {}
    private final IItemHandler items;
    private final IFluidHandler fluids;
    private final MEStorage network;
    private final IActionSource source;
    private final List<Receipt> receipts = new ArrayList<>();
    RavenResources(BlockEntity table, IActionSource source) {
        items = ApothicAccess.inventory(table);
        var level = table.getLevel();
        IFluidHandler found = level.getCapability(Capabilities.FluidHandler.BLOCK, table.getBlockPos(), null);
        if (found == null) for (var side : Direction.values()) {
            found = level.getCapability(Capabilities.FluidHandler.BLOCK, table.getBlockPos(), side);
            if (found != null) break;
        }
        fluids = found;
        var host = source.machine().orElse(null);
        network = host == null || host.getActionableNode() == null ? null : host.getActionableNode().getGrid().getStorageService().getInventory();
        this.source = source;
    }
    boolean available(int lapis, long experience) { return take(lapis, experience, true); }
    boolean charge(int lapis, long experience) { return take(lapis, experience, false); }
    private boolean take(int lapis, long experience, boolean simulate) {
        var key = AEItemKey.of(Items.LAPIS_LAZULI);
        for (int slot = 0; slot < items.getSlots() && lapis > 0; slot++) {
            if (!items.getStackInSlot(slot).is(Items.LAPIS_LAZULI)) continue;
            int index = slot;
            var extracted = items.extractItem(slot, lapis, simulate);
            if (extracted.isEmpty()) continue;
            lapis -= extracted.getCount();
            if (!simulate) receipts.add(new Receipt(new GenericStack(key, extracted.getCount()),
                    () -> extracted.getCount() - items.insertItem(index, extracted.copy(), false).getCount()));
        }
        if (lapis > 0 && network != null) {
            long taken = network.extract(key, lapis, simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
            if (!simulate && taken > 0) recordNetwork(key, taken);
            lapis -= (int) taken;
        }
        if (lapis > 0) return false;
        if (fluids != null) {
            // Drain by distinct fluid, not tank index: drain(Resource) can aggregate tanks.
            var seen = new HashSet<AEFluidKey>();
            for (int tank = 0; tank < fluids.getTanks() && experience > 0; tank++) {
                var stored = fluids.getFluidInTank(tank);
                if (stored.isEmpty() || !stored.getFluid().is(ExperienceOrbPickupStrategy.EXPERIENCE)) continue;
                var fluid = AEFluidKey.of(stored);
                if (!seen.add(fluid)) continue;
                var extracted = fluids.drain(fluid.toStack((int) Math.min(Integer.MAX_VALUE, experience)),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if (extracted.isEmpty()) continue;
                experience -= extracted.getAmount();
                if (!simulate) receipts.add(new Receipt(new GenericStack(fluid, extracted.getAmount()),
                        () -> fluids.fill(extracted.copy(), IFluidHandler.FluidAction.EXECUTE)));
            }
        }
        if (network != null && experience > 0) {
            var options = new ArrayList<GenericStack>();
            for (var entry : network.getAvailableStacks()) if (entry.getKey() instanceof AEFluidKey fluid
                    && fluid.getFluid().is(ExperienceOrbPickupStrategy.EXPERIENCE) && entry.getLongValue() > 0)
                options.add(new GenericStack(fluid, entry.getLongValue()));
            options.sort(Comparator.comparingLong(GenericStack::amount).reversed().thenComparing(s -> s.what().getId().toString()));
            for (var option : options) {
                if (experience == 0) break;
                long taken = network.extract(option.what(), experience, simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
                experience -= taken;
                if (!simulate && taken > 0) recordNetwork(option.what(), taken);
            }
        }
        return experience == 0;
    }
    private void recordNetwork(AEKey key, long taken) {
        receipts.add(new Receipt(new GenericStack(key, taken), () -> network.insert(key, taken, Actionable.MODULATE, source)));
    }
    void refund(Consumer<GenericStack> retain) {
        for (var receipt : receipts.reversed()) {
            long returned;
            try { returned = receipt.refund().getAsLong(); }
            catch (RuntimeException e) {
                // A throwing handler may already have accepted this refund. Do not
                // mint a second copy from an unconfirmed insertion receipt.
                com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics.failure(RavenResources.class, "refund", e);
                continue;
            }
            long remaining = receipt.stack().amount() - returned;
            if (remaining > 0) retain.accept(new GenericStack(receipt.stack().what(), remaining));
        }
        receipts.clear();
    }
}
