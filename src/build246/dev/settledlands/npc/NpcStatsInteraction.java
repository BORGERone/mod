package dev.settledlands.npc;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Both event paths; only the server sends an inspection snapshot. */
public final class NpcStatsInteraction {
    private NpcStatsInteraction() {}
    public static boolean isBodiless(Player player) {
        if (player==null) return false;
        try {
            ResourceKey<MobEffect> key=ResourceKey.create(Registries.MOB_EFFECT,PossessionSystem.BODILESS_ID);
            Holder<MobEffect> effect=player.registryAccess().lookupOrThrow(Registries.MOB_EFFECT).getOrThrow(key);
            return player.hasEffect(effect);
        } catch (RuntimeException unavailable) { return false; }
    }
    private static boolean handles(Player player,Entity target,ItemStack stack) {
        return target instanceof GuideNpc && !(target instanceof GuideCorpseNpc)
            && isBodiless(player) && !PossessionSystem.settledlands$isOpTool(stack);
    }
    public static void specific(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player=event.getEntity(); Entity entity=event.getTarget();
        if (!handles(player,entity,event.getItemStack())) return;
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        if (player instanceof ServerPlayer server&&entity.isAlive()&&!entity.isRemoved())
            NpcStatsNetwork.open(server,(GuideNpc)entity,event.getHand());
    }
    public static void generic(PlayerInteractEvent.EntityInteract event) {
        Player player=event.getEntity(); Entity entity=event.getTarget();
        if (!handles(player,entity,event.getItemStack())) return;
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        if (player instanceof ServerPlayer server&&entity.isAlive()&&!entity.isRemoved())
            NpcStatsNetwork.open(server,(GuideNpc)entity,event.getHand());
    }
}
