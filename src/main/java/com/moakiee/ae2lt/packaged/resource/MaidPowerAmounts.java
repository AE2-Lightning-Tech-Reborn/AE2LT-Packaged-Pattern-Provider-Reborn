package com.moakiee.ae2lt.packaged.resource;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal float boundaries preserve native hundredths without inventing fractional orb values. */
public final class MaidPowerAmounts {
    private MaidPowerAmounts() {}

    public static long stored(float power) {
        if (!Float.isFinite(power) || power <= 0) return 0;
        try { return new BigDecimal(Float.toString(power)).movePointRight(2).setScale(0, RoundingMode.FLOOR).longValueExact(); }
        catch (ArithmeticException e) { return 0; }
    }

    /** Reject unsupported sub-cent costs instead of rounding a recipe's price. */
    public static long recipeCost(float power) {
        if (!Float.isFinite(power) || power < 0 || power > 5) return -1;
        try { return new BigDecimal(Float.toString(power)).movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { return -1; }
    }

    public record Transfer(float after, long moved) {}

    public static Transfer transfer(float before, float capacity, long requested, boolean inserting) {
        if (requested <= 0 || !Float.isFinite(before) || !Float.isFinite(capacity)
                || before < 0 || capacity < 0 || before > capacity) return new Transfer(before, 0);
        long initial = stored(before);
        long room = inserting ? Math.max(0, stored(capacity) - initial) : initial;
        long move = Math.min(requested, room);
        if (move == 0) return new Transfer(before, 0);
        float after = Math.max(0, Math.min(capacity, new BigDecimal(Float.toString(before))
                .add(BigDecimal.valueOf(inserting ? move : -move, 2)).floatValue()));
        // At very large configured capacities, one float ULP can exceed 0.01 P.
        // Only report a representable transfer that is no larger than the request.
        long receipt = inserting ? stored(after) - initial : initial - stored(after);
        if (receipt > move) {
            after = inserting ? Math.nextDown(after) : Math.nextUp(after);
            receipt = inserting ? stored(after) - initial : initial - stored(after);
        }
        return receipt > 0 && receipt <= move ? new Transfer(after, receipt) : new Transfer(before, 0);
    }
}
