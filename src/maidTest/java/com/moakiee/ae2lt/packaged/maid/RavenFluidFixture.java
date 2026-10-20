package com.moakiee.ae2lt.packaged.maid;

import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.moakiee.ae2lt.packaged.logic.multiblock.apothic.ApothicAccess;

/** Test-only stand-in for an addon supplying a fluid capability to the raven table. */
@EventBusSubscriber(modid="ae2ltpp",bus=EventBusSubscriber.Bus.MOD)
public final class RavenFluidFixture {
    private static final WeakHashMap<BlockEntity,FluidTank> TANKS=new WeakHashMap<>();
    public static FluidTank tank(BlockEntity table){return TANKS.computeIfAbsent(table,ignored->new FluidTank(1000000));}
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,BlockEntityType.ENCHANTING_TABLE,
                (table,side)->BuiltInRegistries.BLOCK.getKey(table.getBlockState().getBlock()).equals(ApothicAccess.RAVEN)?tank(table):null);
    }
}
