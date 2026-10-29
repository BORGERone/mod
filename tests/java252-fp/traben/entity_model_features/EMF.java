package traben.entity_model_features;
import traben.entity_model_features.config.EMFConfig;
import traben.tconfig.TConfigHandler;
public class EMF {
 private static final TConfigHandler<EMFConfig> H = new TConfigHandler<>(new EMFConfig());
 public static TConfigHandler<EMFConfig> config(){return H;}
}
