package com.moakiee.ae2lt.packaged.resource;

import java.util.Comparator;
import java.util.function.Supplier;
import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Uses the existing plane's powered sink; never replaces AE2's fluid/block pickup. */
public final class ExperienceOrbPickupStrategy implements PickupStrategy {
    public static final TagKey<Fluid> EXPERIENCE = TagKey.create(Registries.FLUID, new ResourceLocation("ae2ltpp:experience"));
    public static final int MB_PER_XP = 20;
    public static final String PENDING = "ae2ltpp:experience_conversion";
    private final Supplier<KeyCounter> inventory;

    public ExperienceOrbPickupStrategy(Supplier<KeyCounter> inventory) { this.inventory = inventory; }
    @Override public void reset() {}
    @Override public boolean canPickUpEntity(Entity entity) { return entity instanceof ExperienceOrb; }
    @Override public Result tryPickup(IEnergySource energy, PickupSink sink) { return Result.CANT_PICKUP; }
    public static boolean pending(ExperienceOrb orb) { return orb.getPersistentData().contains(PENDING); }

    /** Highest network amount wins; an ID tie-break makes empty networks deterministic. */
    public static AEFluidKey select(KeyCounter stock) {
        return BuiltInRegistries.FLUID.stream().filter(fluid -> fluid != Fluids.EMPTY
                        && fluid.defaultFluidState().isSource() && fluid.is(EXPERIENCE))
                .map(AEFluidKey::of)
                .sorted(Comparator.<AEFluidKey>comparingLong(key -> stock.get(key)).reversed()
                        .thenComparing(key -> key.getId().toString()))
                .findFirst().orElse(null);
    }
    @Override public boolean pickUpEntity(IEnergySource energy, PickupSink sink, Entity entity) {
        if (!(entity instanceof ExperienceOrb orb) || !orb.isAlive() || orb.level().isClientSide()
                || !(orb instanceof ExperienceOrbAccess merged) || orb.getValue() <= 0 || merged.ae2ltpp$getCount() <= 0) return false;
        AEFluidKey fluid;
        long requested;
        if (pending(orb)) {
            var data = orb.getPersistentData().getCompound(PENDING);
            var key = AEKey.fromTagGeneric(data.getCompound("fluid"));
            if (!(key instanceof AEFluidKey found)) return false;
            fluid = found;
            requested = data.getLong("remaining");
            if (requested <= 0 || requested > (long) orb.getValue() * MB_PER_XP) return false;
        } else {
            fluid = select(inventory.get());
            if (fluid == null) return false;
            requested = (long) orb.getValue() * MB_PER_XP;
            // Consume one whole native member at a time. Insufficient space leaves
            // it available to players, other collectors, or a later plane collision.
            if (sink.insert(fluid, requested, Actionable.SIMULATE) < requested) return false;
        }
        long inserted = sink.insert(fluid, requested, Actionable.MODULATE);
        if (inserted < 0 || inserted > requested) throw new IllegalStateException("Invalid experience fluid receipt");
        if (inserted == 0) return false;
        if (inserted == requested) {
            orb.getPersistentData().remove(PENDING);
            int count = merged.ae2ltpp$getCount() - 1;
            merged.ae2ltpp$setCount(count);
            if (count == 0) orb.discard();
        } else {
            // A modded sink may accept less than it simulated. Persist the exact
            // unpaid fluid amount on this orb, and prevent vanilla pickup/merging
            // from spending the partially redeemed XP again.
            var data = new CompoundTag();
            data.put("fluid", fluid.toTagGeneric());
            data.putLong("remaining", requested - inserted);
            orb.getPersistentData().put(PENDING, data);
        }
        return true;
    }
}
