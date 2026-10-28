package strm.touchnmotion.interaction;

import java.util.*;
import java.util.function.Supplier;

/** Controlled replacement for the real clock/state store, not Minecraft state. */
public class EntityStates {
 public static final class Entry { public Object value; }
 public static Object lastState;
 private final Supplier<?> factory;
 private final Map<UUID,Entry> entries=new HashMap<>();
 public EntityStates(Supplier<?> f){factory=f;}
 public Entry seen(UUID id,long time){
  return entries.computeIfAbsent(id,k->{
   Entry e=new Entry();e.value=factory.get();lastState=e.value;
   try {
    Class<?> c=e.value.getClass();
    for(String name:new String[]{"lower","rightBend","leftBend"}){
     java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);
     f.setFloat(e.value,name.equals("lower")?2f:name.equals("rightBend")?1.0f:.7f);
    }
   }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
   return e;
  });
 }
 public static double due(Entry e,long now){return -1;}
 public Object fresh(UUID id){Entry e=entries.get(id);return e==null?null:e.value;}
}
