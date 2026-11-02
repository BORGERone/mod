package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface At {
    String value();
    String target() default "";
    int ordinal() default -1;
    Shift shift() default Shift.BEFORE;
    int by() default 0;
    enum Shift { BEFORE, AFTER, BY }
}
