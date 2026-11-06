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
     * Build 377: doors this mod opened by cracking. The guard of build 371 closes every locked door that
     * is open, so without this a cracked door would slam shut in the player's face on the next tick.
     *
     * <p>Build 395: the mark has no clock any more. 377 kept a timestamp and let the guard close the door
     * eight seconds later - and that is exactly what the author saw: «чето немного странное происходит,
     * дверь после взлома сама закрывается через секунд 5-10». Eight seconds was a guess, not a rule:
     * nothing in the game closes a door by itself, and a door opened on purpose is a door. The mark is a
     * set now and it lives as long as the door stands open with somebody at it - see
     * {@link #crackedOpen}.</p>
     */
    private static final java.util.Set<String> CRACKED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Build 395: how far from a cracked door a player still counts as walking through it, in blocks. This
     * is the whole of the closing rule now - not a timer. Inside it the door is left alone however long
     * the player takes; outside it the door is a locked door standing open in an empty room, and 371 is
     * right to close it.
     */
    private static final double KEEP_OPEN = 16.0D;

    /**
     * Build 380: panels this client has solved and not yet seen opened - {@code key -> {sentAt, retried,
     * x, y, z}}. A door that stays shut is indistinguishable from a door nobody asked about unless the
     * client keeps asking, so it does: the request is repeated once after a second, and if the door is
     * still closed after four the log says so instead of the player being left to guess.
     */
    private static final java.util.Map<String, long[]> PENDING =
            new java.util.concurrent.ConcurrentHashMap<String, long[]>();

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
            PENDING.put(key, new long[] {System.currentTimeMillis(), 0L,
                    pos.getX(), pos.getY(), pos.getZ()});
            LOG.info("door: the panel at " + key + " was solved - asking the server to open the door");
        } catch (Throwable failure) {
            LOG.warn("door: a solved panel could not be sent to the server: " + failure);
        }
    }

    /** Server side: open the door this key names, if it is still locked and the player is beside it. */
    public static void handle(DoorHack377 payload, IPayloadContext context) {
        try {
            if (payload == null || context == null) return;
            // Build 379: the first line of this method is a report. A packet that never arrived and a
            // packet that arrived and was refused look exactly the same from the player's chair - the
            // door simply does not open - and this line is the difference in the log.
            if (!(context.player() instanceof ServerPlayer player)) {
                note("door: door_hack arrived without a player - the packet was ignored");
                return;
            }
            note("door: door_hack arrived from " + player.getName().getString()
                    + ": key=" + payload.key());
            if (player.level() == null || !(player.level() instanceof ServerLevel server)) {
                note("door: door_hack arrived for " + player.getName().getString()
                        + " but the player is not in a server level - the packet was ignored");
                return;
            }
            BlockPos pos = read(server, payload.key());
            if (pos == null) {
                note("door: door_hack named " + payload.key()
                        + " which is not a block position - nothing opened");
                return;
            }
            if (!Doors368.isDoor(server.getBlockState(pos))) {
                note("door: door_hack named " + pos + " in " + server.dimension()
                        + ", but that block is " + server.getBlockState(pos).getBlock()
                        + " - nothing opened");
                player.displayClientMessage(Component.literal(
                        "Сервер не нашёл дверь в этой точке - смотри строки door: в логе"), true);
                return;
            }
            DoorStore368 store = DoorStore368.get(server);
            if (store == null || !store.isLocked(server, pos)) {
                note("door: a solved panel named " + payload.key()
                        + ", but that door is not locked any more - nothing opened");
                player.displayClientMessage(Component.literal(
                        "Взлом прошёл, но дверь уже не заперта - открывать нечего"), true);
                return;
            }
            if (!near(player, pos)) {
                note("door: a solved panel named " + payload.key()
                        + ", but the player was too far away - nothing opened");
                player.displayClientMessage(Component.literal(
                        "Взлом прошёл, но до двери далеко - подойди и попробуй снова"), true);
                return;
            }
            boolean opened = Doors368.setOpen(server, pos, true);
            remember(server, pos);
            boolean nowOpen = Doors368.anyOpen(server, pos);
            note("door: the locked door at " + pos + " was cracked by " + player.getName().getString()
                    + " (changed=" + opened + ", open now=" + nowOpen + "), the lock stays");
            if (!nowOpen) {
                LOG.warn("door: the door at " + pos + " was asked to open and did not - it is locked by "
                        + "the store of this level, the click was accepted, but the block refused");
                player.displayClientMessage(Component.literal(
                        "Дверь не поддалась - сервер её не открыл (open now=false)"), true);
            }
            player.displayClientMessage(Component.literal("Замок вскрыт - дверь открыта"), true);
            // Build 389: this is the hack - the lock panel was solved by this player - so this is where
            // the door's graph pulses: every «Импульс» node fires and every blue «Цель» node is stamped
            // with the name of the player who did it. An ordinary click on a door that is not sealed is
            // not a hack and pulses nothing (see DoorEvents368).
            if (nowOpen) DoorSignal388.hack(player, server, pos);
        } catch (Throwable failure) {
            LOG.warn("door: a solved panel could not be applied: " + failure);
        }
    }

    /**
     * {@code minecraft:overworld@12,64,-30} to a position, or null when the line carries no position.
     *
     * <p>Build 379: the position is read on its own, and the dimension half of the key is only
     * <b>compared</b>, not required. Build 377 threw the whole request away when the two strings differed
     * - and they can differ without anything being wrong: a door inside a sub-level (Sable, Immersive
     * Portals) or a pocket dimension is named by one side of the game and found by the other under a
     * different dimension id. The packet arriving with a key this level would spell differently is not a
     * reason to leave the door shut; the two checks that matter are the lock and the distance, and both
     * are applied to the position that was read.</p>
     */
    private static BlockPos read(Level level, String key) {
        if (level == null || key == null) return null;
        try {
            int at = key.indexOf('@');
            String body = at >= 0 ? key.substring(at + 1) : key;
            String[] parts = body.split(",");
            if (parts.length != 3) return null;
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            String again = DoorSync375.key(level, pos);
            if (!again.equals(key)) {
                note("door: the solved panel named " + key + ", this level calls that block " + again
                        + " - the position is used as it is");
            }
            return pos;
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
            for (int i = 0; i < parts.size(); i++) {
                String key = DoorSync375.key(server, parts.get(i));
                if (!key.isEmpty()) CRACKED.add(key);
            }
        } catch (Throwable failure) {
            LOG.warn("door: a cracked door could not be remembered: " + failure);
        }
    }

    /**
     * Build 380: called from the client tick. Watches every request this client has sent and reports what
     * became of it - opened, refused, or never answered - then drops it. This is the line that turns
     * "the door does not open" into an answer.
     */
    public static void watch() {
        if (PENDING.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            java.util.Iterator<java.util.Map.Entry<String, long[]>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, long[]> entry = it.next();
                String key = entry.getKey();
                long[] state = entry.getValue();
                BlockPos pos = new BlockPos((int) state[2], (int) state[3], (int) state[4]);
                long age = System.currentTimeMillis() - state[0];
                if (Doors368.anyOpen(mc.level, pos)) {
                    LOG.info("door: the door at " + key + " is open - the server answered the panel after "
                            + age + " ms");
                    it.remove();
                    continue;
                }
                if (age > 4000L) {
                    LOG.warn("door: the door at " + key + " is still closed " + age + " ms after the panel "
                            + "was solved - the server never opened it. The log above says whether the "
                            + "request arrived (door_hack arrived) and what refused it");
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal(
                                "Сервер не открыл дверь за 4 с - смотри строки door: в логе"), true);
                    }
                    it.remove();
                    continue;
                }
                if (age > 1200L && state[1] == 0L) {
                    state[1] = 1L;
                    LOG.warn("door: no answer for " + key + " after " + age + " ms - sending the request "
                            + "again");
                    PacketDistributor.sendToServer(new DoorHack377(key));
                }
            }
        } catch (Throwable failure) {
            LOG.warn("door: the panel watchdog could not run: " + failure);
        }
    }

    /**
     * Build 395: may the guard of build 371 leave this door alone?
     *
     * <p>Three questions, in the order that makes the answer cheap and the reason clear:</p>
     *
     * <ol>
     *   <li>did <b>this mod</b> crack this door open - a door somebody else opened is the guard's business,
     *       exactly as build 371 says;</li>
     *   <li>is the door still standing open - a door that was closed again (by the player, by redstone)
     *       is a door the guard may close, and the mark goes with it, so a mark can never outlive the door
     *       it was made for;</li>
     *   <li>is somebody still at the door - within {@link #KEEP_OPEN} blocks, nobody is being slammed in
     *       the face; outside it, a locked door standing open in an empty room is closed again, which is
     *       the promise of 371.</li>
     * </ol>
     *
     * <p>The eight-second timer of 377 answered only the first two, which is why the author saw a door
     * close itself «через секунд 5-10» while he was standing at it.</p>
     */
    public static boolean crackedOpen(Level level, BlockPos pos) {
        if (level == null || pos == null || CRACKED.isEmpty()) return false;
        try {
            String key = DoorSync375.key(level, pos);
            if (key.isEmpty() || !CRACKED.contains(key)) return false;
            boolean door = Doors368.isDoor(level.getBlockState(pos));
            boolean open = door && Doors368.anyOpen(level, pos);
            boolean player = open && someoneNear(level, pos);
            if (!keepOpen(true, door, open, player)) {
                // The mark is taken back the moment the rule stops holding, so it can never outlive the
                // open it was made for.
                forget(level, pos);
                return false;
            }
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Build 395: the rule, without a world - four facts in, one answer out.
     *
     * <p>It is a method of its own because this is the whole of the change the author asked for and it has
     * to be readable and testable on its own: a door is left alone only when <b>this mod</b> cracked it
     * open, it is <b>still a door</b>, it is <b>still open</b>, and <b>somebody is at it</b>. No clock
     * appears anywhere in it, which is the difference from build 377.</p>
     */
    public static boolean keepOpen(boolean marked, boolean door, boolean open, boolean playerNear) {
        return marked && door && open && playerNear;
    }

    /** Build 395: the mark of one door, both halves - what was given is taken back. */
    private static void forget(Level level, BlockPos pos) {
        try {
            java.util.List<BlockPos> parts = Doors368.parts(level, pos);
            for (int i = 0; i < parts.size(); i++) {
                String key = DoorSync375.key(level, parts.get(i));
                if (!key.isEmpty()) CRACKED.remove(key);
            }
        } catch (Throwable failure) {
            CRACKED.clear();
        }
    }

    /** Build 395: is a player standing at this door? The server's list, filtered by dimension. */
    private static boolean someoneNear(Level level, BlockPos pos) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return false;
        java.util.List<ServerPlayer> players = server.getPlayerList().getPlayers();
        double reach = KEEP_OPEN * KEEP_OPEN;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == null || player.level() != level) continue;
            if (player.distanceToSqr(x, y, z) <= reach) return true;
        }
        return false;
    }

    /** Is the player still standing at the door they solved the panel for? */
    private static boolean near(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    private static void note(String line) {
        if (said >= 12) return;
        said++;
        LOG.info(line);
    }
}
