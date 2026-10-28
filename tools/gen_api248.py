#!/usr/bin/env python3
"""Compile-only silhouettes for the client bridge; never package in a mod jar."""
import pathlib,sys
SRC={
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity; import java.util.UUID; public class Entity { public UUID getUUID(){return null;} }''',
'net/minecraft/world/entity/LivingEntity.java':'''package net.minecraft.world.entity; public class LivingEntity extends Entity {}''',
'net/minecraft/client/player/AbstractClientPlayer.java':'''package net.minecraft.client.player; import net.minecraft.world.entity.LivingEntity; public class AbstractClientPlayer extends LivingEntity {}''',
'net/minecraft/client/renderer/MultiBufferSource.java':'''package net.minecraft.client.renderer; public interface MultiBufferSource {}''',
'net/minecraft/client/model/geom/ModelPart.java':'''package net.minecraft.client.model.geom; import java.util.*; public final class ModelPart { public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1; public ModelPart(List<?> cubes,Map<String,ModelPart> children){} public boolean hasChild(String name){return false;} public void setPos(float x,float y,float z){this.x=x;this.y=y;this.z=z;} public void setRotation(float x,float y,float z){xRot=x;yRot=y;zRot=z;} }''',
'com/mojang/blaze3d/vertex/PoseStack.java':'''package com.mojang.blaze3d.vertex; public class PoseStack { public void pushPose(){} public void popPose(){} }''',
'org/apache/maven/artifact/versioning/ArtifactVersion.java':'''package org.apache.maven.artifact.versioning; public interface ArtifactVersion {}''',
'net/neoforged/neoforgespi/language/IModInfo.java':'''package net.neoforged.neoforgespi.language; import org.apache.maven.artifact.versioning.ArtifactVersion; public interface IModInfo { ArtifactVersion getVersion(); }''',
'net/neoforged/fml/ModContainer.java':'''package net.neoforged.fml; import net.neoforged.neoforgespi.language.IModInfo; public class ModContainer { public IModInfo getModInfo(){return null;} }''',
'net/neoforged/fml/ModList.java':'''package net.neoforged.fml; import java.util.Optional; public class ModList { public static ModList get(){return null;} public boolean isLoaded(String id){return false;} public Optional<? extends ModContainer> getModContainerById(String id){return Optional.empty();} }''',
'dev/settledlands/model/BbModel.java':'''package dev.settledlands.model; import net.minecraft.client.model.geom.ModelPart; import net.minecraft.world.entity.LivingEntity; import com.mojang.blaze3d.vertex.PoseStack; import net.minecraft.client.renderer.MultiBufferSource; public class BbModel { public String getName(){return null;} public ModelPart getPart(String name){return null;} public ModelPart getRootPart(){return null;} public void settledlands$renderActorOriginal(LivingEntity e,float s,float partial,PoseStack p,MultiBufferSource b,int light){} }''',
}
def main(dest):
 root=pathlib.Path(dest)
 for name,content in SRC.items():
  path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content+'\n',encoding='utf8')
 print('wrote',len(SRC),'248 compile-only silhouettes')
if __name__=='__main__':
 if len(sys.argv)!=2:raise SystemExit('usage: gen_api248.py DEST')
 main(sys.argv[1])
