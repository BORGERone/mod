package net.minecraft.client;
import net.minecraft.client.model.geom.EntityModelSet;import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
public class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft();
 public final EntityRenderDispatcher dispatcher=new EntityRenderDispatcher();public final EntityModelSet models=new EntityModelSet();
 public Options options=new Options();public DeltaTracker getTimer(){return b->.5f;}public static Minecraft getInstance(){return INSTANCE;}
 public EntityRenderDispatcher getEntityRenderDispatcher(){return dispatcher;}
 public EntityModelSet getEntityModels(){return models;}
}
