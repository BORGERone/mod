package strm.touchnmotion.footgrounding.compat;
import java.util.UUID;import java.util.function.Function;import net.minecraft.client.model.geom.ModelPart;
/** Terrain solver is deliberately a test double; it captures the actual hook inputs. */
public class TerrainBalance {
 public static float[] right,left;public static int calls;
 public void apply(UUID id,Function<String,ModelPart> f,float[] r,float[] l){right=r==null?null:r.clone();left=l==null?null:l.clone();calls++;}
}
