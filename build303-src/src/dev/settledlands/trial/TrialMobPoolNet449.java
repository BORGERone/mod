package dev.settledlands.trial;

import dev.settledlands.chest.ChestLootNet436;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 449: C2S/S2C sync for {@code config/settledlands/mob_pools/*.txt}, registered
 * alongside {@link ChestLootNet436#onRegister}.
 */
public final class TrialMobPoolNet449 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/TrialMobPoolNet449");
    public static final int MAX_WIRE_CHARS = 30_000;

    private TrialMobPoolNet449() {}

    /** S2C: newline-joined sorted list of {@code *.txt} files in {@code mob_pools/}. */
    public record Pools(String joined) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Pools> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "trial_mob_pools"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Pools> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Pools::joined, Pools::new);

        @Override
        public CustomPacketPayload.Type<Pools> type() {
            return TYPE;
        }
    }

    /** C2S: request the contents of one mob pool file (or a fresh suggested name). */
    public record PoolQuery(String poolFile) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PoolQuery> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "trial_mob_pool_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoolQuery> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolQuery::poolFile, PoolQuery::new);

        @Override
        public CustomPacketPayload.Type<PoolQuery> type() {
            return TYPE;
        }
    }

    /** S2C: status + lines of one mob pool file or save/delete result. */
    public record PoolData(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PoolData> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "trial_mob_pool_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoolData> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolData::payload, PoolData::new);

        @Override
        public CustomPacketPayload.Type<PoolData> type() {
            return TYPE;
        }
    }

    /** C2S: save or delete a mob pool file on the server. */
    public record PoolSave(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PoolSave> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "trial_mob_pool_save"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoolSave> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolSave::payload, PoolSave::new);

        @Override
        public CustomPacketPayload.Type<PoolSave> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        if (registrar == null) return;
        try {
            registrar.playToClient(Pools.TYPE, Pools.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPools(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to register settledlands:trial_mob_pools: " + failure);
        }
        try {
            registrar.playToServer(PoolQuery.TYPE, PoolQuery.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPoolQuery(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to register settledlands:trial_mob_pool_query: " + failure);
        }
        try {
            registrar.playToClient(PoolData.TYPE, PoolData.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPoolData(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to register settledlands:trial_mob_pool_data: " + failure);
        }
        try {
            registrar.playToServer(PoolSave.TYPE, PoolSave.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPoolSave(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to register settledlands:trial_mob_pool_save: " + failure);
        }
    }

    public static void sendPools(ServerPlayer player) {
        if (player == null) return;
        try {
            List<String> pools = TrialMobPoolManager449.listPools();
            PacketDistributor.sendToPlayer(player, new Pools(String.join("\n", pools)));
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to send pool list to client: " + failure);
        }
    }

    public static void sendPoolsToOperators(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;
        try {
            List<String> pools = TrialMobPoolManager449.listPools();
            Pools pkt = new Pools(String.join("\n", pools));
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players == null) return;
            for (ServerPlayer sp : players) {
                if (sp != null && sp.hasPermissions(2)) {
                    PacketDistributor.sendToPlayer(sp, pkt);
                }
            }
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to broadcast mob pool list to operators: " + failure);
        }
    }

    public static void sendPoolQuery(String poolFile) {
        try {
            String clean = poolFile == null ? "" : TrialMobPool449.sanitizeFileName(poolFile);
            PacketDistributor.sendToServer(new PoolQuery(clean));
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to query mob pool file: " + failure);
        }
    }

    public static boolean sendPoolSave(String command, String targetFile,
            List<TrialConfig446.SpawnEntry> entries) {
        try {
            String cmd = (command == null || command.isEmpty()) ? "SAVE" : command.trim();
            String target = targetFile == null ? "" : targetFile.trim().replace("\n", "").replace("\r", "");
            String body = entries == null ? "" : TrialMobPool449.encodeEntriesWire(entries);
            String wire = body.isEmpty() ? (cmd + "\n" + target) : (cmd + "\n" + target + "\n" + body);
            if (wire.length() > MAX_WIRE_CHARS) return false;
            PacketDistributor.sendToServer(new PoolSave(wire));
            return true;
        } catch (Throwable failure) {
            LOG.warn("mob_pool: failed to send mob pool save packet: " + failure);
            return false;
        }
    }

    private static void onPools(Pools payload, IPayloadContext context) {
        if (payload == null) return;
        String raw = payload.joined();
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                String clean = TrialMobPool449.sanitizeFileName(line);
                if (!clean.isEmpty()) out.add(clean);
            }
        }
        TrialMobPool449.setServerPools(out);
        TrialMobPool449.notifyClientPoolsUpdated();
    }

    private static void onPoolData(PoolData payload, IPayloadContext context) {
        if (payload == null) return;
        TrialMobPool449.notifyClientPoolData(payload.payload());
    }

    private static void onPoolQuery(PoolQuery payload, IPayloadContext context) {
        if (payload == null || context == null) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(player, new PoolData("ERR:noperm\n"));
            return;
        }
        sendPools(player);
        String requested = TrialMobPool449.sanitizeFileName(payload.poolFile());
        if (!requested.isEmpty() && TrialMobPoolManager449.poolExists(requested)) {
            List<TrialConfig446.SpawnEntry> entries = TrialMobPoolManager449.loadPoolQuiet(requested);
            String body = TrialMobPool449.encodeEntriesWire(entries);
            String wire = body.isEmpty() ? ("OK\n" + requested) : ("OK\n" + requested + "\n" + body);
            if (wire.length() > MAX_WIRE_CHARS) {
                wire = wire.substring(0, MAX_WIRE_CHARS);
                int lastNl = wire.lastIndexOf('\n');
                if (lastNl > 0) wire = wire.substring(0, lastNl);
            }
            PacketDistributor.sendToPlayer(player, new PoolData(wire));
            return;
        }
        String suggested = TrialMobPool449.normalizeSaveName(requested);
        if (suggested.isEmpty()) {
            suggested = TrialMobPool449.suggestNextPoolName(TrialMobPoolManager449.listPools());
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
            String norm = TrialMobPool449.normalizeSaveName(target);
            if (norm.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PoolData("ERR:bad_name\n" + target));
                return;
            }
            TrialMobPoolManager449.deletePool(norm);
            sendPoolsToOperators(player.getServer());
            PacketDistributor.sendToPlayer(player, new PoolData("DELETED\n" + norm));
            return;
        }

        if ("SAVE".equals(cmd)) {
            String norm = TrialMobPool449.normalizeSaveName(target);
            if (norm.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PoolData("ERR:bad_name\n" + target));
                return;
            }
            List<TrialConfig446.SpawnEntry> entries = TrialMobPool449.decodeEntriesWire(lines, 2);
            String saved = TrialMobPoolManager449.savePool(norm, entries);
            if (saved.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PoolData("ERR:io\n" + norm));
                return;
            }
            sendPoolsToOperators(player.getServer());
            String body = TrialMobPool449.encodeEntriesWire(entries);
            String wire = body.isEmpty() ? ("SAVED\n" + saved) : ("SAVED\n" + saved + "\n" + body);
            PacketDistributor.sendToPlayer(player, new PoolData(wire));
        }
    }
}
