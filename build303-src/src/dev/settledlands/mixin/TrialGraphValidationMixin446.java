package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: allows {@link TrialGraph446#TRIAL_TYPE} ({@code 60}) cards, blue player wires
 * targeting {@code IF}, and {@link TrialControlNode446} nodes carrying full spawner JSON payloads
 * (> 2048 chars) to validate cleanly through {@code NpcDialogueGraph#error()} and cleans up the
 * card's third socket ({@code originStep}) whenever a target node is removed.
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class TrialGraphValidationMixin446 {

    private static final ThreadLocal<List<Object[]>> MASK446 = new ThreadLocal<List<Object[]>>();
    private static final int MAX_SPAWNER_JSON_LEN = 60000;

    @Inject(method = "remove(I)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$keepTrialCard446(int id, CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraph graph = (NpcDialogueGraph) (Object) this;
        if (TrialGraph446.isTrialNode(graph.get(id))) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(method = "remove(I)Z", at = @At("RETURN"), remap = false)
    private void settledlands$cleanTrialThirdSocket446(int id, CallbackInfoReturnable<Boolean> cir) {
        if (cir != null && Boolean.TRUE.equals(cir.getReturnValue())) {
            TrialGraph446.onNodeRemoved((NpcDialogueGraph) (Object) this, id);
        }
    }

    @Inject(method = "settledlands$errorOriginal()Ljava/lang/String;",
            at = @At("HEAD"), remap = false)
    private void settledlands$maskTrialNodes446(CallbackInfoReturnable<String> cir) {
        NpcDialogueGraph graph = (NpcDialogueGraph) (Object) this;
        MASK446.remove();
        if (graph.nodes == null || graph.nodes.isEmpty()) return;

        List<Object[]> saved = null;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (n == null) continue;
            boolean isTrialCard = TrialGraph446.isTrialNode(n);
            boolean isLongSpawnerCtrl = (TrialControlNode446.isControlNode(n)
                    || TrialModule447.isModuleNode(n))
                    && n.action != null && n.action.length() > 2048
                    && n.action.length() <= MAX_SPAWNER_JSON_LEN;
            boolean isTrialValueWire = n.varInputNode >= 0
                    && TrialGraph446.isTrialNode(graph.get(n.varInputNode));
            if (!isTrialCard && !isLongSpawnerCtrl && !isTrialValueWire) continue;
            if (saved == null) saved = new ArrayList<Object[]>();
            saved.add(new Object[] {
                n,
                Integer.valueOf(n.type),
                n.action,
                Integer.valueOf(n.varInputNode)
            });
            if (isTrialCard) {
                n.type = NpcDialogueGraph.LINE;
                if (n.action != null && n.action.length() > 2048
                        && n.action.length() <= MAX_SPAWNER_JSON_LEN) {
                    n.action = "";
                }
            }
            if (isLongSpawnerCtrl) {
                n.action = "";
            }
            if (isTrialValueWire) {
                n.varInputNode = -1;
            }
        }
        if (saved != null) {
            MASK446.set(saved);
        }
    }

    @Inject(method = "settledlands$errorOriginal()Ljava/lang/String;",
            at = @At("RETURN"), remap = false)
    private void settledlands$restoreTrialNodes446(CallbackInfoReturnable<String> cir) {
        List<Object[]> saved = MASK446.get();
        if (saved == null) return;
        MASK446.remove();
        for (int i = 0; i < saved.size(); i++) {
            Object[] row = saved.get(i);
            NpcDialogueGraph.Node n = (NpcDialogueGraph.Node) row[0];
            n.type = ((Integer) row[1]).intValue();
            n.action = (String) row[2];
            n.varInputNode = ((Integer) row[3]).intValue();
        }
    }
}
