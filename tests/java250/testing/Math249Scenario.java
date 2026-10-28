package testing;
import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.model.geom.ModelPart;import org.joml.Matrix4f;import traben.entity_model_features.models.animation.state.EMFState;import java.util.*;
/** Real JOML, synthetic vanilla and TnM. No game code or visual test. */
public class Math249Scenario {
 static float angle(Random r){return (r.nextFloat()*2-1)*(float)Math.PI;}
 public static void main(String[] a){Random r=new Random(0x249L);AbstractClientPlayer p=new AbstractClientPlayer();EMFState.ENTITY=p;float worst=0;
  for(int i=0;i<1200;i++){BbModel m=new BbModel();Map<String,Matrix4f> rest=Bridge249Scenarios.rest(m);PoseStack s=new PoseStack();m.swing=m.amount=m.headYaw=m.pitch=0;final int it=i;
   Probe.override=parts->{for(String n:Bridge249Scenarios.V){ModelPart part=parts.apply(n);part.xRot=angle(r);part.yRot=angle(r);part.zRot=angle(r);if(it%4==0&&n.equals("body"))part.yRot=(float)Math.PI/2;if(it%4==1&&n.equals("body"))part.yRot=-(float)Math.PI/2;if(it%4==2&&n.equals("head"))part.yRot=(float)Math.PI/2-.0001f;part.x+=r.nextFloat()*3-1.5f;part.y+=r.nextFloat()*3-1.5f;part.z+=r.nextFloat()*3-1.5f;}};
   Probe.expected.clear();Probe.events.clear();Probe.recorded=null;int previous=Probe.nativeCount;PlayerMotionBridge.render(m,p,1,.5f,s,null,0);if(Probe.nativeCount!=previous)throw new AssertionError("fallback sample "+i+" "+Probe.events);
   for(int j=0;j<6;j++){Matrix4f wanted=new Matrix4f(Probe.expected.get(Bridge249Scenarios.V[j])).mul(Bridge249Scenarios.restV(j).invert()).mul(rest.get(Bridge249Scenarios.R[j]));Matrix4f got=m.drawn.get(Bridge249Scenarios.R[j]);float[] x=got.get(new float[16]),y=wanted.get(new float[16]);for(int k=0;k<16;k++)worst=Math.max(worst,Math.abs(x[k]-y[k]));if(!got.equals(wanted,.00038f))throw new AssertionError("sample "+i+" bone "+j+" error "+worst);}
   if(s.depth()!=1)throw new AssertionError("stack "+i);
  }Probe.override=null;System.out.println("PASS 1200 249 retarget poses, synthetic vanilla/TnM, real JOML, max error="+worst);
 }
}
