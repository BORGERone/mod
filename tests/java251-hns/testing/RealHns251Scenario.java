package testing;
import java.io.*;import java.util.*;import java.util.zip.*;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.*;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.PlayerAnimatorLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import traben.entity_model_features.models.animation.state.EMFState;
import dev.settledlands.model.BbModel;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import team.unnamed.mocha.MochaEngine;

/** Actual HnS resources + PAL 1.1.6 parser/controller; test substitutes MC, EMF/TnM and PAL processor. */
public final class RealHns251Scenario {
 static void ok(boolean x,String m){if(!x)throw new AssertionError(m+" events="+Probe.events+" bones="+Probe251.bones);}
 static final class Animated extends AbstractClientPlayer implements IAnimatedPlayer {
  final PlayerAnimManager manager=new PlayerAnimManager(this);
  final PlayerAnimationController controller=new PlayerAnimationController(this,(c,s,setter)->PlayState.STOP,c->MochaEngine.createStandard(c));
  final IAnimation counting=new IAnimation(){
   public boolean isActive(){return controller.isActive();}
   public void setupAnim(AnimationData data){controller.setupAnim(data);}
   public PlayerAnimBone get3DTransform(PlayerAnimBone b){Probe251.bones.add(b.getName());Probe.events.add("pal-"+b.getName());return controller.get3DTransform(b);}
  };
  final AnimationProcessor processor=new AnimationProcessor(){public void handleAnimations(float t,boolean f){Probe251.processorCalls++;manager.setupAnim(new AnimationData(0f,t));}};
  Animated(){manager.addAnimLayer(10,counting);manager.setTickDelta(.5f);main=new ItemStack();off=new ItemStack();}
  public PlayerAnimManager playerAnimLib$getAnimManager(){return manager;}
  public AnimationProcessor playerAnimLib$getAnimProcessor(){return processor;}
  public IAnimation playerAnimLib$getAnimation(ResourceLocation id){return id.toString().equals("hackersandslashers:attack_layer")?counting:null;}
 }
 public static void main(String[] args)throws Exception{
  boolean combined=Boolean.getBoolean("combinedExternal");
  if(combined){
   String emf=String.valueOf(traben.entity_model_features.models.parts.EMFModelPartRoot.class.getProtectionDomain().getCodeSource().getLocation());
   String tnm=String.valueOf(strm.touchnmotion.TouchNMotionHook.class.getProtectionDomain().getCodeSource().getLocation());
   ok(java.nio.file.Path.of(java.net.URI.create(emf)).toAbsolutePath().equals(java.nio.file.Path.of(System.getProperty("actualEmfPath")).toAbsolutePath()),"wrong EMF class source");
   ok(java.nio.file.Path.of(java.net.URI.create(tnm)).toAbsolutePath().equals(java.nio.file.Path.of(System.getProperty("actualTnmPath")).toAbsolutePath()),"wrong TnM class source");
  }
  ModList.HNS=true;ModList.PAL=true;int animations=0,frames=0;Set<String> names=new TreeSet<>();BbModel model=RealModel249Scenario.make();
  try(ZipFile zip=new ZipFile(args[0])){
   var entries=zip.stream().filter(e->e.getName().startsWith("assets/hackersandslashers/player_animations/hnsattack_")&&e.getName().endsWith(".json")).sorted(Comparator.comparing(ZipEntry::getName)).toList();
   for(ZipEntry e:entries){
    Animation animation;try(Reader r=new InputStreamReader(zip.getInputStream(e),java.nio.charset.StandardCharsets.UTF_8)){animation=PlayerAnimatorLoader.GSON.fromJson(r,Animation.class);}
    ok(animation!=null&&animation.length()>0,"HnS parser "+e.getName());animations++;names.addAll(animation.boneAnimations().keySet());
    for(float ratio:new float[]{.08f,.18f,.35f,.55f,.75f}){
     Animated p=new Animated();EMFState.ENTITY=p;p.controller.triggerAnimation(animation,Math.max(0,animation.length()*ratio-.5f));
     Probe.events.clear();Probe251.bones.clear();Probe251.rootAtDraw=null;Probe251.items.clear();int oldCalls=Probe251.processorCalls;
     PoseStack stack=new PoseStack();MultiBufferSource b=type->new VertexConsumer(){};model.renderActor(p,1,.5f,stack,b,15);frames++;
     ok(stack.depth()==1,"stack "+e.getName());ok(Probe251.processorCalls==oldCalls+1,"processor "+e.getName());
     ok(Probe251.bones.containsAll(List.of("body","head","torso","right_arm","left_arm","right_leg","left_leg","right_item","left_item")),"attack not transferred "+e.getName()+" ratio="+ratio);
     ok(Probe251.rootAtDraw!=null&&Probe251.rootAtDraw.isFinite()&&Probe251.items.size()==2&&Probe251.items.values().stream().allMatch(m->m.isFinite()),"non-finite pose "+e.getName());
     if(combined){
      int fresh=Probe.events.indexOf("fresh"),record=Probe.events.indexOf("record"),apply=Probe.events.indexOf("apply"),pal=Probe.events.indexOf("pal-head"),draw=Probe.events.indexOf("actual-draw");
      ok(fresh>=0&&fresh<record&&record<apply&&apply<pal&&pal<draw,"wrong combined stage ordering "+e.getName());
      ok(Collections.frequency(Probe.events,"fresh")==1&&Collections.frequency(Probe.events,"apply")==1,"duplicate FA/TnM stage "+e.getName());
     }
     ok(Probe251.items.get("right").equals(Motion251Scenario.actualPalItem(model,p,false,false),.0006f),"right item differs from actual PAL mixin "+e.getName()+" ratio="+ratio);
     ok(Probe251.items.get("left").equals(Motion251Scenario.actualPalItem(model,p,true,false),.0006f),"left item differs from actual PAL mixin "+e.getName()+" ratio="+ratio);
    }
   }
  }
  ok(animations==39,"expected 39 HnS attacks, found "+animations);ok(names.containsAll(List.of("body","head","left_arm","right_arm","left_leg","right_leg","right_item")),"missing actual HnS channels "+names);
  System.out.println("PASS real HnS JSON " +animations+" attacks / "+frames+" sampled frames, PAL loader/controller/mixin; " + (combined ? "actual EMF 3.3.12 root and TnM 1.0.4 hook; " : "synthetic EMF/TnM; ") + "synthetic MC/processor/animation providers; NOT a game test");
 }
}
