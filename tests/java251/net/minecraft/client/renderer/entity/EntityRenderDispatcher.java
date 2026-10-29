package net.minecraft.client.renderer.entity;
import net.minecraft.world.entity.Entity;import net.minecraft.client.renderer.entity.player.PlayerRenderer;import net.minecraft.client.player.AbstractClientPlayer;
public class EntityRenderDispatcher {
 public final PlayerRenderer renderer=new PlayerRenderer();public final PlayerRenderer slimRenderer=new PlayerRenderer();public boolean absent;
 public net.minecraft.client.renderer.ItemInHandRenderer getItemInHandRenderer(){return new net.minecraft.client.renderer.ItemInHandRenderer();}
 public EntityRenderDispatcher(){slimRenderer.reload(true);}
 @SuppressWarnings("unchecked") public <T extends Entity> EntityRenderer<? super T> getRenderer(T e){return absent?null:(EntityRenderer<? super T>)(e instanceof AbstractClientPlayer p && p.slim?slimRenderer:renderer);}
}
