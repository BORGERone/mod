package fixture;

import java.lang.reflect.*;
import java.util.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;

/** Simplified annotated subscriber dispatch; not NeoForge's actual event bus. */
public class TestBus {
    public static final List<Method> handlers = new ArrayList<>();
    static {
        try {
            Class<?> c = Class.forName("dev.settledlands.npc.BodilessProtection");
            EventBusSubscriber subscriber = c.getAnnotation(EventBusSubscriber.class);
            if (subscriber == null || !subscriber.modid().equals("settledlands")) throw new AssertionError("subscriber");
            for (Method m : c.getDeclaredMethods()) {
                SubscribeEvent a=m.getAnnotation(SubscribeEvent.class);
                if (a != null) {
                    if (!Modifier.isPublic(m.getModifiers()) || !Modifier.isStatic(m.getModifiers()) || a.priority()!=EventPriority.LOWEST) throw new AssertionError("handler " + m);
                    handlers.add(m);
                }
            }
            if (handlers.size()!=4) throw new AssertionError("handler count " + handlers.size());
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    public static void post(Event event) {
        try {
            for (Method m : handlers) if (m.getParameterTypes()[0].isInstance(event)) {
                if (event instanceof ICancellableEvent c && c.isCanceled() && !m.getAnnotation(SubscribeEvent.class).receiveCanceled()) continue;
                m.invoke(null,event);
            }
        } catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
          catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
