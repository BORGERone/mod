package traben.entity_model_features.models.parts;
import java.util.*;import net.minecraft.client.model.geom.ModelPart;
public class EMFModelPartVanilla extends EMFModelPart {
 public String name; public EMFModelPartVanilla(){super(new LinkedHashMap<>());}
 public EMFModelPartVanilla(String name,ModelPart part,Collection<?> c,Map<?,?> m,EMFModelPartRoot root){super(new LinkedHashMap<>());this.name=name;}
 public void setVariantStateTo(int variant){}
}
