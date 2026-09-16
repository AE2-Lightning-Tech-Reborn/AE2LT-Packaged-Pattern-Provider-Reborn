package com.moakiee.ae2lt.packaged.gametest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdapterConnectionHarnessContractTest {

    @Test
    void undoPlacementRestoresOriginalBlockInsteadOfLeavingAir() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionCheck.java");
        int undo = source.indexOf("private static void undoPlacement(");
        int restore = source.indexOf("restoreBlock(level, placement.pos(), placement.original()", undo);
        int methodEnd = source.indexOf("public static void runCase(", undo);
        String undoBody = source.substring(undo, methodEnd);
        assertTrue(undo > 0 && restore > undo && methodEnd > restore);
        assertFalse(undoBody.contains("removeBlock"),
                "undoPlacement must restore the original block, not leave air");
        assertTrue(source.contains("captureBe(level, pos)"));
        assertTrue(source.contains("BlockEntity.loadStatic(pos, state, beTag)"));
    }

    @Test
    void commandConn38RequiresSuccessfulPostSettleScan() throws IOException {
        var command = read(
                "src/main/java/com/moakiee/ae2lt/packaged/gametest/ConnectionDiagnosticCommand.java");
        assertTrue(command.contains("TickEvent.ServerTickEvent"));
        assertTrue(command.contains("COLOCATED_SETTLE_TICKS"));
        assertTrue(command.contains("CONN38_PENDING"));
        assertTrue(command.contains("ServerStoppingEvent"));
        int finish = command.indexOf("private static void finishConn38(");
        int scan = command.indexOf("AdapterConnectionCheck.scanColocated(", finish);
        int pass = command.indexOf("pending.sink.pass(\"conn38_all_adapters_colocated\"", scan);
        assertTrue(finish > 0 && scan > finish && pass > scan);
        assertTrue(command.contains("if (ok)"));
        assertFalse(command.contains("new TickTask("));
        assertFalse(command.contains("removed again within the same tick"));
        assertTrue(command.contains("command not finished"));
        assertTrue(command.contains("if (conn38Pending)"));
        assertTrue(command.contains("ae2ltdiag finished:"));
        int pendingReturn = command.indexOf("if (conn38Pending) {");
        int pendingZero = command.indexOf("return 0;", pendingReturn);
        int finalReturn = command.indexOf("return tally.failed == 0 ? Command.SINGLE_SUCCESS : 0;", pendingReturn);
        assertTrue(pendingReturn > 0 && pendingZero > pendingReturn && pendingZero < finalReturn);
    }

    @Test
    void inventoryAndColocatedFailClosedOnMissingMods() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionCheck.java");
        assertTrue(source.contains("compatible-mod blocks not registered:"));
        assertFalse(source.contains("CASES.size() - 4"));
        assertTrue(source.contains("expectedAdapterNames()"));
        assertTrue(source.contains("missing registered adapters"));
        assertTrue(source.contains("placements.isEmpty()"));
        assertTrue(source.contains("skipped unplaceable"));
        assertFalse(source.contains("placements.size() != expected"));
    }

    @Test
    void extremeSmithingHasPositiveOrInertCase() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionCheck.java");
        assertTrue(source.contains("conn40_avaritia_extreme_smithing"));
        assertTrue(source.contains("AvaritiaExtremeSmithingAdapter"));
        assertTrue(source.contains("forkOnlyBlock()"));
        var tests = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionTests.java");
        assertTrue(tests.contains("conn40_avaritia_extreme_smithing"));
        assertTrue(tests.contains("timeoutTicks = 200"));
    }

    @Test
    void idleDispatchHarvestIsFailClosedAndDoesNotCommitVirtualPlans() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionCheck.java");
        assertTrue(source.contains("checkIdleDispatchHarvest("));
        assertTrue(source.contains("runDispatchHarvestColocated("));
        assertTrue(source.contains("conn41_idle_dispatch_harvest"));
        assertTrue(source.contains("dummyUnmatchedPattern()"));
        assertTrue(source.contains("spawnHarvestBait("));
        assertTrue(source.contains("discardBait("));
        assertTrue(source.contains("failed to spawn harvest bait"));
        assertTrue(source.contains("extractOutputs(level, pos, filter, source, AdapterPersistentScope.NOOP)"));
        assertTrue(source.contains("planVirtualWithBinding("));
        assertFalse(source.contains("virtualPlan.commit("));
        assertFalse(source.contains("result.commit("));
        assertTrue(source.contains("Items.BARRIER"));
        assertTrue(source.contains("Items.DIAMOND"));
        assertTrue(source.contains("diamond-bait filter"));
        var command = read(
                "src/main/java/com/moakiee/ae2lt/packaged/gametest/ConnectionDiagnosticCommand.java");
        int finish = command.indexOf("private static void finishConn38(");
        int harvest = command.indexOf("runDispatchHarvestColocated(", finish);
        int pass41 = command.indexOf("pending.sink.pass(\"conn41_idle_dispatch_harvest\"", harvest);
        assertTrue(finish > 0 && harvest > finish && pass41 > harvest);
        var tests = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionTests.java");
        assertTrue(tests.contains("runDispatchHarvestColocated("));
    }

    @Test
    void harnessRestoresOnEveryPathAndKeepsColocatedInBounds() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/gametest/AdapterConnectionCheck.java");
        int runCase = source.indexOf("public static void runCase(");
        int runNeg = source.indexOf("public static void runNegative(");
        int forkSplit = source.indexOf("public static void runForkSplit(");
        String caseBody = source.substring(runCase, runNeg);
        String negBody = source.substring(runNeg, forkSplit);
        assertTrue(caseBody.contains("try {"));
        assertTrue(caseBody.contains("undoPlacement(level, placed)"));
        assertTrue(caseBody.indexOf("try {") < caseBody.lastIndexOf("finally {"));
        assertTrue(negBody.contains("try {"));
        assertTrue(negBody.contains("restoreBlock(level, pos, original, originalBe)"));
        assertTrue(source.contains("HARNESS_SET_FLAGS"));
        assertTrue(source.contains("UPDATE_KNOWN_SHAPE"));
        assertFalse(source.contains("origin.offset(spare++, 0, 8)"));
        assertFalse(source.contains("origin.offset(spare, 0, 8)"));
        int placeColocated = source.indexOf("public static Colocated placeColocated(");
        int stress = source.indexOf("public static boolean stressColocated(");
        String colocated = source.substring(placeColocated, stress);
        assertTrue(colocated.contains("forkOnlyBlock() && !avaritiaForkClassesPresent()"));
        assertFalse(colocated.contains("if (c.forkGated() && !avaritiaForkClassesPresent())"));
        assertTrue(colocated.contains("colocatedSlot("));
        assertTrue(source.contains("expected inert under AvaritiaNeo"));
        var snbt = read("src/main/resources/data/ae2ltpp/structures/adapter_conn.snbt");
        assertTrue(snbt.contains("size: [16, 8, 16]"));
        assertFalse(snbt.contains("size: [5, 5, 5]"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
