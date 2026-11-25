package dev.settledlands.chest;

import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphAnchor420;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 436: reads {@code config/settledlands/loot_pools/*.txt} and populates chest/furniture
 * containers during {@code settledlands:hell_dungeon} room generation.
 */
public final class LootPoolManager436 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/LootPool436");
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());
    private static final String GUIDE_FILE = "README_FORMAT.txt.disabled";
    private static final String GUIDE_TEXT =
            "# SettledLands Build 436 - Loot Pools Guide / Справка по пулам предметов\n"
            + "# Put your .txt pool files in this directory: config/settledlands/loot_pools/<name>.txt\n"
            + "# Format per line (columns separated by '|'):\n"
            + "#   item_id | rarity | weight | [count or min-max]\n"
            + "#\n"
            + "# Examples (create your own .txt file with lines like these):\n"
            + "#   minecraft:cooked_beef | 5 | 80 | 2-6\n"
            + "#   minecraft:iron_ingot  | 20 | 50 | 1-4\n"
            + "#   minecraft:diamond     | 80 | 10 | 1\n"
            + "#   minecraft:iron_sword  | 35 | 25\n";

    private LootPoolManager436() {}

    public static Path poolDirectory() {
        try {
            Path cfg = FMLPaths.CONFIGDIR.get();
            if (cfg != null) {
                return cfg.resolve("settledlands").resolve("loot_pools");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", "settledlands", "loot_pools");
    }

    public static Path ensureDirectory() {
        Path dir = poolDirectory();
        try {
            boolean fresh = !Files.isDirectory(dir);
            Files.createDirectories(dir);
            Path guide = dir.resolve(GUIDE_FILE);
            if (fresh && !Files.exists(guide)) {
                Files.writeString(guide, GUIDE_TEXT, StandardCharsets.UTF_8);
            }
        } catch (Throwable ex) {
            warnOnce("mkdir:" + dir, "loot_pool: failed to create " + dir + ": " + ex.getMessage());
        }
        return dir;
    }

    /** Returns sorted {@code *.txt} filenames inside {@code config/settledlands/loot_pools/}. */
    public static List<String> listPools() {
        Path dir = ensureDirectory();
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && p.getFileName() != null) {
                    names.add(p.getFileName().toString());
                }
            }
        } catch (Throwable ex) {
            warnOnce("list:" + dir, "loot_pool: failed to list pools in " + dir + ": " + ex.getMessage());
        }
        Collections.sort(names);
        return names;
    }

    public static List<LootPool436.PoolEntry> loadPool(String rawFileName) {
        String safe = LootPool436.sanitizeFileName(rawFileName);
        if (safe.isEmpty()) return Collections.emptyList();
        Path dir = ensureDirectory();
        Path file = dir.resolve(safe).normalize();
        if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
            warnOnce("missing:" + safe, "loot_pool: pool file not found: " + file);
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return LootPool436.parseLines(lines);
        } catch (Throwable ex) {
            warnOnce("read:" + safe, "loot_pool: failed to read " + file + ": " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Called by {@code DungeonTemplate425.placeBlockEntitiesAndEntities} once a dungeon room's
     * blocks, block entities and entities have been placed. Materialises carried anchor markers and
     * populates every chest/furniture node that contains «Пул предметов».
     */
    public static int onDungeonRoomPlaced(ServerLevel level, List<Entity> spawnedAnchors) {
        if (level == null || spawnedAnchors == null || spawnedAnchors.isEmpty()) return 0;
        if (!DungeonManager425.isInDungeon(level)) return 0;
        MinecraftServer server = level.getServer();
        if (server == null) return 0;
        ChestGraph396Store store = ChestGraph396Store.get(server);
        if (store == null) return 0;

        int filledContainers = 0;
        int placedStacks = 0;
        Set<BlockPos> seen = new HashSet<>();
        for (Entity marker : spawnedAnchors) {
            if (marker == null) continue;
            List<BlockPos> chestPositions = NpcGraphAnchor420.materialiseForDungeon(level, marker);
            for (BlockPos pos : chestPositions) {
                if (pos == null || !seen.add(pos)) continue;
                String key = ChestGraph396Store.key(level, pos);
                NpcDialogueGraph graph = store.graph(key);
                if (graph == null) continue;
                int stacks = populateContainer(level, pos, graph);
                if (stacks > 0) {
                    filledContainers++;
                    placedStacks += stacks;
                }
            }
        }
        if (filledContainers > 0) {
            LOG.info("loot_pool: populated " + filledContainers + " dungeon container(s) with "
                    + placedStacks + " stack(s)");
        }
        return placedStacks;
    }

    public static int populateContainer(ServerLevel level, BlockPos pos, NpcDialogueGraph graph) {
        if (level == null || pos == null || graph == null || graph.nodes == null) return 0;
        if (!DungeonManager425.isInDungeon(level)) return 0;
        if (!ChestGraph396.isChestAt(level, pos)) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return 0;

        Random rnd = new Random(ThreadLocalRandom.current().nextLong()
                ^ ((long) pos.getX() * 3129871L)
                ^ ((long) pos.getZ() * 116129781L)
                ^ (long) pos.getY());
        List<NpcDialogueGraph.Node> poolNodes = collectActiveLootPoolNodes(graph, rnd);
        if (poolNodes.isEmpty()) return 0;

        int slotCap = Math.min(container.getContainerSize(),
                ChestDiegesisCompat436.usableSlotCount(be, container));
        if (slotCap <= 0) return 0;

        List<Integer> freeSlots = new ArrayList<>(slotCap);
        for (int i = 0; i < slotCap; i++) {
            ItemStack cur = container.getItem(i);
            if (cur == null || cur.isEmpty()) {
                freeSlots.add(i);
            }
        }
        if (freeSlots.isEmpty()) return 0;
        Collections.shuffle(freeSlots, rnd);

        int placedStacks = 0;
        for (NpcDialogueGraph.Node node : poolNodes) {
            LootPool436.Parsed spec = LootPool436.parse(node.action);
            if (spec == null || spec.poolFile.isEmpty()) continue;
            List<LootPool436.PoolEntry> entries = loadPool(spec.poolFile);
            if (entries.isEmpty()) continue;
            List<LootPool436.Drop> drops = LootPool436.rollDrops(entries, spec, rnd);
            for (LootPool436.Drop drop : drops) {
                if (freeSlots.isEmpty()) break;
                Item item = resolveItem(drop.itemId);
                if (item == null) {
                    warnOnce("item:" + drop.itemId,
                            "loot_pool: unknown item id '" + drop.itemId + "' in pool " + spec.poolFile);
                    continue;
                }
                int remaining = drop.count;
                while (remaining > 0 && !freeSlots.isEmpty()) {
                    ItemStack probe = new ItemStack(item, 1);
                    int perSlotMax = Math.max(1,
                            Math.min(probe.getMaxStackSize(), container.getMaxStackSize()));
                    int put = Math.min(remaining, perSlotMax);
                    ItemStack stack = new ItemStack(item, put);

                    int chosenIdx = -1;
                    for (int idx = 0; idx < freeSlots.size(); idx++) {
                        int slot = freeSlots.get(idx);
                        ItemStack existing = container.getItem(slot);
                        if ((existing == null || existing.isEmpty()) && container.canPlaceItem(slot, stack)) {
                            chosenIdx = idx;
                            break;
                        }
                    }
                    if (chosenIdx < 0) break;
                    int slot = freeSlots.remove(chosenIdx);
                    container.setItem(slot, stack);
                    placedStacks++;
                    remaining -= put;
                }
            }
        }

        if (placedStacks > 0) {
            try {
                be.setChanged();
            } catch (Throwable ex) {
                warnOnce("changed:" + pos, "loot_pool: setChanged failed at " + pos + ": " + ex.getMessage());
            }
        }
        return placedStacks;
    }

    /**
     * Collects Loot Pool nodes in {@code graph}: nodes sitting behind {@code RANDOM} branches are
     * picked one-per-RANDOM-fanout, while all direct/standalone Loot Pool nodes in the chest graph run.
     */
    private static List<NpcDialogueGraph.Node> collectActiveLootPoolNodes(
            NpcDialogueGraph graph, Random rnd) {
        Set<Integer> randomOwned = new HashSet<>();
        List<NpcDialogueGraph.Node> randomNodes = new ArrayList<>();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && node.type == NpcDialogueGraph.RANDOM && node.randomBranches != null) {
                boolean touchesPool = false;
                for (Integer tid : node.randomBranches) {
                    if (tid == null || tid < 0) continue;
                    NpcDialogueGraph.Node target = graph.get(tid);
                    if (LootPool436.isLootPoolNode(target)) {
                        touchesPool = true;
                        markChain(graph, tid, randomOwned);
                    }
                }
                if (touchesPool) {
                    randomNodes.add(node);
                }
            }
        }

        List<NpcDialogueGraph.Node> out = new ArrayList<>();
        Set<Integer> added = new HashSet<>();
        for (NpcDialogueGraph.Node rnode : randomNodes) {
            List<Integer> candidates = new ArrayList<>();
            for (Integer tid : rnode.randomBranches) {
                if (tid != null && tid >= 0 && LootPool436.isLootPoolNode(graph.get(tid))) {
                    candidates.add(tid);
                }
            }
            if (!candidates.isEmpty()) {
                int pickedId = candidates.get(rnd.nextInt(candidates.size()));
                collectPoolChain(graph, pickedId, out, added);
            }
        }
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (LootPool436.isLootPoolNode(node)
                    && !randomOwned.contains(node.id)
                    && added.add(node.id)) {
                out.add(node);
            }
        }
        return out;
    }

    private static void markChain(NpcDialogueGraph graph, int startId, Set<Integer> seen) {
        int curId = startId;
        int guard = 0;
        while (curId >= 0 && guard++ < 64) {
            NpcDialogueGraph.Node cur = graph.get(curId);
            if (!LootPool436.isLootPoolNode(cur) || !seen.add(curId)) break;
            curId = cur.next;
        }
    }

    private static void collectPoolChain(NpcDialogueGraph graph, int startId,
            List<NpcDialogueGraph.Node> out, Set<Integer> added) {
        int curId = startId;
        int guard = 0;
        while (curId >= 0 && guard++ < 64) {
            NpcDialogueGraph.Node cur = graph.get(curId);
            if (!LootPool436.isLootPoolNode(cur) || !added.add(curId)) break;
            out.add(cur);
            curId = cur.next;
        }
    }

    private static Item resolveItem(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(rawId.trim());
        if (rl == null || BuiltInRegistries.ITEM == null) return null;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) return null;
        ResourceLocation actual = BuiltInRegistries.ITEM.getKey(item);
        return rl.equals(actual) ? item : null;
    }

    private static void warnOnce(String key, String msg) {
        if (WARNED.add(key)) {
            LOG.warn(msg);
        }
    }
}
