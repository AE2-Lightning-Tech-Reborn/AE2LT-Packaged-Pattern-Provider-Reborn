package com.moakiee.ae2lt.packaged.patternprovider;

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
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;

import org.junit.jupiter.api.Test;

/**
 * JUnit regression test requiring a full JDK.
 * Compiles unchanged production members extracted with the JDK parser, against
 * small boundary fakes, without Minecraft bootstrap or Mixin.
 * This exercises the actual ticker and connection-cache
 * control flow, not a second implementation. It is not an in-game integration
 * test: network injection and world access are controlled test doubles.
 */
public final class StablePatternProviderLogicRegressionTest {
    @Test
    void productionBehaviorAndOriginalBugMutations() throws Exception {
        Path root = Path.of("");
        String source = Files.readString(root.resolve(
                "src/main/java/com/moakiee/ae2lt/packaged/patternprovider/StablePatternProviderLogic.java"));
        String members = extractMembers(source);
        run(members);
        System.out.println("PASS: production ticker and connection-cache behavior with 10000 rollback cycles");

        // Prove the suite detects both original bugs without editing production.
        String noSave = members.replace("saveChanges();", "");
        expectRegression(noSave, "successful work must save");
        String requiresBe = members.replace("valid.add(connection);",
                "if (targetLevel.getBlockEntity(connection.pos()) == null) continue; valid.add(connection);");
        expectRegression(requiresBe, "loaded block-only candidate must survive");
        System.out.println("PASS: both original-bug mutations rejected");
    }

    @Test
    void hostValidationPreservesOnlyRecognizedBlockOnlyTargets() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/packaged/patternprovider/StablePatternProviderBlockEntity.java"));
        String validator = extractMembers(source, Set.of(), Set.of("validateConnection"), Set.of());
        String spiritFire = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/occultism/OccultismSpiritFireAdapter.java"));
        String recognition = extractMembers(spiritFire, Set.of(), Set.of("recognizesMain"), Set.of());
        run(validator, VALIDATION_HARNESS.replace("/* SPIRIT_FIRE_RECOGNITION */", recognition));

        // Wiring contracts supplement behavior: all server mutations use the same validator.
        String add = extractMembers(source, Set.of(), Set.of("addOrUpdateConnection"), Set.of());
        org.junit.jupiter.api.Assertions.assertTrue(add.indexOf("validateConnection(")
                < add.indexOf("connections.set("));
        org.junit.jupiter.api.Assertions.assertTrue(add.contains("Status.VALID"));
        String prune = extractMembers(source, Set.of(), Set.of("pruneInvalidConnections"), Set.of());
        org.junit.jupiter.api.Assertions.assertTrue(prune.contains("validateConnection("));
        org.junit.jupiter.api.Assertions.assertTrue(prune.contains("Status.REMOVE"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("WirelessConnectionValidator.validate("));
        String cleanup = extractMembers(source, Set.of(), Set.of("tickWirelessConnectionCleanup"), Set.of());
        org.junit.jupiter.api.Assertions.assertTrue(cleanup.contains("pruneInvalidConnections("));
    }

