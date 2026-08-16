package org.jeecgframework.boot.easy_store_boot.app.config.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.PinyinUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Letters;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class MybatisInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            Object paramObj = invocation.getArgs()[1];

            // 情况1：直接是实体类
            if (paramObj != null && !isMyBatisInternalMap(paramObj)) {
                processLetters(paramObj);
            }

            // 情况2：是 Map（如 ParamMap）
            else if (paramObj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) paramObj;
                for (Object val : map.values()) {
                    if (val != null && !isMyBatisInternalMap(val)) {
                        processLetters(val);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Mybatis Interceptor Error: {}", e.getMessage(), e);
        }

        return invocation.proceed();
    }


    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无需配置
    }

    private boolean isMyBatisInternalMap(Object obj) {
        return obj instanceof org.apache.ibatis.reflection.MetaObject
                || obj.getClass().getName().contains("ParamMap")
                || obj instanceof Map<?, ?>;
    }

    private void processLetters(Object obj) {
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Letters.class)) {
                Letters letters = field.getAnnotation(Letters.class);
                String codeFieldName = letters.code();

                try {
                    field.setAccessible(true);
                    Object nameValue = field.get(obj);
                    if (nameValue != null) {
                        String py = PinyinUtil.getFirstLetters(nameValue.toString());

                        // 找 code 字段并赋值
                        Field codeField = clazz.getDeclaredField(codeFieldName);
                        codeField.setAccessible(true);
                        codeField.set(obj, py);

                        log.debug("字段 {} 转为拼音: {}", field.getName(), py);
                    }
                } catch (Exception e) {
                    log.warn("处理拼音字段异常: {}", e.getMessage(), e);
                }
            }
        }
    }


}
