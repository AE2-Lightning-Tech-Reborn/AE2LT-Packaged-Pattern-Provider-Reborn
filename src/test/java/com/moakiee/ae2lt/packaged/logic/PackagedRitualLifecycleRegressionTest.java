package com.moakiee.ae2lt.packaged.logic;

import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;

import org.junit.jupiter.api.Test;

/** Executes unchanged production lifecycle methods with controlled world boundaries. */
public class PackagedRitualLifecycleRegressionTest {
    private static final Path BASE = Path.of("src/main/java/com/moakiee/ae2lt/packaged/logic");

    @Test
    void lifecycleBehavior() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        String ritual = Files.readString(BASE.resolve("multiblock/occultism/OccultismRitualAdapter.java"));
        String provider = Files.readString(BASE.resolve("PackagedPatternProviderLogic.java"));
        String methods = extract(ritual, Set.of("tickPending", "supportsPatternIndependentHarvest"), -1)
                + extract(ritual, Set.of("canDispatch"), 4)
                + extract(ritual, Set.of("extractOutputs"), 5);
        String polling = extract(provider,
                Set.of("runAutoReturnTick", "autoReturnNormal", "autoReturnWireless"), -1)
                + """
                    // This harness covers ordinary/ritual forwarding. Mek polling has separate tests.
                    private void pollOutputs(MultiblockAdapter adapter, ServerLevel level, BlockPos pos,
                            AllowedOutputFilter filter, long tick) {
                        var outputs = adapter.extractOutputs(level, pos, filter, getActionSource(), adapterScope());
                        if (!outputs.isEmpty()) enqueueVirtualOutputs(insertOutputsToReturnInv(outputs));
                    }
                  """;
        run(HARNESS.replace("/* RITUAL */", methods).replace("/* PROVIDER */", polling));

        // Verify the harness rejects the two original lifecycle regressions.
        expectFailure(HARNESS.replace("/* RITUAL */", methods.replace(
                "tickPending(level, mainPos, scope);", ""))
                .replace("/* PROVIDER */", polling));
        expectFailure(HARNESS.replace("/* RITUAL */", methods).replace("/* PROVIDER */",
                polling.replace("var filter = getOrBuildOutputFilter();",
                        "var filter = getOrBuildOutputFilter(); if (filter.isEmpty()) return;")));

        expectFailure(HARNESS.replace("/* RITUAL */", methods).replace("/* PROVIDER */",
                polling.replace("} else {", "} else if (!filter.isEmpty()) {")));
        String wireless = extract(provider, Set.of("autoReturnWireless"), -1);
        expectFailure(HARNESS.replace("/* RITUAL */", methods).replace("/* PROVIDER */",
                polling.replace(wireless, wireless.replace(
                        "if (filter.isEmpty() && !adapter.supportsPatternIndependentHarvest())",
                        "if (false)"))));

