package net.minecraft.client.player;
import net.minecraft.client.resources.PlayerSkin;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
/** The PAL mixin makes this method a member of AbstractClientPlayer at runtime. */
public class AbstractClientPlayer extends net.minecraft.world.entity.player.Player {
 public boolean slim;public float getViewYRot(float t){return yHeadRotO+t*(yHeadRot-yHeadRotO);}
 public PlayerSkin getSkin(){return new PlayerSkin(slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);}
 public PlayerAnimManager playerAnimLib$getAnimManager(){return null;}
}
