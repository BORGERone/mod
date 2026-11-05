package dev.settledlands.door;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 376: the client is told which doors are locked.
 *
 * <p>375 stopped the client from opening doors on its own - it refused <b>every</b> door click and let
 * the server answer, which cost one round trip on every ordinary door. The author saw exactly that:
 * "normal doors open laggy, the top half with a delay". Two blocks of one door are written by the
 * server in one packet for the half that was hit and a beat later for the other, and without the local
 * prediction the beat became visible.</p>
 *
 * <p>The refusal only has to cover <b>locked</b> doors, so the client has to know them. This class is
 * that knowledge: one payload, {@code settledlands:door_sync}, carrying plain text.</p>
 *
 * <ul>
 *   <li>{@code C|} - forget every lock (sent once on login, before the snapshot);</li>
 *   <li>{@code S|k1;k2;...} - forget everything and take these keys (the snapshot, chunked);</li>
 *   <li>{@code A|k1;k2;...} - append these keys (the rest of a chunked snapshot);</li>
 *   <li>{@code L|key} / {@code U|key} - this door is locked / unlocked now.</li>
 * </ul>
 *
 * <p>A key is the same string the store uses, {@code minecraft:overworld@12,64,-30}, written for both
 * halves of the door. The payload is a single string field: one codec, one thing to get wrong.</p>
 */
public record DoorSync375(String data) implements CustomPacketPayload {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorSync375");

    public static final CustomPacketPayload.Type<DoorSync375> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "door_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DoorSync375> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DoorSync375::data, DoorSync375::new);

    /** A chunk of the snapshot has to stay well under the 32767 bytes one string may take. */
    private static final int CHUNK = 7000;

    /** What the client knows. Empty and unused on a server. */
    private static final Set<String> LOCKS = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Build 382: the two numbers the door's own graph gives the hack panel - how many keys it asks for
     * and how many seconds one of them stays. The panel is opened locally, so a door whose graph was
     * edited in the editor has to be known here; a door that is not in this map keeps the panel of
     * build 378 (a random four to ten keys, five seconds each).
     */
    private static final Map<String, int[]> SETTINGS =
            Collections.synchronizedMap(new HashMap<String, int[]>());

    /** False until the first snapshot arrives: until then the client refuses every door click. */
    private static volatile boolean synced;

    @Override
    public CustomPacketPayload.Type<DoorSync375> type() {
        return TYPE;
    }

    /** Has this client been told where the locks are? */
    public static boolean synced() {
        return synced;
    }

    /**
     * Is this door - either of its two halves - locked as far as the client knows?
     *
     * <p>Only the client asks this. The server never trusts it: the store is the truth there, and the
     * door-side guard of 373 asks the store, not this mirror.</p>
     */
    public static boolean isLocked(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String here = key(level, pos);
        if (!here.isEmpty() && LOCKS.contains(here)) return true;
        String above = key(level, new BlockPos(pos.getX(), pos.getY() + 1, pos.getZ()));
        if (!above.isEmpty() && LOCKS.contains(above)) return true;
        String below = key(level, new BlockPos(pos.getX(), pos.getY() - 1, pos.getZ()));
        return !below.isEmpty() && LOCKS.contains(below);
    }

