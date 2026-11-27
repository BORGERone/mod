package dev.settledlands.mixin;

import dev.settledlands.npc.NpcPoints;
import dev.settledlands.npc.NpcPointsRemoveBridge457;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Exposes safe shared/personal variable deletion on {@link NpcPoints} for the Build 457 catalog editor. */
@Mixin(NpcPoints.class)
public abstract class NpcPointsCatalogMixin457 extends SavedData implements NpcPointsRemoveBridge457 {

    @Shadow(remap = false)
    @Final
    private Map<String, Integer> shared;

    @Shadow(remap = false)
    @Final
    private Map<UUID, Map<String, Integer>> personal;

    @Override
    public boolean settledlands457$removeVar(String cleanName, boolean isShared) {
        if (cleanName == null || cleanName.isEmpty()) return false;
        boolean changed = false;
        if (isShared) {
            if (this.shared != null && this.shared.remove(cleanName) != null) {
                changed = true;
            }
        } else if (this.personal != null) {
            Iterator<Map.Entry<UUID, Map<String, Integer>>> it = this.personal.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Map<String, Integer>> entry = it.next();
                Map<String, Integer> map = entry.getValue();
                if (map != null && map.remove(cleanName) != null) {
                    changed = true;
                    if (map.isEmpty()) it.remove();
                }
            }
        }
        if (changed) {
            this.setDirty();
        }
        return changed;
    }
}
