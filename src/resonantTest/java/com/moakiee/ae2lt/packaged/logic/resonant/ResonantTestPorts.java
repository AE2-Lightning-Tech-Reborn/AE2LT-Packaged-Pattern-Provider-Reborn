package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Forge fixture capabilities resolve handlers lazily after block placement. */
@Mod.EventBusSubscriber(modid = "ae2ltpp", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ResonantTestPorts {
    record Port(GlobalPos pos, Direction face) {}
    static final Map<Port, IItemHandler> ITEMS = new HashMap<>();
    static final Map<Port, IFluidHandler> FLUIDS = new HashMap<>();

    @SubscribeEvent
    public static void register(AttachCapabilitiesEvent<BlockEntity> event) {
        var be = event.getObject();
        if (!(be instanceof BeaconBlockEntity || be instanceof ComparatorBlockEntity)) return;
        event.addCapability(new ResourceLocation("ae2ltpp", "resonant_test_ports"), new ICapabilityProvider() {
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction face) {
                if (be.getLevel() == null || face == null) return LazyOptional.empty();
                var port = new Port(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()), face);
                if (capability == ForgeCapabilities.ITEM_HANDLER && ITEMS.containsKey(port)) {
                    return LazyOptional.of(() -> ITEMS.get(port)).cast();
                }
                if (capability == ForgeCapabilities.FLUID_HANDLER && FLUIDS.containsKey(port)) {
                    return LazyOptional.of(() -> FLUIDS.get(port)).cast();
                }
                return LazyOptional.empty();
            }
        });
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