    /** {@code minecraft:overworld@12,64,-30} - the same key the store writes. */
    public static String key(Level level, BlockPos pos) {
        if (level == null || pos == null) return "";
        if (level.dimension() == null || level.dimension().location() == null) return "";
        return String.valueOf(level.dimension().location()) + "@"
                + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Client side: take one payload. Never throws - a bad line must not break a door. */
    public static void apply(String text) {
        if (text == null || text.length() < 2) return;
        try {
            char op = text.charAt(0);
            String body = text.substring(2);
            if (op == 'C') {
                LOCKS.clear();
                synced = true;
                return;
            }
            if (op == 'S') {
                LOCKS.clear();
                synced = true;
                addAll(body);
                return;
            }
            if (op == 'A') {
                addAll(body);
                return;
            }
            if (op == 'L') {
                if (!body.isEmpty()) LOCKS.add(body);
                return;
            }
            if (op == 'D') {
                SETTINGS.clear();
                putSettings(body);
                return;
            }
            if (op == 'P') {
                putSettings(body);
                return;
            }
            if (op == 'U') {
                if (!body.isEmpty()) LOCKS.remove(body);
                return;
            }
            LOG.warn("door: unknown door sync op " + op);
        } catch (Throwable failure) {
            LOG.warn("door: a door sync line could not be read: " + failure);
        }
    }

    private static void addAll(String body) {
        if (body == null || body.isEmpty()) return;
        String[] keys = body.split(";");
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i].trim();
            if (!key.isEmpty()) LOCKS.add(key);
        }
    }

    /** One or more {@code key=count:seconds} pairs, separated by {@code ;}. */
    private static void putSettings(String body) {
        if (body == null || body.isEmpty()) return;
        String[] pairs = body.split(";");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i].trim();
            int eq = pair.lastIndexOf('=');
            if (eq <= 0 || eq + 1 >= pair.length()) continue;
            String[] values = pair.substring(eq + 1).split(":");
            if (values.length < 2) continue;
            try {
                int count = Integer.parseInt(values[0].trim());
                int seconds = Integer.parseInt(values[1].trim());
                SETTINGS.put(pair.substring(0, eq), new int[] {count, seconds});
            } catch (NumberFormatException notANumber) {
                // a bad pair costs one door its numbers, never the packet
            }
        }
    }

    /**
     * Build 382, client side: the numbers the door's graph gives the panel, or null when this door was
     * never edited. The door is two blocks, so the position asked about and the one above and below it
     * are all tried - a click may land on either half.
     */
    public static int[] settings(Level level, BlockPos pos) {
        if (level == null || pos == null || SETTINGS.isEmpty()) return null;
        int[] direct = SETTINGS.get(key(level, pos));
        if (direct != null) return direct;
        int[] above = SETTINGS.get(key(level, new BlockPos(pos.getX(), pos.getY() + 1, pos.getZ())));
        if (above != null) return above;
        return SETTINGS.get(key(level, new BlockPos(pos.getX(), pos.getY() - 1, pos.getZ())));
    }

    /**
     * Server side, build 382: tell everyone the numbers of every door that has a graph.
     *
     * <p>Chunked with the same 7000 characters the lock snapshot uses, and sent as {@code D|} for the
     * first chunk and {@code A|}... no - a second {@code D} would clear what the first wrote, so every
     * chunk is a {@code P|} (put), which adds to what is already there.</p>
     */
    public static void sendParams(ServerLevel level) {
        if (level == null) return;
        try {
            DoorGraph382Store store = DoorGraph382Store.get(level);
            List<String> keys = store == null ? new ArrayList<String>() : store.keys();
            List<ServerPlayer> players = null;
            MinecraftServer server = level.getServer();
            if (server != null && server.getPlayerList() != null) {
                players = server.getPlayerList().getPlayers();
            }
            if (players == null || players.isEmpty()) return;
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                int[] settings = DoorGraph382.settings(store.graph(key));
                if (settings == null) continue;
                String pair = key + "=" + settings[0] + ":" + settings[1];
                if (chunk.length() + pair.length() + 1 > CHUNK) {
                    broadcast(players, "P|" + chunk);
                    chunk.setLength(0);
                }
                if (chunk.length() > 0) chunk.append(';');
                chunk.append(pair);
            }
            if (chunk.length() > 0) broadcast(players, "P|" + chunk);
            LOG.info("door: sent the settings of " + keys.size() + " door graph(s)");
        } catch (Throwable failure) {
            LOG.warn("door: the door settings could not be sent: " + failure);
        }
    }

    /** Server side, build 382: the same list for one player that has just joined. */
    public static void sendParams(ServerPlayer player) {
        if (player == null) return;
        try {
            ServerLevel level = player.level() instanceof ServerLevel server ? server : null;
            if (level == null) return;
            DoorGraph382Store store = DoorGraph382Store.get(level);
            if (store == null) return;
            List<String> keys = store.keys();
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                int[] settings = DoorGraph382.settings(store.graph(key));
                if (settings == null) continue;
                String pair = key + "=" + settings[0] + ":" + settings[1];
                if (chunk.length() + pair.length() + 1 > CHUNK) {
                    send(player, "P|" + chunk);
                    chunk.setLength(0);
                }
                if (chunk.length() > 0) chunk.append(';');
                chunk.append(pair);
            }
            if (chunk.length() > 0) send(player, "P|" + chunk);
        } catch (Throwable failure) {
            LOG.warn("door: the door settings could not be sent to " + player.getName().getString()
                    + ": " + failure);
        }
    }

    private static void broadcast(List<ServerPlayer> players, String text) {
        for (int i = 0; i < players.size(); i++) {
            send(players.get(i), text);
        }
    }

    /**
     * Server side: tell one player every lock there is.
     *
     * <p>Sent on login, before the player can reach a door. Chunked, because one string field may not
     * exceed 32767 bytes and {@code MAX_LOCKS} keys are far more than that.</p>
     */
    public static void sendSnapshot(ServerPlayer player) {
        if (player == null) return;
        try {
            MinecraftServer server = player.level() == null ? null : player.level().getServer();
            DoorStore368 store = DoorStore368.get(server);
            List<String> keys = store == null ? new ArrayList<String>() : new ArrayList<String>(store.snapshot());
            Collections.sort(keys);
            StringBuilder chunk = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                if (key == null || key.isEmpty()) continue;
                if (chunk.length() + key.length() + 1 > CHUNK) {
                    send(player, (first ? "S|" : "A|") + chunk);
                    first = false;
                    chunk.setLength(0);
                }
                if (chunk.length() > 0) chunk.append(';');
                chunk.append(key);
            }
            if (chunk.length() > 0) {
                send(player, (first ? "S|" : "A|") + chunk);
            } else if (first) {
                send(player, "C|");
            }
            LOG.info("door: sent " + keys.size() + " lock(s) to " + player.getName().getString());
        } catch (Throwable failure) {
            LOG.warn("door: the lock snapshot could not be sent: " + failure);
        }
    }

    /** Server side: this door was locked or unlocked - tell everyone who can see it. */
    public static void sendDelta(ServerLevel level, BlockPos pos, boolean locked) {
        if (level == null || pos == null) return;
        try {
            List<BlockPos> parts = Doors368.parts(level, pos);
            // Every player the server has, not only this dimension: a client that knows about a lock it
            // cannot see yet is only stricter, and one that does not would predict a locked door open
            // after a teleport. The list the base mod already loops over.
            MinecraftServer server = level.getServer();
            List<ServerPlayer> players = server == null ? level.players()
                    : server.getPlayerList().getPlayers();
            for (int i = 0; i < parts.size(); i++) {
                String key = key(level, parts.get(i));
                if (key.isEmpty()) continue;
                DoorSync375 payload = new DoorSync375((locked ? "L|" : "U|") + key);
                for (int p = 0; p < players.size(); p++) {
                    // sendToPlayer is the call the base mod already makes everywhere - a per-dimension
                    // broadcast would be one new API for the same result.
                    PacketDistributor.sendToPlayer(players.get(p), payload);
                }
            }
        } catch (Throwable failure) {
            LOG.warn("door: the lock change could not be sent: " + failure);
        }
    }

    private static void send(ServerPlayer player, String text) {
        try {
            PacketDistributor.sendToPlayer(player, new DoorSync375(text));
        } catch (Throwable failure) {
            LOG.warn("door: a lock line could not be sent: " + failure);
        }
    }
}
