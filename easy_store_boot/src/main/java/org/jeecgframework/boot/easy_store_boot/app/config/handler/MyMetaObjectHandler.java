package org.jeecgframework.boot.easy_store_boot.app.config.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.jeecgframework.boot.easy_store_boot.app.common.PinyinUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Letters;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Date;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        if (metaObject.hasGetter("createTime")
                && metaObject.getValue("createTime") == null) {
            this.setFieldValByName("createTime", new Date(), metaObject);
        }

    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updateTime", new Date(), metaObject);

    }

   /* private void handleLetters(MetaObject metaObject) {
        Object originalObject = metaObject.getOriginalObject();
        Class<?> clazz = originalObject.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            System.err.println(field);
            if (field.isAnnotationPresent(Letters.class)) {

                Letters annotation = field.getAnnotation(Letters.class);
                String codeFieldName = annotation.code();

                try {
                    field.setAccessible(true);
                    Object nameVal = field.get(originalObject);

                    if (nameVal != null) {
                        String firstLetters = PinyinUtil.getFirstLetters(nameVal.toString());

                        // 设置拼音字段
                        Field codeField = clazz.getDeclaredField(codeFieldName);
                        codeField.setAccessible(true);
                        codeField.set(originalObject, firstLetters);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }*/

}
