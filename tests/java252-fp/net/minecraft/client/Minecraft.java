package net.minecraft.client;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
public class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft();
 public LocalPlayer player;
 public Entity cameraEntity;
 public Options options=new Options();
 public final EntityRenderDispatcher dispatcher=new EntityRenderDispatcher();
 public final EntityModelSet models=new EntityModelSet();
 public static Minecraft getInstance(){return INSTANCE;}
 public Entity getCameraEntity(){return cameraEntity!=null?cameraEntity:player;}
 public boolean paused;
 public boolean isPaused(){return paused;}
 public EntityRenderDispatcher getEntityRenderDispatcher(){return dispatcher;}
 public EntityModelSet getEntityModels(){return models;}
}
