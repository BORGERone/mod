package dev.settledlands.npc;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.door.DoorStore368;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Build 420: the four world files, as the anchor writes them.
 *
 * <p>{@link NpcGraphAnchorCore420} owns the rule - what an entry is, how a turn moves it, how a
 * fingerprint picks the turn. This class owns the world: it opens the four {@code SavedData} files the
 * mod has kept its graphs in since builds 351, 368, 382 and 396, and hands them to the rule one write at
 * a time. Nothing about the formats changes: an anchor writes exactly what the editor writes, so every
 * reader built before this build sees an ordinary graph.</p>
 *
 * <p>Each store is opened on its own, so one unreadable file can cost only its own kind: a broken door
 * file still lets the blocks and the chests of a pasted build come back.</p>
 */
public final class NpcGraphAnchorFiles420 {

    private NpcGraphAnchorFiles420() {
    }

    /**
     * Every graph of one dimension whose place lies within {@code radius} of the anchor, as entries
     * relative to it. The order is stable - blocks, then doors, then chests - so two seals of the same
     * build produce the same list.
     */
    public static List<NpcGraphAnchorCore420.Entry> collect(ServerLevel level, MinecraftServer server,
            BlockPos anchor, int radius) {
        List<NpcGraphAnchorCore420.Entry> out = new ArrayList<NpcGraphAnchorCore420.Entry>();
        if (level == null || server == null || anchor == null || radius < 0) return out;
        String dimension = BlockTriggerStore351.dimensionOf(level);

        BlockTriggerStore351 blocks = blockStore(server);
        if (blocks != null) {
            for (BlockTriggerStore351.Entry entry : blocks.ofDimension(dimension)) {
                if (entry == null || entry.graph == null) continue;
                if (out.size() >= NpcGraphAnchorCore420.MAX_ENTRIES) break;
                if (!NpcGraphAnchorCore420.inside(entry.x, entry.y, entry.z, anchor, radius)) continue;
                out.add(new NpcGraphAnchorCore420.Entry(NpcGraphAnchorCore420.BLOCK_KIND,
                        entry.x - anchor.getX(), entry.y - anchor.getY(), entry.z - anchor.getZ(),
                        entry.graph.save(), null));
            }
        }

        DoorGraph382Store doors = doorStore(server);
        if (doors != null) {
            DoorStore368 locks = lockStore(server);
            for (String key : doors.ofDimension(dimension)) {
                if (out.size() >= NpcGraphAnchorCore420.MAX_ENTRIES) break;
                NpcDialogueGraph graph = doors.graph(key);
                BlockPos at = DoorStore368.positionOf(key);
                if (graph == null || at == null) continue;
                if (!NpcGraphAnchorCore420.inside(at.getX(), at.getY(), at.getZ(), anchor, radius)) continue;
                out.add(new NpcGraphAnchorCore420.Entry(NpcGraphAnchorCore420.DOOR_KIND,
                        at.getX() - anchor.getX(), at.getY() - anchor.getY(), at.getZ() - anchor.getZ(),
                        graph.save(), doorLocks(level, locks, at)));
            }
        }

        ChestGraph396Store chests = chestStore(server);
        if (chests != null) {
            for (String key : chests.ofDimension(dimension)) {
                if (out.size() >= NpcGraphAnchorCore420.MAX_ENTRIES) break;
                NpcDialogueGraph graph = chests.graph(key);
                BlockPos at = ChestGraph396Store.positionOf(key);
                if (graph == null || at == null) continue;
                if (!NpcGraphAnchorCore420.inside(at.getX(), at.getY(), at.getZ(), anchor, radius)) continue;
                ChestGraph396.ensureBlockEntityGraph(level, at, graph);
                out.add(new NpcGraphAnchorCore420.Entry(NpcGraphAnchorCore420.CHEST_KIND,
                        at.getX() - anchor.getX(), at.getY() - anchor.getY(), at.getZ() - anchor.getZ(),
                        graph.save(), null));
            }
        }
        return out;
    }

