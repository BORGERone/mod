import dev.settledlands.chest.ChestLootNet436;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.LootPoolCard436;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.entity.player.Player;

/**
 * Build 440 JVM verification probe: exercises LootPool436 save-name normalization, file formatting,
 * wire serialization, filter eligibility, LootPoolManager436 save/load/delete CRUD on disk,
 * ChestLootNet436 PoolQuery/PoolSave/PoolData/Pools round-trips & OP permission gating, and
 * LootPoolCard436 pool options/dropdown helpers.
 */
public final class Build440Probe {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        testSaveNameNormalization();
        testFormatAndWireCodec();
        testSpecEligibility();
        testDiskCrudAndPathSafety();
        testNetworkRoundTripAndPermissions();
        testDropdownOptionsAndListeners();
        System.out.println("Build440Probe: " + passed + " checks passed");
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            throw new AssertionError("FAIL: " + label);
        }
        passed++;
    }

    private static void testSaveNameNormalization() {
        check("armory.txt".equals(LootPool436.normalizeSaveName("armory")), "auto-append .txt");
        check("armory.txt".equals(LootPool436.normalizeSaveName("ARMORY.TXT".replace("ARMORY", "armory"))),
                "uppercase .TXT normalized");
        check("my_dungeon_loot.txt".equals(LootPool436.normalizeSaveName("  my dungeon loot  ")),
                "whitespace converted to underscores");
        check("Текстовый_документ.txt".equals(LootPool436.normalizeSaveName("Текстовый документ.txt")),
                "unicode letters preserved in save names");
        check("".equals(LootPool436.normalizeSaveName("../evil.txt")), "rejects parent traversal");
        check("".equals(LootPool436.normalizeSaveName(".hidden")), "rejects leading dot only");
        check("".equals(LootPool436.normalizeSaveName("")), "rejects empty name");
        check("".equals(LootPool436.normalizeSaveName("README_FORMAT.txt")), "rejects readme stem");
        check("".equals(LootPool436.normalizeSaveName("README_FORMAT.txt.disabled")), "rejects disabled readme");
        check("sub.txt".equals(LootPool436.normalizeSaveName("/a/b/sub")), "strips directory components");

        String next = LootPool436.suggestNextPoolName(Arrays.asList("loot_pool_1.txt", "loot_pool_2.txt"));
        check("loot_pool_3.txt".equals(next), "suggests first free pool name");

        check("minecraft:diamond".equals(LootPool436.cleanItemId("diamond")), "adds minecraft namespace");
        check("modid:custom_blade".equals(LootPool436.cleanItemId("modid:custom_blade|")), "strips pipe");
        check("50".equals(LootPool436.formatWeight(50.0D)), "formats whole weight cleanly");
        check("2.5".equals(LootPool436.formatWeight(2.5D)), "formats decimal weight cleanly");
        check("1".equals(LootPool436.formatCountRange(1, 1)), "single count format");
        check("2-6".equals(LootPool436.formatCountRange(2, 6)), "range count format");
        check("4-4".length() > 0 && "4".equals(LootPool436.formatCountRange(4, 2)),
                "clamps inverted range count");
    }

    private static void testFormatAndWireCodec() {
        List<LootPool436.PoolEntry> original = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:cooked_beef", 5, 80.0D, 2, 6),
                new LootPool436.PoolEntry("minecraft:diamond", 80, 10.5D, 1, 1),
                new LootPool436.PoolEntry("refurbished_furniture:oak_chair", 15, 25.0D, 1, 3)
        );
        String text = LootPool436.formatPoolFile("dungeon_test.txt", original);
        check(text.contains("# SettledLands Loot Pool: dungeon_test.txt"), "pool file header");
        List<LootPool436.PoolEntry> reparsed = LootPool436.parseLines(Arrays.asList(text.split("\n")));
        check(reparsed.size() == 3, "reparsed count == 3");
        check("minecraft:cooked_beef".equals(reparsed.get(0).itemId)
                && reparsed.get(0).minCount == 2 && reparsed.get(0).maxCount == 6
                && reparsed.get(0).rarity == 5 && Math.abs(reparsed.get(0).weight - 80.0D) < 1e-6,
                "first entry round-trip");
        check("minecraft:diamond".equals(reparsed.get(1).itemId)
                && reparsed.get(1).minCount == 1 && reparsed.get(1).maxCount == 1
                && reparsed.get(1).rarity == 80 && Math.abs(reparsed.get(1).weight - 10.5D) < 1e-6,
                "second decimal-weight entry round-trip");

        String wire = LootPool436.encodeEntriesWire(original);
        String[] wireLines = ("OK\ndungeon_test.txt\n" + wire).split("\n", -1);
        List<LootPool436.PoolEntry> decoded = LootPool436.decodeEntriesWire(wireLines, 2);
        check(decoded.size() == 3, "wire decoded size == 3");
        check(Math.abs(decoded.get(1).weight - 10.5D) < 1e-6 && decoded.get(2).maxCount == 3,
                "wire decoded fields preserved");

        // Invalid wire lines skipped safely
        String[] dirtyWire = new String[] {
                "badline",
                "minecraft:apple|10|0|1|2",
                "badid|-5|10|1|1",
                "minecraft:bread|5|12.25|3|5"
        };
        List<LootPool436.PoolEntry> clean = LootPool436.decodeEntriesWire(dirtyWire, 0);
        check(clean.size() == 1 && "minecraft:bread".equals(clean.get(0).itemId),
                "filters invalid wire lines");
    }

    private static void testSpecEligibility() {
        LootPool436.Parsed spec = new LootPool436.Parsed("a.txt", "0-50", "40", "3");
        check(LootPool436.passesSpec(new LootPool436.PoolEntry("minecraft:apple", 10, 40.0D, 1, 2), spec),
                "within rarity & weight caps");
        check(!LootPool436.passesSpec(new LootPool436.PoolEntry("minecraft:diamond", 60, 10.0D, 1, 1), spec),
                "exceeds maxRarity");
        check(!LootPool436.passesSpec(new LootPool436.PoolEntry("minecraft:coal", 5, 50.0D, 1, 4), spec),
                "exceeds maxWeight");
    }

    private static void testDiskCrudAndPathSafety() throws Exception {
        Path dir = LootPoolManager436.ensureDirectory();
        check(Files.isDirectory(dir), "loot_pools directory exists");

        List<LootPool436.PoolEntry> items = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:iron_ingot", 20, 45.0D, 1, 4),
                new LootPool436.PoolEntry("minecraft:emerald", 50, 15.0D, 1, 2)
        );
        String saved = LootPoolManager436.savePool("probe_pool", items);
        check("probe_pool.txt".equals(saved), "savePool returned canonical filename");
        check(LootPoolManager436.poolExists("probe_pool.txt"), "poolExists true after save");
        check(LootPoolManager436.listPools().contains("probe_pool.txt"), "listPools contains saved file");

        List<LootPool436.PoolEntry> loaded = LootPoolManager436.loadPoolQuiet("probe_pool.txt");
        check(loaded.size() == 2 && loaded.get(0).maxCount == 4 && loaded.get(1).rarity == 50,
                "loadPoolQuiet reads saved items cleanly");

        // Overwrite with updated weights and counts
        String updated = LootPoolManager436.savePool("probe_pool.txt", Arrays.asList(
                new LootPool436.PoolEntry("minecraft:gold_ingot", 30, 77.5D, 2, 5)
        ));
        check("probe_pool.txt".equals(updated), "overwrite kept filename");
        List<LootPool436.PoolEntry> loaded2 = LootPoolManager436.loadPoolQuiet("probe_pool.txt");
        check(loaded2.size() == 1 && "minecraft:gold_ingot".equals(loaded2.get(0).itemId)
                && Math.abs(loaded2.get(0).weight - 77.5D) < 1e-6,
                "overwrite updated file content");

        // Reject traversal
        check("".equals(LootPoolManager436.savePool("../escape", items)), "traversal save refused");
        check(!LootPoolManager436.deletePool("../escape"), "traversal delete refused");

        // Delete
        check(LootPoolManager436.deletePool("probe_pool.txt"), "deletePool removed file");
        check(!LootPoolManager436.poolExists("probe_pool.txt"), "poolExists false after delete");
        check(!LootPoolManager436.listPools().contains("probe_pool.txt"), "listPools drops deleted file");
    }

    private static void testNetworkRoundTripAndPermissions() throws Exception {
        PacketDistributor.SENT_TO_PLAYER.clear();
        PacketDistributor.SENT_TO_SERVER.clear();

        ServerPlayer op = new ServerPlayer();
        ServerPlayer nonOp = new ServerPlayer() {
            @Override public boolean hasPermissions(int level) { return false; }
        };

        IPayloadContext opCtx = new IPayloadContext() {
            @Override public CompletableFuture<?> enqueueWork(Runnable task) {
                if (task != null) task.run();
                return CompletableFuture.completedFuture(null);
            }
            @Override public Player player() { return op; }
        };
        IPayloadContext nonOpCtx = new IPayloadContext() {
            @Override public CompletableFuture<?> enqueueWork(Runnable task) {
                if (task != null) task.run();
                return CompletableFuture.completedFuture(null);
            }
            @Override public Player player() { return nonOp; }
        };

        Method mQuery = ChestLootNet436.class.getDeclaredMethod(
                "onPoolQuery", ChestLootNet436.PoolQuery.class, IPayloadContext.class);
        mQuery.setAccessible(true);
        Method mSave = ChestLootNet436.class.getDeclaredMethod(
                "onPoolSave", ChestLootNet436.PoolSave.class, IPayloadContext.class);
        mSave.setAccessible(true);
        Method mPools = ChestLootNet436.class.getDeclaredMethod(
                "onPools", ChestLootNet436.Pools.class, IPayloadContext.class);
        mPools.setAccessible(true);
        Method mData = ChestLootNet436.class.getDeclaredMethod(
                "onPoolData", ChestLootNet436.PoolData.class, IPayloadContext.class);
        mData.setAccessible(true);

        // 1. Non-OP refused for query and save
        mQuery.invoke(null, new ChestLootNet436.PoolQuery("net_test.txt"), nonOpCtx);
        ChestLootNet436.PoolData deniedQ = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.PoolData.class);
        check(deniedQ != null && deniedQ.payload().startsWith("ERR:noperm"), "non-OP PoolQuery denied");

        PacketDistributor.SENT_TO_PLAYER.clear();
        mSave.invoke(null, new ChestLootNet436.PoolSave("SAVE\nnet_test.txt\nminecraft:apple|1|10|1|2"), nonOpCtx);
        ChestLootNet436.PoolData deniedS = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.PoolData.class);
        check(deniedS != null && deniedS.payload().startsWith("ERR:noperm"), "non-OP PoolSave denied");
        check(!LootPoolManager436.poolExists("net_test.txt"), "denied save wrote nothing");

        // 2. OP client sendsPoolQuery + PoolSave via PacketDistributor.sendToServer -> server processes
        PacketDistributor.SENT_TO_SERVER.clear();
        ChestLootNet436.sendPoolQuery("");
        check(!PacketDistributor.SENT_TO_SERVER.isEmpty()
                && PacketDistributor.SENT_TO_SERVER.get(0) instanceof ChestLootNet436.PoolQuery,
                "sendPoolQuery queued PoolQuery");

        PacketDistributor.SENT_TO_PLAYER.clear();
        mQuery.invoke(null, new ChestLootNet436.PoolQuery(""), opCtx);
        ChestLootNet436.PoolData newResp = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.PoolData.class);
        check(newResp != null && newResp.payload().startsWith("NEW\n"), "blank query yields NEW template");

        // 3. OP saves new pool
        PacketDistributor.SENT_TO_PLAYER.clear();
        PacketDistributor.SENT_TO_SERVER.clear();
        boolean sent = ChestLootNet436.sendPoolSave("SAVE", "net_test", Arrays.asList(
                new LootPool436.PoolEntry("minecraft:emerald", 35, 22.5D, 1, 3),
                new LootPool436.PoolEntry("minecraft:bread", 5, 70.0D, 2, 4)
        ));
        check(sent, "sendPoolSave returned true");
        ChestLootNet436.PoolSave savePkt = lastPayloadOf(PacketDistributor.SENT_TO_SERVER, ChestLootNet436.PoolSave.class);
        check(savePkt != null, "PoolSave delivered to server buffer");

        mSave.invoke(null, savePkt, opCtx);
        ChestLootNet436.PoolData savedResp = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.PoolData.class);
        check(savedResp != null && savedResp.payload().startsWith("SAVED\nnet_test.txt\n"),
                "server confirmed SAVED net_test.txt");
        check(LootPoolManager436.poolExists("net_test.txt"), "net_test.txt created on disk");

        // 4. OP queries net_test.txt -> receives OK + entries
        PacketDistributor.SENT_TO_PLAYER.clear();
        mQuery.invoke(null, new ChestLootNet436.PoolQuery("net_test.txt"), opCtx);
        ChestLootNet436.Pools poolsResp = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.Pools.class);
        ChestLootNet436.PoolData dataResp = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.PoolData.class);
        check(poolsResp != null && poolsResp.joined().contains("net_test.txt"),
                "Pools packet includes net_test.txt");
        check(dataResp != null && dataResp.payload().startsWith("OK\nnet_test.txt\n")
                && dataResp.payload().contains("minecraft:emerald|35|22.5|1|3"),
                "PoolQuery returns exact saved entries");

        // 5. Client side handles Pools & PoolData listeners cleanly
        AtomicInteger poolsHits = new AtomicInteger();
        AtomicReference<String> dataSeen = new AtomicReference<>();
        LootPool436.setClientListeners(poolsHits::incrementAndGet, dataSeen::set);
        mPools.invoke(null, poolsResp, opCtx);
        mData.invoke(null, dataResp, opCtx);
        check(poolsHits.get() == 1 && LootPool436.getServerPools().contains("net_test.txt"),
                "onPools updates serverPools & triggers client listener");
        check(dataResp.payload().equals(dataSeen.get()),
                "onPoolData triggers client listener");
        LootPool436.setClientListeners(null, null);

        // 6. OP deletes net_test.txt
        PacketDistributor.SENT_TO_PLAYER.clear();
        mSave.invoke(null, new ChestLootNet436.PoolSave("DELETE\nnet_test.txt"), opCtx);
        ChestLootNet436.PoolData delResp = lastPayloadOf(PacketDistributor.SENT_TO_PLAYER, ChestLootNet436.PoolData.class);
        check(delResp != null && delResp.payload().equals("DELETED\nnet_test.txt"),
                "server confirmed DELETED net_test.txt");
        check(!LootPoolManager436.poolExists("net_test.txt"), "net_test.txt deleted from disk");
    }

    private static void testDropdownOptionsAndListeners() {
        LootPool436.setServerPools(Arrays.asList("alpha.txt", "beta.txt"));
        List<String> opts = LootPoolCard436.poolOptions("alpha.txt");
        check(opts.size() >= 3 && "".equals(opts.get(0)) && opts.contains("alpha.txt") && opts.contains("beta.txt"),
                "poolOptions preserves blank index 0 + synced pools");
        check(LootPoolCard436.poolDropdownIndex(opts, "beta.txt") == opts.indexOf("beta.txt"),
                "dropdown index locates beta.txt");

        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node node = g.add(NpcDialogueGraph.TRIGGER, 0, 0);
        LootPool436.applyDefaults(node);
        List<Runnable> committed = new ArrayList<>();
        LootPoolCard436.poolPicker(committed::add, node, opts).accept(opts.indexOf("alpha.txt"));
        check(committed.size() == 1, "poolPicker commits selection");
        committed.get(0).run();
        check("alpha.txt".equals(LootPool436.poolFileOf(node)), "node updated to alpha.txt");
    }

    @SuppressWarnings("unchecked")
    private static <T> T lastPayloadOf(List<CustomPacketPayload> list, Class<T> cls) {
        for (int i = list.size() - 1; i >= 0; i--) {
            CustomPacketPayload p = list.get(i);
            if (cls.isInstance(p)) return (T) p;
        }
        return null;
    }
}
