package net.neoforged.neoforge.client.event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
public class RenderPlayerEvent {
 private final Player player;
 public RenderPlayerEvent(Player p){this.player=p;}
 public Player getEntity(){return player;}
 public static class Pre extends RenderPlayerEvent {
  public boolean canceled;
  private final PoseStack stack;
  private final MultiBufferSource buffers;
  private final float partial;
  public Pre(Player p,PoseStack s,MultiBufferSource b,float partial){super(p);this.stack=s;this.buffers=b;this.partial=partial;}
  public void setCanceled(boolean c){this.canceled=c;}
  public boolean isCanceled(){return canceled;}
  public float getPartialTick(){return partial;}
  public PoseStack getPoseStack(){return stack;}
  public MultiBufferSource getMultiBufferSource(){return buffers;}
  public int getPackedLight(){return 15;}
 }
}
