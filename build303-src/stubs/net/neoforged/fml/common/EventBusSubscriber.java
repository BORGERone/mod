package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.neoforged.api.distmarker.Dist;

/** Compile-time shape of the auto-registration marker the mod's listeners carry. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    Dist[] value() default {Dist.CLIENT};

    String modid() default "";

    /** Build 368: the item is registered on the mod bus, exactly as the mob effect already is. */
    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        MOD
    }
}
