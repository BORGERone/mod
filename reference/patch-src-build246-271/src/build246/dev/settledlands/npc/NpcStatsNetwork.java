package dev.settledlands.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server-owned, one-use per-player sessions; never trust client stat values. */
@EventBusSubscriber(modid="settledlands")
public final class NpcStatsNetwork {
    private static final long TTL=20L*60L;
    private static final double MAX_DISTANCE_SQ=36.0;
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static Consumer<Open> clientOpener=ignored->{};
    private record Pending(UUID nonce,UUID entityUuid,int entityId,ServerLevel level,long expiry,
                           InteractionHand hand,int health,int damage,int speed) {}
    public record Open(int entityId,long nonceMost,long nonceLeast,int health,int damage,int speed,boolean eligible) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands","npc_stats_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.entityId);b.writeLong(v.nonceMost);b.writeLong(v.nonceLeast);b.writeVarInt(v.health);b.writeVarInt(v.damage);b.writeVarInt(v.speed);b.writeBoolean(v.eligible);},
            b->new Open(b.readVarInt(),b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
        @Override public Type<Open> type(){return TYPE;}
    }
    public record Enter(int entityId,long nonceMost,long nonceLeast) implements CustomPacketPayload {
        public static final Type<Enter> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands","npc_stats_enter"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Enter> CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.entityId);b.writeLong(v.nonceMost);b.writeLong(v.nonceLeast);},
            b->new Enter(b.readVarInt(),b.readLong(),b.readLong()));
        @Override public Type<Enter> type(){return TYPE;}
    }
    private NpcStatsNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event){
        PayloadRegistrar r=event.registrar("2");
        r.playToClient(Open.TYPE,Open.CODEC,NpcStatsNetwork::clientReceive);
        r.playToServer(Enter.TYPE,Enter.CODEC,NpcStatsNetwork::serverReceive);
    }
    public static void setClientOpener(Consumer<Open> opener){clientOpener=opener==null?ignored->{}:opener;}
    private static void clientReceive(Open message,IPayloadContext context){context.enqueueWork(()->clientOpener.accept(message));}
    private static void serverReceive(Enter message,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer p)enter(p,message);});}
    public static void open(ServerPlayer player,GuideNpc npc,InteractionHand hand){
        if(player==null||npc==null||npc instanceof GuideCorpseNpc||hand==null||!player.isAlive()||!npc.isAlive()||npc.isRemoved()||player.serverLevel()!=npc.level()||player.distanceToSqr(npc)>MAX_DISTANCE_SQ||!PossessionSystem.isBodiless(player)) return;
        NpcProfile profile=npc.profile();NpcDialogueGraph graph=profile==null?null:profile.dialogueGraph;
        int h=NpcStatsGraph.health(graph),d=NpcStatsGraph.damage(graph),s=NpcStatsGraph.speed(graph);
        UUID nonce=UUID.randomUUID();ServerLevel level=player.serverLevel();
        PENDING.put(player.getUUID(),new Pending(nonce,npc.getUUID(),npc.getId(),level,level.getGameTime()+TTL,hand,h,d,s));
        NpcNet.sendTo(player,new Open(npc.getId(),nonce.getMostSignificantBits(),nonce.getLeastSignificantBits(),h,d,s,PossessionSystem.cult(npc)>=1));
    }
    private static void deny(ServerPlayer p,String key){p.displayClientMessage(Component.translatable(key),true);}
    public static void enter(ServerPlayer player,Enter request){
        if(player==null||request==null)return;
        Pending p=PENDING.remove(player.getUUID());UUID nonce=new UUID(request.nonceMost,request.nonceLeast);
        if(p==null||!p.nonce.equals(nonce)||p.entityId!=request.entityId||!player.isAlive()||player.serverLevel()!=p.level||p.level.getGameTime()>p.expiry||!PossessionSystem.isBodiless(player)||PossessionSystem.settledlands$isOpTool(player.getItemInHand(p.hand))){deny(player,"message.settledlands.stats.stale");return;}
        Entity entity=p.level.getEntity(p.entityUuid);
        if(!(entity instanceof GuideNpc npc)||entity instanceof GuideCorpseNpc||npc.getId()!=p.entityId||!npc.isAlive()||npc.isRemoved()||player.distanceToSqr(npc)>MAX_DISTANCE_SQ){deny(player,"message.settledlands.stats.stale");return;}
        NpcProfile profile=npc.profile();NpcDialogueGraph graph=profile==null?null:profile.dialogueGraph;
        if(p.health!=NpcStatsGraph.health(graph)||p.damage!=NpcStatsGraph.damage(graph)||p.speed!=NpcStatsGraph.speed(graph)){deny(player,"message.settledlands.stats.stale");return;}
        if(PossessionSystem.cult(npc)<1){deny(player,"message.settledlands.possession.no_cult");return;}
        PossessionSystem.settledlands$possess(player,npc);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){PENDING.remove(event.getEntity().getUUID());}
}
