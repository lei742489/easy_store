package org.jeecgframework.boot.easy_store_boot.app.common.anno;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Letters {    String code();
}
