package org.jeecgframework.boot.easy_store_boot.app.common.dict;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.jeecgframework.boot.easy_store_boot.app.common.DateUtils;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.CommonDictMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Component
@Slf4j
public class DictFieldHandler {

    @Autowired
    private CommonDictMapper commonDictMapper;

    private static final Set<Class<?>> DATE_TYPES = new HashSet<Class<?>>() {{
        add(Date.class);
        add(LocalDateTime.class);
        add(LocalDate.class);
        add(LocalTime.class);
    }};

    /**
     * 处理接口返回的 Result 对象，自动填充其中 data 字段的字典文本。
     */
    public <T> Result<?> fillDictFields(Result<T> result) {
        if (result == null) {
            return null;
        }

        T data = result.getData();
        if (data == null) {
            return result;
        }

        // 对 data 进行字典转换，返回转换后的对象（JSONObject、List<JSONObject>、IPage 包含 JSONObject 等）
        Object newData = fillData(data);

        // 替换 Result 的 data
        // 这里 unchecked cast 不影响，因为 data 泛型本身就灵活
        @SuppressWarnings("unchecked")
        Result<Object> newResult = (Result<Object>) result;
        newResult.setData(newData);
        return newResult;
    }

    /**
     * 处理具体 data 字段对象（单个、集合、分页）
     */
    private Object fillData(Object data) {
        if (data instanceof Collection<?>) {
            List<Object> newList = new ArrayList<>();
            for (Object item : (Collection<?>) data) {
                newList.add(fillSingleObject(item));
            }
            return newList;
        } else if (data instanceof IPage<?>) {
            IPage<?> page = (IPage<?>) data;
            List<Object> newRecords = new ArrayList<>();
            for (Object record : page.getRecords()) {
                newRecords.add(fillSingleObject(record));
            }
            // 注意：这里是给 MyBatis-Plus 分页对象设置新的 records
            ((IPage<Object>) page).setRecords(newRecords);
            return page;
        } else {
            return fillSingleObject(data);
        }
    }

    private Object fillSingleObject(Object obj) {
        if (obj == null) {
            return null;
        }

        // 基本类型、包装类型、String，不做处理，直接返回
        if (isPrimitiveOrWrapper(obj.getClass()) || obj instanceof String) {
            return obj;
        }

        // 如果是 Date，格式化后返回字符串
        if (obj instanceof Date) {
            return DateUtils.format((Date) obj);
        }

        // 如果是数组
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            JSONArray jsonArray = new JSONArray();
            for (int i = 0; i < length; i++) {
                Object element = Array.get(obj, i);
                Object processed = fillSingleObject(element);
                jsonArray.add(processed);
            }
            return jsonArray;
        }

        // 如果是集合
        if (obj instanceof Collection) {
            JSONArray jsonArray = new JSONArray();
            for (Object element : (Collection<?>) obj) {
                Object processed = fillSingleObject(element);
                jsonArray.add(processed);
            }
            return jsonArray;
        }

        // 如果是 Map，也可以递归处理
        if (obj instanceof Map) {
            JSONObject jsonObject = new JSONObject();
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                jsonObject.put(String.valueOf(key), fillSingleObject(value));
            }
            return jsonObject;
        }

        // 否则认为是普通对象
        JSONObject jsonObject;
        try {
            jsonObject = (JSONObject) JSONObject.toJSON(obj);
        } catch (Exception e) {
            return obj;
        }

        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                // 处理字典字段
                DictField dictField = field.getAnnotation(DictField.class);
                if (dictField != null) {
                    Object codeValue = field.get(obj);
                    if (codeValue != null) {
                        String text = commonDictMapper.selectDictText(
                                dictField.dictTable(),
                                dictField.dicText(),
                                dictField.dicCode(),
                                codeValue
                        );
                        if (text != null) {
                            jsonObject.put(field.getName() + "_dictText", text);
                        }
                    }
                }

                // 处理时间字段格式化
                if (isDateField(field)) {
                    Object dateValue = field.get(obj);
                    if (dateValue != null) {
                        String formatted = DateUtils.format((Date) dateValue);
                        jsonObject.put(field.getName(), formatted);
                    }
                }

                // 处理数组/集合/对象递归
                Object value = field.get(obj);
                if (value != null) {
                    Object processedValue = fillSingleObject(value);
                    jsonObject.put(field.getName(), processedValue);
                }

            } catch (Exception e) {
                log.error("字典查询异常。", e);
            }
        }

        return jsonObject;
    }

    /**
     * 判断是否是基本类型或包装类型
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == Boolean.class
                || clazz == Byte.class
                || clazz == Character.class
                || clazz == Short.class
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == Float.class
                || clazz == Double.class
                || clazz == Void.class;
    }




    private boolean isDateField(Field field) {
        return DATE_TYPES.contains(field.getType());
    }
}
