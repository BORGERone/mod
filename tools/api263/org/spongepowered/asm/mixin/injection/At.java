package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface At { String value(); String target() default ""; int ordinal() default -1; }
