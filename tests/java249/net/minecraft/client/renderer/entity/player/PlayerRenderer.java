package net.minecraft.client.renderer.entity.player;
import net.minecraft.client.model.*;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.model.geom.builders.*;import net.minecraft.client.renderer.entity.LivingEntityRenderer;
public class PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {public PlayerRenderer(){model=new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot(),false);}}
