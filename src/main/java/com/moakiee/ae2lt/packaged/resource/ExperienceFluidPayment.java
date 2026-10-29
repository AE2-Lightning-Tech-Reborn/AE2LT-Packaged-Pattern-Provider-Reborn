package com.moakiee.ae2lt.packaged.resource;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Pays only the experience fluid missing from a pattern, retaining exact rollback receipts. */
public final class ExperienceFluidPayment {
    private record Receipt(GenericStack stack, LongSupplier refund) {}
    private final IFluidHandler tank;
    private final MEStorage network;
    private final IActionSource source;
    private final List<Receipt> receipts = new ArrayList<>();

    public ExperienceFluidPayment(BlockEntity machine, IActionSource source) {
        var level = machine.getLevel();
        var found = level.getCapability(Capabilities.FluidHandler.BLOCK, machine.getBlockPos(), null);
        if (found == null) for (var side : Direction.values()) {
            found = level.getCapability(Capabilities.FluidHandler.BLOCK, machine.getBlockPos(), side);
            if (found != null) break;
        }
        tank = found;
        var host = source.machine().orElse(null);
        var node = host == null ? null : host.getActionableNode();
        network = node == null ? null : node.getGrid().getStorageService().getInventory();
        this.source = source;
    }

    public boolean available(long amount) { return take(amount, true); }
    public boolean charge(long amount) { return take(amount, false); }

    private boolean take(long amount, boolean simulate) {
        if (amount < 0) return false;
        if (tank != null) {
            var seen = new HashSet<AEFluidKey>();
            for (int i = 0; i < tank.getTanks() && amount > 0; i++) {
                var stored = tank.getFluidInTank(i);
                if (stored.isEmpty() || !stored.getFluid().is(ExperienceOrbPickupStrategy.EXPERIENCE)) continue;
                var key = AEFluidKey.of(stored);
                if (!seen.add(key)) continue;
                int requested = (int) Math.min(Integer.MAX_VALUE, amount);
                var extracted = tank.drain(key.toStack(requested), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if (extracted.isEmpty()) continue;
                if (!key.equals(AEFluidKey.of(extracted)) || extracted.getAmount() > requested)
                    throw new IllegalStateException("Invalid experience fluid tank receipt");
                amount -= extracted.getAmount();
                if (!simulate) receipts.add(new Receipt(new GenericStack(key, extracted.getAmount()),
                        () -> tank.fill(extracted.copy(), IFluidHandler.FluidAction.EXECUTE)));
            }
        }
        if (network != null && amount > 0) {
            var options = new ArrayList<GenericStack>();
            for (var entry : network.getAvailableStacks()) if (entry.getKey() instanceof AEFluidKey fluid
                    && fluid.getFluid().is(ExperienceOrbPickupStrategy.EXPERIENCE) && entry.getLongValue() > 0)
                options.add(new GenericStack(fluid, entry.getLongValue()));
            options.sort(Comparator.comparingLong(GenericStack::amount).reversed().thenComparing(s -> s.what().getId().toString()));
            for (var option : options) {
                if (amount == 0) break;
                long taken = network.extract(option.what(), amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
                if (taken < 0 || taken > amount) throw new IllegalStateException("Invalid experience fluid network receipt");
                amount -= taken;
                if (!simulate && taken > 0) receipts.add(new Receipt(new GenericStack(option.what(), taken),
                        () -> network.insert(option.what(), taken, Actionable.MODULATE, source)));
            }
        }
        return amount == 0;
    }

    public void refund(Consumer<GenericStack> retain) {
        for (var receipt : receipts.reversed()) {
            long restored;
            try { restored = receipt.refund().getAsLong(); }
            catch (RuntimeException e) {
                // An unknown insertion result must not be duplicated into another sink.
                com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics.failure(getClass(), "experience refund", e);
                continue;
            }
            if (restored < 0 || restored > receipt.stack().amount())
                throw new IllegalStateException("Invalid experience fluid refund receipt");
            long remaining = receipt.stack().amount() - restored;
            if (remaining > 0) retain.accept(new GenericStack(receipt.stack().what(), remaining));
        }
        receipts.clear();
    }
}
