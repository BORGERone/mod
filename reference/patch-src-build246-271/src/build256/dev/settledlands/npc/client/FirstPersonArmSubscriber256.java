package dev.settledlands.npc.client;

import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;

/**
 * Build 256 NeoForge client subscriber replacing vanilla first-person arm rendering
 * (empty hand / offhand) with the possessed NPC's BbModel arm when in first person.
 */
@EventBusSubscriber(modid = "settledlands", value = {Dist.CLIENT})
public final class FirstPersonArmSubscriber256 {
    private FirstPersonArmSubscriber256() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderArm(RenderArmEvent event) {
        if (event == null || event.isCanceled()) return;
        boolean rightArm = event.getArm() != HumanoidArm.LEFT;
        if (FirstPersonPossession256.renderPossessedArm(
            event.getPoseStack(),
            event.getMultiBufferSource(),
            event.getPackedLight(),
            event.getPlayer(),
            rightArm
        )) {
            event.setCanceled(true);
        }
    }
}
