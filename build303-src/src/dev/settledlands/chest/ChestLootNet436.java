package dev.settledlands.chest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
 * Build 436 + Build 439: syncs the server's {@code config/settledlands/loot_pools/*.txt} file list
 * to the chest graph editor client, and in Build 439 also carries the client-measured 3D scatter
 * poses ({@code settledlands:chest_loot_scatter}) back to the server on first container open.
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class ChestLootNet436 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestLootNet436");

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
