package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 355: a button that takes the graph off a block.
 *
 * <p>Until now the only way to clear a block was to delete every node by hand and save, and a block
 * that kept an empty entry still sparked when the wand was out, so "cleared" and "never configured"
 * looked different for no good reason. The button sits in the top bar of the editor, next to undo and
 * redo, and it only exists while a block id is open - an NPC graph has its own ways to be edited and
 * the stock screen is not ours to rearrange there.</p>
 *
 * <p>Two clicks, because one is too easy to hit by accident and a graph can be a lot of work: the
 * first arms the button and renames it, the second (within five seconds) empties the graph, resets the
 * start node, and saves. The save is what tells the server: an empty graph is removed from the world
 * data, so the block stops sparkling and a right click on it does nothing - the same state as a block
 * that was never touched.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcBlockDelete355Mixin {

    /** The button lives where the stock undo/redo pair lives, clear of the canvas and the tabs. */
    private static final int X = 66;
    private static final int Y = 10;
    private static final int WIDTH = 104;
    private static final int HEIGHT = 20;

    /** How long the armed button waits for its confirming click. */
    private static final long ARMED_MS = 5000L;

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Shadow
    private boolean save() {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Unique
    private long settledlands$armedAt;

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$deleteButton(CallbackInfo ci) {
        if (graph == null) return;
        if (!BlockTrigger351.isBlockEditorId(((NpcDialogueGraphScreen) (Object) this).entityId())) return;

        boolean armed = System.currentTimeMillis() - settledlands$armedAt < ARMED_MS;
        Button delete = button(armed ? "gui.settledlands.block.delete_ask"
                : "gui.settledlands.block.delete", X, Y, WIDTH, HEIGHT, () -> settledlands$delete());
        if (delete == null) return;
        // An empty graph is nothing to delete: the button says so instead of pretending.
        if (graph.nodes.isEmpty() && !armed) {
            delete.active = false;
        }
    }

    @Unique
    private void settledlands$delete() {
        if (System.currentTimeMillis() - settledlands$armedAt >= ARMED_MS) {
            settledlands$armedAt = System.currentTimeMillis();
            widgets();
            return;
        }
        settledlands$armedAt = 0L;
        graph.nodes.clear();
        graph.entry = -1;
        changed();
        save();
        widgets();
    }
}
