package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.*;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import mekanism.api.Action;
import mekanism.api.chemical.*;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ChemicalReturnGameTests {
    @GameTest(template = "mek_routing_test")
    public static void filteredSecondTankCannotDrainOrCreditFirstChemical(GameTestHelper h) {
        var tanks = new ChemicalStack[] {MekanismChemicals.OXYGEN.asStack(100), MekanismChemicals.CHLORINE.asStack(50)};
        IChemicalHandler handler = new IChemicalHandler() {
            public int getChemicalTanks() { return tanks.length; }
            public ChemicalStack getChemicalInTank(int tank) { return tanks[tank]; }
            public void setChemicalInTank(int tank, ChemicalStack stack) { tanks[tank] = stack; }
            public long getChemicalTankCapacity(int tank) { return 1000; }
            public boolean isValid(int tank, ChemicalStack stack) { return true; }
            public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) { return stack; }
            public ChemicalStack extractChemical(int tank, long amount, Action action) {
                var result = tanks[tank].copyWithAmount(Math.min(amount, tanks[tank].getAmount()));
                if (action.execute()) tanks[tank].shrink(result.getAmount());
                return result;
            }
        };
        var chlorine = AppmekReflection.fromChemicalStack(tanks[1]);
        var filter = new AllowedOutputFilter(); filter.allowStrict(chlorine);
        var result = new ArrayList<GenericStack>();
        MekanismMoreMachinesAdapter.extractChemicals(handler, filter, result);
        h.assertTrue(tanks[0].getAmount() == 100, "unmatched oxygen tank must remain untouched");
        h.assertTrue(tanks[1].isEmpty(), "the selected chlorine tank is drained");
        h.assertTrue(result.equals(List.of(new GenericStack(chlorine, 50))), "credit matches exact extracted chemistry");
        result.clear();
        MekanismMoreMachinesAdapter.extractChemicals(handler, filter, result);
        h.assertTrue(result.isEmpty() && tanks[0].getAmount() == 100, "no repeat extraction or type conversion");
        h.succeed();
    }
}
