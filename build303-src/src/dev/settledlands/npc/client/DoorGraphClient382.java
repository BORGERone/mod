package dev.settledlands.npc.client;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 382: the client half of a door's graph - the stock editor on the door, and the three lines the
 * door's node shows on the canvas.
 *
 * <p>Nothing is re-drawn and nothing is re-implemented: the editor of the NPCs is handed a profile
 * whose main graph is the door's graph and an id from the door range, exactly the way build 351 does
 * it for blocks. Every canvas gesture, undo, copy and save is the stock one, and the save goes back as
 * the usual {@code CfgSave} - the server reads the id, not an entity, and writes the door.</p>
 *
 * <p>The card of the door node is the one thing the stock painter cannot draw: its type is our own
 * ({@link DoorGraph382#DOOR_TYPE}), so the stock switch draws the card box and nothing inside it. The
 * box, the selection, the drag, the ports and the wires stay stock; only the text is ours
 * ({@link #title}, {@link #lines}), and {@code NpcDoorCard382Mixin} writes it.</p>
 *
 * <p>A failure here must cost the editor, not the client: the whole path is one try/catch and a throw
 * is reported once (the rule of build 349).</p>
 */
public final class DoorGraphClient382 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorGraph382");

    private static boolean reported;

    private DoorGraphClient382() {}

    /** The graph the server sent, or an empty one - a door that has none yet opens blank. */
    public static NpcDialogueGraph graphOf(NpcNet.CfgView view) {
        CompoundTag data = view == null ? null : view.data();
        if (data == null || !data.contains("DialogueGraph", 10)) return new NpcDialogueGraph();
        try {
            NpcDialogueGraph graph = NpcDialogueGraph.load(data.getCompound("DialogueGraph"));
            // Build 388: a graph made by build 386 still holds the three types of that build, which no
            // longer run. They are dropped here - the author sees the graph he has, not nodes that
            // silently do nothing - and the drop is one line in the log.
            DoorGraph382.migrate(graph);
            return graph;
        } catch (Throwable failure) {
            report(failure);
            return new NpcDialogueGraph();
        }
    }

    /** Opens the stock graph editor on the door's graph. Called from the {@code NpcConfigScreen} hook. */
    public static void openEditor(NpcNet.CfgView view) {
        if (view == null) return;
        try {
            NpcProfile profile = new NpcProfile();
            profile.dialogueGraph = graphOf(view);
            Minecraft.getInstance().setScreen(
                    new NpcDialogueGraphScreen(view.entityId(), profile, null, -1));
        } catch (Throwable failure) {
            report(failure);
        }
    }

    /** True when this id belongs to the door editor rather than to an NPC. */
    public static boolean isDoorId(int id) {
        return DoorGraph382.isDoorEditorId(id);
    }

    /** Message the door editor shows instead of a node it cannot create. */
    public static String paletteNote() {
        return Component.translatable("gui.settledlands.door.palette").getString();
    }

    /** The name of the door node on the canvas. */
    public static String title() {
        return Component.translatable("gui.settledlands.door.node").getString();
    }

    /**
     * The three lines inside the card of a door node, in the order the author reads them: how many
     * keys, how fast, and whether the door is closed to a plain click.
     */
    public static String[] lines(NpcDialogueGraph.Node node) {
        if (node == null) return new String[] {"", "", ""};
        String keys = safe("gui.settledlands.door.count", Integer.toString(DoorGraph382.keys(node)));
        String speed = safe("gui.settledlands.door.speed", Integer.toString(DoorGraph382.speed(node)));
        String state = Component.translatable(DoorGraph382.sealed(node)
                ? "gui.settledlands.door.sealed" : "gui.settledlands.door.unsealed").getString();
        return new String[] {keys, speed, state};
    }

    /** A translated line with one argument, never a throw. */
    public static String safe(String key, String argument) {
        try {
            return Component.translatable(key, Component.literal(argument)).getString();
        } catch (Throwable failure) {
            return key;
        }
    }

    private static void report(Throwable failure) {
        if (reported) return;
        reported = true;
        LOG.warn("door graph editor skipped: " + String.valueOf(failure));
    }
}
