package dev.settledlands.dungeon;

import dev.settledlands.npc.TeleportNode424;
import java.util.Collections;
import java.util.List;
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
 * Build 425 / 427: coordinator for the «Адский данж» ({@code settledlands:hell_dungeon}) dimension.
 *
 * <p>Reads Axiom {@code .bp} schematics from the configured/discovered blueprint folder via
 * {@link DungeonBlueprints427}, plans each {@code 512 x 512} grid instance once via
 * {@link DungeonGenerator427}, places the entrance plus immediate nearby rooms synchronously on
 * entry, and streams remaining rooms across server ticks without lag while prioritizing any
 * rooms near players.</p>
 */
public final class DungeonManager425 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    public static final String DIMENSION_ID = "settledlands:hell_dungeon";
    public static final ResourceKey<Level> HELL_DUNGEON_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("settledlands", "hell_dungeon"));

    private static final Set<?> NO_RELATIVES = Collections.emptySet();
    private static final double PRIORITY_RADIUS_SQ = 64.0D * 64.0D;

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
        return ensureInstanceInitialized(dungeon, data, inst, DungeonBlueprints427.get());
    }

    public static boolean ensureInstanceInitialized(ServerLevel dungeon, DungeonData425 data,
            DungeonData425.Instance inst, DungeonBlueprints427.Library lib) {
        if (dungeon == null || data == null || inst == null) return false;
        if (inst.placed) return false;

        boolean hasCustomOrPool = lib != null && (lib.hasExternalPieces()
                || (lib.startPiece != null && !lib.startPiece.embeddedFallback));

        if (!hasCustomOrPool) {
            DungeonTemplate425.StartTemplate tpl = DungeonTemplate425.loadStart();
            DungeonTemplate425.PlacementStats stats =
                    DungeonTemplate425.place(dungeon, tpl, inst.originX, inst.originY, inst.originZ);
            data.markPlaced(inst.id);
            LOG.info("settledlands: placed fallback hell dungeon start '" + inst.id + "' at ("
                    + inst.originX + ", " + inst.originY + ", " + inst.originZ + "): "
                    + stats.blocksPlaced + " blocks (" + stats.blocksSkipped + " skipped), "
                    + stats.blockEntitiesLoaded + " block entities, "
                    + stats.entitiesSpawned + " entities");
            return true;
        }

        if (!inst.planGenerated) {
            DungeonGenerator427.Plan plan = DungeonGenerator427.generate(
                    lib, inst.originX, inst.originY, inst.originZ, inst.seed);
            String src = lib.sourceDir == null ? "" : lib.sourceDir.toString();
            data.setPlan(inst, plan, lib.config.doorwayBlock, src);
            String biomeInfo = lib.biomeProgression
                    ? " [к:" + plan.cryptCount + ", а:" + plan.infernalCount + ", п:" + plan.fleshCount + "]"
                    : "";
            LOG.info("settledlands: generated plan for '" + inst.id + "' ("
                    + plan.pieces.size() + " rooms" + biomeInfo + ", " + plan.seals.size() + " seals) from " + src);
        }

        int immediateCount = Math.min(lib.config.immediatePieces, inst.pieces.size());
        boolean didWork = false;
        for (int i = 0; i < immediateCount; i++) {
            DungeonData425.PieceProgress pp = inst.pieces.get(i);
            if (pp.done) continue;
            AxiomBlueprint427.PieceTemplate pt = lib.resolvePiece(pp.pieceKey);
            if (pt == null) {
                pp.done = true;
                continue;
            }
            DungeonTemplate425.placeSlice(
                    dungeon, pt.content, pp.ox, pp.oy, pp.oz, pp.turns,
                    inst.doorwayBlock, pp.blockCursor, pt.blockCount() - pp.blockCursor, null);
            pp.blockCursor = pt.blockCount();
            pp.done = true;
            didWork = true;
        }

        // Place start entrance seal immediately so stepping backward never drops into void.
        for (DungeonData425.SealProgress sp : inst.seals) {
            if (sp.done) continue;
            if (isNearDonePiece(inst, sp)) {
                DungeonTemplate425.placeSeal(dungeon, sp.x, sp.y, sp.z, sp.outDir, inst.doorwayBlock);
                sp.done = true;
                didWork = true;
            }
        }

        if (isAllDone(inst)) {
            data.markPlaced(inst.id);
        } else if (didWork) {
            data.setDirty();
        }
        return didWork;
    }

    public static int tickPendingGeneration(MinecraftServer server) {
        if (server == null) return 0;
        return tickPendingGeneration(server, DungeonBlueprints427.get());
    }

    public static int tickPendingGeneration(MinecraftServer server, DungeonBlueprints427.Library lib) {
        if (server == null || lib == null) return 0;
        DungeonData425 data = DungeonData425.get(server);
        boolean anyPending = false;
        for (DungeonData425.Instance inst : data.allInstances()) {
            if (inst.hasPendingWork()) {
                anyPending = true;
                break;
            }
        }
        if (!anyPending) return 0;

        ServerLevel dungeon = dungeonLevel(server);
        if (dungeon == null) return 0;

        List<ServerPlayer> players = dungeon.players();
        int budget = lib.config.blocksPerTick;
        int totalPlacedThisTick = 0;
        boolean modified = false;

        for (DungeonData425.Instance inst : data.allInstances()) {
            if (!inst.hasPendingWork() || budget <= 0) continue;

            while (budget > 0) {
                DungeonData425.PieceProgress next = pickNextPiece(inst, players);
                if (next == null) break;

                AxiomBlueprint427.PieceTemplate pt = lib.resolvePiece(next.pieceKey);
                if (pt == null || pt.content == null) {
                    LOG.warn("settledlands: missing blueprint '" + next.pieceKey
                            + "' while building '" + inst.id + "', skipping piece");
                    next.done = true;
                    modified = true;
                    continue;
                }

                int remaining = Math.max(0, pt.blockCount() - next.blockCursor);
                boolean urgent = isUrgentForAnyPlayer(next, players);
                int sliceSize = urgent ? remaining : Math.min(remaining, budget);
                if (sliceSize <= 0 && pt.blockCount() == 0) {
                    next.done = true;
                    modified = true;
                    continue;
                }

                DungeonTemplate425.PlacementStats stats = new DungeonTemplate425.PlacementStats();
                int newCursor = DungeonTemplate425.placeSlice(
                        dungeon, pt.content, next.ox, next.oy, next.oz, next.turns,
                        inst.doorwayBlock, next.blockCursor, sliceSize, stats);
                next.blockCursor = newCursor;
                if (newCursor >= pt.blockCount()) {
                    next.done = true;
                }
                int used = Math.max(1, sliceSize);
                budget -= used;
                totalPlacedThisTick += stats.blocksPlaced;
                modified = true;
            }

            // Place seals whose pieces have finished building (or once all pieces are done).
            boolean allPiecesDone = true;
            for (DungeonData425.PieceProgress pp : inst.pieces) {
                if (!pp.done) {
                    allPiecesDone = false;
                    break;
                }
            }
            int sealCap = 16;
            for (DungeonData425.SealProgress sp : inst.seals) {
                if (sp.done) continue;
                if (!allPiecesDone && !isNearDonePiece(inst, sp)) continue;
                DungeonTemplate425.placeSeal(dungeon, sp.x, sp.y, sp.z, sp.outDir, inst.doorwayBlock);
                sp.done = true;
                modified = true;
                if (--sealCap <= 0 && !allPiecesDone) break;
            }

            if (isAllDone(inst)) {
                data.markPlaced(inst.id);
                LOG.info("settledlands: completed hell dungeon instance '" + inst.id + "' ("
                        + inst.pieces.size() + " rooms, " + inst.seals.size() + " seals)");
            }
        }

        if (modified) {
            data.setDirty();
        }
        return totalPlacedThisTick;
    }

    private static DungeonData425.PieceProgress pickNextPiece(
            DungeonData425.Instance inst, List<ServerPlayer> players) {
        DungeonData425.PieceProgress closestUrgent = null;
        double closestDist = PRIORITY_RADIUS_SQ;
        DungeonData425.PieceProgress firstPending = null;

        for (DungeonData425.PieceProgress pp : inst.pieces) {
            if (pp.done) continue;
            if (firstPending == null) {
                firstPending = pp;
            }
            if (players != null && !players.isEmpty()) {
                for (ServerPlayer pl : players) {
                    if (pl == null) continue;
                    double d2 = pp.distSqTo(pl.getX(), pl.getY(), pl.getZ());
                    if (d2 < closestDist) {
                        closestDist = d2;
                        closestUrgent = pp;
                    }
                }
            }
        }
        return closestUrgent != null ? closestUrgent : firstPending;
    }

    private static boolean isUrgentForAnyPlayer(DungeonData425.PieceProgress pp, List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) return false;
        for (ServerPlayer pl : players) {
            if (pl != null && pp.distSqTo(pl.getX(), pl.getY(), pl.getZ()) <= (48.0D * 48.0D)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearDonePiece(DungeonData425.Instance inst, DungeonData425.SealProgress sp) {
        for (DungeonData425.PieceProgress pp : inst.pieces) {
            if (!pp.done) continue;
            if (sp.x >= pp.minX - 1 && sp.x <= pp.maxX + 1
                    && sp.y >= pp.minY - 1 && sp.y <= pp.maxY + 1
                    && sp.z >= pp.minZ - 1 && sp.z <= pp.maxZ + 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllDone(DungeonData425.Instance inst) {
        for (DungeonData425.PieceProgress pp : inst.pieces) {
            if (!pp.done) return false;
        }
        for (DungeonData425.SealProgress sp : inst.seals) {
            if (!sp.done) return false;
        }
        return true;
    }

    public static boolean enterFromTeleportNode(ServerPlayer player, TeleportNode424.Parsed parsed) {
        if (player == null || parsed == null) return false;
        boolean blankCoords = parsed.rawX.isEmpty() && parsed.rawY.isEmpty() && parsed.rawZ.isEmpty();
        boolean zeroOrBlank = blankCoords
                || (isZeroCoord(parsed.rawX) && isZeroCoord(parsed.rawY) && isZeroCoord(parsed.rawZ));
        DungeonBlueprints427.Library lib = DungeonBlueprints427.get();
        boolean uniqueMode = lib == null || lib.config == null || lib.config.uniqueDungeonPerEntry;
        if (uniqueMode && zeroOrBlank) {
            return createAndEnterUniqueInstance(player, null) != null;
        }
        return enterDungeon(player, parsed.x, parsed.y, parsed.z, blankCoords,
                DungeonData425.DEFAULT_INSTANCE_ID);
    }

    public static DungeonData425.Instance enterUniqueOrDefault(ServerPlayer player) {
        if (player == null) return null;
        DungeonBlueprints427.Library lib = DungeonBlueprints427.get();
        boolean uniqueMode = lib == null || lib.config == null || lib.config.uniqueDungeonPerEntry;
        if (uniqueMode) {
            return createAndEnterUniqueInstance(player, null);
        }
        if (!enterDungeon(player, DungeonData425.DEFAULT_INSTANCE_ID)) {
            return null;
        }
        MinecraftServer server = player.getServer();
        return server == null ? null : DungeonData425.get(server).getInstance(DungeonData425.DEFAULT_INSTANCE_ID);
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

        DungeonBlueprints427.Library lib = DungeonBlueprints427.get();
        DungeonData425 data = DungeonData425.get(server);
        DungeonData425.Instance inst = data.getOrCreateInstance(
                instanceId, dungeon.getSeed(), ((Level) dungeon).getGameTime());
        ensureInstanceInitialized(dungeon, data, inst, lib);

        saveReturnPointIfOutside(player, data);

        double destX = useDefaultEntrance ? inst.spawnX : targetX;
        double destY = useDefaultEntrance ? inst.spawnY : targetY;
        double destZ = useDefaultEntrance ? inst.spawnZ : targetZ;
        float yaw = useDefaultEntrance ? inst.spawnYaw : player.getYRot();
        float pitch = useDefaultEntrance ? inst.spawnPitch : player.getXRot();

        boolean ok = teleportSafely(player, dungeon, destX, destY, destZ, yaw, pitch);
        if (ok && lib != null && !lib.hasExternalPieces()) {
            player.displayClientMessage(
                    Component.translatable("message.settledlands.dungeon.no_blueprints"), false);
        }
        return ok;
    }

    public static boolean createAndEnterNewInstance(ServerPlayer player, String requestedId) {
        return createAndEnterUniqueInstance(player, requestedId) != null;
    }

    public static DungeonData425.Instance createAndEnterUniqueInstance(
            ServerPlayer player, String requestedId) {
        if (player == null) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;

        ServerLevel dungeon = dungeonLevel(server);
        if (dungeon == null) {
            player.displayClientMessage(Component.translatable("message.settledlands.dungeon.not_loaded"), false);
            return null;
        }

        java.util.UUID pid = player.getUUID();
        long extraSalt = System.nanoTime()
                ^ (pid == null ? 0L : (pid.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ pid.getLeastSignificantBits()));
        String baseId = (requestedId != null && !requestedId.trim().isEmpty())
                ? requestedId
                : (pid == null ? "run" : "p_" + Long.toHexString((pid.getMostSignificantBits() >>> 40) & 0xFFFFFFL));

        DungeonBlueprints427.Library lib = DungeonBlueprints427.get();
        DungeonData425 data = DungeonData425.get(server);
        DungeonData425.Instance inst = data.allocateNewInstance(
                baseId, dungeon.getSeed(), ((Level) dungeon).getGameTime(), extraSalt);
        ensureInstanceInitialized(dungeon, data, inst, lib);
        saveReturnPointIfOutside(player, data);

        boolean ok = teleportSafely(
                player,
                dungeon,
                inst.spawnX,
                inst.spawnY,
                inst.spawnZ,
                inst.spawnYaw,
                inst.spawnPitch);
        if (!ok) return null;
        if (lib != null && !lib.hasExternalPieces()) {
            player.displayClientMessage(
                    Component.translatable("message.settledlands.dungeon.no_blueprints"), false);
        }
        return inst;
    }

    private static boolean isZeroCoord(String raw) {
        if (raw == null || raw.trim().isEmpty()) return true;
        try {
            return Math.abs(Double.parseDouble(raw.trim().replace(',', '.'))) < 1e-9;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
