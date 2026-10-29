package net.minecraft.client.player;
import java.util.*;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranim.animation.PlayerAnimationProcessor;
import com.zigythebird.playeranimcore.animation.AnimationProcessor;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
public class AbstractClientPlayer extends net.minecraft.world.entity.player.Player implements IAnimatedPlayer {
 public boolean slim;
 public PlayerAnimManager animManager;
 public AnimationProcessor animProcessor;
 public final Map<ResourceLocation,IAnimation> modAnimations=new HashMap<>();
 public PlayerSkin getSkin(){return new PlayerSkin(slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);}
 public void initPal(){
  animManager=new PlayerAnimManager(this);
  animProcessor=new PlayerAnimationProcessor(this);
 }
 public PlayerAnimManager playerAnimLib$getAnimManager(){return animManager;}
 public IAnimation playerAnimLib$getAnimation(ResourceLocation id){return modAnimations.get(id);}
 public AnimationProcessor playerAnimLib$getAnimProcessor(){return animProcessor;}
}
