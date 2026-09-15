package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DispatchHarvestContractTest {

    @Test
    void virtualPushCommitsOnlyAfterLaneAcquire() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/logic/PackagedPatternProviderLogic.java");
        int acquire = source.indexOf("virtualPushLimiter.tryAcquire");
        int commit = source.indexOf("if (!result.commit())", acquire);
        int enqueue = source.indexOf("enqueueVirtualOutputs(result.outputs())", acquire);
        assertTrue(acquire > 0 && commit > acquire && enqueue > commit,
                "virtual commit must run after tryAcquire and before enqueue");
    }

    @Test
    void petalAndManaAndReconstructorDeferWorldMutationToCommit() throws IOException {
        var petal = read("src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/botania/PetalApothecaryAdapter.java");
        assertTrue(petal.contains("apothecarySetEmpty(current)"));
        assertTrue(petal.indexOf("return new VirtualCraftingResult(outputs, () ->")
                < petal.indexOf("apothecarySetEmpty(current)"));

        var mana = read("src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/botania/ManaPoolAdapter.java");
        assertTrue(mana.contains("manaPoolReceiveMana(current, -manaCost)"));
        assertTrue(mana.contains("new VirtualCraftingResult("));

        var reconstructor = read(
                "src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/aa/ActuallyAdditionsAtomicReconstructorAdapter.java");
        assertTrue(reconstructor.contains("extractEnergy(current, energyCost)"));
        assertTrue(reconstructor.contains("new VirtualCraftingResult("));
    }

    @Test
    void draconicRestoresOriginalOutputOnVerifyFailure() throws IOException {
        var source = read(
                "src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/de/DraconicFusionCraftingAdapter.java");
        assertTrue(source.contains("var original = stack.copy();"));
        assertTrue(source.contains("setOutputStackMethod.invoke(be, original)"));
    }

    @Test
    void terraExtractRefusesExpiredOwnership() throws IOException {
        var source = read(
                "src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/botania/TerraPlateAdapter.java");
        int extract = source.indexOf("public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,");
        int expired = source.indexOf("DroppedItemHarvestOwnership.expired(level, ownership)", extract);
        int collect = source.indexOf("DroppedItemHarvestOwnership.collectExpectedOutput", extract);
        assertTrue(extract > 0 && expired > extract && collect > expired);
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
