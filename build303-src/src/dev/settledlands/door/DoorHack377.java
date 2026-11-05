package dev.settledlands.door;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 377: a locked door can be <b>cracked</b> - the player holds every button of the panel at once
 * and the door opens.
 *
 * <p>The lock itself does not change: a locked door is still closed to a player who only clicks it, it
 * still cannot be broken, and NPCs still walk through it. What changes is that "nothing happens" is no
 * longer the whole answer - the click now opens {@link HackPanel377}, and the panel asks the server to
 * open <b>this</b> door once the player has actually solved it.</p>
 *
 * <p>One payload, {@code settledlands:door_hack}, one string field: the door key the store already uses,
 * {@code minecraft:overworld@12,64,-30}. The client never sends a position, it sends the key it was
 * looking at, and the server rebuilds the position from it and checks the two things that matter:</p>
 *
 * <ul>
 *   <li>this exact door is locked <b>on the server</b> - the client's mirror is never trusted;</li>
 *   <li>the player is still standing next to it, so a panel solved at one door cannot open another.</li>
 * </ul>
 *
 * <p>What the server cannot check is whether a human actually held the keys - that is a client-side
 * skill check, the same way vanilla lets the client decide it hit a block. It costs nothing here: the
 * worst case is a player who opens a door they had already locked themselves.</p>
 */
public record DoorHack377(String key) implements CustomPacketPayload {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorHack377");

    public static final CustomPacketPayload.Type<DoorHack377> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "door_hack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DoorHack377> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DoorHack377::key, DoorHack377::new);

    /** How far the player may be from the door when the panel is solved, in blocks. */
    private static final double REACH = 6.0D;

    /**
     * Build 377: doors this mod opened by cracking, and when. The guard of build 371 closes every
     * locked door that is open, so without this a cracked door would slam shut in the player's face on
     * the next tick. Long enough to walk through, short enough that the lock stays a lock.
     */
    private static final java.util.Map<String, Long> CRACKED =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();

    /** How long a cracked door is allowed to stand open before the guard may close it again. */
    private static final long OPEN_FOR_MS = 8000L;

    private static int said;

    @Override
    public CustomPacketPayload.Type<DoorHack377> type() {
        return TYPE;
    }

    /**
     * Client side: the panel was solved. The key comes from the position the panel was opened for, and
     * the dimension from the level this client is in - the server compares both.
     */
    public static void send(BlockPos pos) {
        if (pos == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            Level level = mc.player.level();
            String key = DoorSync375.key(level, pos);
            if (key.isEmpty()) return;
            PacketDistributor.sendToServer(new DoorHack377(key));
            LOG.info("door: the panel at " + key + " was solved - asking the server to open the door");
        } catch (Throwable failure) {
            LOG.warn("door: a solved panel could not be sent to the server: " + failure);
        }
    }

    /** Server side: open the door this key names, if it is still locked and the player is beside it. */
    public static void handle(DoorHack377 payload, IPayloadContext context) {
        try {
            if (payload == null || context == null) return;
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.level() == null || !(player.level() instanceof ServerLevel server)) return;
            BlockPos pos = read(server, payload.key());
            if (pos == null) return;
            if (!Doors368.isDoor(server.getBlockState(pos))) return;
            DoorStore368 store = DoorStore368.get(server);
            if (store == null || !store.isLocked(server, pos)) {
                note("door: a solved panel named " + payload.key()
                        + ", but that door is not locked any more - nothing opened");
                return;
            }
            if (!near(player, pos)) {
                note("door: a solved panel named " + payload.key()
                        + ", but the player was too far away - nothing opened");
                return;
            }
            boolean opened = Doors368.setOpen(server, pos, true);
            remember(server, pos);
            note("door: the locked door at " + pos + " was cracked by " + player.getName().getString()
                    + " (opened=" + opened + "), the lock stays");
            player.displayClientMessage(Component.literal("Замок вскрыт - дверь открыта"), true);
        } catch (Throwable failure) {
            LOG.warn("door: a solved panel could not be applied: " + failure);
        }
    }

    /**
     * {@code minecraft:overworld@12,64,-30} to a position, or null when the line is not exactly the key
     * of a block in this level. Comparing the rebuilt key with the sent one checks the dimension too,
     * so a key from a door in another world can never name one here.
     */
    private static BlockPos read(Level level, String key) {
        if (level == null || key == null) return null;
        try {
            int at = key.indexOf('@');
            if (at <= 0) return null;
            String body = key.substring(at + 1);
            String[] parts = body.split(",");
            if (parts.length != 3) return null;
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            String again = DoorSync375.key(level, pos);
            return again.equals(key) ? pos : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Build 377: leave the door alone for a moment - both halves, because the guard walks the store's
     * keys and a door is two keys.
     */
    private static void remember(net.minecraft.server.level.ServerLevel server, BlockPos pos) {
        try {
            java.util.List<BlockPos> parts = Doors368.parts(server, pos);
            long now = System.currentTimeMillis();
            for (int i = 0; i < parts.size(); i++) {
                String key = DoorSync375.key(server, parts.get(i));
                if (!key.isEmpty()) CRACKED.put(key, Long.valueOf(now));
            }
        } catch (Throwable failure) {
            LOG.warn("door: a cracked door could not be remembered: " + failure);
        }
    }

    /**
     * Was this door cracked by a player just now? The guard of 371 asks this before it closes a locked
     * door, and the answer forgets itself as soon as it is too old.
     */
    public static boolean cracked(Level level, BlockPos pos) {
        if (level == null || pos == null || CRACKED.isEmpty()) return false;
        try {
            String key = DoorSync375.key(level, pos);
            if (key.isEmpty()) return false;
            Long at = CRACKED.get(key);
            if (at == null) return false;
            if (System.currentTimeMillis() - at.longValue() > OPEN_FOR_MS) {
                CRACKED.remove(key);
                return false;
            }
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    /** Is the player still standing at the door they solved the panel for? */
    private static boolean near(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    private static void note(String line) {
        if (said >= 8) return;
        said++;
        LOG.info(line);
    }
}
