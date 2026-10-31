package dev.settledlands.npc;

import net.minecraft.nbt.CompoundTag;

/**
 * Build 254: default Blockbench model ("ivy_oleander.bbmodel") for regular GuideNpc
 * profiles while keeping "" for GuideTriggerNpc, GuideCorpseNpc, and explicit ""
 * saved in NBT ("Стандартная (Guide NPC)").
 *
 * Server-safe: contains zero references to net.minecraft.client.* classes.
 */
public final class NpcDefaultModel254 {
    public static final String DEFAULT_MODEL = "ivy_oleander.bbmodel";

    private NpcDefaultModel254() {}

    public static String defaultTexture() {
        return DEFAULT_MODEL;
    }

    public static String initialTexture(GuideNpc npc) {
        if (npc == null || npc instanceof GuideTriggerNpc || npc instanceof GuideCorpseNpc) {
            return "";
        }
        return DEFAULT_MODEL;
    }

    public static void initDefaultProfile(GuideNpc npc, NpcProfile profile) {
        if (profile != null) {
            profile.texture = initialTexture(npc);
        }
    }

    public static NpcProfile createDefaultProfile(GuideNpc npc) {
        NpcProfile profile = new NpcProfile();
        if (npc != null) {
            profile.scale = npc.initialScale();
            profile.lockInteraction = npc.initialLockInteraction();
            profile.texture = initialTexture(npc);
        }
        return profile;
    }

    public static String loadTexture(CompoundTag tag) {
        if (tag != null && tag.contains("Texture", 8)) {
            return tag.getString("Texture");
        }
        return DEFAULT_MODEL;
    }
}
