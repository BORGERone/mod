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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
            "# SettledLands Build 441 - Loot Pools Guide / Справка по пулам предметов\n"
            + "# Put your .txt pool files in this directory: config/settledlands/loot_pools/<name>.txt\n"
            + "# Format per line (columns separated by '|'):\n"
            + "#   item_id | rarity_chance | weight | [count or min-max]\n"
            + "#\n"
            + "# rarity_chance: relative drop chance (like CS:GO cases; % = rarity / sum(rarity))\n"
            + "# weight: crate capacity weight of this drop (e.g. crate weight 40 fits 2x weight 20 or 4x weight 10)\n"
            + "#\n"
            + "# Examples (create your own .txt file with lines like these):\n"
            + "#   minecraft:cooked_beef | 60 | 10 | 2-6\n"
            + "#   minecraft:iron_ingot  | 30 | 15 | 1-4\n"
            + "#   minecraft:diamond     | 10 | 20 | 1\n"
            + "#   minecraft:iron_sword  | 15 | 20 | 1\n";

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

    /** Returns true if {@code rawFileName} resolves to an existing {@code *.txt} pool file. */
    public static boolean poolExists(String rawFileName) {
        String safe = LootPool436.sanitizeFileName(rawFileName);
        if (safe.isEmpty()) return false;
        Path dir = ensureDirectory();
        Path file = dir.resolve(safe).normalize();
        return file.startsWith(dir.normalize()) && Files.isRegularFile(file);
    }

    /** Reads a pool file for the in-game editor without logging a missing-file warning. */
    public static List<LootPool436.PoolEntry> loadPoolQuiet(String rawFileName) {
        String safe = LootPool436.sanitizeFileName(rawFileName);
        if (safe.isEmpty()) return Collections.emptyList();
        Path dir = ensureDirectory();
        Path file = dir.resolve(safe).normalize();
        if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return LootPool436.parseLines(lines);
        } catch (Throwable ex) {
            LOG.warn("loot_pool: failed to read " + file + ": " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Writes {@code entries} to {@code config/settledlands/loot_pools/<safeName>.txt}.
     * Returns the canonical filename on success, or {@code ""} if the name is invalid or I/O failed.
     */
    public static String savePool(String rawFileName, List<LootPool436.PoolEntry> entries) {
        String safe = LootPool436.normalizeSaveName(rawFileName);
        if (safe.isEmpty()) return "";
        Path dir = ensureDirectory();
        Path normDir = dir.normalize();
        Path file = normDir.resolve(safe).normalize();
        if (!file.startsWith(normDir)) return "";
        try {
            String text = LootPool436.formatPoolFile(safe, entries);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            LOG.info("loot_pool: saved pool '" + safe + "' ("
                    + (entries == null ? 0 : entries.size()) + " entries)");
            return safe;
        } catch (Throwable ex) {
            LOG.warn("loot_pool: failed to save " + file + ": " + ex.getMessage());
            return "";
        }
    }

    /** Deletes {@code config/settledlands/loot_pools/<safeName>.txt} when present. */
    public static boolean deletePool(String rawFileName) {
        String safe = LootPool436.normalizeSaveName(rawFileName);
        if (safe.isEmpty()) return false;
        Path dir = ensureDirectory();
        Path normDir = dir.normalize();
        Path file = normDir.resolve(safe).normalize();
        if (!file.startsWith(normDir)) return false;
        try {
            boolean removed = Files.deleteIfExists(file);
            if (removed) {
                LOG.info("loot_pool: deleted pool '" + safe + "'");
            }
            return removed;
        } catch (Throwable ex) {
            LOG.warn("loot_pool: failed to delete " + file + ": " + ex.getMessage());
            return false;
        }
    }

    /**
     * Called by {@code DungeonTemplate425.placeBlockEntitiesAndEntities} once a dungeon room's
     * blocks, block entities and entities have been placed. Materialises carried anchor markers and
     * populates every chest/furniture node that contains «Пул предметов».
     */
    public static int onDungeonRoomPlaced(ServerLevel level, List<Entity> spawnedAnchors) {
        return onDungeonRoomPlaced(level, spawnedAnchors, -1);
    }

    /**
     * Build 437: overload accepting the dungeon room's exact {@code knownTurns} so anchor graph
     * materialisation can fall back cleanly when blocks near the marker differ from the fingerprint.
     */
    public static int onDungeonRoomPlaced(ServerLevel level, List<Entity> spawnedAnchors, int knownTurns) {
        return onDungeonRoomPlaced(level, spawnedAnchors, knownTurns, null);
    }

    /**
     * Build 438: overload recording every container position handled by materialised anchor markers
     * into {@code populatedOut} so callers that also restore embedded {@code BlockEntity} chest graphs
     * never roll loot twice for the same container.
     */
    public static int onDungeonRoomPlaced(ServerLevel level, List<Entity> spawnedAnchors,
            int knownTurns, Set<BlockPos> populatedOut) {
        if (level == null || spawnedAnchors == null || spawnedAnchors.isEmpty()) return 0;
        if (!DungeonManager425.isInDungeon(level)) return 0;
        MinecraftServer server = level.getServer();
        if (server == null) return 0;
        ChestGraph396Store store = ChestGraph396Store.get(server);
        if (store == null) return 0;

        int filledContainers = 0;
        int placedStacks = 0;
        Set<BlockPos> seen = populatedOut != null ? populatedOut : new HashSet<>();
        for (Entity marker : spawnedAnchors) {
            if (marker == null) continue;
            List<BlockPos> chestPositions = NpcGraphAnchor420.materialiseForDungeon(level, marker, knownTurns);
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
        if (be == null) return 0;

        Random rnd = new Random(ThreadLocalRandom.current().nextLong()
                ^ ((long) pos.getX() * 3129871L)
                ^ ((long) pos.getZ() * 116129781L)
                ^ (long) pos.getY());
        List<NpcDialogueGraph.Node> poolNodes = collectActiveLootPoolNodes(graph, rnd);
        if (poolNodes.isEmpty()) return 0;

        if (be instanceof LecternBlockEntity lectern) {
            return populateLectern(level, pos, lectern, poolNodes, rnd);
        }

        if (!(be instanceof Container container)) return 0;
        boolean isShelf = be instanceof ChiseledBookShelfBlockEntity;

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
        List<Integer> populatedSlots = new ArrayList<>();
        for (NpcDialogueGraph.Node node : poolNodes) {
            LootPool436.Parsed spec = LootPool436.parse(node.action);
            if (spec == null || spec.poolFile.isEmpty()) continue;
            List<LootPool436.PoolEntry> entries = loadPool(spec.poolFile);
            if (isShelf) {
                entries = filterBookshelfEntries(entries, true);
            }
            if (entries.isEmpty()) continue;
            List<LootPool436.Drop> drops = LootPool436.rollDrops(entries, spec, rnd);
            for (LootPool436.Drop drop : drops) {
                if (freeSlots.isEmpty()) break;
                ItemStack probe = CustomBookManager443.resolveItemStack(drop.itemId, 1, true);
                if (probe.isEmpty()) {
                    warnOnce("item:" + drop.itemId,
                            "loot_pool: unknown item id '" + drop.itemId + "' in pool " + spec.poolFile);
                    continue;
                }
                int remaining = drop.count;
                while (remaining > 0 && !freeSlots.isEmpty()) {
                    int perSlotMax = Math.max(1,
                            Math.min(probe.getMaxStackSize(), container.getMaxStackSize()));
                    int put = Math.min(remaining, perSlotMax);
                    ItemStack stack = probe.copyWithCount(put);

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
                    populatedSlots.add(slot);
                    placedStacks++;
                    remaining -= put;
                }
            }
        }

        if (placedStacks > 0) {
            if (isShelf) {
                syncChiseledShelfState(level, pos, container);
            } else {
                try {
                    long scatterSeed = (rnd.nextLong()
                            ^ ((long) pos.getX() * 0x9E3779B97F4A7C15L)
                            ^ ((long) pos.getZ() * 0xBF58476D1CE4E5B9L)
                            ^ ((long) pos.getY() * 0x94D049BB133111EBL)) | 1L;
                    ChestDiegesisCompat436.markLootScatter(be, populatedSlots, scatterSeed);
                } catch (Throwable ignored) {
                }
            }
            try {
                be.setChanged();
            } catch (Throwable ex) {
                warnOnce("changed:" + pos, "loot_pool: setChanged failed at " + pos + ": " + ex.getMessage());
            }
        }
        return placedStacks;
    }

    /**
     * Build 443: populates a lectern (non-{@link Container}) with a single compatible book
     * (filtering mixed pools down to {@code ItemTags.LECTERN_BOOKS} + custom books) and updates
     * {@link LecternBlock#HAS_BOOK}.
     */
    private static int populateLectern(ServerLevel level, BlockPos pos, LecternBlockEntity lectern,
            List<NpcDialogueGraph.Node> poolNodes, Random rnd) {
        if (lectern.hasBook()) return 0;
        for (NpcDialogueGraph.Node node : poolNodes) {
            LootPool436.Parsed spec = LootPool436.parse(node.action);
            if (spec == null || spec.poolFile.isEmpty()) continue;
            List<LootPool436.PoolEntry> filtered = filterLecternEntries(loadPool(spec.poolFile), true);
            if (filtered.isEmpty()) continue;
            List<LootPool436.Drop> drops = LootPool436.rollDrops(filtered, spec, rnd);
            for (LootPool436.Drop drop : drops) {
                ItemStack stack = CustomBookManager443.resolveItemStack(drop.itemId, 1, true);
                if (stack.isEmpty()) continue;
                lectern.setBook(stack.copyWithCount(1));
                BlockState state = level.getBlockState(pos);
                try {
                    LecternBlock.resetBookState(null, (Level) level, pos, state, true);
                } catch (Throwable ex) {
                    warnOnce("lectern_state:" + pos,
                            "loot_pool: LecternBlock.resetBookState failed at " + pos + ": " + ex.getMessage());
                }
                try {
                    ((BlockEntity) lectern).setChanged();
                } catch (Throwable ignored) {
                }
                return 1;
            }
        }
        return 0;
    }

    public static List<LootPool436.PoolEntry> filterLecternEntries(
            List<LootPool436.PoolEntry> entries, boolean preferServerDisk) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<LootPool436.PoolEntry> out = new ArrayList<>();
        for (LootPool436.PoolEntry e : entries) {
            if (e != null && CustomBookManager443.isValidForLectern(e.itemId, preferServerDisk)) {
                out.add(e);
            }
        }
        return out;
    }

    public static List<LootPool436.PoolEntry> filterBookshelfEntries(
            List<LootPool436.PoolEntry> entries, boolean preferServerDisk) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<LootPool436.PoolEntry> out = new ArrayList<>();
        for (LootPool436.PoolEntry e : entries) {
            if (e != null && CustomBookManager443.isValidForChiseledBookshelf(e.itemId, preferServerDisk)) {
                out.add(e);
            }
        }
        return out;
    }

    private static void syncChiseledShelfState(ServerLevel level, BlockPos pos, Container container) {
        try {
            BlockState oldState = level.getBlockState(pos);
            if (oldState == null || !(oldState.getBlock() instanceof ChiseledBookShelfBlock)) return;
            BlockState cur = oldState;
            List<BooleanProperty> props = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES;
            int n = Math.min(props.size(), container.getContainerSize());
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                BooleanProperty prop = props.get(i);
                boolean occupied = !container.getItem(i).isEmpty();
                if (!Boolean.valueOf(occupied).equals(cur.getValue(prop))) {
                    cur = (BlockState) cur.setValue(prop, Boolean.valueOf(occupied));
                    changed = true;
                }
            }
            if (changed) {
                ((Level) level).setBlock(pos, cur, 3);
            }
            level.sendBlockUpdated(pos, oldState, cur, 3);
        } catch (Throwable ex) {
            warnOnce("shelf_sync:" + pos,
                    "loot_pool: chiseled bookshelf state sync failed at " + pos + ": " + ex.getMessage());
        }
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

    private static void warnOnce(String key, String msg) {
        if (WARNED.add(key)) {
            LOG.warn(msg);
        }
    }
}
