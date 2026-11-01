package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time stub of the real mixin annotation (never packaged into the jar). */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ModifyArg {
    String[] method();
    At[] at();
    int index() default -1;
    boolean remap() default true;
    int require() default -1;
    int expect() default 1;
    boolean allowDuplicates() default false;
}
