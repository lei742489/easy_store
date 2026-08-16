package org.jeecgframework.boot.easy_store_boot.app.common.dict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DictField {
    String dictTable();
    String dicText();
    String dicCode();
}
