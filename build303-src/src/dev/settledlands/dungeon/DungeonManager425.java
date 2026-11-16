package dev.settledlands.dungeon;

import dev.settledlands.npc.TeleportNode424;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 425: coordinator for the «Адский данж» ({@code settledlands:hell_dungeon}) dimension.
 *
 * <p>Places {@code start.slbp} exactly once per grid instance, routes Teleport nodes and commands
 * into the dungeon entrance (or custom coordinates when explicitly provided), and saves/restores
 * each player's return point cleanly when leaving via command.</p>
 */
public final class DungeonManager425 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    public static final String DIMENSION_ID = "settledlands:hell_dungeon";
    public static final ResourceKey<Level> HELL_DUNGEON_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("settledlands", "hell_dungeon"));

    private static final Set<?> NO_RELATIVES = Collections.emptySet();

    private DungeonManager425() {}

    public static boolean isDungeonDimension(String dimensionId) {
        if (dimensionId == null) return false;
        String clean = dimensionId.trim().toLowerCase(Locale.ROOT);
        return DIMENSION_ID.equals(clean) || "hell_dungeon".equals(clean) || "dungeon".equals(clean);
    }

    public static boolean isInDungeon(ServerLevel level) {
        if (level == null) return false;
        ResourceKey<Level> key = ((Level) level).dimension();
        return key != null && HELL_DUNGEON_LEVEL.equals(key);
    }

    public static ServerLevel dungeonLevel(MinecraftServer server) {
        return server == null ? null : server.getLevel(HELL_DUNGEON_LEVEL);
    }

    public static boolean ensureStartPlaced(ServerLevel dungeon, DungeonData425 data,
            DungeonData425.Instance inst) {
        if (dungeon == null || data == null || inst == null) return false;
        if (inst.placed) return false;

        DungeonTemplate425.StartTemplate tpl = DungeonTemplate425.loadStart();
        DungeonTemplate425.PlacementStats stats =
                DungeonTemplate425.place(dungeon, tpl, inst.originX, inst.originY, inst.originZ);
        data.markPlaced(inst.id);
        LOG.info("settledlands: placed hell dungeon start '" + inst.id + "' at ("
                + inst.originX + ", " + inst.originY + ", " + inst.originZ + "): "
                + stats.blocksPlaced + " blocks (" + stats.blocksSkipped + " skipped), "
                + stats.blockEntitiesLoaded + " block entities, "
                + stats.entitiesSpawned + " entities");
        return true;
    }

    public static boolean enterFromTeleportNode(ServerPlayer player, TeleportNode424.Parsed parsed) {
        if (player == null || parsed == null) return false;
        boolean blankCoords = parsed.rawX.isEmpty() && parsed.rawY.isEmpty() && parsed.rawZ.isEmpty();
        return enterDungeon(player, parsed.x, parsed.y, parsed.z, blankCoords,
                DungeonData425.DEFAULT_INSTANCE_ID);
    }

    public static boolean enterDungeon(ServerPlayer player, String instanceId) {
        return enterDungeon(player, 0.0D, 0.0D, 0.0D, true, instanceId);
    }

    public static boolean enterDungeon(ServerPlayer player, double targetX, double targetY,
            double targetZ, boolean useDefaultEntrance, String instanceId) {
        if (player == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ServerLevel dungeon = dungeonLevel(server);
        if (dungeon == null) {
            player.displayClientMessage(Component.translatable("message.settledlands.dungeon.not_loaded"), false);
            LOG.warn("settledlands: cannot enter hell_dungeon - dimension is not loaded in this save");
            return false;
        }

        DungeonData425 data = DungeonData425.get(server);
        DungeonData425.Instance inst = data.getOrCreateInstance(
                instanceId, dungeon.getSeed(), ((Level) dungeon).getGameTime());
        ensureStartPlaced(dungeon, data, inst);

        saveReturnPointIfOutside(player, data);

        double destX = useDefaultEntrance ? (inst.originX + DungeonTemplate425.SPAWN_OFFSET_X) : targetX;
        double destY = useDefaultEntrance ? (inst.originY + DungeonTemplate425.SPAWN_OFFSET_Y) : targetY;
        double destZ = useDefaultEntrance ? (inst.originZ + DungeonTemplate425.SPAWN_OFFSET_Z) : targetZ;
        float yaw = useDefaultEntrance ? DungeonTemplate425.SPAWN_YAW : player.getYRot();
        float pitch = useDefaultEntrance ? DungeonTemplate425.SPAWN_PITCH : player.getXRot();

        return teleportSafely(player, dungeon, destX, destY, destZ, yaw, pitch);
    }

    public static boolean createAndEnterNewInstance(ServerPlayer player, String requestedId) {
        if (player == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ServerLevel dungeon = dungeonLevel(server);
        if (dungeon == null) {
            player.displayClientMessage(Component.translatable("message.settledlands.dungeon.not_loaded"), false);
            return false;
        }

        DungeonData425 data = DungeonData425.get(server);
        DungeonData425.Instance inst = data.allocateNewInstance(
                requestedId, dungeon.getSeed(), ((Level) dungeon).getGameTime());
        ensureStartPlaced(dungeon, data, inst);
        saveReturnPointIfOutside(player, data);

        return teleportSafely(
                player,
                dungeon,
                inst.originX + DungeonTemplate425.SPAWN_OFFSET_X,
                inst.originY + DungeonTemplate425.SPAWN_OFFSET_Y,
                inst.originZ + DungeonTemplate425.SPAWN_OFFSET_Z,
                DungeonTemplate425.SPAWN_YAW,
                DungeonTemplate425.SPAWN_PITCH);
    }

    public static boolean leaveDungeon(ServerPlayer player) {
        if (player == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        DungeonData425 data = DungeonData425.get(server);
        DungeonData425.ReturnPoint rp = data.getReturnPoint(player.getUUID());
        if (rp != null && !isDungeonDimension(rp.dimension)) {
            ResourceLocation rl = ResourceLocation.tryParse(rp.dimension);
            ServerLevel target = rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
            if (target != null) {
                boolean ok = teleportSafely(player, target, rp.x, rp.y, rp.z, rp.yaw, rp.pitch);
                if (ok) {
                    data.clearReturnPoint(player.getUUID());
                }
                return ok;
            }
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null) return false;
        BlockPos spawn = ((Level) overworld).getSharedSpawnPos();
        if (spawn == null) spawn = new BlockPos(0, 64, 0);
        boolean ok = teleportSafely(
                player, overworld,
                spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        if (ok) {
            data.clearReturnPoint(player.getUUID());
        }
        return ok;
    }

    private static void saveReturnPointIfOutside(ServerPlayer player, DungeonData425 data) {
        ServerLevel cur = player.serverLevel();
        if (cur == null || isInDungeon(cur)) return;
        ResourceKey<Level> key = ((Level) cur).dimension();
        String dim = (key != null && key.location() != null)
                ? key.location().toString()
                : TeleportNode424.DEFAULT_DIMENSION;
        if (isDungeonDimension(dim)) return;
        data.saveReturnPoint(
                player.getUUID(),
                dim,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot());
    }

    private static boolean teleportSafely(ServerPlayer player, ServerLevel target,
            double x, double y, double z, float yaw, float pitch) {
        player.fallDistance = 0.0F;
        player.clearFire();
        player.setDeltaMovement(Vec3.ZERO);
        return player.teleportTo(target, x, y, z, NO_RELATIVES, yaw, pitch);
    }
}
