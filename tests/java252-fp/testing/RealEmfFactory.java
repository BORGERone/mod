package testing;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.models.EMFModel_ID;
import traben.entity_model_features.models.parts.*;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
public class RealEmfFactory {
 static Object unsafe() throws Exception {
  Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return f.get(null);
 }
 static void set(Object o,Class<?> owner,String n,Object value)throws Exception{
  Field f=owner.getDeclaredField(n);f.setAccessible(true);f.set(o,value);
 }
 private static EMFModelPartVanilla vanillaPart(String name, Map<String,ModelPart> subChildren) {
  EMFModelPartVanilla p=new EMFModelPartVanilla();
  p.name=name;
  p.children.putAll(subChildren);
  return p;
 }
 public static ModelPart make(boolean slim){
  try{
   Object u=unsafe();
   EMFModelPartRoot root=(EMFModelPartRoot)u.getClass().getMethod("allocateInstance",Class.class).invoke(u,EMFModelPartRoot.class);
   Map<String,ModelPart> c=new LinkedHashMap<>();
   String[] names={
    "body","jacket","head","hat",
    "right_arm","right_sleeve","left_arm","left_sleeve",
    "right_leg","right_pants","left_leg","left_pants"
   };
   for(String n:names){
    Map<String,ModelPart> subs=new LinkedHashMap<>();
    if(n.equals("right_arm")||n.equals("left_arm")||n.equals("right_sleeve")||n.equals("left_sleeve")){
     EMFModelPart box=new EMFModelPart(new LinkedHashMap<>());
     box.setPos(n.startsWith("right")?5f:-5f,22f,0f);
     box.saveRest();
     subs.put("EMF_"+n,box);
    }
    c.put(n,vanillaPart(n,subs));
   }
   set(root,ModelPart.class,"children",c);
   set(root,ModelPart.class,"rest",new float[]{0,0,0,0,0,0});
   root.xScale=root.yScale=root.zScale=1;
   root.visible=true;
   float armY=slim?2.5f:2f;
   c.get("right_arm").setPos(-5,armY,0);c.get("right_sleeve").setPos(-5,armY,0);
   c.get("left_arm").setPos(5,armY,0);c.get("left_sleeve").setPos(5,armY,0);
   c.get("right_leg").setPos(-1.9f,12,0);c.get("right_pants").setPos(-1.9f,12,0);
   c.get("left_leg").setPos(1.9f,12,0);c.get("left_pants").setPos(1.9f,12,0);
   c.values().forEach(ModelPart::saveRest);
   set(root,EMFModelPartRoot.class,"modelName",new EMFModel_ID(slim?"player_slim":"player"));
   set(root,EMFModelPartRoot.class,"allVanillaParts",c);
   set(root,EMFModelPartRoot.class,"animations",new HashMap<Integer,Runnable>());
   root.receiveAnimationHandler(1,new EMFAnimationHandler(root));
   root.setVariantStateTo(1);
   return root;
  }catch(Exception e){throw new RuntimeException(e);}
 }
}
