package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

@EventBusSubscriber(modid = "ae2ltpp")
public final class ResonantTestPorts {
    record Port(GlobalPos pos, Direction face) {}
    static final Map<Port, IItemHandler> ITEMS = new HashMap<>();
    static final Map<Port, IFluidHandler> FLUIDS = new HashMap<>();

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> ITEMS.get(new Port(GlobalPos.of(level.dimension(), pos), side)), Blocks.OBSIDIAN);
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> FLUIDS.get(new Port(GlobalPos.of(level.dimension(), pos), side)), Blocks.CRYING_OBSIDIAN);
    }

    static final class PartialHandler extends ItemStackHandler {
        int budget;
        boolean throwAfterInsert;
        PartialHandler(int budget) { super(1); this.budget = budget; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate) return super.insertItem(slot, stack, true);
            int offer = Math.min(stack.getCount(), budget);
            if (offer <= 0) return stack;
            var left = super.insertItem(slot, stack.copyWithCount(offer), false);
            int inserted = offer - left.getCount();
            budget -= inserted;
            if (throwAfterInsert) throw new IllegalStateException("injected failure after native insert");
            return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
        }
    }
}
