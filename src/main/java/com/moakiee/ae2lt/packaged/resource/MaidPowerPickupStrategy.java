package com.moakiee.ae2lt.packaged.resource;

import java.lang.reflect.Field;
import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.world.entity.Entity;

/** AE2 performs collision, permissions, powered insertion and plane upgrade checks. */
public final class MaidPowerPickupStrategy implements PickupStrategy {
    private final Class<?> orb;
    private final Field value;

    public MaidPowerPickupStrategy() {
        try {
            orb = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint");
            value = orb.getField("value");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Missing native P orb API", e); }
    }
    @Override public void reset() {}
    @Override public boolean canPickUpEntity(Entity entity) { return orb.isInstance(entity); }
    @Override public Result tryPickup(IEnergySource energy, PickupSink sink) { return Result.CANT_PICKUP; }
    @Override public boolean pickUpEntity(IEnergySource energy, PickupSink sink, Entity entity) {
        if (!canPickUpEntity(entity) || !entity.isAlive() || entity.level().isClientSide()) return false;
        try {
            int before = value.getInt(entity);
            if (before <= 0) return false;
            long inserted = sink.insert(MaidPowerKey.INSTANCE, before, Actionable.MODULATE);
            if (inserted < 0 || inserted > before) throw new IllegalStateException("Invalid P pickup receipt");
            if (inserted == 0) return false;
            // Native orb values are integers, so partial storage never requires rounding.
            value.setInt(entity, before - (int) inserted);
            if (inserted == before) entity.discard();
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(MaidPowerPickupStrategy.class, "pickup", e);
            return false;
        }
    }
}
