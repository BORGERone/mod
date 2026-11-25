package dev.settledlands.chest;

import java.util.ArrayList;
import java.util.List;
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
 * Build 436: syncs the server's {@code config/settledlands/loot_pools/*.txt} file list to the
 * chest graph editor client when the editor opens, without disturbing {@link ChestNet397} and
 * keeping zero client-package references.
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

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        if (event == null) return;
        try {
            PayloadRegistrar registrar = event.registrar("1");
            registrar.playToClient(Pools.TYPE, Pools.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onPools(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("loot_pool: failed to register settledlands:chest_loot_pools: " + failure);
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
}
