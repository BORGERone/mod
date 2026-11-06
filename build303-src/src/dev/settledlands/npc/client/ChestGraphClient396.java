package dev.settledlands.npc.client;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 396: the client half of a chest's graph - the stock editor on the chest, and the three lines the
 * chest's card shows on the canvas.
 *
 * <p>This is {@code DoorGraphClient382} line for line, with the door's range and the door's card replaced
 * by the chest's. That is the point of the build: the author asked for «такой-же граф для сундуков», so
 * there is no second editor, no second canvas and no second save path - the NPCs' editor is handed a
 * profile whose main graph is the chest's graph and an id from the chest range, every gesture (drag,
 * wire, copy, undo, save) stays the stock one, and the save goes back as the ordinary {@code CfgSave}
 * that the server reads by id.</p>
 *
 * <p>The one thing the stock painter cannot do is draw the card: a chest card is type
 * {@link ChestGraph396#CHEST_TYPE}, which the stock switch does not know, so it would fall through to the
 * «Реплика» rows - the build 359/382 pink-box lesson. {@link ChestCard396} draws it instead, over the same
 * box the canvas hit-tests a node with.</p>
 */
public final class ChestGraphClient396 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestGraph396");

    private static boolean reported;

    private ChestGraphClient396() {}

    /** The graph the server sent, or an empty one - a chest that has none yet opens blank. */
    public static NpcDialogueGraph graphOf(NpcNet.CfgView view) {
        CompoundTag data = view == null ? null : view.data();
        if (data == null || !data.contains("DialogueGraph", 10)) {
            return ChestGraph396.defaultGraph();
        }
        try {
            NpcDialogueGraph graph = NpcDialogueGraph.load(data.getCompound("DialogueGraph"));
            // The stock editor adds two cards to every graph it opens; a chest's graph holds the chest and
            // the nodes the author put in it, the rule of build 383 for a door.
            ChestGraph396.withChestNode(graph);
            ChestGraph396.stripCards(graph);
            return graph;
        } catch (Throwable failure) {
            report(failure);
            return ChestGraph396.defaultGraph();
        }
    }

    /** Opens the stock graph editor on the chest's graph. Called from the {@code NpcConfigScreen} hook. */
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

    /** True when this id belongs to the chest editor rather than to an NPC. */
    public static boolean isChestId(int id) {
        return ChestGraph396.isChestEditorId(id);
    }

    /** The name of the chest card on the canvas. */
    public static String title() {
        return Component.translatable(ChestGraph396.TITLE_KEY).getString();
    }

    /**
     * The three lines inside the card of a chest, in the order the author reads them: how many times the
     * chest was opened, who opened it last, and the moment the chain runs.
     */
    public static String[] lines(NpcDialogueGraph.Node node) {
        String count = safe("gui.settledlands.chest.count", Integer.toString(ChestGraph396.count(node)));
        String who = ChestGraph396.opener(node);
        String opener = who.isEmpty()
                ? Component.translatable("gui.settledlands.chest.opener_none").getString()
                : safe("gui.settledlands.chest.opener_last", who);
        String moment = Component.translatable("gui.settledlands.chest.trigger").getString();
        return new String[] {count, opener, moment};
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
        LOG.warn("chest graph editor skipped: " + String.valueOf(failure));
    }
}
