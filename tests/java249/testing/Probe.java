package testing;
import java.util.*;import org.joml.Matrix4f;
public class Probe { public static List<String> events=new ArrayList<>(); public static UUID recorded; public static int nativeCount; public static float[] values; public static boolean mutate,failBefore,failApply,badPose,failRender,rotateBefore; public static org.joml.Matrix3f drawNormal; public static Map<String,Matrix4f> expected=new HashMap<>(), initial=new HashMap<>(); public static java.util.function.Consumer<java.util.function.Function<String,net.minecraft.client.model.geom.ModelPart>> override; }