    @Test
    void pruningStaysBudgetedAndClearsOnlyRemovedOwnership() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/packaged/patternprovider/StablePatternProviderBlockEntity.java"));
        String members = extractMembers(source, Set.of("MAX_WIRELESS_CONNECTIONS", "invalidConnectionScanCursor"),
                Set.of("pruneInvalidConnections", "clearInvalidConnections"), Set.of())
                .replace("com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity", "PackagedProvider");
        run(members, PRUNING_HARNESS);
    }

    @Test
    void connectorCompatibilityIsRegisteredAndScopedToPackagedHosts() throws Exception {
        String base = "src/main/java/com/moakiee/ae2lt/packaged/mixin/";
        String item = Files.readString(Path.of(base + "WirelessConnectorItemMixin.java"));
        String host = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/packaged/blockentity/PackagedPatternProviderBlockEntity.java"));
        String config = Files.readString(Path.of("src/main/resources/ae2ltpp.mixins.json"));
        var mixinConfig = com.google.gson.JsonParser.parseString(config).getAsJsonObject();
        var itemMixin = new com.google.gson.JsonPrimitive("WirelessConnectorItemMixin");
        var packetMixin = new com.google.gson.JsonPrimitive("WirelessConnectorPacketMixin");
        var commonMixins = mixinConfig.getAsJsonArray("mixins");
        var clientMixins = mixinConfig.getAsJsonArray("client");
        org.junit.jupiter.api.Assertions.assertTrue(clientMixins.contains(itemMixin),
                "Screen-referencing item mixin must load only on the client");
        org.junit.jupiter.api.Assertions.assertFalse(commonMixins.contains(itemMixin),
                "Item mixin must not be transformed on a dedicated server");
        org.junit.jupiter.api.Assertions.assertFalse(commonMixins.contains(packetMixin),
                "Target admission must use the public host API instead of a packet redirect");
        org.junit.jupiter.api.Assertions.assertFalse(clientMixins.contains(packetMixin));
        if (mixinConfig.has("server")) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    mixinConfig.getAsJsonArray("server").contains(itemMixin));
        }
        org.junit.jupiter.api.Assertions.assertFalse(item.contains("getSelectedProvider("));
        org.junit.jupiter.api.Assertions.assertTrue(item.contains("WirelessConnectorBlockOnlyTarget.shouldSubmit(providerSelected,"));
        org.junit.jupiter.api.Assertions.assertTrue(host.contains("public boolean acceptsWirelessTarget("));
        org.junit.jupiter.api.Assertions.assertTrue(host.contains("installed.isEmpty()"));
        org.junit.jupiter.api.Assertions.assertTrue(host.contains("MultiblockAdapterItem.stackCovers(installed, required)"));
        org.junit.jupiter.api.Assertions.assertTrue(host.contains("public Component getWirelessTargetRejectionMessage()"));
    }

    private static String extractMembers(String source) throws Exception {
        return extractMembers(source,
                Set.of("GRID_TICK_MIN", "GRID_TICK_MAX", "VALIDATE_INTERVAL",
                        "validConnectionsCache", "validConnectionsCacheTick", "connectionsDirty"),
                Set.of("getValidConnections", "invalidateConnections",
                        "hasCombinedTickWork", "hasAnyTickWork", "hasAutoReturnWork"),
                Set.of("Ticker"));
    }

    private static String extractMembers(String source, Set<String> fields,
            Set<String> methods, Set<String> classes) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A full JDK is required");
        }
        var input = new SimpleJavaFileObject(URI.create("string:///StablePatternProviderLogic.java"),
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
            StringBuilder selected = new StringBuilder();
            int count = 0;
            for (var member : type.getMembers()) {
                boolean include = member instanceof VariableTree v && fields.contains(v.getName().toString())
                        || member instanceof MethodTree m && methods.contains(m.getName().toString())
                        || member instanceof ClassTree c && classes.contains(c.getSimpleName().toString());
                if (include) {
                    selected.append(source, (int) positions.getStartPosition(unit, member),
                            (int) positions.getEndPosition(unit, member)).append('\n');
                    count++;
                }
            }
            if (count != fields.size() + methods.size() + classes.size()) {
                throw new AssertionError("Production members changed; update the harness explicitly");
            }
            return selected.toString();
        }
    }

    private static void expectRegression(String members, String expectedMessage) throws Exception {
        try {
            run(members);
        } catch (AssertionError failure) {
            if (expectedMessage.equals(failure.getMessage())) {
                return;
            }
            throw failure;
        }
        throw new AssertionError("Suite failed to detect regression: " + expectedMessage);
    }

    private static void run(String members) throws Exception {
        run(members, HARNESS);
    }

    private static void run(String members, String harness) throws Exception {
        Path temp = Files.createTempDirectory("stable-provider-regression-");
        try {
            Path file = temp.resolve("ProviderHarness.java");
            Files.writeString(file, "import java.util.*;\npublic class ProviderHarness {\n"
                    + members + harness + "\n}");
            int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-proc:none", "-d", temp.toString(), file.toString());
            if (result != 0) {
                throw new AssertionError("Harness compilation failed: " + result);
            }
            try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                try {
                    loader.loadClass("ProviderHarness").getMethod("verify").invoke(null);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    if (e.getCause() instanceof AssertionError failure) {
                        throw failure;
                    }
                    throw new AssertionError("Harness execution failed", e.getCause());
                }
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static final String PRUNING_HARNESS = """
        record BlockPos(int value) {}
        record WirelessConnection(BlockPos pos, WirelessConnectionValidator.Status status) {}
        static class ServerLevel {}
        static class WirelessPatternProviderPolicy { static int maxDistance() { return 32; } }
        static class WirelessConnectionValidator { enum Status { VALID, UNLOADED, REMOVE } }
        Object level = new ServerLevel();
        BlockPos worldPosition = new BlockPos(0);
        final TrackingList connections = new TrackingList();
        final List<BlockPos> checked = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        void recomputeIdlePower() { events.add("power"); }
        void notifyLogicStateChanged() { events.add("notify"); }
        void saveChanges() { events.add("save"); }
        void markForUpdate() { events.add("update"); }
        WirelessConnectionValidator.Status validateConnection(
                ServerLevel serverLevel, BlockPos origin, WirelessConnection connection, int distance) {
            checked.add(connection.pos());
            return connection.status();
        }
        static class TrackingList extends ArrayList<WirelessConnection> {
            int reads, snapshots;
            @Override public WirelessConnection get(int index) { reads++; return super.get(index); }
            @Override public Object[] toArray() { snapshots++; return super.toArray(); }
            @Override public <Value> Value[] toArray(Value[] array) { snapshots++; return super.toArray(array); }
        }
        static class PackagedProvider extends ProviderHarness {
            final Set<BlockPos> cleared = new HashSet<>();
            void clearFlagsForTarget(BlockPos pos) {
                for (var remaining : connections) {
                    check(!remaining.pos().equals(pos), "cleanup must follow connection removal");
                }
                check(cleared.add(pos), "removed ownership must be cleared exactly once");
                events.add("clear");
            }
        }
        static class WirelessConnectionLists {
            record PruneResult(int removed, int nextCursor) {}
            static PruneResult prune(List<WirelessConnection> connections, int cursor, int maxChecks,
                    java.util.function.Predicate<WirelessConnection> remove) {
                int remaining = Math.min(maxChecks, connections.size());
                int index = Math.min(Math.max(cursor, 0), connections.size() - 1);
                int removed = 0;
                while (remaining-- > 0 && !connections.isEmpty()) {
                    if (index >= connections.size()) index = 0;
                    if (remove.test(connections.get(index))) { connections.remove(index); removed++; }
                    else index++;
                }
                return new PruneResult(removed, connections.isEmpty() ? 0 : index % connections.size());
            }
            static int indexOf(List<WirelessConnection> connections, Object dimension, BlockPos pos) {
                throw new AssertionError("pruning must not search the full list for removed targets");
            }
        }
        static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
        static void fill(ProviderHarness host, WirelessConnectionValidator.Status status) {
            for (int index = 0; index < MAX_WIRELESS_CONNECTIONS; index++) {
                host.connections.add(new WirelessConnection(new BlockPos(index), status));
            }
        }
        public static void verify() {
            var host = new PackagedProvider();
            check(host.pruneInvalidConnections(64) == 0 && host.events.isEmpty(), "empty pruning has no effects");
            fill(host, WirelessConnectionValidator.Status.VALID);
            check(host.pruneInvalidConnections(0) == 0 && host.pruneInvalidConnections(-1) == 0,
                    "nonpositive budgets do not prune");
            host.level = new Object();
            check(host.pruneInvalidConnections(64) == 0 && host.checked.isEmpty(), "no pruning on client level");
            host.level = new ServerLevel();
            for (int pass = 0; pass < 160; pass++) {
                int reads = host.connections.reads;
                check(host.pruneInvalidConnections(64) == 0, "valid targets survive periodic pruning");
                check(host.connections.reads - reads == 64, "periodic checks stay within budget");
                check(((ProviderHarness) host).invalidConnectionScanCursor == ((pass + 1) * 64) % MAX_WIRELESS_CONNECTIONS,
                        "scan cursor wraps without skipping targets");
            }
            check(host.connections.snapshots == 0, "no full connection snapshots");
            check(host.events.isEmpty() && host.cleared.isEmpty(), "unchanged pruning has no state notifications");
            check(new HashSet<>(host.checked).size() == MAX_WIRELESS_CONNECTIONS, "all targets scanned fairly");

            host = new PackagedProvider();
            for (int index = 0; index < MAX_WIRELESS_CONNECTIONS; index++) {
                var status = WirelessConnectionValidator.Status.values()[index % 3];
                host.connections.add(new WirelessConnection(new BlockPos(index), status));
            }
            int removed = 0;
            for (int pass = 0; pass < 16; pass++) {
                int checked = host.checked.size();
                host.events.clear();
                int count = host.pruneInvalidConnections(64);
                removed += count;
                check(host.checked.size() - checked == 64, "removals do not consume additional budget");
                if (count > 0) {
                    var expected = new ArrayList<String>(Collections.nCopies(count, "clear"));
                    expected.addAll(List.of("power", "notify", "save", "update"));
                    check(host.events.equals(expected), "cleanup precedes one batched state notification");
                } else check(host.events.isEmpty(), "unchanged batch has no effects");
            }
            check(removed == 341 && host.connections.size() == 683, "all and only invalid targets removed");
            check(host.cleared.size() == removed, "ownership cleanup matches removed connections");
            for (var target : host.connections) {
                check(target.status() != WirelessConnectionValidator.Status.REMOVE
                        && !host.cleared.contains(target.pos()), "valid and unloaded ownership retained");
            }
            check(host.connections.snapshots == 0, "removals require no full connection snapshots");

            host = new PackagedProvider();
            fill(host, WirelessConnectionValidator.Status.REMOVE);
            ((ProviderHarness) host).invalidConnectionScanCursor = MAX_WIRELESS_CONNECTIONS - 7;
            check(host.clearInvalidConnections() == MAX_WIRELESS_CONNECTIONS && host.connections.isEmpty(),
                    "manual cleanup removes all invalid connections from wrapped cursor");
            check(host.checked.size() == MAX_WIRELESS_CONNECTIONS && ((ProviderHarness) host).invalidConnectionScanCursor == 0
                    && host.cleared.size() == MAX_WIRELESS_CONNECTIONS, "full removal resets cursor and clears ownership");
            check(host.connections.snapshots == 0, "manual cleanup requires no full connection snapshot");
            var plain = new ProviderHarness();
            fill(plain, WirelessConnectionValidator.Status.REMOVE);
            check(plain.pruneInvalidConnections(64) == 64
                    && plain.events.equals(List.of("power", "notify", "save", "update")),
                    "non-packaged host still batches notifications");
        }
        """;

    private static final String VALIDATION_HARNESS = """
        record BlockPos(int x) {}
        record WirelessConnectionRef(String dimension, BlockPos pos) {}
        static class WirelessConnectionValidator { enum Status { VALID, UNLOADED, REMOVE } }
        static class WirelessConnectionRange {
            static boolean isInRange(String from, BlockPos origin, String to, BlockPos pos, int max) {
                return from.equals(to) && Math.abs(pos.x() - origin.x()) <= max;
            }
        }
        static class State { boolean air; boolean isAir() { return air; } }
        static class BlockEntity {}
        interface Adapter { boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be); }
        static class SpiritFireAdapter implements Adapter {
            static final String SPIRIT_FIRE_BLOCK = "occultism:spirit_fire";
            boolean isOccultismLoaded() { return true; }
            String blockId(State state) { return state.air ? "minecraft:air" : SPIRIT_FIRE_BLOCK; }
            /* SPIRIT_FIRE_RECOGNITION */
        }
        static class ServerLevel {
            boolean exists = true, loaded = true, recognized = true;
            Object be;
            State state = new State();
            int reads, adapterReads;
            String dimension() { return "home"; }
            ServerLevel getServer() { return this; }
            ServerLevel getLevel(String dimension) { return exists ? this : null; }
            boolean isLoaded(BlockPos pos) { return loaded; }
            State getBlockState(BlockPos pos) { read(); return state; }
            Object getBlockEntity(BlockPos pos) { read(); return be; }
            void read() {
                check(loaded && exists, "no world reads before loaded check");
                reads++;
            }
        }
        static class MultiblockAdapterRegistry {
            static Object find(ServerLevel level, BlockPos pos, Object be) {
                level.read(); level.adapterReads++;
                var adapter = new SpiritFireAdapter();
                return level.recognized && adapter.recognizesMain(level, pos, null) ? adapter : null;
            }
        }
        static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
        public static void verify() {
            var level = new ServerLevel();
            var origin = new BlockPos(0);
            var fire = new WirelessConnectionRef("home", new BlockPos(4));
            for (int i = 0; i < 1000; i++) {
                check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.VALID,
                        "recognized Spirit Fire survives repeated validation without BE");
            }
            level.recognized = false;
            check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.REMOVE,
                    "unrecognized non-BE block rejected");
            level.state.air = true;
            check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.REMOVE,
                    "non-adapter air rejected after replacement");
            level.state.air = false; level.be = new Object();
            int adapters = level.adapterReads;
            check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.VALID
                    && level.adapterReads == adapters, "ordinary BE target retains existing semantics");
            level.loaded = false;
            int reads = level.reads;
            check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.UNLOADED
                    && level.reads == reads, "unloaded connection retained without reads");
            level.exists = false;
            check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.REMOVE
                    && level.reads == reads, "missing dimension removed without reads");
            level.exists = true; level.loaded = true; level.be = null; level.recognized = true;
            check(validateConnection(level, origin, fire, 2) == WirelessConnectionValidator.Status.REMOVE
                    && level.reads == reads, "adapter cannot bypass range");
            check(validateConnection(level, origin, new WirelessConnectionRef("other", fire.pos()), 32)
                    == WirelessConnectionValidator.Status.REMOVE && level.reads == reads,
                    "adapter cannot bypass dimension");
            check(validateConnection(level, origin, fire, 32) == WirelessConnectionValidator.Status.VALID,
                    "reloaded recognized target valid again");
        }
        """;

    private static final String HARNESS = """
        final StablePatternProviderBlockEntity providerHost = new StablePatternProviderBlockEntity();
        final GridNode gridNode = new GridNode();
        final ReturnInventory returnInventory = new ReturnInventory();
        final Accessor parent = new Accessor();
        final List<String> events = new ArrayList<>();
        long savedAmount = -1;
        int saves;
        boolean failAutoReturn;
        Accessor accessor() { return parent; }
        void saveChanges() {
            events.add("save");
            saves++;
            savedAmount = returnInventory.amount;
        }
        void tickAutoReturn() {
            events.add("auto");
            if (failAutoReturn) throw new IllegalStateException("auto failure");
        }
        TickRateModulation tick() { return new Ticker().tickingRequest(null, 1); }
        class Accessor {
            boolean workRemaining;
            long accepted;
            boolean ae2ltpp$hasWorkToDo() { return workRemaining; }
            boolean ae2ltpp$doWork() {
                events.add("work");
                // Boundary fake: like injectIntoNetwork, mutate without a listener.
                long transferred = Math.min(accepted, returnInventory.amount);
                returnInventory.amount -= transferred;
                return transferred > 0;
            }
        }
        static class ReturnInventory {
            long amount;
            boolean isEmpty() { return amount == 0; }
        }
        static class GridNode { boolean active = true; boolean isActive() { return active; } }
        interface IGridNode {}
        interface IGridTickable {
            TickingRequest getTickingRequest(IGridNode node);
            TickRateModulation tickingRequest(IGridNode node, int elapsed);
        }
        enum TickRateModulation { SLEEP, SLOWER, URGENT }
        record TickingRequest(int min, int max, boolean sleeping, boolean alertable) {}
        static class StablePatternProviderBlockEntity {
            enum ReturnMode { OFF, AUTO }
            record WirelessConnection(String dimension, int pos) {}
            final List<WirelessConnection> connections = new ArrayList<>();
            ReturnMode mode = ReturnMode.OFF;
            ReturnMode getReturnMode() { return mode; }
            List<WirelessConnection> getConnections() { return connections; }
            int getBlockPos() { return 0; }
        }
        static class WirelessPatternProviderPolicy { static int maxDistance() { return 32; } }
        static class WirelessConnectionRange {
            static int calls;
            // Controlled range boundary, not a test of AE2LT's distance algorithm.
            static boolean isInRange(String from, int origin, String to, int pos, int max) {
                calls++;
                check(from.equals("home") && origin == 0 && max == 32, "range arguments");
                return pos != 99;
            }
        }
        static class Server {
            final Map<String, ServerLevel> levels = new HashMap<>();
            ServerLevel getLevel(String dimension) { return levels.get(dimension); }
        }
        static class ServerLevel {
            final Server server;
            final Set<Integer> loaded = new HashSet<>(List.of(1, 2, 99));
            int blockEntityReads;
            ServerLevel(Server server) { this.server = server; }
            String dimension() { return "home"; }
            Server getServer() { return server; }
            boolean isLoaded(int pos) { return loaded.contains(pos); }
            Object getBlockEntity(int pos) {
                blockEntityReads++;
                check(isLoaded(pos), "must not read unloaded target");
                return pos == 2 ? new Object() : null;
            }
        }
        static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
        public static void verify() {
            // Final drain must save even though the ticker immediately sleeps.
            var p = new ProviderHarness();
            p.returnInventory.amount = 8; p.parent.accepted = 8;
            check(p.tick() == TickRateModulation.SLEEP, "final drain sleeps");
            check(p.saves == 1 && p.savedAmount == 0, "successful work must save");
            check(p.events.equals(List.of("work", "save", "auto")), "save before auto return");

            p = new ProviderHarness();
            p.returnInventory.amount = 8; p.parent.accepted = 3;
            check(p.tick() == TickRateModulation.SLOWER, "partial drain slows");
            check(p.savedAmount == 5 && p.saves == 1, "partial residual persisted");

            p = new ProviderHarness(); p.returnInventory.amount = 8;
            check(p.tick() == TickRateModulation.SLOWER && p.saves == 0, "blocked return not dirty");
            p = new ProviderHarness();
            check(p.tick() == TickRateModulation.SLEEP && p.saves == 0, "idle not dirty");
            p = new ProviderHarness(); p.gridNode.active = false; p.returnInventory.amount = 8;
            check(p.tick() == TickRateModulation.SLEEP && p.events.isEmpty(), "inactive has no side effects");

            p = new ProviderHarness(); p.providerHost.mode = StablePatternProviderBlockEntity.ReturnMode.AUTO;
            p.returnInventory.amount = 1; p.parent.accepted = 1; p.parent.workRemaining = true;
            check(p.tick() == TickRateModulation.SLOWER && p.saves == 1, "auto cadence wins");
            p.providerHost.mode = StablePatternProviderBlockEntity.ReturnMode.OFF;
            p.returnInventory.amount = 1;
            check(p.tick() == TickRateModulation.URGENT, "successful remaining parent work urgent");
            check(p.tick() == TickRateModulation.SLOWER, "blocked parent work slower");

            p = new ProviderHarness(); p.returnInventory.amount = 1; p.parent.accepted = 1;
            p.failAutoReturn = true;
            try { p.tick(); throw new AssertionError("expected auto failure"); }
            catch (IllegalStateException expected) { check(p.savedAmount == 0, "save survives auto exception"); }

            p = new ProviderHarness();
            check(p.new Ticker().getTickingRequest(null).sleeping(), "idle initial request");
            p.returnInventory.amount = 1;
            var request = p.new Ticker().getTickingRequest(null);
            check(!request.sleeping() && request.min() == 1 && request.max() == 20
                    && request.alertable(), "return inventory wakes initial request");

            var server = new Server(); var level = new ServerLevel(server);
            server.levels.put("home", level);
            var blockOnly = new StablePatternProviderBlockEntity.WirelessConnection("home", 1);
            var machine = new StablePatternProviderBlockEntity.WirelessConnection("home", 2);
            p.providerHost.connections.addAll(List.of(blockOnly, machine,
                    new StablePatternProviderBlockEntity.WirelessConnection("home", 3),
                    new StablePatternProviderBlockEntity.WirelessConnection("missing", 1),
                    new StablePatternProviderBlockEntity.WirelessConnection("home", 99)));
            var valid = p.getValidConnections(level, 100);
            check(valid.contains(blockOnly), "loaded block-only candidate must survive");
            check(valid.equals(List.of(blockOnly, machine)), "range, dimension and loaded guards");
            check(level.blockEntityReads == 0, "candidate filter leaves BE checks to adapters");
            try { valid.clear(); throw new AssertionError("cache must be immutable"); }
            catch (UnsupportedOperationException expected) {}

            int calls = WirelessConnectionRange.calls;
            check(p.getValidConnections(level, 119) == valid && WirelessConnectionRange.calls == calls,
                    "cache remains valid before interval");
            level.loaded.remove(1);
            check(p.getValidConnections(level, 120).equals(List.of(machine)), "interval revalidates unloaded target");
            p.providerHost.connections.clear();
            p.invalidateConnections();
            check(p.getValidConnections(level, 121).isEmpty(), "dirty cache refreshes immediately");

            p.providerHost.connections.addAll(List.of(blockOnly, machine));
            level.loaded.add(1);
            p.invalidateConnections();
            for (int cycle = 0; cycle < 10000; cycle++) {
                long forward = 1000L + cycle * 100;
                level.loaded.add(1);
                check(p.getValidConnections(level, forward).equals(List.of(blockOnly, machine)),
                        "forward clock discovers reloaded candidate");
                level.loaded.remove(1);
                var rolledBack = p.getValidConnections(level, forward - 25);
                check(rolledBack.equals(List.of(machine)), "clock rollback refreshes stale wireless cache");
                int beforeHit = WirelessConnectionRange.calls;
                check(p.getValidConnections(level, forward - 6) == rolledBack
                        && WirelessConnectionRange.calls == beforeHit, "rollback cache keeps normal TTL");
                level.loaded.add(1);
                check(p.getValidConnections(level, forward - 5).equals(List.of(blockOnly, machine)),
                        "rollback cache expires at interval boundary");
            }
            p.invalidateConnections();
            check(p.getValidConnections(level, Long.MIN_VALUE + 100).contains(blockOnly),
                    "negative game clock can populate wireless cache");
            level.loaded.remove(1);
            check(p.getValidConnections(level, Long.MAX_VALUE - 100).equals(List.of(machine)),
                    "overflowed elapsed time must refresh wireless cache");
            level.loaded.add(1);
            check(p.getValidConnections(level, Long.MIN_VALUE + 101).equals(List.of(blockOnly, machine)),
                    "wrapped game clock must refresh wireless cache");
            System.out.println("STRESS PASS wireless cache rollback cycles=10000 plus overflow and wrap");
        }
        """;
}
