package dev.settledlands.trial;

import dev.settledlands.npc.GuideNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 446: server-side execution of the cross-graph «Спавнер» control node
 * ({@code trigger:spawner:...}) from NPCs, common, block, door, chest, or trial spawner graphs.
 */
public final class TrialControlActions446 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/TrialControl446");

    private static final class Origin {
        final ServerLevel level;
        final BlockPos pos;

        Origin(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }

    private static final ThreadLocal<Origin> ORIGIN = new ThreadLocal<>();

    private TrialControlActions446() {}

    public static void withOrigin(ServerLevel level, BlockPos pos, Runnable task) {
        if (task == null) return;
        Origin prev = ORIGIN.get();
        ORIGIN.set(new Origin(level, pos));
        try {
            task.run();
        } finally {
            if (prev == null) ORIGIN.remove();
            else ORIGIN.set(prev);
        }
    }

    public static int withOriginInt(ServerLevel level, BlockPos pos, java.util.function.IntSupplier task) {
        if (task == null) return 0;
        Origin prev = ORIGIN.get();
        ORIGIN.set(new Origin(level, pos));
        try {
            return task.getAsInt();
        } finally {
            if (prev == null) ORIGIN.remove();
            else ORIGIN.set(prev);
        }
    }

    public static boolean execute(ServerPlayer player, GuideNpc npc, String action) {
        TrialControlNode446.Parsed parsed = TrialControlNode446.parse(action);
        if (parsed == null) return false;

        Origin origin = ORIGIN.get();
        ServerLevel baseLevel = null;
        BlockPos basePos = null;
        if (origin != null && origin.level != null) {
            baseLevel = origin.level;
            basePos = origin.pos;
        } else if (npc != null && npc.level() instanceof ServerLevel sl) {
            baseLevel = sl;
            basePos = npc.blockPosition();
        } else if (player != null && player.level() instanceof ServerLevel sl) {
            baseLevel = sl;
            basePos = player.blockPosition();
        }
        if (baseLevel == null) return false;
        if (basePos == null) basePos = new BlockPos(0, 0, 0);

        ServerLevel targetLevel = resolveLevel(baseLevel, parsed.dimension);
        if (targetLevel == null) return false;

        int x = parsed.resolveX(basePos.getX());
        int y = parsed.resolveY(basePos.getY());
        int z = parsed.resolveZ(basePos.getZ());
        BlockPos targetPos = new BlockPos(x, y, z);

        if (!targetLevel.hasChunkAt(targetPos)) {
            LOG.warn("trial control skipped: chunk at {} in {} is not loaded",
                    targetPos, parsed.dimension);
            return false;
        }
        if (!TrialSpawnerBridge446.isSpawnerAt(targetLevel, targetPos)) {
            return false;
        }

        String op = parsed.op;
        return switch (op) {
            case TrialControlNode446.OP_CONFIG -> {
                String json = parsed.config().toJson();
                yield syncConfigAndCard(targetLevel, targetPos, json);
            }
            case TrialControlNode446.OP_START ->
                    TrialSpawnerBridge446.startWave(targetLevel, targetPos, player);
            case TrialControlNode446.OP_CONFIG_START -> {
                String json = parsed.config().toJson();
                syncConfigAndCard(targetLevel, targetPos, json);
                yield TrialSpawnerBridge446.startWave(targetLevel, targetPos, player);
            }
            case TrialControlNode446.OP_STOP ->
                    TrialSpawnerBridge446.stopWave(targetLevel, targetPos, parsed.killMobs);
            case TrialControlNode446.OP_RESET_CD ->
                    TrialSpawnerBridge446.resetCooldown(targetLevel, targetPos);
            case TrialControlNode446.OP_DESTROY ->
                    TrialSpawnerBridge446.destroySpawner(targetLevel, targetPos);
            default -> false;
        };
    }

    public static boolean executeForEntity(Entity entity, GuideNpc npc, String action) {
        if (entity instanceof ServerPlayer sp) {
            return execute(sp, npc, action);
        }
        if (entity != null && entity.level() instanceof ServerLevel sl) {
            return withOriginInt(sl, entity.blockPosition(),
                    () -> execute(null, npc, action) ? 1 : 0) > 0;
        }
        return execute(null, npc, action);
    }

    private static boolean syncConfigAndCard(ServerLevel level, BlockPos pos, String json) {
        boolean ok = TrialSpawnerBridge446.applyConfigJson(level, pos, json);
        if (!ok) return false;
        // Also keep the spawner's own card graph in sync if one exists at that position.
        try {
            TrialGraph446Store store = TrialGraph446Store.get(level.getServer());
            if (store != null) {
                String key = TrialGraph446Store.key(level, pos);
                var graph = store.graph(key);
                if (graph != null) {
                    var card = TrialGraph446.trialNode(graph);
                    if (card != null) {
                        card.action = json;
                        store.put(key, graph);
                        TrialGraph446.writeBlockEntityGraph(level, pos, graph);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static ServerLevel resolveLevel(ServerLevel baseLevel, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) return baseLevel;
        MinecraftServer server = baseLevel.getServer();
        if (server == null) return baseLevel;
        ResourceLocation rl = ResourceLocation.tryParse(dimensionId);
        if (rl == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        return server.getLevel(key);
    }
}
