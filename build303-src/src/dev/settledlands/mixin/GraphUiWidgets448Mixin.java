package dev.settledlands.mixin;

import dev.settledlands.npc.client.GraphTooltips448;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 448: records dropdowns and sliders created via {@code NpcUi} so the hover tooltip
 * engine can resolve exact widget areas and labels.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcUi", remap = false)
public abstract class GraphUiWidgets448Mixin {

    @Inject(method = "dropdown(IIILjava/util/List;ILjava/util/function/IntConsumer;)Ldev/settledlands/npc/client/NpcUi$NpcDropdown;",
            at = @At("RETURN"), remap = false)
    private static void settledlands$trackDropdown448(int x, int y, int width,
            List<Component> entries, int selected, IntConsumer onSelect,
            CallbackInfoReturnable<NpcUi.NpcDropdown> cir) {
        GraphTooltips448.recordUiDropdown(cir.getReturnValue(), x, y, width, entries);
    }

    @Inject(method = "slider(IIIIIILjava/util/function/IntFunction;Ljava/util/function/IntConsumer;)Ldev/settledlands/npc/client/NpcUi$NpcSlider;",
            at = @At("RETURN"), remap = false)
    private static void settledlands$trackSlider448(int x, int y, int width, int min, int max,
            int value, IntFunction<String> labeler, IntConsumer onChange,
            CallbackInfoReturnable<NpcUi.NpcSlider> cir) {
        GraphTooltips448.recordUiSlider(cir.getReturnValue(), x, y, width);
    }
}
