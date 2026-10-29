package net.neoforged.fml;
import java.util.*;
public class ModList {
 public static boolean LOADED=true, TNM=true, CORE=true, EMF=true, HNS=true, PAL=true, ADDON=false;
 public static String VERSION="1.0.4", CORE_VERSION="2.3.2", EMF_VERSION="3.3.9", HNS_VERSION="2.0-beta2.5", PAL_VERSION="1.1.6+mc.1.21.1";
 static final ModList INSTANCE=new ModList();
 public static ModList get(){return LOADED?INSTANCE:null;}
 public boolean isLoaded(String id){
  if(!LOADED)return false;
  return switch(id){
   case "touch_n_motion"->TNM;
   case "emf_compat_core"->CORE;
   case "entity_model_features"->EMF;
   case "entity_texture_features"->EMF;
   case "hackersandslashers"->HNS;
   case "player_animation_library"->PAL;
   case "emf_compat_hackers_and_slashers"->ADDON;
   default->false;
  };
 }
 public Optional<? extends ModContainer> getModContainerById(String id){
  if(!isLoaded(id))return Optional.empty();
  String version=switch(id){
   case "touch_n_motion"->VERSION;
   case "entity_model_features"->EMF_VERSION;
   case "entity_texture_features"->"7.2.4";
   case "emf_compat_core"->CORE_VERSION;
   case "hackersandslashers"->HNS_VERSION;
   case "player_animation_library"->PAL_VERSION;
   default->null;
  };
  return version!=null?Optional.of(new ModContainer(version)):Optional.empty();
 }
}
