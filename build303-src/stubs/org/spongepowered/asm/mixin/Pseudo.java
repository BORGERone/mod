package org.spongepowered.asm.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time shape of Mixin's optional-target marker.
 *
 * <p>Build 338 hooks a closed-source mod, so its class has to be a target that may simply not be
 * there: with {@code @Pseudo} the mixin is skipped when the target is absent instead of failing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Pseudo {
}
