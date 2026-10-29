package traben.entity_model_features.models.parts;
import net.minecraft.client.model.geom.ModelPart;
import java.util.*;
public class EMFModelPart extends ModelPart {
 public boolean isSetByAnimation;
 public EMFModelPart(Map<String,ModelPart> children){super(List.of(),children);}
}
