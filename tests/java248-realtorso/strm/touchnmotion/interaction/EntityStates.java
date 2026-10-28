package strm.touchnmotion.interaction;
import java.util.*;import java.util.function.Supplier;
/** Controlled, injected pose state; not a gameplay motion or tick loop. */
public class EntityStates {
 public static final class Entry { public Object value; }
 private final Supplier<?> factory;private final Map<UUID,Entry> entries=new HashMap<>();
 public EntityStates(Supplier<?> f){factory=f;}
 public Entry seen(UUID id,long time){return entries.computeIfAbsent(id,k->{
  Entry e=new Entry();e.value=factory.get();
  try{Class<?> c=e.value.getClass();java.lang.reflect.Field f=c.getDeclaredField("lean");f.setAccessible(true);
   float[] lean=(float[])f.get(e.value);lean[0]=.16f;lean[1]=.11f;lean[2]=.09f;lean[3]=.3f;
   for(String name:new String[]{"wallYaw","wallShift","legShare"}){f=c.getDeclaredField(name);f.setAccessible(true);f.setFloat(e.value,.10f);}
  }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}return e;});}
 public Object fresh(UUID id){Entry e=entries.get(id);return e==null?null:e.value;}
}
