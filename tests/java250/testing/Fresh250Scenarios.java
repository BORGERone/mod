package testing;
import java.util.*;import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.model.PlayerModel;
import traben.entity_model_features.EMFManager;import traben.entity_model_features.models.IEMFModel;import traben.entity_model_features.models.animation.state.EMFState;

/** Controlled EMF contract + TnM doubles, actual 250 classes and real JOML. Not a game test. */
public final class Fresh250Scenarios {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg+" events="+Probe.events);}
 static void draw(BbModel m,AbstractClientPlayer p){PoseStack s=new PoseStack();PlayerMotionBridge.render(m,p,1,.5f,s,null,15);check(s.depth()==1,"stack");}
 public static void main(String[] args){String mode=args[0];AbstractClientPlayer p=new AbstractClientPlayer();EMFState.ENTITY=p;
  Probe250.firstPlayerId=p.getUUID();Probe250.useVariants=mode.equals("variants");
  var mc=Minecraft.getInstance();var source=mc.dispatcher.renderer.getModel();
  var state=EMFState.state();state.setLimbAngle(3);state.setLimbDistance(4);state.setHeadYaw(5);state.setHeadPitch(6);
  source.body.x=3.25f;source.rightLeg.xRot=.77f;source.leftArm.y=-2.5f;
  boolean oldMain=EMFState.isMainPhase,oldLayer=true;EMFState.isLayerPhase=oldLayer;
  long oldCount=EMFManager.getInstance().entityRenderCount;int oldBakes=Probe250.bakes;
  switch(mode){
   case "source-no-animation" -> ((IEMFModel)source).emf$getEMFRootModel().sourceHasAnimation=false;
   case "missing-marker" -> Probe250.missingMarker=true;
   case "missing-creator" -> Probe250.missingCreator=true;
   case "root-motion" -> Probe250.rootMoved=true;
   case "synthetic-gait" -> Probe250.badExpression=true;
   case "unconnected-gait" -> Probe250.unconnectedMovement=true;
   case "wrong-target" -> Probe250.wrongTarget=true;
   case "paused" -> Probe250.paused=true;
   case "cancelled" -> Probe250.cancel=true;
   case "exception" -> Probe250.throwAnimation=true;
   case "no-animation" -> Probe250.noModelAnimation=true;
   case "wrong-model-id" -> Probe250.wrongModelId=true;
   case "forced-vanilla" -> Probe250.forced=true;
   case "skip-variate" -> Probe250.skip=true;
   case "no-hook" -> EMFState.animationHooks.clear();
   case "duplicate-hook" -> EMFState.animationHooks.add(new strm.touchnmotion.TouchNMotionHook());
   case "unsupported-emf" -> net.neoforged.fml.ModList.EMF_VERSION="3.3.12";
  }
  BbModel model=new BbModel();var rest=Bridge249Scenarios.rest(model);Probe.mutate=true;draw(model,p);
  check(source.body.x==3.25f&&source.rightLeg.xRot==.77f&&source.leftArm.y==-2.5f,"shared renderer mutated");
  check(EMFState.ENTITY==p&&EMFState.state().emfEntity()==p,"caller state changed");
  check(state.limbAngle()==3&&state.limbDistance()==4&&state.headYaw()==5&&state.headPitch()==6,"EMF inputs leaked");
  check(EMFState.isMainPhase==oldMain&&EMFState.isLayerPhase==oldLayer,"EMF phase leaked");
  if(Set.of("normal","pool","two-players","slim","reload","repeat","variants","source-no-animation").contains(mode)){
   check(Probe.nativeCount==0&&Probe250.packRuns==1&&Probe250.tnmRuns==1,"pack and TnM once");
   check(Probe.events.indexOf("fresh")<Probe.events.indexOf("record")&&Probe.events.indexOf("record")<Probe.events.indexOf("apply"),"order");
   check(Math.abs(Probe.initial.get("right_leg").getUnnormalizedRotation(new org.joml.Quaternionf()).x)>.1,"TnM must see pack gait");
   check(EMFManager.getInstance().entityRenderCount==oldCount,"EMF count");
   check(Probe250.bakes==oldBakes+1,"one isolated bake");
  }else{
   boolean absentHook=Set.of("no-hook","duplicate-hook","unsupported-emf").contains(mode);
   check(Probe.nativeCount==(absentHook ? 1 : 0),"expected neutral/no-clips or absent-mod fallback");
   if(!absentHook){check(!Probe.events.contains("native"),"conflicting clips must stay off");for(String name:rest.keySet())check(model.drawn.get(name).equals(rest.get(name),.0004f),"neutral rest "+name);}
   if(Set.of("missing-marker","missing-creator","wrong-target","root-motion","synthetic-gait","unconnected-gait","cancelled","exception").contains(mode))check(Probe250.tnmRuns==1,"hooks weren't observed");
   else check(Probe250.tnmRuns==0,"unexpected TnM on fallback");
  }
  if(mode.equals("pool")||mode.equals("repeat")||mode.equals("slim")||mode.equals("reload")){
   Probe.events.clear();draw(model,p);check(Probe250.bakes==oldBakes+1,"failed to reuse isolated rig");check(Probe250.packRuns==2&&Probe250.tnmRuns==2,"second draw once");
   if(mode.equals("slim")){p.slim=true;draw(model,p);check(Probe250.bakes==oldBakes+2,"slim rig not baked");p.slim=false;draw(model,p);check(Probe250.bakes==oldBakes+2,"wide rig lost");}
   if(mode.equals("reload")){mc.dispatcher.renderer.reload(false);draw(model,p);check(Probe250.bakes==oldBakes+3,"resource reload did not invalidate anchor");}
  }
  if(mode.equals("variants")){
   check(Probe250.selectedVariant==2&&Probe250.variantCalls==1,"first variant");
   var global=EMFManager.getInstance().lastModelSuffixOfEntity;global.put(p.getUUID(),99);var saved=new LinkedHashMap<>(global);
   draw(model,p);check(Probe250.selectedVariant==2&&Probe250.variantCalls==1&&Probe250.expireChecks==1,"variant cache");
   AbstractClientPlayer q=new AbstractClientPlayer();EMFState.ENTITY=q;draw(new BbModel(),q);
   check(Probe250.selectedVariant==3&&Probe250.variantCalls==2&&global.equals(saved),"other variant or global suffix leaked");
  }
  if(mode.equals("two-players")){
   AbstractClientPlayer q=new AbstractClientPlayer();EMFState.ENTITY=q;draw(new BbModel(),q);
   check(Probe250.bakes==oldBakes+1&&Probe250.packRuns==2&&Probe250.tnmRuns==2,"second player reuse/once");
  }
  System.out.println("PASS "+mode);
 }
}
