package net.minecraft.client.renderer.entity.player;
import net.minecraft.client.model.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
public class PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
 public PlayerRenderer(){reload(false);}
 public PlayerRenderer(PlayerModel<AbstractClientPlayer> m){model=m;}
 public void reload(boolean slim){
  ModelPart baked=new EntityModelSet().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
  if(baked instanceof EMFModelPartRoot r)r.isMainModel=true;
  model=new PlayerModel<>(baked,slim);
 }
}
