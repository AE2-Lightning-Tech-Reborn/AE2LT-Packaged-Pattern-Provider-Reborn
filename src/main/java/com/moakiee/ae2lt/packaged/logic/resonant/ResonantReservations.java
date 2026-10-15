package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;

import appeng.api.AECapabilities;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Shadow reservations prevent several sparse entries from each claiming the same empty slot/tank. */
final class ResonantReservations {
    private record Port(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {}
    private final Map<IItemHandler, ItemStack[]> items = new IdentityHashMap<>();
    private final Map<IFluidHandler, FluidStack[]> fluids = new IdentityHashMap<>();
    private final Map<Port, ItemStack[]> itemPorts = new HashMap<>();
    private final Map<Port, FluidStack[]> fluidPorts = new HashMap<>();

    boolean reserve(ServerLevel level, PendingBatchInputs.Delivery delivery) {
        // Native ME storage may have semantics different from the exposed inventory; its own simulation wins.
        if (level.getCapability(AECapabilities.ME_STORAGE, delivery.pos(), delivery.face()) != null) return true;
        if (delivery.stack().what() instanceof AEItemKey key) {
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, delivery.pos(), delivery.face());
            return handler == null || reserveItems(handler, new Port(delivery.pos(), delivery.face()), key, delivery.stack().amount());
        }
        if (delivery.stack().what() instanceof AEFluidKey key) {
            var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, delivery.pos(), delivery.face());
            return handler == null || reserveFluids(handler, new Port(delivery.pos(), delivery.face()), key, delivery.stack().amount());
        }
        // Other AE key types still use their registered external-storage strategy and durable remainder queue.
        return true;
    }

    private boolean reserveItems(IItemHandler handler, Port port, AEItemKey key, long amount) {
        var shadow = itemPorts.computeIfAbsent(port, p -> items.computeIfAbsent(handler, h -> {
            var slots = new ItemStack[h.getSlots()];
            for (int i = 0; i < slots.length; i++) slots[i] = h.getStackInSlot(i).copy();
            return slots;
        }));
        for (int slot = 0; slot < shadow.length && amount > 0; slot++) {
            var current = shadow[slot];
            if (!current.isEmpty() && !key.equals(AEItemKey.of(current))) continue;
            var probe = key.toStack((int) Math.min(amount, Integer.MAX_VALUE));
            int room = Math.max(0, Math.min(handler.getSlotLimit(slot), probe.getMaxStackSize()) - current.getCount());
            if (room == 0 || !handler.isItemValid(slot, probe)) continue;
            int offered = (int) Math.min(amount, room);
            probe.setCount(offered);
            int accepted = offered - handler.insertItem(slot, probe, true).getCount();
            if (accepted <= 0) continue;
            // The real simulation sees the original inventory, so clamp against the shadow's remaining room.
            accepted = Math.min(accepted, room);
            shadow[slot] = key.toStack(current.getCount() + accepted);
            amount -= accepted;
        }
        return amount == 0;
    }

    private boolean reserveFluids(IFluidHandler handler, Port port, AEFluidKey key, long amount) {
        var shadow = fluidPorts.computeIfAbsent(port, p -> fluids.computeIfAbsent(handler, h -> {
            var tanks = new FluidStack[h.getTanks()];
            for (int i = 0; i < tanks.length; i++) tanks[i] = h.getFluidInTank(i).copy();
            return tanks;
        }));
        for (int tank = 0; tank < shadow.length && amount > 0; tank++) {
            var current = shadow[tank];
            if (!current.isEmpty() && !key.equals(AEFluidKey.of(current))) continue;
            var probe = key.toStack((int) Math.min(amount, Integer.MAX_VALUE));
            if (!handler.isFluidValid(tank, probe)) continue;
            int room = Math.max(0, handler.getTankCapacity(tank) - current.getAmount());
            int accepted = (int) Math.min(amount, room);
            if (accepted > 0) {
                shadow[tank] = key.toStack(current.getAmount() + accepted);
                amount -= accepted;
            }
        }
        return amount == 0;
    }
}
