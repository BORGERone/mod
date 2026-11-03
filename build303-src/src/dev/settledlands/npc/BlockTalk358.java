package dev.settledlands.npc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 358: a "Реплика" node of a block opens the dialogue window - the same one a body opens - with
 * the camera turned to the block.
 *
 * <p>Build 357 said the line in the chat and walked on, because a block has no body to show a window
 * for. That was the wrong call: the window is not the body. {@code NpcNet.DlgView} carries a plain
 * {@code entityId}, a speaker, a line and a style, and the screen opens for whatever id it is handed -
 * the entity is only used for one thing, aiming the camera ({@code getEntity(id)}). Give it an id the
 * world does not answer for and every one of those paths is already null safe: the screen still draws,
 * the camera simply is not aimed.</p>
 *
 * <p>So this class hands the screen a block id ({@link BlockTrigger351#isBlockEditorId}), the author's
 * line and a style, and aims the camera itself: one {@code CfgView} carrying the block's coordinates
 * goes out first (ordered channel, so it lands before the window), the client turns the head to the
 * block centre with {@link #aimAngles}, and the window covers the view of it. The style blocks the
 * mouse look, so the view stays on the block while it speaks.</p>
 *
 * <p>The chain waits for the player: the walk stops at the line, {@code DlgPick} continues from
 * {@code node.next}, {@code DlgClose} (Esc) drops the chain. A window left open for two minutes is
 * forgotten, so a late click cannot walk a chain the player has long walked away from. When no window
 * can be shown - no player, no style, a blank line - the caller falls back to the chat of build 357,
 * so a line is never lost.</p>
 *
 * <p>Cost: one map entry per open window (capped, pruned), two small payloads per line, and one
 * reflective lookup of {@code GuideDialogue#style} that is resolved once and never at startup.</p>
 */
public final class BlockTalk358 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/BlockTalk358");

    /** Where the talking block is: the graph store's key, the level, and the block itself. */
    public record Where(ServerLevel level, String key, int x, int y, int z) {}

    /** Marker keys of the aim packet - a {@code CfgView} whose data carries them is a talk, not an edit. */
    public static final String MARK_X = "BlockTalkX";
    public static final String MARK_Y = "BlockTalkY";
    public static final String MARK_Z = "BlockTalkZ";

    /** A window left open this long cannot be continued by a late click. */
    private static final long OPEN_MS = 120_000L;

    /** More open windows than this and the map is dropped: a leak, not a queue. */
    private static final int MAX_OPEN = 64;

    private static final Map<UUID, Open> OPEN = new HashMap<>();

    private static Method styleMethod;
    private static boolean styleBroken;

    private static final class Open {
        final int id;
        final NpcDialogueGraph graph;
        final int nodeId;
        final Where where;
        final long at;

        Open(int id, NpcDialogueGraph graph, int nodeId, Where where, long at) {
            this.id = id;
            this.graph = graph;
            this.nodeId = nodeId;
            this.where = where;
            this.at = at;
        }
    }

    private BlockTalk358() {}

    // ---- the window ---------------------------------------------------------

    /**
     * Open the dialogue window for one line of a block graph. Returns true when the window went out -
     * the caller then stops the chain, exactly the way the NPC walk stops at a line of its own. False
     * means no window could be shown and the caller must fall back to the chat.
     */
    public static boolean say(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, Where where) {
        if (player == null || graph == null || node == null || where == null
                || where.level() == null) {
            return false;
        }
        try {
            // The node text with the stock placeholders filled; the variants of build 336 are the
            // lineFor of relation 0 - a block has no relation of its own.
            String text = NpcProfile.fill(node.lineFor(0), player, 0);
            if (text == null || text.isBlank()) return false;
            NpcProfile profile = new NpcProfile();
            profile.blockMouseLook = true;   // the view stays on the block while it speaks
            profile.blockMovement = true;    // and so does the player
            // Build 359/361: the block speaks under the name it was given, in the colour it was given,
            // and under nothing else. A block nobody has named has an empty name - the window says so
            // by leaving the island blank, exactly as the author asked; the coordinates of the block
            // are not a name and never appear in one (build 361).
            String speaker = BlockName359.name(graph);
            String subtitle = "";
            profile.nameColor = BlockName359.color(graph);
            NpcNet.DlgStyle style = style(profile, player);
            if (style == null) return false;
            int id = BlockTrigger351.idFor(where.key());
            prune();
            // The camera first: the channel is ordered, so the head turns before the window is drawn.
            NpcNet.sendTo(player, new NpcNet.CfgView(id, marker(where.x(), where.y(), where.z())));
            NpcNet.sendTo(player, new NpcNet.DlgView(id, speaker, subtitle, text,
                    List.of(), List.of(), 0, 1, false, style));
            OPEN.put(player.getUUID(), new Open(id, graph, node.id, where, System.currentTimeMillis()));
            return true;
        } catch (Throwable failure) {
            LOG.warn("a block line could not be shown in the dialogue window", failure);
            return false;
        }
    }

    /**
     * A row of the block window was picked: close the window and walk on from the node after the line.
     * Returns true when the pick belonged to a block, so the stock handler must not see it.
     */
    public static boolean pick(ServerPlayer player, int entityId, int index) {
        if (player == null) return false;
        Open open = OPEN.get(player.getUUID());
        if (open == null || open.id != entityId) return false;
        OPEN.remove(player.getUUID());
        try {
            NpcNet.sendTo(player, new NpcNet.DlgEnd(entityId));
            // A negative row is a "finish" row: the chain ends with the window.
            int next = index < 0 ? -1 : next(open.graph, open.nodeId);
            if (next >= 0) BlockTrigger351.resume(player, open.graph, next, open.where);
        } catch (Throwable failure) {
            LOG.warn("a block window could not be continued", failure);
        }
        return true;
    }

    /** The player closed the window (Esc): the chain is dropped, nothing walks on. */
    public static void closed(ServerPlayer player) {
        if (player == null) return;
        OPEN.remove(player.getUUID());
    }

    /** Is this player looking at a block window right now? */
    public static boolean talking(ServerPlayer player) {
        return player != null && OPEN.containsKey(player.getUUID());
    }

    /**
     * The node a chain continues at once the line is closed: what the NPC graph calls {@code next}.
     * Pure, so the probe can check it without a server.
     */
    public static int next(NpcDialogueGraph graph, int nodeId) {
        if (graph == null) return -1;
        NpcDialogueGraph.Node node = graph.get(nodeId);
        return node == null ? -1 : node.next;
    }

    // ---- the aim ------------------------------------------------------------

    /** The aim packet: a config view the client reads as "the block at these coordinates speaks". */
    public static CompoundTag marker(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(MARK_X, x);
        tag.putInt(MARK_Y, y);
        tag.putInt(MARK_Z, z);
        return tag;
    }

    /** The coordinates of an aim packet, or null when the data is not one. */
    public static int[] readMarker(CompoundTag data) {
        if (data == null || !data.contains(MARK_X)) return null;
        return new int[] {data.getInt(MARK_X), data.getInt(MARK_Y), data.getInt(MARK_Z)};
    }

    /**
     * The head angles that look at the centre of a block - the same arithmetic the screen uses for a
     * body ({@code GuideDialogueScreen#face}), only at a point instead of an entity, because a block
     * is not one and {@code getEntity} answers null for it.
     *
     * @return {yaw, pitch} in degrees
     */
    public static float[] aimAngles(double px, double eyeY, double pz, int bx, int by, int bz) {
        double dx = (bx + 0.5D) - px;
        double dz = (bz + 0.5D) - pz;
        double dy = (by + 0.5D) - eyeY;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        return new float[] {yaw, pitch};
    }

    // ---- internals ----------------------------------------------------------

    /** "x, y, z" - the name island of the window, so a talking block can be told from another one. */
    private static String where(Where where) {
        return where.x() + ", " + where.y() + ", " + where.z();
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        OPEN.values().removeIf(open -> now - open.at > OPEN_MS);
        if (OPEN.size() >= MAX_OPEN) OPEN.clear();
    }

    /**
     * The style of a body's dialogue, built by the stock {@code GuideDialogue#style} - the one place
     * that knows how a profile becomes a window. It is private, and build 342 taught that an
     * {@code @Invoker} in an interface mixin is refused for a class target, so it is resolved once,
     * reflectively, and a failure is a chat line rather than a crash.
     */
    private static NpcNet.DlgStyle style(NpcProfile profile, ServerPlayer player) {
        if (styleBroken) return null;
        try {
            Method method = styleMethod;
            if (method == null) {
                method = GuideDialogue.class.getDeclaredMethod("style",
                        NpcProfile.class, ServerPlayer.class, int.class);
                method.setAccessible(true);
                styleMethod = method;
            }
            return (NpcNet.DlgStyle) method.invoke(null, profile, player, 0);
        } catch (Throwable failure) {
            styleBroken = true;
            LOG.warn("the dialogue style is out of reach, a block line goes to the chat", failure);
            return null;
        }
    }
}
