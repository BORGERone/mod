package testing;import java.util.UUID;import strm.touchnmotion.interaction.EntityStates;
/** Seed the real algorithm through its internal store, without pretending to simulate the world. */
public class SeedTorso {public static void state(UUID id){try{Class<?> c=Class.forName("strm.touchnmotion.torso.TorsoLean");java.lang.reflect.Field f=c.getDeclaredField("STATES");f.setAccessible(true);((EntityStates)f.get(null)).seen(id,System.nanoTime());}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}}}
