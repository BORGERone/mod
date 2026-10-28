package traben.entity_model_features;
public class EMFManager { private static final EMFManager INSTANCE=new EMFManager();public long entityRenderCount;public final java.util.Map<java.util.UUID,Integer> lastModelSuffixOfEntity=new java.util.LinkedHashMap<>();public static EMFManager getInstance(){return INSTANCE;} }
