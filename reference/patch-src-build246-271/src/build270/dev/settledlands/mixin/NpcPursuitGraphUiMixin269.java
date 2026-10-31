package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitGraphUiMixin269 {
    @Shadow private NpcDialogueGraph graph;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int sidebarHeight;
    @Shadow private AbstractWidget triggerBox;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Inject(method = "widgets()V", at = @At("TAIL"), remap = false)
    private void settledlands$addPursuitCommands(CallbackInfo ci) {
        if (graph == null || selected < 0 || triggerBox == null) return;
        Node node = graph.get(selected);
        if (node == null || node.type != NpcDialogueGraph.TRIGGER) return;

        int x = inspectorX + 8;
        int y = triggerBox.getY() + triggerBox.getHeight() + 5;
        List<Component> options = List.of(
                Component.translatable("gui.settledlands.graph.pursuit_commands"),
                Component.translatable("gui.settledlands.graph.pursuit_start_npc"),
                Component.translatable("gui.settledlands.graph.pursuit_start_player"),
                Component.translatable("gui.settledlands.graph.pursuit_stop"));
        AbstractWidget dropdown = NpcUi.dropdown(x, y,
                Math.max(120, triggerBox.getWidth()), options, 0, selectedOption -> {
                    String command = switch (selectedOption) {
                        case 1 -> "trigger:pursuit:start:npc:";
                        case 2 -> "trigger:pursuit:start:player:";
                        case 3 -> "trigger:pursuit:stop";
                        default -> null;
                    };
                    if (command == null) return;
                    NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
                    screen.settledlands$remember();
                    node.action = command;
                    try {
                        Method setter = triggerBox.getClass().getMethod("setValue", String.class);
                        setter.invoke(triggerBox, command);
                    } catch (ReflectiveOperationException ignored) {
                        // The graph model is still updated; reopening the inspector refreshes the editor.
                    }
                    screen.settledlands$changed();
                });
        try {
            Method addWidget = getClass().getMethod("settledlands$addRenderableWidget", AbstractWidget.class);
            addWidget.invoke(this, dropdown);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to add pursuit command dropdown to NPC graph inspector", error);
        }
        if (inspectorWidgets != null) inspectorWidgets.add(dropdown);
        sidebarHeight += dropdown.getHeight() + 28;
    }
}
