package dev.settledlands.chest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 436 + Build 439 + Build 440: syncs {@code config/settledlands/loot_pools/*.txt} lists and
 * contents between server and chest graph editor, plus client-measured 3D scatter poses.
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class ChestLootNet436 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestLootNet436");
    public static final int MAX_WIRE_CHARS = 30_000;

    private ChestLootNet436() {}

    public record Pools(String joined) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Pools> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "chest_loot_pools"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Pools> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Pools::joined, Pools::new);

        @Override
        public CustomPacketPayload.Type<Pools> type() {
            return TYPE;
        }
    }

    /**
     * Build 439: {@code x,y,z|seed|base64} carrying exact client-computed {@code ItemPose}
     * coordinates for a scattered {@code StationHost} container.
     */
    public record ScatterPoses(String data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ScatterPoses> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "chest_loot_scatter"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ScatterPoses> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ScatterPoses::data, ScatterPoses::new);

        @Override
        public CustomPacketPayload.Type<ScatterPoses> type() {
            return TYPE;
        }
    }

    /** Build 440 (C2S): requests the contents of one pool file (or a fresh template name). */
    public record PoolQuery(String poolFile) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PoolQuery> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "loot_pool_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoolQuery> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolQuery::poolFile, PoolQuery::new);

        @Override
        public CustomPacketPayload.Type<PoolQuery> type() {
            return TYPE;
        }
    }

    /** Build 440 (S2C): returns pool entries or save/delete status to the in-game pool editor. */
    public record PoolData(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PoolData> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "loot_pool_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoolData> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolData::payload, PoolData::new);

        @Override
        public CustomPacketPayload.Type<PoolData> type() {
            return TYPE;
        }
    }

    /** Build 440 (C2S): saves or deletes a {@code *.txt} pool file on the server. */
    public record PoolSave(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PoolSave> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "loot_pool_save"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoolSave> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolSave::payload, PoolSave::new);

        @Override
        public CustomPacketPayload.Type<PoolSave> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        if (event == null) return;
        PayloadRegistrar registrar;
        try {
            registrar = event.registrar("1");
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to obtain payload registrar: " + failure);
            return;
        }
        try {
            registrar.playToClient(Pools.TYPE, Pools.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPools(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to register settledlands:chest_loot_pools: " + failure);
        }
        try {
            registrar.playToServer(ScatterPoses.TYPE, ScatterPoses.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onScatterPoses(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to register settledlands:chest_loot_scatter: " + failure);
        }
        try {
            registrar.playToServer(PoolQuery.TYPE, PoolQuery.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPoolQuery(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to register settledlands:loot_pool_query: " + failure);
        }
        try {
            registrar.playToClient(PoolData.TYPE, PoolData.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPoolData(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to register settledlands:loot_pool_data: " + failure);
        }
        try {
            registrar.playToServer(PoolSave.TYPE, PoolSave.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPoolSave(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to register settledlands:loot_pool_save: " + failure);
        }
        CustomBookNet443.register(registrar);
    }

    public static void sendPools(ServerPlayer player) {
        if (player == null) return;
        try {
            List<String> pools = LootPoolManager436.listPools();
            String joined = String.join("\n", pools);
            PacketDistributor.sendToPlayer(player, new Pools(joined));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to send pool list to client: " + failure);
        }
    }

    public static void sendPoolsToOperators(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;
        try {
            List<String> pools = LootPoolManager436.listPools();
            String joined = String.join("\n", pools);
            Pools pkt = new Pools(joined);
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players == null) return;
            for (ServerPlayer sp : players) {
                if (sp != null && sp.hasPermissions(2)) {
                    PacketDistributor.sendToPlayer(sp, pkt);
                }
            }
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to broadcast pool list to operators: " + failure);
        }
    }

    public static void sendScatterPoses(BlockPos pos, long seed, String base64Poses) {
        if (pos == null || seed == 0L || base64Poses == null || base64Poses.isEmpty()) return;
        try {
            String wire = pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + "|" + Long.toString(seed) + "|" + base64Poses;
            PacketDistributor.sendToServer(new ScatterPoses(wire));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to send chest scatter poses to server: " + failure);
        }
    }

    public static void sendPoolQuery(String poolFile) {
        try {
            String clean = poolFile == null ? "" : LootPool436.sanitizeFileName(poolFile);
            PacketDistributor.sendToServer(new PoolQuery(clean));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to query pool file: " + failure);
        }
    }

    public static boolean sendPoolSave(String command, String targetFile,
            List<LootPool436.PoolEntry> entries) {
        try {
            String cmd = (command == null || command.isEmpty()) ? "SAVE" : command.trim();
            String target = targetFile == null ? "" : targetFile.trim().replace("\n", "").replace("\r", "");
            String body = entries == null ? "" : LootPool436.encodeEntriesWire(entries);
            String wire = body.isEmpty() ? (cmd + "\n" + target) : (cmd + "\n" + target + "\n" + body);
            if (wire.length() > MAX_WIRE_CHARS) {
                return false;
            }
            PacketDistributor.sendToServer(new PoolSave(wire));
            return true;
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to send pool save packet: " + failure);
            return false;
        }
    }

    private static void onPools(Pools payload, IPayloadContext context) {
        if (payload == null) return;
        String raw = payload.joined();
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                String clean = LootPool436.sanitizeFileName(line);
                if (!clean.isEmpty()) out.add(clean);
            }
        }
        LootPool436.setServerPools(out);
        LootPool436.notifyClientPoolsUpdated();
    }

    private static void onPoolData(PoolData payload, IPayloadContext context) {
        if (payload == null) return;
        LootPool436.notifyClientPoolData(payload.payload());
    }

    private static void onPoolQuery(PoolQuery payload, IPayloadContext context) {
        if (payload == null || context == null) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(player, new PoolData("ERR:noperm\n"));
            return;
        }
        sendPools(player);
        CustomBookNet443.sendBookSummaries(player);
        String requested = LootPool436.sanitizeFileName(payload.poolFile());
        if (!requested.isEmpty() && LootPoolManager436.poolExists(requested)) {
            List<LootPool436.PoolEntry> entries = LootPoolManager436.loadPoolQuiet(requested);
            String body = LootPool436.encodeEntriesWire(entries);
            String wire = body.isEmpty() ? ("OK\n" + requested) : ("OK\n" + requested + "\n" + body);
            if (wire.length() > MAX_WIRE_CHARS) {
                wire = wire.substring(0, MAX_WIRE_CHARS);
                int lastNl = wire.lastIndexOf('\n');
                if (lastNl > 0) wire = wire.substring(0, lastNl);
            }
            PacketDistributor.sendToPlayer(player, new PoolData(wire));
            return;
        }
        String suggested = LootPool436.normalizeSaveName(requested);
        if (suggested.isEmpty()) {
            suggested = LootPool436.suggestNextPoolName(LootPoolManager436.listPools());
        }
        PacketDistributor.sendToPlayer(player, new PoolData("NEW\n" + suggested));
    }

    private static void onPoolSave(PoolSave payload, IPayloadContext context) {
        if (payload == null || context == null) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        String raw = payload.payload();
        if (raw == null || raw.isEmpty() || raw.length() > MAX_WIRE_CHARS) return;
        String[] lines = raw.split("\n", -1);
        String cmd = lines.length >= 1 ? lines[0].trim() : "";
        String target = lines.length >= 2 ? lines[1].trim() : "";
        if (!player.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(player, new PoolData("ERR:noperm\n" + target));
            return;
        }

        if ("DELETE".equals(cmd)) {
            String norm = LootPool436.normalizeSaveName(target);
            if (norm.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PoolData("ERR:bad_name\n" + target));
                return;
            }
            LootPoolManager436.deletePool(norm);
            sendPoolsToOperators(player.getServer());
            PacketDistributor.sendToPlayer(player, new PoolData("DELETED\n" + norm));
            return;
        }

        if ("SAVE".equals(cmd)) {
            String norm = LootPool436.normalizeSaveName(target);
            if (norm.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PoolData("ERR:bad_name\n" + target));
                return;
            }
            List<LootPool436.PoolEntry> entries = LootPool436.decodeEntriesWire(lines, 2);
            String saved = LootPoolManager436.savePool(norm, entries);
            if (saved.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PoolData("ERR:io\n" + norm));
                return;
            }
            sendPoolsToOperators(player.getServer());
            String body = LootPool436.encodeEntriesWire(entries);
            String wire = body.isEmpty() ? ("SAVED\n" + saved) : ("SAVED\n" + saved + "\n" + body);
            PacketDistributor.sendToPlayer(player, new PoolData(wire));
        }
    }

    private static void onScatterPoses(ScatterPoses payload, IPayloadContext context) {
        if (payload == null || context == null) return;
        if (!(context.player() instanceof ServerPlayer sp)) return;
        String wire = payload.data();
        if (wire == null || wire.isEmpty() || wire.length() > 31_000) return;

        int bar1 = wire.indexOf('|');
        int bar2 = bar1 > 0 ? wire.indexOf('|', bar1 + 1) : -1;
        if (bar1 <= 0 || bar2 <= bar1 + 1 || bar2 + 1 >= wire.length()) return;

        BlockPos pos = parsePos(wire.substring(0, bar1));
        if (pos == null) return;
        long seed;
        try {
            seed = Long.parseLong(wire.substring(bar1 + 1, bar2));
        } catch (NumberFormatException ignored) {
            return;
        }
        if (seed == 0L) return;

        String base64 = wire.substring(bar2 + 1);
        ChestDiegesisCompat436.commitScatteredPoses(sp, pos, seed, base64);
    }

    private static BlockPos parsePos(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
