package net.minecraft.client.renderer.entity.player;
import net.minecraft.client.model.*;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.model.geom.*;import net.minecraft.client.renderer.entity.LivingEntityRenderer;
public class PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
 public PlayerRenderer(){reload(false);}
 public void reload(boolean slim){model=new PlayerModel<>(new EntityModelSet().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),slim);}
}
