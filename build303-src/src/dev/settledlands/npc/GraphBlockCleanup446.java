package dev.settledlands.npc;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.door.DoorStore368;
import dev.settledlands.door.DoorSync375;
import dev.settledlands.door.Doors368;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialGraph446Store;
import dev.settledlands.trial.TrialSpawnerBridge446;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 446: automatic cleanup of block, door, chest, and trial-spawner graphs (and locks)
 * whenever the host block is destroyed or replaced by any means (survival/creative break,
 * explosions, {@code /setblock}, pistons, spawner self-destruct, or mods such as Axiom),
 * while strictly leaving property-only blockstate changes (opening doors/chests, pressing
 * buttons, flipping levers, taking/placing books) untouched.
 */
public final class GraphBlockCleanup446 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/GraphCleanup446");

    private GraphBlockCleanup446() {}

    public static String blockIdOf(BlockState state) {
        if (state == null || state.getBlock() == null) return "";
        try {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return rl == null ? "" : rl.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Fast check used on {@code Level#setBlock} HEAD so ordinary terrain edits skip allocation
     * unless this exact position (or a door half above/below it) actually carries SettledLands data.
     */
    public static boolean isTracked(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        String key = BlockTriggerStore351.key(level, pos);
        if (key.isEmpty()) return false;

        BlockTriggerStore351 blocks = BlockTriggerStore351.get(server);
        if (blocks != null && blocks.graph(key) != null) return true;

        ChestGraph396Store chests = ChestGraph396Store.get(server);
        if (chests != null && chests.graph(key) != null) return true;

        TrialGraph446Store trials = TrialGraph446Store.get(server);
        if (trials != null && trials.contains(key)) return true;

        DoorStore368 locks = DoorStore368.get(server);
        if (locks != null && locks.isLocked(level, pos)) return true;

        DoorGraph382Store doors = DoorGraph382Store.get(server);
        if (doors != null) {
            if (doors.graph(key) != null) return true;
            String belowKey = BlockTriggerStore351.key(level, pos.offset(0, -1, 0));
            if (!belowKey.isEmpty() && doors.graph(belowKey) != null) return true;
        }
        return false;
    }

    /**
     * Invoked right after {@code Level#setBlock(..., flags, recursionLeft)} returns {@code true}.
     * Ignores blockstate property updates where the {@code Block} instance did not change.
     */
    public static void onBlockChanged(ServerLevel level, BlockPos pos,
            BlockState oldState, BlockState afterState) {
        if (level == null || pos == null || oldState == null || afterState == null) return;
        if (afterState.is(oldState.getBlock())) return; // property change only (open/close/press/flip)

        MinecraftServer server = level.getServer();
        if (server == null) return;
        String key = BlockTriggerStore351.key(level, pos);
        if (key.isEmpty()) return;

        // 1. Trial spawner graph (+ fire onDestroy socket if TrialTriggers didn't fire before removal)
        if (TrialSpawnerBridge446.isSpawner(oldState)) {
            if (!TrialGraph446.consumeDestroyFired(level, pos)) {
                TrialGraph446.onSpawnerDestroyed(level, pos, null, null, false);
            }
            TrialGraph446Store trials = TrialGraph446Store.get(server);
            if (trials != null && trials.remove(key)) {
                RadiusPass354.clearCooldowns(key);
            }
        } else {
            TrialGraph446Store trials = TrialGraph446Store.get(server);
            if (trials != null && trials.contains(key) && !TrialSpawnerBridge446.isSpawner(afterState)) {
                trials.remove(key);
                RadiusPass354.clearCooldowns(key);
            }
        }

        // 2. Doors: graph (lower half) + locks (both halves) + client sync
        boolean wasDoor = Doors368.isDoor(oldState);
        if (wasDoor && !Doors368.isDoor(afterState)) {
            removeDoorAt(level, server, pos, oldState);
        } else if (!Doors368.isDoor(afterState)) {
            cleanupStrayDoorPos(level, server, pos);
        }

        // 3. Chests / lecterns / chiseled bookshelves / furniture: graph + lock
        if (!ChestGraph396.isChest(afterState)) {
            ChestGraph396Store chests = ChestGraph396Store.get(server);
            if (chests != null && chests.graph(key) != null) {
                chests.put(key, null);
                ChestLock397.clearCracksForKey(key);
                RadiusPass354.clearCooldowns(key);
            }
        }

        // 4. Generic block graphs
        BlockTriggerStore351 blocks = BlockTriggerStore351.get(server);
        if (blocks != null && blocks.graph(key) != null) {
            if (blocks.remove(key)) {
                RadiusPass354.clearCooldowns(key);
            }
        }
    }

    /**
     * Periodic loaded-chunk reconciliation inside {@link RadiusPass354}: catches direct chunk-section
     * edits or mod tools (e.g. Axiom) that bypass {@code Level#setBlock}, while never touching
     * unloaded chunks.
     */
    public static void reconcileLoaded(ServerLevel level) {
        if (level == null || level.isClientSide) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        String dim = BlockTriggerStore351.dimensionOf(level);

        // A. BlockTriggerStore351
        BlockTriggerStore351 blocks = BlockTriggerStore351.get(server);
        if (blocks != null) {
            List<BlockTriggerStore351.Entry> snapshot = new ArrayList<>(blocks.ofDimension(dim));
            for (BlockTriggerStore351.Entry entry : snapshot) {
                if (entry == null) continue;
                BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
                if (!level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state == null || state.isAir()) {
                    if (blocks.remove(entry.key)) RadiusPass354.clearCooldowns(entry.key);
                    continue;
                }
                String curId = blockIdOf(state);
                if (entry.blockId == null || entry.blockId.isEmpty()) {
                    if (!curId.isEmpty()) blocks.rememberBlockId(entry.key, curId);
                } else if (!curId.isEmpty() && !entry.blockId.equals(curId)) {
                    if (blocks.remove(entry.key)) RadiusPass354.clearCooldowns(entry.key);
                }
            }
        }

        // B. DoorGraph382Store (graphs on lower half)
        DoorGraph382Store doors = DoorGraph382Store.get(server);
        if (doors != null) {
            List<String> keys = new ArrayList<>(doors.ofDimension(dim));
            for (String key : keys) {
                BlockPos pos = DoorStore368.positionOf(key);
                if (pos == null || !level.hasChunkAt(pos)) continue;
                if (!Doors368.isDoor(level.getBlockState(pos))) {
                    doors.put(key, null);
                    RadiusPass354.clearCooldowns(key);
                    unlockDoorPair(level, server, pos);
                }
            }
        }

        // C. DoorStore368 (locks on doors without or with a graph)
        DoorStore368 locks = DoorStore368.get(server);
        if (locks != null) {
            for (String key : locks.snapshot()) {
                if (!dim.equals(DoorStore368.dimensionOf(key))) continue;
                BlockPos pos = DoorStore368.positionOf(key);
                if (pos == null || !level.hasChunkAt(pos)) continue;
                if (!Doors368.isDoor(level.getBlockState(pos))) {
                    if (locks.unlockExact(key)) {
                        DoorSync375.sendUnlockExact(level, key);
                        DoorSync375.sendClearParam(level, key);
                    }
                }
            }
        }

        // D. ChestGraph396Store (chest/lectern/bookshelf graphs + locks)
        ChestGraph396Store chests = ChestGraph396Store.get(server);
        if (chests != null) {
            List<String> keys = new ArrayList<>(chests.ofDimension(dim));
            for (String key : keys) {
                BlockPos pos = ChestGraph396Store.positionOf(key);
                if (pos == null || !level.hasChunkAt(pos)) continue;
                if (!ChestGraph396.isChest(level.getBlockState(pos))) {
                    chests.put(key, null);
                    ChestLock397.clearCracksForKey(key);
                    RadiusPass354.clearCooldowns(key);
                }
            }
        }

        // E. TrialGraph446Store (trialtuner:trial_spawner graphs)
        TrialGraph446Store trials = TrialGraph446Store.get(server);
        if (trials != null) {
            List<String> keys = new ArrayList<>(trials.keysOfDimension(dim));
            for (String key : keys) {
                BlockPos pos = TrialGraph446Store.positionOf(key);
                if (pos == null || !level.hasChunkAt(pos)) continue;
                if (!TrialSpawnerBridge446.isSpawner(level.getBlockState(pos))) {
                    if (!TrialGraph446.consumeDestroyFired(level, pos)) {
                        TrialGraph446.onSpawnerDestroyed(level, pos, null, null, false);
                    }
                    trials.remove(key);
                    RadiusPass354.clearCooldowns(key);
                }
            }
        }
    }

    private static void removeDoorAt(ServerLevel level, MinecraftServer server,
            BlockPos pos, BlockState oldDoorState) {
        BlockPos above = pos.above();
        BlockPos below = pos.offset(0, -1, 0);
        String posKey = BlockTriggerStore351.key(level, pos);
        String aboveKey = BlockTriggerStore351.key(level, above);
        String belowKey = BlockTriggerStore351.key(level, below);

        DoorGraph382Store doors = DoorGraph382Store.get(server);
        DoorStore368 locks = DoorStore368.get(server);

        // Identify whether pos was the upper half of a door whose lower half sits at below
        // (either its graph lives at belowKey, below is no longer a door, or below has no door below it).
        boolean checkBelow = false;
        if (!belowKey.isEmpty()) {
            boolean belowHasGraph = doors != null && doors.graph(belowKey) != null;
            boolean belowIsDoor = Doors368.isDoor(level.getBlockState(below));
            boolean twoBelowIsDoor = Doors368.isDoor(level.getBlockState(pos.offset(0, -2, 0)));
            checkBelow = belowHasGraph || !belowIsDoor || !twoBelowIsDoor;
        }

        // Remove graph & locks at pos itself + above (if above is no longer part of a live door).
        if (doors != null) {
            if (!posKey.isEmpty() && doors.graph(posKey) != null) {
                doors.put(posKey, null);
                RadiusPass354.clearCooldowns(posKey);
                DoorSync375.sendClearParam(level, posKey);
            }
            if (checkBelow && !belowKey.isEmpty() && doors.graph(belowKey) != null) {
                doors.put(belowKey, null);
                RadiusPass354.clearCooldowns(belowKey);
                DoorSync375.sendClearParam(level, belowKey);
            }
            if (!aboveKey.isEmpty() && !Doors368.isDoor(level.getBlockState(above))
                    && doors.graph(aboveKey) != null) {
                doors.put(aboveKey, null);
                RadiusPass354.clearCooldowns(aboveKey);
                DoorSync375.sendClearParam(level, aboveKey);
            }
        }

        if (locks != null) {
            if (!posKey.isEmpty() && locks.unlockExact(posKey)) {
                DoorSync375.sendUnlockExact(level, posKey);
                DoorSync375.sendClearParam(level, posKey);
            }
            if (!aboveKey.isEmpty() && !Doors368.isDoor(level.getBlockState(above))
                    && locks.unlockExact(aboveKey)) {
                DoorSync375.sendUnlockExact(level, aboveKey);
                DoorSync375.sendClearParam(level, aboveKey);
            }
            if (checkBelow && !belowKey.isEmpty() && locks.unlockExact(belowKey)) {
                DoorSync375.sendUnlockExact(level, belowKey);
                DoorSync375.sendClearParam(level, belowKey);
            }
        }
    }

    private static void cleanupStrayDoorPos(ServerLevel level, MinecraftServer server, BlockPos pos) {
        String key = BlockTriggerStore351.key(level, pos);
        DoorGraph382Store doors = DoorGraph382Store.get(server);
        if (doors != null && doors.graph(key) != null) {
            doors.put(key, null);
            RadiusPass354.clearCooldowns(key);
        }
        DoorStore368 locks = DoorStore368.get(server);
        if (locks != null && locks.unlockExact(key)) {
            DoorSync375.sendUnlockExact(level, key);
            DoorSync375.sendClearParam(level, key);
        }
    }

    private static void unlockDoorPair(ServerLevel level, MinecraftServer server, BlockPos lower) {
        unlockPairAndSync(level, server, lower, lower.above());
    }

    private static void unlockPairAndSync(ServerLevel level, MinecraftServer server,
            BlockPos lower, BlockPos upper) {
        DoorStore368 locks = DoorStore368.get(server);
        String lowerKey = BlockTriggerStore351.key(level, lower);
        String upperKey = BlockTriggerStore351.key(level, upper);
        boolean changed = false;
        if (locks != null) {
            if (locks.unlockExact(lowerKey)) changed = true;
            if (locks.unlockExact(upperKey)) changed = true;
        }
        if (changed) {
            DoorSync375.sendUnlockExact(level, lowerKey);
            DoorSync375.sendUnlockExact(level, upperKey);
            DoorSync375.sendClearParam(level, lowerKey);
        }
    }
}
