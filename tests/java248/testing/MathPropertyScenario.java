package testing;

import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;
import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;import traben.entity_model_features.models.animation.state.EMFState;
import java.util.*;

/** Deterministic real-JOML property checks, synthetic hook and renderer only. */
public class MathPropertyScenario {
 static final String[] TARGET={"body","head","right_arm","left_arm","right_leg","left_leg"};
 static final String[] ACTUAL={"body","head","rightarm","leftarm","rightleg","leftleg"};
 static float angle(Random r){return (r.nextFloat()*2-1)*(float)Math.PI;}
 public static void main(String[] ignored){
  Random random=new Random(0x248L);AbstractClientPlayer player=new AbstractClientPlayer();EMFState.ENTITY=player;float worst=0;
  for(int i=0;i<1200;i++){
   BbModel model=new BbModel();PoseStack stack=new PoseStack();final int iteration=i;
   // Mix normal poses, extreme rotations and exact/near positive/negative gimbal lock.
   Probe.override=parts->{
    for(String n:TARGET){ModelPart p=parts.apply(n);p.xRot=angle(random);p.yRot=angle(random);p.zRot=angle(random);
     if(iteration%4==0&&n.equals("body"))p.yRot=(float)Math.PI/2;
     if(iteration%4==1&&n.equals("body"))p.yRot=-(float)Math.PI/2;
     if(iteration%4==2&&n.equals("head"))p.yRot=(float)Math.PI/2-0.0001f;
     p.x+=random.nextFloat()*3-1.5f;p.y+=random.nextFloat()*3-1.5f;p.z+=random.nextFloat()*3-1.5f;
    }
   };
   Probe.expected.clear();Probe.events.clear();Probe.recorded=null;
   TouchMotionBridge.render(model,player,1,.5f,stack,null,0);
   if(Probe.expected.size()!=6)throw new AssertionError("controller rejected sample "+i+" "+Probe.events);
   for(int j=0;j<TARGET.length;j++){
    Matrix4f got=model.drawn.get(ACTUAL[j]),expected=Probe.expected.get(TARGET[j]);
    float[] actual=got.get(new float[16]),want=expected.get(new float[16]);for(int k=0;k<16;k++)worst=Math.max(worst,Math.abs(actual[k]-want[k]));
    if(!got.equals(expected,.0003f))throw new AssertionError("sample "+i+" bone "+TARGET[j]+" got "+got+" expected "+expected);
   }
   if(stack.depth()!=1)throw new AssertionError("stack "+i);
  }
  Probe.override=null;System.out.println("PASS 1200 deterministic real-JOML pose round trips, including gimbal lock; maximum matrix error="+worst+"; synthetic model/hook");
 }
}
