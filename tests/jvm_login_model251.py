#!/usr/bin/env python3
"""Actual 250/251 login code, payload handler/codec and body renderer.

Minecraft, NeoForge dispatch, NBT persistence, transport and render targets
are controlled stand-ins. The old baseline must reproduce the missing sync;
this does not prove a real Minecraft game or the visual result.
"""
import argparse,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,jvm_recovery238 as legacy

S={
'net/neoforged/neoforge/common/extensions/IEntityExtension.java':'''package net.neoforged.neoforge.common.extensions;import net.minecraft.nbt.CompoundTag;public interface IEntityExtension{CompoundTag getPersistentData();}''',
'net/minecraft/nbt/CompoundTag.java':'''package net.minecraft.nbt;import java.util.*;public class CompoundTag{public Map<String,Object> values=new HashMap<>();public boolean getBoolean(String k){return Boolean.TRUE.equals(values.get(k));}public String getString(String k){Object x=values.get(k);return x instanceof String?(String)x:"";}public void putBoolean(String k,boolean v){values.put(k,v);}public void putString(String k,String v){values.put(k,v);}public void remove(String k){values.remove(k);}}''',
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity;import java.util.UUID;import net.minecraft.nbt.CompoundTag;import net.neoforged.neoforge.common.extensions.IEntityExtension;public class Entity implements IEntityExtension{public UUID uuid;private CompoundTag tag=new CompoundTag();public Entity(UUID id){uuid=id;}public UUID getUUID(){return uuid;}public CompoundTag getPersistentData(){return tag;}}''',
'net/minecraft/world/entity/LivingEntity.java':'''package net.minecraft.world.entity;import java.util.UUID;public class LivingEntity extends Entity{public LivingEntity(UUID id){super(id);}}''',
'net/minecraft/world/entity/player/Player.java':'''package net.minecraft.world.entity.player;import java.util.UUID;import net.minecraft.world.entity.LivingEntity;public class Player extends LivingEntity{public Player(UUID id){super(id);}}''',
'net/minecraft/server/level/ServerPlayer.java':'''package net.minecraft.server.level;import java.util.*;import dev.settledlands.npc.PossessionClientSync;import net.minecraft.server.MinecraftServer;import net.minecraft.world.entity.player.Player;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.core.*;import net.minecraft.world.effect.*;public class ServerPlayer extends Player{public MinecraftServer server;public boolean bodiless;public int addEffectCount;public List<CustomPacketPayload> inbox=new ArrayList<>();public Map<UUID,PossessionClientSync.State> clientStates=new HashMap<>();public ServerPlayer(MinecraftServer s,UUID id){super(id);server=s;}public MinecraftServer getServer(){return server;}public boolean hasEffect(Holder<MobEffect> h){return bodiless;}public boolean addEffect(MobEffectInstance e){bodiless=true;addEffectCount++;return true;}public RegistryAccess registryAccess(){return new RegistryAccess(){};}}''',
'net/minecraft/server/players/PlayerList.java':'''package net.minecraft.server.players;import java.util.*;import net.minecraft.server.level.ServerPlayer;public class PlayerList{private List<ServerPlayer> players=new ArrayList<>();public List<ServerPlayer> getPlayers(){return players;}}''',
'net/minecraft/server/MinecraftServer.java':'''package net.minecraft.server;import net.minecraft.server.players.PlayerList;public class MinecraftServer{private PlayerList p=new PlayerList();public PlayerList getPlayerList(){return p;}}''',
'net/minecraft/resources/ResourceLocation.java':'''package net.minecraft.resources;public class ResourceLocation{public static ResourceLocation fromNamespaceAndPath(String ns,String name){return new ResourceLocation();}}''',
'net/minecraft/resources/ResourceKey.java':'''package net.minecraft.resources;public class ResourceKey<T>{public static <T> ResourceKey<T> create(ResourceKey<?> p,ResourceLocation id){return new ResourceKey<>();}}''',
'net/minecraft/core/registries/Registries.java':'''package net.minecraft.core.registries;import net.minecraft.resources.ResourceKey;public class Registries{public static final ResourceKey<?> MOB_EFFECT=new ResourceKey<>();}''',
'net/minecraft/core/Holder.java':'''package net.minecraft.core;public interface Holder<T>{public static class Reference<T> implements Holder<T>{}}''',
'net/minecraft/core/HolderLookup.java':'''package net.minecraft.core;import net.minecraft.resources.ResourceKey;public interface HolderLookup{public interface RegistryLookup<T>{Holder.Reference<T> getOrThrow(ResourceKey<T> k);}}''',
'net/minecraft/core/RegistryAccess.java':'''package net.minecraft.core;import net.minecraft.resources.ResourceKey;public interface RegistryAccess{default <T> HolderLookup.RegistryLookup<T> lookupOrThrow(ResourceKey<?> k){return x->new Holder.Reference<T>();}}''',
'net/minecraft/world/effect/MobEffect.java':'''package net.minecraft.world.effect;public class MobEffect{}''',
'net/minecraft/world/effect/MobEffectInstance.java':'''package net.minecraft.world.effect;import net.minecraft.core.Holder;public class MobEffectInstance{public MobEffectInstance(Holder<MobEffect> h,int t,int a,boolean b,boolean c,boolean d){}}''',
'net/neoforged/neoforge/event/entity/player/PlayerEvent.java':'''package net.neoforged.neoforge.event.entity.player;import net.minecraft.world.entity.player.Player;public class PlayerEvent{private final Player p;public PlayerEvent(Player p){this.p=p;}public Player getEntity(){return p;}public static class PlayerLoggedInEvent extends PlayerEvent{public PlayerLoggedInEvent(Player p){super(p);}}}''',
'net/minecraft/network/protocol/common/custom/CustomPacketPayload.java':'''package net.minecraft.network.protocol.common.custom;import net.minecraft.resources.ResourceLocation;public interface CustomPacketPayload{Type<? extends CustomPacketPayload> type();record Type<T extends CustomPacketPayload>(ResourceLocation id){}}''',
'net/minecraft/network/FriendlyByteBuf.java':'''package net.minecraft.network;import java.util.*;public class FriendlyByteBuf{private final List<Object> values=new ArrayList<>();private int next;public FriendlyByteBuf writeUUID(UUID v){values.add(v);return this;}public UUID readUUID(){return (UUID)values.get(next++);}public FriendlyByteBuf writeUtf(String v){values.add(v);return this;}public String readUtf(){return (String)values.get(next++);}}''',
'net/minecraft/network/RegistryFriendlyByteBuf.java':'''package net.minecraft.network;public class RegistryFriendlyByteBuf extends FriendlyByteBuf{}''',
'net/minecraft/network/codec/StreamEncoder.java':'''package net.minecraft.network.codec;public interface StreamEncoder<B,V>{void encode(B b,V v);}''',
'net/minecraft/network/codec/StreamDecoder.java':'''package net.minecraft.network.codec;public interface StreamDecoder<B,V>{V decode(B b);}''',
'net/minecraft/network/codec/StreamCodec.java':'''package net.minecraft.network.codec;public interface StreamCodec<B,V>{void encode(B b,V v);V decode(B b);static <B,V> StreamCodec<B,V> of(StreamEncoder<B,V> e,StreamDecoder<B,V> d){return new StreamCodec<>(){public void encode(B b,V v){e.encode(b,v);}public V decode(B b){return d.decode(b);}};}}''',
'net/neoforged/neoforge/network/handling/IPayloadContext.java':'''package net.neoforged.neoforge.network.handling;import java.util.concurrent.CompletableFuture;public interface IPayloadContext{CompletableFuture<Void> enqueueWork(Runnable work);}''',
'net/neoforged/neoforge/network/PacketDistributor.java':'''package net.neoforged.neoforge.network;import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.ServerPlayer;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;public class PacketDistributor{public static MinecraftServer server;public static int attempts,failOnAttempt=-1;public static void sendToPlayer(ServerPlayer p,CustomPacketPayload first,CustomPacketPayload... rest){attempts++;if(attempts==failOnAttempt)throw new IllegalStateException("controlled transport failure");p.inbox.add(first);for(var x:rest)p.inbox.add(x);}public static void sendToAllPlayers(CustomPacketPayload first,CustomPacketPayload... rest){for(ServerPlayer p:server.getPlayerList().getPlayers())sendToPlayer(p,first,rest);}}''',
'dev/settledlands/npc/BodyWear.java':'''package dev.settledlands.npc;import net.minecraft.server.level.ServerPlayer;import net.neoforged.neoforge.event.entity.player.PlayerEvent;public class BodyWear{public static int logins,deactivations;public static void login(PlayerEvent.PlayerLoggedInEvent e){logins++;}public static void deactivate(ServerPlayer p){deactivations++;}}''',
'dev/settledlands/npc/NpcBodyStats.java':'''package dev.settledlands.npc;import net.minecraft.server.level.ServerPlayer;import net.neoforged.neoforge.event.entity.player.PlayerEvent;public class NpcBodyStats{public static int logins,deactivations;public static void login(PlayerEvent.PlayerLoggedInEvent e){logins++;}public static void deactivate(ServerPlayer p){deactivations++;}}''',
'net/minecraft/client/player/AbstractClientPlayer.java':'''package net.minecraft.client.player;import java.util.UUID;import net.minecraft.world.entity.player.Player;public class AbstractClientPlayer extends Player{public AbstractClientPlayer(UUID id){super(id);}public boolean isSpectator(){return false;}public boolean isInvisible(){return false;}}''',
'net/minecraft/client/player/LocalPlayer.java':'''package net.minecraft.client.player;import java.util.UUID;public class LocalPlayer extends AbstractClientPlayer{public LocalPlayer(UUID id){super(id);}}''',
'net/minecraft/client/CameraType.java':'''package net.minecraft.client;public class CameraType{public boolean isFirstPerson(){return false;}}''',
'net/minecraft/client/Options.java':'''package net.minecraft.client;public class Options{public CameraType getCameraType(){return new CameraType();}}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.client.player.LocalPlayer;public class Minecraft{private static final Minecraft I=new Minecraft();public LocalPlayer player;public Options options=new Options();public static Minecraft getInstance(){return I;}}''',
'com/mojang/blaze3d/vertex/PoseStack.java':'''package com.mojang.blaze3d.vertex;public class PoseStack{}''',
'net/minecraft/client/renderer/MultiBufferSource.java':'''package net.minecraft.client.renderer;public interface MultiBufferSource{}''',
'net/neoforged/neoforge/client/event/RenderPlayerEvent.java':'''package net.neoforged.neoforge.client.event;import net.minecraft.world.entity.player.Player;import net.minecraft.client.renderer.MultiBufferSource;import com.mojang.blaze3d.vertex.PoseStack;public class RenderPlayerEvent{public static class Pre{private final Player p;public boolean canceled;public Pre(Player p){this.p=p;}public Player getEntity(){return p;}public void setCanceled(boolean b){canceled=b;}public float getPartialTick(){return .5f;}public PoseStack getPoseStack(){return new PoseStack();}public MultiBufferSource getMultiBufferSource(){return null;}public int getPackedLight(){return 15;}}}''',
'dev/settledlands/model/BbModel.java':'''package dev.settledlands.model;import net.minecraft.world.entity.LivingEntity;import net.minecraft.client.renderer.MultiBufferSource;import com.mojang.blaze3d.vertex.PoseStack;public class BbModel{private final String name;public BbModel(String n){name=n;}public void renderActor(LivingEntity p,float a,float b,PoseStack s,MultiBufferSource m,int l){BbModelManager.lastRendered=name;}}''',
'dev/settledlands/model/BbModelManager.java':'''package dev.settledlands.model;public class BbModelManager{public static String lastRendered="";public static BbModel getModel(String name){return name.equals("ivy_oleander")||name.equals("sage")?new BbModel(name):null;}}''',
}
PS='dev/settledlands/npc/PossessionSystem'
METHODS={'<clinit>','data','bodiless','isPossessed','possessModelKey','possessName','makeBodiless','onPlayerLogin','setPossessed','clearPossessed'}

def run(java,jar,old):
 with tempfile.TemporaryDirectory(prefix='login-model251-') as d:
  root=pathlib.Path(d);src=root/'src';out=root/'classes';out.mkdir()
  for name,content in S.items():
   file=src/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content+'\n',encoding='utf8')
  files=sorted(src.rglob('*.java'))+sorted((ROOT/'tests/java251-login').rglob('*.java'))
  subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(jar),'-d',str(out),*map(str,files)],check=True)
  path=out/(PS+'.class');path.parent.mkdir(parents=True,exist_ok=True);subset=legacy.subset(jar,PS,METHODS);path.write_bytes(subset)
  # The dependency-pruned class contains the exact, unmodified login Code from
  # the candidate, not a Java reimplementation of the logic under test.
  with zipfile.ZipFile(jar) as z:full=classedit.Klass(z.read(PS+'.class'))
  kept=classedit.Klass(subset)
  for name in METHODS:
   assert classedit.code_parts(full,full.find_method(name))['code']==classedit.code_parts(kept,kept.find_method(name))['code'],name
  cp=str(out)+':'+str(jar)
  cmd=[str(java),'-Xverify:all','-DfixtureJar='+str(jar),'-cp',cp,'dev.settledlands.npc.LoginModelScenario']+(['base'] if old else [])
  result=subprocess.run(cmd,capture_output=True,text=True)
  if result.returncode:raise AssertionError(result.stdout+'\n'+result.stderr)
  print(result.stdout.strip())
  assert ('baseline 250 reproduces' if old else 'real payload codec/client handler/renderer') in result.stdout

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',type=pathlib.Path,required=True);p.add_argument('--base250',type=pathlib.Path,required=True);p.add_argument('--jar',type=pathlib.Path,required=True);a=p.parse_args()
 run(a.java.resolve(),a.base250.resolve(),True);run(a.java.resolve(),a.jar.resolve(),False)
if __name__=='__main__':main()
