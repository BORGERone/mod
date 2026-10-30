package dev.settledlands.mixin;

import dev.settledlands.npc.client.PursuitGraphNodes271;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "dev.settledlands.npc.client.NpcGraphContextMenu", remap = false)
public abstract class NpcPursuitContextMenuMixin271 {
    @ModifyConstant(method = "rowAt(DD)I", constant = @Constant(intValue = 16), remap = false)
    private int settledlands$addThreePursuitRows(int original) {
        return PursuitGraphNodes271.MENU_ITEM_COUNT;
    }

    @ModifyConstant(method = "scroll(I)V", constant = @Constant(intValue = 11), remap = false)
    private int settledlands$extendMenuScroll(int original) {
        return PursuitGraphNodes271.MENU_ITEM_COUNT - 5;
    }
}
