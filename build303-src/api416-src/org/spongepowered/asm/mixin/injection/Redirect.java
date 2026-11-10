package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time mirror (Build 416) of SpongePowered Mixin's {@code @Redirect}.
 *
 * <p>Unlike {@code @Inject} (whose {@code at} element is {@code At[]}), the real Mixin 0.8
 * {@code @Redirect} declares {@code At at()} as a single annotation (constant-pool tag {@code 0x40},
 * not array {@code 0x5b}). {@code stubs/Redirect.java} had {@code At[] at()}, which caused builds
 * 360-361's {@code @Redirect} to be silently ignored at runtime.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Redirect {
    String[] method();
    At at();
    int require() default -1;
    int expect() default 1;
    boolean remap() default true;
}