    /** Both halves of one door, relative to its lower half - a lock may sit on either of them. */
    private static List<int[]> doorLocks(ServerLevel level, DoorStore368 locks, BlockPos lower) {
        List<int[]> out = new ArrayList<int[]>();
        if (level == null || locks == null || lower == null) return out;
        if (locks.isLocked(level, lower)) out.add(new int[] {0, 0, 0});
        BlockPos upper = lower.offset(0, 1, 0);
        if (locks.isLocked(level, upper)) out.add(new int[] {0, 1, 0});
        return out;
    }

    /**
     * Writes the whole list into the world at the pasted place and answers what happened.
     *
     * <p>The anchor has already decided the turn; here every entry is turned, laid over the anchor's new
     * position and written into its own file. A write that fails (a full file) is counted, never thrown -
     * a pasted build must not take the server down.</p>
     */
    public static NpcGraphAnchorCore420.Result materialize(ServerLevel level, MinecraftServer server,
            List<NpcGraphAnchorCore420.Entry> entries, int turn, BlockPos anchor) {
        if (level == null || server == null || anchor == null) return new NpcGraphAnchorCore420.Result();
        final ServerLevel target = level;
        final BlockTriggerStore351 blocks = blockStore(server);
        final DoorGraph382Store doors = doorStore(server);
        final ChestGraph396Store chests = chestStore(server);
        final DoorStore368 locks = lockStore(server);
        final String dimension = BlockTriggerStore351.dimensionOf(level);

        return NpcGraphAnchorCore420.materialize(entries, turn, anchor,
                new NpcGraphAnchorCore420.StoreSink() {
                    @Override
                    public boolean block(int x, int y, int z, CompoundTag graph) {
                        if (blocks == null || graph == null || graph.isEmpty()) return false;
                        String key = dimension + "@" + x + "," + y + "," + z;
                        return blocks.put(key, NpcDialogueGraph.load(graph));
                    }

                    @Override
                    public boolean door(int x, int y, int z, CompoundTag graph) {
                        if (doors == null || graph == null || graph.isEmpty()) return false;
                        String key = dimension + "@" + x + "," + y + "," + z;
                        return doors.put(key, NpcDialogueGraph.load(graph));
                    }

                    @Override
                    public boolean chest(int x, int y, int z, CompoundTag graph) {
                        if (chests == null || graph == null || graph.isEmpty()) return false;
                        String key = dimension + "@" + x + "," + y + "," + z;
                        NpcDialogueGraph loaded = NpcDialogueGraph.load(graph);
                        boolean stored = chests.put(key, loaded);
                        if (stored) {
                            ChestGraph396.writeBlockEntityGraph(target, new BlockPos(x, y, z), loaded);
                        }
                        return stored;
                    }

                    @Override
                    public boolean lock(int x, int y, int z) {
                        if (locks == null) return false;
                        // Both halves of a door answer the same lock, so both are written: a player who
                        // clicks the top of a pasted door must be stopped exactly like one who clicks the
                        // bottom, and the anchor cannot know which half the author meant.
                        boolean low = locks.lock(target, new BlockPos(x, y, z));
                        locks.lock(target, new BlockPos(x, y + 1, z));
                        return low;
                    }
                });
    }

    /** The block standing at one offset from a point, as {@code namespace:path} - the fingerprint's eyes. */
    public static String blockAt(ServerLevel level, BlockPos anchor, int dx, int dy, int dz) {
        if (level == null || anchor == null) return "";
        BlockPos at = anchor.offset(dx, dy, dz);
        if (!level.hasChunkAt(at)) return "";
        try {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
            if (state == null) return "";
            net.minecraft.world.level.block.Block block = state.getBlock();
            if (block == null) return "";
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            return id == null ? "" : String.valueOf(id);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
            return "";
        }
    }

    // ---- the four files -----------------------------------------------------

    private static BlockTriggerStore351 blockStore(MinecraftServer server) {
        try {
            return BlockTriggerStore351.get(server);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
            return null;
        }
    }

    private static DoorGraph382Store doorStore(MinecraftServer server) {
        try {
            return DoorGraph382Store.get(server);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
            return null;
        }
    }

    private static ChestGraph396Store chestStore(MinecraftServer server) {
        try {
            return ChestGraph396Store.get(server);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
            return null;
        }
    }

    private static DoorStore368 lockStore(MinecraftServer server) {
        try {
            return DoorStore368.get(server);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
            return null;
        }
    }
}
