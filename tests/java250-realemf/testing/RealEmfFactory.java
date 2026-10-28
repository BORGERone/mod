package testing;
import java.util.*;import java.lang.reflect.*;import net.minecraft.client.model.geom.ModelPart;import traben.entity_model_features.models.EMFModel_ID;import traben.entity_model_features.models.parts.*;import traben.entity_model_features.models.animation.EMFAnimationHandler;
public class RealEmfFactory {
 static Object unsafe() throws Exception {Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return f.get(null);}
 static void set(Object o,Class<?> owner,String n,Object value)throws Exception{Field f=owner.getDeclaredField(n);f.setAccessible(true);f.set(o,value);}
 @SuppressWarnings("unchecked") public static ModelPart make(boolean slim){try{
  Object u=unsafe();EMFModelPartRoot root=(EMFModelPartRoot)u.getClass().getMethod("allocateInstance",Class.class).invoke(u,EMFModelPartRoot.class);
  Map<String,ModelPart> c=new LinkedHashMap<>();for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"}){EMFModelPartVanilla p=new EMFModelPartVanilla();p.name=n;c.put(n,p);}
  set(root,ModelPart.class,"children",c);set(root,ModelPart.class,"rest",new float[]{0,0,0,0,0,0});root.xScale=root.yScale=root.zScale=1;
  c.get("right_arm").setPos(-5,2,0);c.get("left_arm").setPos(5,2,0);c.get("right_leg").setPos(-1.9f,12,0);c.get("left_leg").setPos(1.9f,12,0);c.values().forEach(ModelPart::saveRest);
  set(root,EMFModelPartRoot.class,"modelName",new EMFModel_ID(slim?"player_slim":"player"));set(root,EMFModelPartRoot.class,"allVanillaParts",c);set(root,EMFModelPartRoot.class,"animations",new HashMap<Integer,Runnable>());
  if(Boolean.getBoolean("variantTest")){
   set(root,EMFModelPartRoot.class,"entitySuffixMap",new traben.entity_texture_features.utils.ETFLruCache.UUIDInteger());
   set(root,EMFModelPartRoot.class,"variantTester",(traben.entity_texture_features.ETFApi.ETFVariantSuffixProvider)(state->2));
   root.receiveAnimationHandler(2,new EMFAnimationHandler(root));
  }else root.receiveAnimationHandler(1,new EMFAnimationHandler(root));
  root.setVariantStateTo(1);return root;
 }catch(Exception e){throw new RuntimeException(e);}}
}
