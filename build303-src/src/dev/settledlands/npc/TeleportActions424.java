package dev.settledlands.npc;

import java.util.Collections;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 424: server-side execution of the «Телепорт» graph node.
 *
 * <p>Only teleports {@link ServerPlayer}; NPCs and other entities are never moved. Clears fall
 * distance, fire and velocity before {@link ServerPlayer#teleportTo} so a teleport mid-fall or across
 * dimensions never kills the player on arrival.</p>
 */
public final class TeleportActions424 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    private TeleportActions424() {}

    public static boolean teleport(ServerPlayer player, String action) {
        if (player == null || action == null) return false;
        TeleportNode424.Parsed parsed = TeleportNode424.parse(action);
        if (parsed == null) return false;

        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ServerLevel target = resolveLevel(server, parsed.dimension);
        if (target == null) {
            LOG.warn("settledlands: teleport node skipped - dimension '{}' is not loaded", parsed.dimension);
            return false;
        }

        player.fallDistance = 0.0F;
        player.clearFire();
        player.setDeltaMovement(Vec3.ZERO);
        return player.teleportTo(
                target,
                parsed.x,
                parsed.y,
                parsed.z,
                Collections.emptySet(),
                player.getYRot(),
                player.getXRot());
    }

    public static boolean executeForEntity(Entity entity, String action) {
        if (entity instanceof ServerPlayer player) {
            return teleport(player, action);
        }
        return false;
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        String id = (dimensionId == null || dimensionId.isEmpty())
                ? TeleportNode424.DEFAULT_DIMENSION : dimensionId;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        return server.getLevel(key);
    }
}
