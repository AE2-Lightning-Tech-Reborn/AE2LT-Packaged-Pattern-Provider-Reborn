package com.moakiee.ae2lt.packaged.patternprovider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import org.junit.jupiter.api.Test;

class PatternProviderPowerCostTest {
    @Test
    void oneEnergyProbePreservesEveryAffordableCountAtRoundingBoundaries() {
        var grid = mock(IGrid.class);
        var energy = mock(IEnergyService.class);
        when(grid.getEnergyService()).thenReturn(energy);
        double[] budget = {0};
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(call -> Math.min((double) call.getArgument(0), budget[0]));
        for (double cost : new double[]{0.1, 1, 3, 1.0e18}) {
            for (double reserve : new double[]{0, 0.25, 1000}) {
                when(energy.getIdlePowerUsage()).thenReturn(reserve);
                for (int capacity = 0; capacity <= 64; capacity++) {
                    double boundary = cost * capacity + reserve;
                    for (double available : new double[]{Math.nextDown(boundary), boundary, Math.nextUp(boundary),
                            Math.max(0, boundary - 1.0e-6), boundary + 1.0e-6}) {
                        budget[0] = available;
                        int expected = 0;
                        for (int n = 1; n <= 64; n++) if (PatternProviderPowerCost.canAfford(grid, cost * n)) expected = n;
                        clearInvocations(energy);
                        assertEquals(expected, PatternProviderPowerCost.affordableCopies(grid, cost, 64));
                        verify(energy, times(1)).extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG));
                        verify(energy, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
                    }
                }
            }
        }
    }

    @Test
    void emptyFreeAndInvalidBudgetsNeedNoEnergyAccess() {
        assertEquals(64, PatternProviderPowerCost.affordableCopies(null, 0, 64));
        assertEquals(0, PatternProviderPowerCost.affordableCopies(null, 1, 64));
        assertEquals(0, PatternProviderPowerCost.affordableCopies(null, 1, 0));
        assertEquals(0, PatternProviderPowerCost.affordableCopies(null, Double.NaN, 64));
        assertEquals(0, PatternProviderPowerCost.affordableCopies(null, Double.POSITIVE_INFINITY, 64));
    }
}
