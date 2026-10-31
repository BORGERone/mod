package dev.settledlands.npc;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Persistent stats belong to the possessed body and survive its paid revival. */
@EventBusSubscriber(modid="settledlands")
public final class NpcBodyStats {
    private static final String HEALTH="settledlands_body_stats_health";
    private static final String DAMAGE="settledlands_body_stats_damage";
    private static final String SPEED="settledlands_body_stats_speed";
    private static final String ACTIVE="settledlands_body_stats_active";
    private static final ResourceLocation HEALTH_ID=ResourceLocation.fromNamespaceAndPath("settledlands","body_stats_health");
    private static final ResourceLocation SPEED_ID=ResourceLocation.fromNamespaceAndPath("settledlands","body_stats_speed");
    private static final ResourceLocation WEAR_ID=ResourceLocation.fromNamespaceAndPath("settledlands","body_wear");
    private NpcBodyStats(){}
    private static CompoundTag data(ServerPlayer p){return p.getPersistentData();}
    private static int read(CompoundTag d,String key){return d.contains(key,3)?NpcStatsGraph.clamp(d.getInt(key)):NpcStatsGraph.DEFAULT;}
    public static void activate(ServerPlayer player,GuideNpc npc){
        NpcProfile profile=npc.profile();NpcDialogueGraph graph=profile==null?null:profile.dialogueGraph;
        CompoundTag d=data(player);d.putInt(HEALTH,NpcStatsGraph.health(graph));d.putInt(DAMAGE,NpcStatsGraph.damage(graph));d.putInt(SPEED,NpcStatsGraph.speed(graph));d.putBoolean(ACTIVE,true);sync(player);
    }
    public static void deactivate(ServerPlayer player){data(player).remove(ACTIVE);sync(player);}
    public static void resume(ServerPlayer player){data(player).putBoolean(ACTIVE,true);sync(player);}
    public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player){if(!PossessionSystem.isPossessed(player))data(player).remove(ACTIVE);sync(player);}}
    public static void clonePlayer(PlayerEvent.Clone event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        CompoundTag old=event.getOriginal().getPersistentData(),now=data(player);
        for(String key:new String[]{HEALTH,DAMAGE,SPEED}){if(old.contains(key,3))now.putInt(key,read(old,key));else now.remove(key);}
        if(!event.isWasDeath()&&old.getBoolean(ACTIVE))now.putBoolean(ACTIVE,true);else now.remove(ACTIVE);
        sync(player);
    }
    private static void sync(ServerPlayer player){
        boolean active=data(player).getBoolean(ACTIVE);
        syncHealth(player,active);
        AttributeInstance speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null)setModifier(speed,SPEED_ID,active?read(data(player),SPEED)/10.0-1.0:0.0,active);
        BodyWear.sync(player);
        player.setHealth(Math.min(player.getHealth(),player.getMaxHealth()));
    }
    private static void setModifier(AttributeInstance attr,ResourceLocation id,double amount,boolean active){
        if(!active||Math.abs(amount)<1.0e-12){attr.removeModifier(id);return;}
        AttributeModifier old=attr.getModifier(id);
        if(old!=null&&old.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL&&Math.abs(old.amount()-amount)<1.0e-12)return;
        attr.addOrReplacePermanentModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static double healthBeforeOwnModifiers(AttributeInstance attr){
        double base=attr.getBaseValue();
        for(AttributeModifier m:attr.getModifiers())if(!m.id().equals(HEALTH_ID)&&!m.id().equals(WEAR_ID)&&m.operation()==AttributeModifier.Operation.ADD_VALUE)base+=m.amount();
        double value=base;
        for(AttributeModifier m:attr.getModifiers())if(!m.id().equals(HEALTH_ID)&&!m.id().equals(WEAR_ID)&&m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE)value+=base*m.amount();
        for(AttributeModifier m:attr.getModifiers())if(!m.id().equals(HEALTH_ID)&&!m.id().equals(WEAR_ID)&&m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)value*=1.0+m.amount();
        return value;
    }
    private static void syncHealth(ServerPlayer player,boolean active){
        AttributeInstance attr=player.getAttribute(Attributes.MAX_HEALTH);if(attr==null)return;
        if(!active||read(data(player),HEALTH)==NpcStatsGraph.DEFAULT){attr.removeModifier(HEALTH_ID);return;}
        double raw=healthBeforeOwnModifiers(attr);if(!Double.isFinite(raw)||raw<=0.0)return;
        Attribute type=attr.getAttribute().value();double base=type.sanitizeValue(raw);
        double desired=type.sanitizeValue(base+2.0*(read(data(player),HEALTH)-NpcStatsGraph.DEFAULT));
        if(Math.abs(desired-base)<1.0e-12){attr.removeModifier(HEALTH_ID);return;}
        setModifier(attr,HEALTH_ID,desired/raw-1.0,true);
    }
    public static void attributeDirty(Object entity,Holder<Attribute> attribute){
        if(entity instanceof ServerPlayer player&&attribute.is(Attributes.MAX_HEALTH)&&data(player).getBoolean(ACTIVE)){syncHealth(player,true);BodyWear.sync(player);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer player))return;
        CompoundTag tag=data(player);if(!tag.getBoolean(ACTIVE)||!PossessionSystem.isPossessed(player))return;
        float amount=event.getAmount();if(!Float.isFinite(amount)||amount<=0.0f)return;
        event.setAmount(amount*(read(tag,DAMAGE)/10.0f));
    }
}
