package com.moakiee.ae2lt.packaged.logic.multiblock.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenArcanusClibanoStateTest {
    @Test
    void remainingFuelAndSoulsDoNotBlockIdleMachine() {
        assertTrue(ForbiddenArcanusClibanoAdapter.isIdle(new MachineState(0, 0)));
    }

    @Test
    void eitherProcessingSlotBlocksDispatch() {
        assertFalse(ForbiddenArcanusClibanoAdapter.isIdle(new MachineState(1, 0)));
        assertFalse(ForbiddenArcanusClibanoAdapter.isIdle(new MachineState(0, 1)));
    }

    @Test
    void missingOrInvalidStateFailsClosed() {
        assertFalse(ForbiddenArcanusClibanoAdapter.isIdle(null));
        assertFalse(ForbiddenArcanusClibanoAdapter.isIdle(new Object()));
        assertFalse(ForbiddenArcanusClibanoAdapter.isIdle(new MachineState(-1, 0)));
    }

    @Test
    void recipeCannotUseAHotterFireThanTheClibanoProvides() {
        assertTrue(ForbiddenArcanusClibanoAdapter.supportsFireType(
                new FireState(FireType.SOUL_FIRE, FireType.FIRE), new RequiredFire(FireType.FIRE)));
        assertTrue(ForbiddenArcanusClibanoAdapter.supportsFireType(
                new FireState(FireType.SOUL_FIRE, FireType.FIRE), new RequiredFire(FireType.SOUL_FIRE)));
        assertFalse(ForbiddenArcanusClibanoAdapter.supportsFireType(
                new FireState(FireType.FIRE, FireType.FIRE), new RequiredFire(FireType.SOUL_FIRE)));
        assertFalse(ForbiddenArcanusClibanoAdapter.supportsFireType(
                new Object(), new RequiredFire(FireType.FIRE)));
    }

    @Test
    void queuedSoulMaterialCanSupplyTheRequiredFire() {
        for (var current : FireType.values()) {
            for (var queued : FireType.values()) {
                for (var required : FireType.values()) {
                    boolean expected = Math.max(current.ordinal(), queued.ordinal()) >= required.ordinal();
                    assertEquals(expected,
                            ForbiddenArcanusClibanoAdapter.supportsFireType(
                                    new FireState(current, queued), new RequiredFire(required)),
                            current + "/" + queued + "/" + required);
                }
            }
        }
    }

    @Test
    void missingFireInformationFailsClosed() {
        assertFalse(ForbiddenArcanusClibanoAdapter.supportsFireType(null, new RequiredFire(FireType.FIRE)));
        assertFalse(ForbiddenArcanusClibanoAdapter.supportsFireType(new FireState(FireType.FIRE, FireType.FIRE), null));
        assertFalse(ForbiddenArcanusClibanoAdapter.supportsFireType(new FireState(null, FireType.FIRE),
                new RequiredFire(FireType.FIRE)));
        assertFalse(ForbiddenArcanusClibanoAdapter.supportsFireType(new FireState(FireType.FIRE, null),
                new RequiredFire(FireType.FIRE)));
    }

    private static final class MachineState {
        private final int cookingProgressFirst;
        private final int cookingProgressSecond;
        private final int burnTime = 100;
        private final int soulTime = 100;

        private MachineState(int firstProgress, int secondProgress) {
            this.cookingProgressFirst = firstProgress;
            this.cookingProgressSecond = secondProgress;
        }
    }

    private static final class FireState {
        private final FireType fireType;
        private final FireType queuedFireType;

        private FireState(FireType fireType, FireType queuedFireType) {
            this.fireType = fireType;
            this.queuedFireType = queuedFireType;
        }

        private FireType getFireTypeFromInput() {
            return queuedFireType;
        }
    }

    private static final class RequiredFire {
        private final FireType fireType;

        private RequiredFire(FireType fireType) {
            this.fireType = fireType;
        }

        @SuppressWarnings("unused")
        public FireType getRequiredFireType() {
            return fireType;
        }
    }

    private enum FireType {
        FIRE, SOUL_FIRE, ENCHANTED_FIRE { }
    }
}