        // Extraction's OFF guard must run before any world lookup or mutation.
        String extraction = extract(ritual, Set.of("extractOutputs"), 5);
        int guard = extraction.indexOf("if (scope.getReturnMode() != ReturnMode.AUTO)");
        if (guard < 0 || guard > extraction.indexOf("level.getBlockEntity(mainPos)")) {
            throw new AssertionError("OFF must guard extraction before world access");
        }
        String plan = extract(ritual, Set.of("planWithBinding"), 7);
        if (!plan.contains("!canDispatch(level, mainPos, handle, scope)")
                || !plan.contains("boolean autoHarvest = scope.getReturnMode() == ReturnMode.AUTO;")
                || !plan.contains("autoHarvest ? patternItemOutputs(pattern) : List.<ItemStack>of()")
                || !plan.contains("preexistingEntities, outputBowls, bowlBaselines, autoHarvest")) {
            throw new AssertionError("dispatch must guard ownership and snapshot harvest policy");
        }
        System.out.println("PASS: ritual lifecycle, empty-pattern polling, and original-bug mutations");
    }

    @Test
    void adjacentTargetRemovalBehavior() throws Exception {
        String provider = Files.readString(BASE.resolve("PackagedPatternProviderLogic.java"));
        String host = Files.readString(BASE.resolve("../blockentity/PackagedPatternProviderBlockEntity.java"));
        String methods = extract(provider,
                Set.of("onNeighborChanged", "clearRemovedAdjacentTargetState"), -1)
                + "private void resetMekScheduling() {}";
        String source = NEIGHBOR_HARNESS.replace("/* PROVIDER */", methods)
                .replace("/* HOST */", extract(host, Set.of("clearFlagsForTarget"), -1));
        run(source);
        expectFailure(source.replace("clearRemovedAdjacentTargetState();", ""));
        expectFailure(source.replace("if (!level.isLoaded(pos))", "if (false)"));
        expectFailure(source.replace("if (!remains)", "if (true)"));
    }

    @Test
    void neighborIndexReusesSnapshotsAndRefreshesAcrossClockChanges() throws Exception {
        String index = Files.readString(BASE.resolve("multiblock/NeighborMainBlockIndex.java"));
        String indexClass = index.substring(index.indexOf("public final class NeighborMainBlockIndex"))
                .replace("public final class NeighborMainBlockIndex", "static final class NeighborMainBlockIndex")
                .replace("@Nullable", "");
        run(INDEX_HARNESS.replace("/* INDEX */", indexClass));
    }

    @Test
    void outputResidualsMergeWithoutQuadraticSearchOrDeliveryReplay() throws Exception {
        String provider = Files.readString(BASE.resolve("PackagedPatternProviderLogic.java"));
        String methods = extract(provider, Set.of("insertOutputsToReturnInv", "addMergedResidual"), -1);
        run(OUTPUT_HARNESS.replace("/* OUTPUT */", methods));
    }

    private static String extract(String source, Set<String> names, int arity) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var input = new SimpleJavaFileObject(URI.create("string:///Source.java"),
                javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, files, null,
                    List.of("-proc:none"), null, List.of(input));
            var unit = task.parse().iterator().next();
            var positions = Trees.instance(task).getSourcePositions();
            var type = (ClassTree) unit.getTypeDecls().get(0);
            var selected = new StringBuilder();
            int count = 0;
            for (var member : type.getMembers()) {
                if (member instanceof MethodTree method && names.contains(method.getName().toString())
                        && (arity < 0 || method.getParameters().size() == arity)) {
                    selected.append(source, (int) positions.getStartPosition(unit, member),
                            (int) positions.getEndPosition(unit, member)).append('\n');
                    count++;
                }
            }
            if (count != names.size()) {
                throw new AssertionError("Production signatures changed; update lifecycle harness");
            }
            return selected.toString();
        }
    }

    private static void expectFailure(String source) throws Exception {
        try {
            run(source);
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError("Original bug mutation was not detected");
    }

    private static void run(String source) throws Exception {
        Path temp = Files.createTempDirectory("packaged-ritual-lifecycle-");
        try {
            Path file = temp.resolve("LifecycleHarness.java");
            Files.writeString(file, source);
            if (ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-proc:none", "-d", temp.toString(), file.toString()) != 0) {
                throw new IllegalStateException("Lifecycle harness failed to compile");
            }
            try (var loader = new URLClassLoader(new java.net.URL[] {temp.toUri().toURL()})) {
                try {
                    loader.loadClass("LifecycleHarness").getMethod("run").invoke(null);
                } catch (java.lang.reflect.InvocationTargetException failure) {
                    if (failure.getCause() instanceof AssertionError assertion) {
                        throw assertion;
                    }
                    throw failure;
                }
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final String OUTPUT_HARNESS = """
        import java.util.*;
        public class LifecycleHarness {
            record AEKey(int id) {
                static int comparisons;
                @Override public boolean equals(Object other) {
                    comparisons++;
                    return other instanceof AEKey key && id == key.id;
                }
                @Override public int hashCode() { return id; }
            }
            record GenericStack(AEKey what, long amount) {}
            enum Actionable { MODULATE }
            static class Logger { void warn(String message, Object... args) {} }
            static final Logger LOG = new Logger();
            static class Grid {
                long affordable = Long.MAX_VALUE;
                long charged;
                int checks, charges;
                boolean failCheck, failCharge;
            }
            static class Node {
                final Grid grid = new Grid();
                Grid getGrid() { return grid; }
            }
            static class ReturnInventory {
                long limit = Long.MAX_VALUE;
                final Map<AEKey, Long> delivered = new LinkedHashMap<>();
                boolean failInsert;
                int inserts;
                long insert(int slot, AEKey key, long amount, Actionable mode) {
                    inserts++;
                    if (failInsert) throw new IllegalStateException("insert rejected before delivery");
                    long accepted = Math.min(limit, amount);
                    if (accepted > 0) delivered.merge(key, accepted, Long::sum);
                    return accepted;
                }
            }
            static class PatternProviderPowerCost {
                static long maxAffordable(Grid grid, AEKey key, long total) {
                    grid.checks++;
                    if (grid.failCheck) throw new IllegalStateException("affordability failed");
                    return Math.min(grid.affordable, total);
                }
                static void consume(Grid grid, AEKey key, long amount) {
                    grid.charges++;
                    if (grid.failCharge) throw new IllegalStateException("charge failed after delivery");
                    grid.charged += amount;
                }
            }
            final Node node = new Node();
            final ReturnInventory inventory = new ReturnInventory();
            int alerts;
            boolean failAlert;
            Node getGridNode() { return node; }
            ReturnInventory getInternalReturnInv() { return inventory; }
            void alertGridTick() {
                alerts++;
                if (failAlert) throw new IllegalStateException("ticker notification failed");
            }
            /* OUTPUT */
            static void check(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }
            public static void run() {
                var host = new LifecycleHarness();
                check(host.insertOutputsToReturnInv(List.of()).isEmpty() && host.node.grid.checks == 0,
                        "empty output performs no delivery work");
                var outputs = new ArrayList<GenericStack>();
                for (int pass = 0; pass < 2; pass++) {
                    for (int index = 0; index < 1024; index++) {
                        outputs.add(new GenericStack(new AEKey(index), pass + 1));
                    }
                }
                host.node.grid.affordable = 0;
                AEKey.comparisons = 0;
                var residuals = host.insertOutputsToReturnInv(outputs);
                check(AEKey.comparisons < 10000, "many distinct residuals must not use quadratic key comparisons");
                check(residuals.size() == 1024 && host.alerts == 1 && host.inventory.inserts == 0,
                        "blocked outputs are merged and wake the ticker once");
                for (int index = 0; index < residuals.size(); index++) {
                    check(residuals.get(index).what().id() == index && residuals.get(index).amount() == 3,
                            "residuals preserve first-seen key order and amount");
                }
                try {
                    residuals.add(new GenericStack(new AEKey(-1), 1));
                    throw new AssertionError("residual snapshot must be immutable");
                } catch (UnsupportedOperationException expected) {}

                var iron = new AEKey(1);
                var gold = new AEKey(2);
                host = new LifecycleHarness();
                host.node.grid.affordable = 4;
                host.inventory.limit = 2;
                residuals = host.insertOutputsToReturnInv(List.of(
                        new GenericStack(iron, 10), new GenericStack(gold, 5), new GenericStack(new AEKey(1), 3)));
                check(residuals.equals(List.of(new GenericStack(iron, 9), new GenericStack(gold, 3))),
                        "partial deliveries retain only undelivered amounts");
                check(host.node.grid.charged == 6 && host.inventory.delivered.get(iron) == 4
                        && host.inventory.delivered.get(gold) == 2, "energy is charged only for actual deliveries");
                host.node.grid.affordable = Long.MAX_VALUE;
                host.inventory.limit = Long.MAX_VALUE;
                check(host.insertOutputsToReturnInv(residuals).isEmpty() && host.node.grid.charged == 18,
                        "retry delivers exact residuals without duplication");

                host = new LifecycleHarness();
                host.node.grid.failCheck = true;
                check(host.insertOutputsToReturnInv(List.of(new GenericStack(iron, 7))).equals(
                        List.of(new GenericStack(iron, 7))) && host.inventory.inserts == 0,
                        "affordability failure retains all products without insertion");
                host = new LifecycleHarness();
                host.inventory.failInsert = true;
                check(host.insertOutputsToReturnInv(List.of(new GenericStack(iron, 7))).equals(
                        List.of(new GenericStack(iron, 7))) && host.node.grid.charges == 0,
                        "insertion failure retains all products without charging");
                host = new LifecycleHarness();
                host.node.grid.failCharge = true;
                host.inventory.limit = 3;
                host.failAlert = true;
                check(host.insertOutputsToReturnInv(List.of(new GenericStack(iron, 7))).equals(
                        List.of(new GenericStack(iron, 4))) && host.inventory.delivered.get(iron) == 3,
                        "post-delivery charge and wake failures must not replay delivered products");
                host = new LifecycleHarness();
                var invalid = Arrays.asList(null, new GenericStack(null, 5), new GenericStack(iron, 0),
                        new GenericStack(iron, -1));
                check(host.insertOutputsToReturnInv(invalid).isEmpty() && host.node.grid.checks == 0,
                        "invalid stacks cause no delivery or retained amounts");
                var merged = new LinkedHashMap<AEKey, Long>();
                addMergedResidual(merged, iron, Long.MAX_VALUE - 1);
                addMergedResidual(merged, new AEKey(1), 5);
                addMergedResidual(merged, null, 5);
                addMergedResidual(merged, gold, -1);
                check(merged.size() == 1 && merged.get(iron) == Long.MAX_VALUE,
                        "residual overflow saturates without negative amounts");
            }
        }
        """;

    private static final String INDEX_HARNESS = """
        import java.util.*;
        public class LifecycleHarness {
            enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
            record BlockPos(long value) {
                BlockPos relative(Direction face) { return new BlockPos(value + face.ordinal() + 1); }
            }
            record MultiblockAdapter(String id) {}
            static class ServerLevel {
                long tick;
                int reads, loadedChecks;
                final Map<BlockPos, MultiblockAdapter> blocks = new HashMap<>();
                final Set<BlockPos> unloaded = new HashSet<>();
                long getGameTime() { return tick; }
                boolean isLoaded(BlockPos pos) { loadedChecks++; return !unloaded.contains(pos); }
                MultiblockAdapter getBlockEntity(BlockPos pos) {
                    check(!unloaded.contains(pos), "unloaded neighbors must not be read");
                    reads++;
                    return blocks.get(pos);
                }
            }
            static class MultiblockAdapterRegistry {
                static MultiblockAdapter find(ServerLevel level, BlockPos pos, MultiblockAdapter block) {
                    return block;
                }
            }
            /* INDEX */
            static void check(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }
            static void checkImmutable(List<Direction> snapshot) {
                try { snapshot.add(Direction.DOWN); }
                catch (UnsupportedOperationException expected) { return; }
                throw new AssertionError("cached face snapshots must be immutable");
            }
            public static void run() {
                var origin = new BlockPos(0);
                var east = origin.relative(Direction.EAST);
                var west = origin.relative(Direction.WEST);
                var first = new MultiblockAdapter("spirit-fire");
                var replacement = new MultiblockAdapter("replacement");
                var level = new ServerLevel();
                level.tick = 100;
                level.blocks.put(east, first);
                var index = new NeighborMainBlockIndex(origin);
                var initial = index.adapterFaces(level);
                check(initial.equals(List.of(Direction.EAST)) && index.getAdapter(Direction.EAST) == first,
                        "cold cache detects a recognized neighbor");
                checkImmutable(initial);
                for (int lookup = 0; lookup < 10000; lookup++) {
                    check(index.adapterFaces(level) == initial, "cache hit reuses the same face snapshot");
                }
                check(level.reads == 6 && level.loadedChecks == 6, "cache hits perform no rescans");
                level.tick = 299;
                check(index.adapterFaces(level) == initial && level.reads == 6, "cache remains fresh before TTL");
                level.tick = 300;
                level.blocks.put(west, replacement);
                var expanded = index.adapterFaces(level);
                check(expanded.equals(List.of(Direction.WEST, Direction.EAST)) && level.reads == 12,
                        "TTL boundary refreshes faces in direction order");
                check(initial.equals(List.of(Direction.EAST)), "old snapshot is not a mutable cache view");

                level.blocks.put(east, replacement);
                index.invalidate();
                check(index.getAdapter(Direction.EAST) == first, "invalidation preserves old adapter for ownership cleanup");
                var refreshed = index.adapterFaces(level);
                check(index.getAdapter(Direction.EAST) == replacement && level.reads == 18,
                        "explicit invalidation refreshes adapter identity");
                check(index.adapterFaces(level) == refreshed, "new snapshot is reused after invalidation");
                level.unloaded.add(east);
                index.invalidate();
                var unloaded = index.adapterFaces(level);
                check(unloaded.equals(List.of(Direction.WEST)) && index.getAdapter(Direction.EAST) == null
                        && level.reads == 23, "unloaded neighbor is skipped without world reads");
                level.unloaded.clear();
                level.tick = 500;
                check(index.adapterFaces(level).equals(List.of(Direction.WEST, Direction.EAST)),
                        "reloaded neighbor returns at the next TTL");
                level.blocks.clear();
                level.tick = 450;
                check(index.adapterFaces(level).isEmpty() && index.getAdapter(Direction.EAST) == null,
                        "clock rollback refreshes stale neighbors immediately");
                int reads = level.reads;
                var empty = index.adapterFaces(level);
                for (int lookup = 0; lookup < 10000; lookup++) {
                    check(index.adapterFaces(level) == empty, "empty snapshots also reuse cache");
                }
                check(level.reads == reads, "empty cache does not continuously rescan");
                checkImmutable(empty);

                level.tick = Long.MIN_VALUE;
                level.blocks.put(east, first);
                check(index.adapterFaces(level).equals(List.of(Direction.EAST)), "wrapped clock refreshes cache");
                level.tick = Long.MAX_VALUE;
                level.blocks.clear();
                check(index.adapterFaces(level).isEmpty(), "overflowed elapsed time expires stale cache");
                var newest = index.adapterFaces(level);
                reads = level.reads;
                check(index.adapterFaces(level) == newest && level.reads == reads,
                        "extreme clock values still allow stable cache hits");
            }
        }
        """;

    private static final String NEIGHBOR_HARNESS = """
        import java.util.*;
        public class LifecycleHarness {
            enum ProviderMode { NORMAL, WIRELESS }
            enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
            record BlockPos(long value) {
                BlockPos relative(Direction face) { return new BlockPos(value + face.ordinal() + 1); }
                long asLong() { return value; }
            }
            static class ServerLevel {
                Map<BlockPos, String> blocks = new HashMap<>();
                Set<BlockPos> unloaded = new HashSet<>();
                boolean isLoaded(BlockPos pos) { return !unloaded.contains(pos); }
                String getBlockEntity(BlockPos pos) {
                    check(isLoaded(pos), "must not look up an unloaded target");
                    return blocks.get(pos);
                }
            }
            interface Scope { void clearFlagsForTarget(BlockPos pos); }
            static class Host implements Scope {
                ProviderMode mode = ProviderMode.NORMAL;
                Object level = new ServerLevel();
                Map<String, HashSet<Long>> adapterFlags = new HashMap<>();
                Map<String, HashMap<Long, String>> adapterStates = new HashMap<>();
                int saves;
                ProviderMode getProviderMode() { return mode; }
                Object getLevel() { return level; }
                BlockPos getBlockPos() { return new BlockPos(0); }
                void saveChanges() { saves++; }
                void own(BlockPos pos) {
                    adapterFlags.computeIfAbsent("ritual:dispatched", k -> new HashSet<>()).add(pos.asLong());
                    adapterStates.computeIfAbsent("ritual:harvest", k -> new HashMap<>())
                            .put(pos.asLong(), "unpaid-auto-debt");
                }
                boolean owns(BlockPos pos) {
                    return adapterFlags.values().stream().anyMatch(v -> v.contains(pos.asLong()))
                            && adapterStates.values().stream().anyMatch(v -> v.containsKey(pos.asLong()));
                }
                /* HOST */
            }
            record Adapter(String kind) {
                boolean recognizesMain(ServerLevel level, BlockPos pos, String be) {
                    return kind.equals(be);
                }
            }
            record Registration(Adapter adapter, boolean enabled) {}
            static class MultiblockAdapterRegistry {
                static List<Registration> entries = new ArrayList<>();
                static List<Registration> registrations() { return entries; }
            }
            static class NeighborIndex {
                Map<Direction, Adapter> cache = new EnumMap<>(Direction.class);
                boolean dirty;
                Adapter getAdapter(Direction face) { return cache.get(face); }
                void invalidate() { dirty = true; }
            }
            static class BindingTable {
                boolean valid = true;
                void invalidateAll() { valid = false; }
            }
            static class BaseProvider {
                int notifications;
                public void onNeighborChanged() { notifications++; }
            }
            static class Provider extends BaseProvider {
                Host host = new Host();
                NeighborIndex neighborIndex = new NeighborIndex();
                BindingTable bindingTable = new BindingTable();
                Set<String> cooldownTable = new HashSet<>();
                Host getProviderHost() { return host; }
                Scope adapterScope() { return host; }
                /* PROVIDER */
            }
            static void check(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }
            public static void run() {
                var ritual = new Adapter("golden-bowl");
                var other = new Adapter("other-machine");
                MultiblockAdapterRegistry.entries.add(new Registration(ritual, false));
                MultiblockAdapterRegistry.entries.add(new Registration(other, true));
                var p = new Provider();
                var level = (ServerLevel) p.host.level;
                var target = p.host.getBlockPos().relative(Direction.EAST);
                var unrelated = p.host.getBlockPos().relative(Direction.WEST);
                var distant = new BlockPos(100);
                level.blocks.put(target, "golden-bowl");
                level.blocks.put(unrelated, "other-machine");
                p.neighborIndex.cache.put(Direction.EAST, ritual);
                p.host.own(target); p.host.own(unrelated); p.host.own(distant);
                p.onNeighborChanged();
                check(p.host.owns(target) && p.host.saves == 0, "unchanged target retains debt");
                check(!p.bindingTable.valid && p.neighborIndex.dirty && p.notifications == 1,
                        "normal neighbor invalidation still runs");

                level.blocks.remove(target); // normal break event precedes replacement
                p.onNeighborChanged();
                check(!p.host.owns(target) && p.host.saves == 1, "break retires and persists debt");
                check(p.host.owns(unrelated) && p.host.owns(distant), "cleanup is target-scoped");
                level.blocks.put(target, "golden-bowl");
                p.onNeighborChanged();
                check(!p.host.owns(target), "replacement must not inherit old debt");
                p.host.own(target); // a new craft can now own this position
                p.onNeighborChanged();
                check(p.host.owns(target), "new job survives ordinary updates");

                level.unloaded.add(target);
                level.blocks.remove(target);
                p.onNeighborChanged();
                check(p.host.owns(target), "unloaded target retains flags and state");
                level.blocks.put(target, "golden-bowl");
                level.unloaded.clear();
                p.onNeighborChanged();
                check(p.host.owns(target), "reload of same target retains debt");

                level.blocks.remove(unrelated);
                p.onNeighborChanged();
                check(p.host.owns(target) && !p.host.owns(unrelated), "unrelated neighbor removal is isolated");

                // No patterns or bindings, including a cold index after provider reload.
                p.neighborIndex.cache.clear();
                p.bindingTable.invalidateAll();
                p.onNeighborChanged();
                check(p.host.owns(target), "cold disabled registration still recognizes existing target");
                level.blocks.remove(target);
                p.onNeighborChanged();
                check(!p.host.owns(target), "no-pattern cold-index removal clears debt");
                check(p.host.adapterFlags.values().stream().noneMatch(v -> v.contains(target.asLong()))
                        && p.host.adapterStates.values().stream().noneMatch(v -> v.containsKey(target.asLong())),
                        "both flags and values must be cleared");
                level.blocks.put(target, "golden-bowl");
                p.onNeighborChanged();
                check(!p.host.owns(target), "cold-index remove then replace stays debt-free");

                p.host.own(target);
                p.neighborIndex.cache.put(Direction.EAST, ritual);
                level.blocks.put(target, "other-machine");
                p.onNeighborChanged();
                check(!p.host.owns(target), "known target replaced by another adapter clears debt");

                p.host.own(target);
                p.host.mode = ProviderMode.WIRELESS;
                level.blocks.remove(target);
                p.onNeighborChanged();
                check(p.host.owns(target), "local updates must not clear wireless ownership");
                p.host.mode = ProviderMode.NORMAL;
                p.host.level = null;
                p.onNeighborChanged();
                check(p.host.owns(target), "missing server level preserves ownership");
            }
        }
        """;

    private static final String HARNESS = """
        import java.util.*;
        public class LifecycleHarness {
            enum ReturnMode { OFF, AUTO }
            enum ProviderMode { NORMAL, WIRELESS }
            record BlockPos(int value) {
                BlockPos relative(int face) { return new BlockPos(value + face); }
            }
            record Dimension(String location) {}
            record GenericStack(AEItemKey what, long amount) {}
            record AEItemKey() { static AEItemKey of(ItemStack stack) { return new AEItemKey(); } }
            static class IActionSource {}
            static class ItemStack {
                int count;
                ItemStack(int count) { this.count = count; }
                int getCount() { return count; }
                boolean isEmpty() { return count == 0; }
                ItemStack copy() { return new ItemStack(count); }
                void setCount(int count) { this.count = count; }
                void shrink(int count) { this.count -= count; }
                static boolean isSameItemSameTags(ItemStack a, ItemStack b) { return true; }
            }
            static class Handler {
                ItemStack stack = new ItemStack(1);
                int getSlots() { return 1; }
                ItemStack getStackInSlot(int slot) { return stack; }
                ItemStack extractItem(int slot, int count, boolean simulate) {
                    int amount = Math.min(count, stack.count);
                    if (!simulate) stack.shrink(amount);
                    return new ItemStack(amount);
                }
                ItemStack insertItem(int slot, ItemStack value, boolean simulate) { return value; }
            }
            static class ItemEntity {
                ItemStack stack = new ItemStack(1);
                UUID id = UUID.randomUUID();
                boolean isAlive() { return !stack.isEmpty(); }
                ItemStack getItem() { return stack; }
                UUID getUUID() { return id; }
                void discard() { stack = new ItemStack(0); }
                void setItem(ItemStack value) { stack = value; }
            }
            static class Bowl {
                boolean idle;
                Object getBlockState() { return null; }
            }
            static class ServerLevel {
                Bowl bowl = new Bowl();
                List<ItemEntity> entities = new ArrayList<>();
                Set<BlockPos> unloaded = new HashSet<>();
                Map<BlockPos, MultiblockAdapter> adapters = new HashMap<>();
                Server server = new Server();
                Server getServer() { return server; }
                boolean isLoaded(BlockPos pos) { return !unloaded.contains(pos); }
                List<ItemEntity> getEntitiesOfClass(Class<ItemEntity> type, Object bounds) { return entities; }
                Dimension dimension() { return new Dimension("overworld"); }
                Bowl getBlockEntity(BlockPos pos) { return bowl; }
            }
            record Connection(String dimension, BlockPos pos) {}
            static class Server {
                Map<String, ServerLevel> levels = new HashMap<>();
                ServerLevel getLevel(String dimension) { return levels.get(dimension); }
            }
            static class MultiblockAdapterRegistry {
                static MultiblockAdapter find(ServerLevel level, BlockPos pos, Bowl be) {
                    return level.adapters.get(pos);
                }
            }
            static class OccultismReflection {
                static boolean isIdle(Bowl bowl) { return bowl.idle; }
                static boolean isSacrificialBowl(Bowl bowl) { return true; }
                static Handler itemHandler(Bowl bowl) { return null; }
            }
            static class OccultismHarvestState {
                final boolean auto, complete;
                OccultismHarvestState(boolean auto, boolean complete) {
                    this.auto = auto; this.complete = complete;
                }
                boolean autoHarvest() { return auto; }
                boolean complete() { return complete; }
                List<BlockPos> outputBowls() { return List.of(); }
                Map<UUID, Integer> preexistingEntityCounts() { return Map.of(); }
                int claimableFromBowl(BlockPos pos, ItemStack stack) { return claimable(stack, stack.count); }
                int claimable(ItemStack stack, int amount) { return auto && !complete ? Math.min(1, amount) : 0; }
                static int attributableCount(int count, int baseline) { return Math.max(0, count - baseline); }
                OccultismHarvestState consume(ItemStack stack, int amount) {
                    return new OccultismHarvestState(auto, amount > 0);
                }
                String encode() { return auto ? (complete ? "done" : "auto") : "off"; }
                static OccultismHarvestState decode(String value, String dimension) {
                    if ("off".equals(value)) return new OccultismHarvestState(false, true);
                    if ("auto".equals(value)) return new OccultismHarvestState(true, false);
                    if ("done".equals(value)) return new OccultismHarvestState(true, true);
                    return null;
                }
            }
            static class AdapterPersistentScope {
                ReturnMode mode = ReturnMode.OFF;
                Map<String, String> states = new HashMap<>();
                Set<String> flags = new HashSet<>();
                ReturnMode getReturnMode() { return mode; }
                void setState(BlockPos pos, String key, String value) { states.put(key, value); }
                String getState(BlockPos pos, String key) { return states.get(key); }
                boolean hasFlag(BlockPos pos, String key) { return flags.contains(key); }
            }
            static class AllowedOutputFilter {
                boolean empty = true;
                boolean isEmpty() { return empty; }
            }
            static class MultiblockAdapter {
                int polls;
                boolean supportsPatternIndependentHarvest() { return false; }
                void tickPending(ServerLevel l, BlockPos p, AdapterPersistentScope s) {}
                boolean canDispatch(ServerLevel l, BlockPos p, Object h, AdapterPersistentScope s) { return false; }
                List<GenericStack> extractOutputs(ServerLevel l, BlockPos p, AllowedOutputFilter f,
                        IActionSource source, AdapterPersistentScope scope) { polls++; return List.of(); }
            }
            static class VirtualCraftingAdapter extends MultiblockAdapter {}
            static class Ritual extends MultiblockAdapter {
                static final String RITUAL_HARVEST_STATE = "legacy-state";
                static final String RITUAL_DISPATCHED_FLAG = "legacy-flag";
                static final Log LOG = new Log();
                static class Log {
                    void warn(String message, Object... args) {}
                    void error(String message, Object... args) {}
                }
                Object ritualOutputAabb(BlockPos pos) { return null; }
                boolean isUpsideDownBowl(Object state) { return true; }
                String harvestStateKey(ServerLevel l) { return "state"; }
                String dispatchedFlagKey(ServerLevel l) { return "flag"; }
                boolean recognizesMain(ServerLevel l, BlockPos p, Bowl b) { return true; }
                void logStaleState(ServerLevel l, BlockPos p, OccultismHarvestState s) {}
                void clearHarvestState(ServerLevel l, BlockPos p, AdapterPersistentScope s) {
                    s.states.clear(); s.flags.clear();
                }
                boolean canDispatch(ServerLevel l, BlockPos p, Object h) {
                    return l.bowl != null && l.bowl.idle;
                }
                /* RITUAL */
            }
            static class Host {
                ProviderMode mode = ProviderMode.NORMAL;
                ProviderMode getProviderMode() { return mode; }
                BlockPos getBlockPos() { return new BlockPos(0); }
            }
            static class NeighborIndex {
                List<MultiblockAdapter> adapters = new ArrayList<>();
                List<Integer> adapterFaces(ServerLevel l) {
                    return java.util.stream.IntStream.range(0, adapters.size()).boxed().toList();
                }
                MultiblockAdapter getAdapter(int face) { return adapters.get(face); }
            }
            static class Provider {
                Host host = new Host();
                NeighborIndex neighborIndex = new NeighborIndex();
                AdapterPersistentScope scope = new AdapterPersistentScope();
                AllowedOutputFilter filter = new AllowedOutputFilter();
                List<Connection> connections = new ArrayList<>();
                List<Connection> getValidConnections(ServerLevel level, long tick) { return connections; }
                int delivered;
                Host getProviderHost() { return host; }
                AdapterPersistentScope adapterScope() { return scope; }
                IActionSource getActionSource() { return null; }
                AllowedOutputFilter getOrBuildOutputFilter() { return filter; }
                List<GenericStack> insertOutputsToReturnInv(List<GenericStack> outputs) {
                    delivered += outputs.size(); return List.of();
                }
                void enqueueVirtualOutputs(List<GenericStack> outputs) {}
                /* PROVIDER */
            }
            static void check(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }
            public static void run() {
                var adapter = new Ritual();
                var level = new ServerLevel();
                var pos = new BlockPos(0);
                var scope = new AdapterPersistentScope();
                level.bowl.idle = true;
                check(adapter.canDispatch(level, pos, null, scope), "initial OFF dispatch");
                scope.states.put("state", "off"); scope.flags.add("flag");
                level.bowl.idle = false;
                check(!adapter.canDispatch(level, pos, null, scope), "busy OFF blocks");
                check(scope.states.containsKey("state"), "busy OFF retains marker");
                level.bowl.idle = true;
                check(adapter.canDispatch(level, pos, null, scope), "OFF external collection permits next dispatch");
                check(scope.states.isEmpty() && scope.flags.isEmpty(), "OFF cleanup retires marker");

                // Reloaded OFF state is never converted into AUTO ownership.
                scope.states.put("state", "off"); scope.mode = ReturnMode.AUTO;
                level.bowl.idle = false;
                adapter.tickPending(level, pos, scope);
                check("off".equals(scope.states.get("state")), "OFF to AUTO while busy");
                level.bowl.idle = true;
                adapter.tickPending(level, pos, scope);
                check(scope.states.isEmpty(), "OFF to AUTO retires without adopting products");

                scope.states.put("state", "auto"); scope.flags.add("flag");
                scope.mode = ReturnMode.OFF;
                adapter.tickPending(level, pos, scope);
                check(!adapter.canDispatch(level, pos, null, scope), "AUTO to OFF preserves unpaid debt");
                check("auto".equals(scope.states.get("state")), "external collection cannot erase AUTO debt");
                scope.mode = ReturnMode.AUTO;
                check(!adapter.canDispatch(level, pos, null, scope), "AUTO resumes same reserved job");
                scope.states.put("state", "done");
                check(adapter.canDispatch(level, pos, null, scope), "paid AUTO job permits next dispatch");
                for (String encoded : List.of("bad", "auto")) {
                    scope.states.put("state", encoded);
                    adapter.tickPending(level, pos, scope);
                    check(!adapter.canDispatch(level, pos, null, scope), "unknown or unfinished state blocks");
                }
                scope.states.clear(); scope.flags.clear(); scope.flags.add("legacy-flag");
                check(!adapter.canDispatch(level, pos, null, scope), "legacy flag blocks overwrite");
                scope.states.put("state", "off");
                adapter.tickPending(level, pos, scope);
                check(scope.states.containsKey("state"), "conflicting legacy marker retained");

                scope.states.clear(); scope.flags.clear(); scope.states.put("state", "off");
                scope.mode = ReturnMode.OFF;
                var oldOutput = new ItemEntity(); level.entities.add(oldOutput);
                check(adapter.extractOutputs(level, pos, null, null, scope).isEmpty(), "OFF never extracts");
                check(oldOutput.isAlive() && scope.states.containsKey("state"), "OFF extraction changes nothing");
                scope.mode = ReturnMode.AUTO;
                check(adapter.extractOutputs(level, pos, null, null, scope).isEmpty(), "OFF to AUTO cannot adopt old output");
                check(oldOutput.isAlive() && scope.states.isEmpty(), "old output remains externally owned");
                check(adapter.extractOutputs(level, pos, null, null, scope).isEmpty(), "no state means no ownership");
                level.entities.clear();
                var paidOutput = new ItemEntity(); level.entities.add(paidOutput);
                scope.states.put("state", "auto"); scope.mode = ReturnMode.OFF;
                check(adapter.extractOutputs(level, pos, null, null, scope).isEmpty(), "AUTO to OFF pauses harvest");
                check(paidOutput.isAlive() && "auto".equals(scope.states.get("state")), "paused debt survives");
                scope.mode = ReturnMode.AUTO;
                check(adapter.extractOutputs(level, pos, null, null, scope).size() == 1, "AUTO resumes owned harvest");
                check(!paidOutput.isAlive() && scope.states.isEmpty(), "harvest clears paid debt");

                var provider = new Provider();
                adapter = new Ritual() {
                    @Override public List<GenericStack> extractOutputs(ServerLevel l, BlockPos p,
                            AllowedOutputFilter f, IActionSource source, AdapterPersistentScope s) {
                        polls++;
                        return super.extractOutputs(l, p, f, source, s);
                    }
                };
                provider.scope.mode = ReturnMode.AUTO;
                var ordinary = new MultiblockAdapter();
                var virtual = new VirtualCraftingAdapter();
                provider.neighborIndex.adapters.addAll(List.of(adapter, ordinary, virtual));
                provider.filter.empty = false;
                provider.runAutoReturnTick(level, 0);
                check(adapter.polls == 1 && ordinary.polls == 1 && virtual.polls == 0, "normal pattern polling");
                provider.filter.empty = true; // last pattern removed, including after provider reload
                provider.scope.states.put("state", "auto");
                level.entities.clear(); level.entities.add(new ItemEntity());
                provider.runAutoReturnTick(level, 20);
                check(adapter.polls == 2, "owned pending job survives last pattern removal");
                check(provider.delivered == 1 && provider.scope.states.isEmpty()
                        && !level.entities.get(0).isAlive(), "empty-pattern poll finishes persisted paid job");
                check(ordinary.polls == 1 && virtual.polls == 0, "empty filter cannot enable unrelated extraction");
                provider.host.mode = ProviderMode.WIRELESS;
                var remote = new ServerLevel();
                level.server.levels.put("remote", remote);
                var ritualPos = new BlockPos(10);
                var ordinaryPos = new BlockPos(11);
                var virtualPos = new BlockPos(12);
                var unknownPos = new BlockPos(13);
                remote.adapters.put(ritualPos, adapter);
                remote.adapters.put(ordinaryPos, ordinary);
                remote.adapters.put(virtualPos, virtual);
                provider.connections.addAll(List.of(new Connection("remote", ritualPos),
                        new Connection("remote", ordinaryPos), new Connection("remote", virtualPos),
                        new Connection("remote", unknownPos), new Connection("missing", ritualPos)));
                provider.scope.states.put("state", "auto"); // restored paid job with no patterns
                remote.entities.add(new ItemEntity());
                provider.runAutoReturnTick(level, 40);
                check(adapter.polls == 3 && provider.delivered == 1
                        && "auto".equals(provider.scope.states.get("state")), "wireless busy job retains debt");
                remote.bowl.idle = true;
                remote.unloaded.add(ritualPos);
                provider.runAutoReturnTick(level, 60);
                check(adapter.polls == 3 && remote.entities.get(0).isAlive(), "unloaded wireless job is not touched");
                remote.unloaded.clear();
                provider.runAutoReturnTick(level, 80);
                check(adapter.polls == 4 && provider.delivered == 2 && provider.scope.states.isEmpty()
                        && !remote.entities.get(0).isAlive(), "wireless empty-pattern poll finishes remote paid job");
                check(ordinary.polls == 1 && virtual.polls == 0,
                        "wireless empty filter cannot enable unrelated or virtual extraction");
                remote.entities.add(new ItemEntity());
                provider.runAutoReturnTick(level, 100);
                check(provider.delivered == 2 && remote.entities.get(1).isAlive(),
                        "wireless poll without ownership leaves output alone");
                provider.filter.empty = false;
                provider.runAutoReturnTick(level, 120);
                check(ordinary.polls == 2 && virtual.polls == 0, "wireless nonempty-filter behavior preserved");
                provider.connections.clear();
                int pollsBefore = adapter.polls;
                provider.runAutoReturnTick(level, 140);
                check(adapter.polls == pollsBefore, "no wireless connections means no extraction");
            }
        }
        """;
}
