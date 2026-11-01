package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphClipboard318;
import dev.settledlands.npc.client.NpcClipboard318;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 318: Ctrl+C and Ctrl+V on the graph screen.
 *
 * <p>Copy takes the selected nodes - the multi-selection if there is one, otherwise the single
 * selected node - turns them into text with {@link NpcGraphClipboard318} and puts that on the
 * system clipboard. Paste reads the clipboard back, checks it is a node block of the right size
 * before anything is added, takes one undo snapshot for the whole block, and leaves the new nodes
 * selected so they can be dragged off in one go.</p>
 *
 * <p>Both keys are ignored while a text field has the focus: Ctrl+C and Ctrl+V inside a line
 * editor must keep copying the text, which is also the way a block is carried to and from another
 * program.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcClipboardMixin318 {

    private static final int KEY_C = 67;

    private static final int KEY_V = 86;

    @Shadow
    private String status;

    @Shadow
    private int selected;

    @Shadow
    public Set<Integer> multiSelected;

    @Shadow
    private String tr(String key) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isControlDownSafe() {
        throw new AssertionError();
    }

    @Shadow
    public abstract NpcDialogueGraph settledlands$graph();

    @Shadow
    public abstract void settledlands$remember();

    @Shadow
    public abstract void settledlands$changed();

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$copyPaste(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != KEY_C && keyCode != KEY_V) return;
        if (!isControlDownSafe()) return;
        // Cast to the screen itself, not to Screen: the compiler then records the call on the
        // mod's own class, which is what the external-API gate can vouch for.
        Object focused = ((NpcDialogueGraphScreen) (Object) this).getFocused();
        if (focused instanceof EditBox || focused instanceof MultiLineEditBox) return;

        NpcDialogueGraph graph = settledlands$graph();
        if (graph == null) return;
        if (keyCode == KEY_C) {
            cir.setReturnValue(settledlands$copy(graph));
        } else {
            cir.setReturnValue(settledlands$paste(graph));
        }
    }

    private boolean settledlands$copy(NpcDialogueGraph graph) {
        List<Integer> ids = new ArrayList<>();
        if (!multiSelected.isEmpty()) {
            ids.addAll(multiSelected);
        } else if (selected >= 0) {
            ids.add(selected);
        }
        if (ids.isEmpty()) {
            status = tr("gui.settledlands.graph.clip_empty");
            return true;
        }
        String text = NpcGraphClipboard318.copy(graph, ids);
        if (text.isEmpty()) {
            status = tr("gui.settledlands.graph.clip_empty");
            return true;
        }
        NpcClipboard318.write(text);
        status = tr("gui.settledlands.graph.clip_copy") + " " + (ids.size());
        return true;
    }

    private boolean settledlands$paste(NpcDialogueGraph graph) {
        String text = NpcClipboard318.read();
        int count = NpcGraphClipboard318.preview(text);
        if (count <= 0) {
            status = tr("gui.settledlands.graph.clip_fail") + " "
                    + NpcGraphClipboard318.lastError();
            return true;
        }
        settledlands$remember();
        List<Integer> added = NpcGraphClipboard318.paste(graph, text);
        if (added == null || added.isEmpty()) {
            status = tr("gui.settledlands.graph.clip_fail") + " "
                    + NpcGraphClipboard318.lastError();
            return true;
        }
        multiSelected.clear();
        multiSelected.addAll(added);
        selected = added.get(0);
        settledlands$changed();
        status = tr("gui.settledlands.graph.clip_paste") + " " + added.size();
        return true;
    }
}
