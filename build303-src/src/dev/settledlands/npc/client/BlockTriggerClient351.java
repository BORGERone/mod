package dev.settledlands.npc.client;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 351: the client half of a block click - open the stock graph editor on a block's graph.
 *
 * <p>Nothing is re-drawn and nothing is re-implemented: the editor of the NPCs is given a profile of
 * its own whose main graph is the block's graph, and an id from the block range. From there every
 * canvas gesture, undo, copy, wire and inspector field is the stock one, and the save goes back as the
 * usual {@code CfgSave} - the server reads the id, not the entity, and writes the block.</p>
 *
 * <p>A failure here must cost the editor, not the client: the whole path is one try/catch, and a
 * throw is reported once (build 349 rule: no visual pass may take the game down).</p>
 */
public final class BlockTriggerClient351 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/BlockTrigger351");

    private static boolean reported;

    private BlockTriggerClient351() {}

    /** The graph the server sent, or an empty one - a block that has none yet opens blank. */
    public static NpcDialogueGraph graphOf(NpcNet.CfgView view) {
        CompoundTag data = view == null ? null : view.data();
        if (data == null || !data.contains("BlockGraph", 10)) return new NpcDialogueGraph();
        try {
            return NpcDialogueGraph.load(data.getCompound("BlockGraph"));
        } catch (Throwable failure) {
            report(failure);
            return new NpcDialogueGraph();
        }
    }

    /** Opens the stock graph editor on the block's graph. Called from the {@code NpcConfigScreen} hook. */
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

    /** Message the block editor shows instead of a node it cannot create. */
    public static String paletteNote() {
        return net.minecraft.network.chat.Component.translatable("gui.settledlands.block.palette")
                .getString();
    }

    /** True when this id belongs to the block editor rather than to an NPC. */
    public static boolean isBlockId(int id) {
        return BlockTrigger351.isBlockEditorId(id);
    }

    private static void report(Throwable failure) {
        if (reported) return;
        reported = true;
        LOG.warn("block graph editor skipped: {}", String.valueOf(failure));
    }
}
