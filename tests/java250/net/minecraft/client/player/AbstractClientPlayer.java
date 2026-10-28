package net.minecraft.client.player;
import net.minecraft.client.resources.PlayerSkin;
public class AbstractClientPlayer extends net.minecraft.world.entity.player.Player {
 public boolean slim;
 public PlayerSkin getSkin(){return new PlayerSkin(slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);}
}
