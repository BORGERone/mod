package dev.settledlands.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

/**
 * Populates the required entity id for DataComponents.ENTITY_DATA
 * (CustomData.CODEC_WITH_ID) and selects the matching spawn egg item.
 */
public final class NpcCopyEggFix253 {
    public static final String GUIDE_ID = "settledlands:guide";
    public static final String TRIGGER_ID = "settledlands:guide_trigger";

    private NpcCopyEggFix253() {}

    public static Item eggItem(GuideNpc npc) {
        return npc instanceof GuideTriggerNpc ? NpcContent.triggerEgg() : NpcContent.spawnEgg();
    }

    public static CompoundTag withEntityId(GuideNpc npc, CompoundTag entityTag) {
        if (entityTag == null) {
            entityTag = new CompoundTag();
        }
        entityTag.putString("id", npc instanceof GuideTriggerNpc ? TRIGGER_ID : GUIDE_ID);
        return entityTag;
    }
}
