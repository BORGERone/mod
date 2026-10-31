package dev.settledlands.npc;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standard target events, existing targets and registered vanilla brain memories. */
@EventBusSubscriber(modid="settledlands")
public final class NpcBodilessRules {
    private NpcBodilessRules(){}
    private static boolean ignores(Entity entity){return entity instanceof Player player&&NpcStatsInteraction.isBodiless(player);}
    public static void rejectTarget(LivingEntity observer,LivingEntity target,CallbackInfoReturnable<Boolean> cir){
        if(observer instanceof Mob&&ignores(target))cir.setReturnValue(false);
    }
    @SubscribeEvent public static void onTarget(LivingChangeTargetEvent event){
        if(event.getEntity() instanceof Mob&&ignores(event.getNewAboutToBeSetTarget()))event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent public static void onEntityTick(EntityTickEvent.Pre event){
        if(!(event.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level))return;
        if(ignores(mob.getTarget()))mob.setTarget(null);
        Brain<?> brain=mob.getBrain();
        if(brain==null)return;
        clearLiving(brain,MemoryModuleType.ATTACK_TARGET);
        clearLiving(brain,MemoryModuleType.HURT_BY_ENTITY);
        Optional<UUID> angry=brain.getMemoryInternal(MemoryModuleType.ANGRY_AT);
        if(angry!=null&&angry.isPresent()&&ignores(level.getEntity(angry.get())))brain.eraseMemory(MemoryModuleType.ANGRY_AT);
    }
    private static void clearLiving(Brain<?> brain,MemoryModuleType<LivingEntity> type){
        Optional<LivingEntity> memory=brain.getMemoryInternal(type);
        if(memory!=null&&memory.isPresent()&&ignores(memory.get()))brain.eraseMemory(type);
    }
}
