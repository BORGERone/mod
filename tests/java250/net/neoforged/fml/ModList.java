package net.neoforged.fml;
import java.util.*;
public class ModList {
 public static boolean LOADED=true;public static String VERSION="1.0.4";public static String EMF_VERSION="3.3.9";
 static final ModList INSTANCE=new ModList();public static ModList get(){return INSTANCE;}
 public boolean isLoaded(String id){return LOADED&&id.equals("touch_n_motion");}
 public Optional<? extends ModContainer> getModContainerById(String id){
  String version=switch(id){case "touch_n_motion"->VERSION;case "entity_model_features"->EMF_VERSION;case "entity_texture_features"->"7.2.4";case "emf_compat_core"->"2.3.2";default->null;};
  return LOADED&&version!=null?Optional.of(new ModContainer(version)):Optional.empty();
 }
}
