package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Inject { String[] method(); At[] at(); int require() default -1; boolean cancellable() default false; boolean remap() default true; }
