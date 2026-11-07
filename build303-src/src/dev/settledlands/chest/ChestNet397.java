package dev.settledlands.chest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 397: the two packets of a locked chest - and nothing else.
 *
 * <p>They are the door's two packets of builds 376/377 with the door replaced by the chest, and that is
 * the whole design: a lock panel is a client-side skill check, so the client has to be told which chest it
 * was clicked on and with which numbers, and it has to tell the server that the panel was solved.</p>
 *
 * <ul>
 *   <li><b>{@code settledlands:chest_panel}</b> - server to client: the chest at this position is locked,
 *       its panel asks <b>n</b> keys that change every <b>s</b> seconds, out of <b>d</b> rows of the
 *       keyboard. The numbers are the four controls of the chest's card; the client opens the very same
 *       panel a door opens ({@code HackPanel377}), so this mod still has one lock panel and not two;</li>
 *   <li><b>{@code settledlands:chest_hack}</b> - client to server: the panel was solved. The server finds
 *       the chest from the key, re-checks that it is <b>still locked</b> and that the player is <b>still
 *       beside it</b>, and then opens the chest's own window - the menu vanilla would have opened if the
 *       chest had not been locked.</li>
 * </ul>
 *
 * <p>One string per payload, the door's own shape ({@code DoorSync375} carries its whole snapshot in one
 * string): {@code key|keys|seconds|difficulty} for the panel, the bare key for the answer. A payload of
 * four typed fields would need a hand-written codec; one string is one thing to get wrong, and the client
 * parses it defensively - a payload it cannot read costs the panel, never the click.</p>
 *
 * <p>Registration is guarded twice, the way {@code DoorNet375} guards its own: a network registry that
 * refuses a payload must cost the author this one feature, never the launch.</p>
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class ChestNet397 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestNet397");

    private static int said;

    private ChestNet397() {}

    // ---- server to client: the panel ----------------------------------------

    /** "this chest is locked, its panel is these numbers" - one string, as the door's sync is. */
    public record Panel(String data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Panel> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "chest_panel"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Panel::data, Panel::new);

        @Override
        public CustomPacketPayload.Type<Panel> type() {
            return TYPE;
        }

        /** {@code key|keys|seconds|difficulty}. */
        public static String of(String key, int keys, int seconds, int difficulty) {
            return key + "|" + keys + "|" + seconds + "|" + difficulty;
        }
    }

    // ---- client to server: the answer ---------------------------------------

    /** "the panel of this chest was solved" - the key the store, the editor and the log all use. */
    public record Solved(String key) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Solved> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "chest_hack"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Solved> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Solved::key, Solved::new);

        @Override
        public CustomPacketPayload.Type<Solved> type() {
            return TYPE;
        }
    }

    // ---- registration -------------------------------------------------------

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        if (event == null) return;
        PayloadRegistrar registrar;
        try {
            registrar = event.registrar("1");
        } catch (Throwable failure) {
            LOG.warn("chest: the chest packets could not be registered at all - a locked chest will "
                    + "refuse the click and no panel will open: " + failure);
            return;
        }
        try {
            // Build 397: the panel is a screen, so the work is handed to the main thread explicitly -
            // the same courtesy the door's sync gives its own handler.
            registrar.playToClient(Panel.TYPE, Panel.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> panel(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("chest: the panel packet could not be registered - a locked chest will refuse the "
                    + "click and stay silent: " + failure);
        }
        try {
            // The work is handed to the main thread explicitly, the shape every payload of this mod and
            // of the base uses: a handler that touches the world from the network thread is a handler
            // that throws and is never seen again.
            registrar.playToServer(Solved.TYPE, Solved.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> solve(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("chest: the hacking packet could not be registered - a solved panel will change "
                    + "nothing: " + failure);
        }
        note("chest: settledlands:chest_panel and settledlands:chest_hack registered (build 397 chest "
                + "lock)");
    }

    // ---- server: tell one player about one panel ----------------------------

    /** Send the panel of a locked chest to the player who clicked it. */
    public static void sendPanel(ServerPlayer player, ServerLevel level, BlockPos pos, String key,
            int[] panel) {
        if (player == null || panel == null || key == null || key.isEmpty()) return;
        try {
            PacketDistributor.sendToPlayer(player, new Panel(
                    Panel.of(key, panel[0], panel[1], panel[2])));
            note("chest: the panel of " + key + " was sent to " + player.getName().getString()
                    + " - " + panel[0] + " keys, one every " + panel[1] + " s, difficulty " + panel[2]);
        } catch (Throwable failure) {
            LOG.warn("chest: the panel could not be sent: " + failure);
        }
    }

    /** Client half of the panel packet: open the very same panel a door opens, with this chest's numbers. */
    private static void panel(Panel payload, IPayloadContext context) {
        if (payload == null || payload.data() == null) return;
        String[] parts = payload.data().split("\\|");
        if (parts.length != 4) {
            LOG.warn("chest: a panel payload arrived with " + parts.length + " fields, expected 4 - "
                    + "nothing opened");
            return;
        }
        BlockPos pos = ChestGraph396Store.positionOf(parts[0]);
        if (pos == null) {
            LOG.warn("chest: a panel payload named " + parts[0] + " which is not a block position - "
                    + "nothing opened");
            return;
        }
        int keys;
        int seconds;
        int difficulty;
        try {
            keys = Integer.parseInt(parts[1].trim());
            seconds = Integer.parseInt(parts[2].trim());
            difficulty = Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException notANumber) {
            LOG.warn("chest: a panel payload carried numbers this client cannot read (" + payload.data()
                    + ") - nothing opened");
            return;
        }
        // Build 397: the chest's panel IS the door's panel. One screen, one keyboard rule, one way out.
        dev.settledlands.door.HackPanel377.openForChest(parts[0], pos, keys, seconds, difficulty);
    }

    // ---- client: a solved panel asks the server to open the chest -----------

    /**
     * The panel was solved. The key is the one the server sent with the panel, so the answer names exactly
     * the chest the panel was opened for - the client never invents a position, and a panel solved at one
     * chest can never open another.
     */
    public static void sendSolved(String key) {
        if (key == null || key.isEmpty()) return;
        try {
            PacketDistributor.sendToServer(new Solved(key));
            note("chest: the panel of " + key + " was solved - asking the server to open the chest");
        } catch (Throwable failure) {
            LOG.warn("chest: a solved panel could not be sent to the server: " + failure);
        }
    }

    // ---- server: a solved panel opens the chest -----------------------------

    /**
     * The panel was solved. Open the chest it was opened for - the chest's own window, the menu vanilla
     * would have opened if the chest had not been locked.
     *
     * <p>Three checks, the door's own three: the packet names a chest that still exists, that chest is
     * <b>still locked on the server</b> (the client's copy is never trusted), and the player is still
     * standing at it, so a panel solved at one chest cannot open another.</p>
     */
    private static void solve(Solved payload, IPayloadContext context) {
        try {
            if (payload == null || context == null) return;
            if (!(context.player() instanceof ServerPlayer player)) {
                note("chest: chest_hack arrived without a player - the packet was ignored");
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                note("chest: chest_hack arrived for " + player.getName().getString()
                        + " but the player is not in a server level - the packet was ignored");
                return;
            }
            String key = payload.key();
            note("chest: chest_hack arrived from " + player.getName().getString() + ": key=" + key);
            BlockPos pos = ChestGraph396Store.positionOf(key);
            if (pos == null) {
                note("chest: chest_hack named " + key + " which is not a block position - nothing opened");
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!ChestGraph396.isChest(state)) {
                note("chest: chest_hack named " + pos + " in " + level.dimension()
                        + ", but that block is " + (state == null ? "null" : state.getBlock())
                        + " - nothing opened");
                player.displayClientMessage(Component.translatable("message.settledlands.chest.gone"),
                        true);
                return;
            }
            if (!ChestLock397.locked(level, pos)) {
                note("chest: a solved panel named " + key + ", but that chest is not locked any more - "
                        + "nothing opened");
                player.displayClientMessage(Component.translatable("message.settledlands.chest.free"),
                        true);
                return;
            }
            if (!ChestLock397.near(player, pos)) {
                note("chest: a solved panel named " + key + ", but the player was too far away - "
                        + "nothing opened");
                player.displayClientMessage(Component.translatable("message.settledlands.chest.far"),
                        true);
                return;
            }
            // Build 400: the lock is lifted, and that is the whole of the unlock.
            //
            // 397 and 398 opened the chest's menu from here (`player.openMenu(state.getMenuProvider(...))`).
            // It never worked in the author's game - three builds of «после успешного взлома сундук не
            // открывается», with the answer arriving, every guard passing, and no window. So this build stops
            // calling a menu from the server and uses the path a chest has never failed on: the player's own
            // click. The lock of this chest is lifted for a few seconds here, and the client that solved the
            // panel clicks the chest itself (HackPanel377) - the game opens it exactly as it opens an ordinary
            // chest, because by then it IS an ordinary chest.
            ChestLock397.grant(key);
            // Said out loud anyway: a player whose client could not click (a screen in the way, a sneak) must
            // know that the lock is off and that one more click is all that is left.
            player.displayClientMessage(Component.translatable("message.settledlands.chest.again"), true);
            note("chest: the locked chest at " + pos + " was cracked by " + player.getName().getString()
                    + " - its lock is lifted, the click that follows opens it");
        } catch (Throwable failure) {
            LOG.warn("chest: a solved panel could not open the chest: " + failure);
        }
    }

    /** A report, not a rule: a handful of lines per session, never a flood. */
    private static void note(String line) {
        if (said >= 12) return;
        said++;
        LOG.info(line);
    }
}
