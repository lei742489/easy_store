package org.jeecgframework.boot.easy_store_boot.app.common.anno;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {
    boolean value() default true;
}