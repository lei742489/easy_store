package org.jeecgframework.boot.easy_store_boot.app.common;

import java.io.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiModel;

import org.jeecgframework.boot.easy_store_boot.app.common.anno.Required;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.*;

/**
 * 解析实体类，生成前端vue3的基础页面，注：实体类需要有@FiledLabel注解
 */
public class EntityParser {

    private static String baseSavePath = "d:/vueParse/";

    public static JSONObject parseEntity(Class<?> clazz) {
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();

        Field[] fields = clazz.getDeclaredFields();
        // 获取类名（不含包名）
        String className = clazz.getSimpleName();

        ApiModel apiModel = clazz.getAnnotation(ApiModel.class);
        result.put("title", apiModel.description());

        for (Field field : fields) {
            ApiModelProperty labelAnno = field.getAnnotation(ApiModelProperty.class);
            String label = labelAnno != null ? labelAnno.value() : "";
            if (label == null || label.trim().isEmpty()) {
                continue;
            }

            JSONObject fieldObj = new JSONObject();
            fieldObj.put("name", field.getName());
            fieldObj.put("type", field.getType().getSimpleName());
            fieldObj.put("label", label);

            // 是否必填
            Required requiredAnno = field.getAnnotation(Required.class);
            boolean required = requiredAnno != null && requiredAnno.value();
            fieldObj.put("required", required);

            array.add(fieldObj);
        }

        result.put("fields", array);
        result.put("className", className);

        return result;
    }

    /**
     * 生成ts文件
     * @param parsed
     * @return
     */
    public static void generateTsInterface(JSONObject parsed) {
        StringBuilder sb = new StringBuilder();

        String className = parsed.getString("className");
        JSONArray fields = parsed.getJSONArray("fields");

        // 接口名，首字母大写
        String interfaceName = className.substring(0, 1).toUpperCase() + className.substring(1);

        sb.append("export interface ").append(interfaceName).append(" {\n");

        for (int i = 0; i < fields.size(); i++) {
            JSONObject field = fields.getJSONObject(i);
            String name = field.getString("name");
            String type = javaTypeToTs(field.getString("type"));
            String label = field.getString("label");

            // 添加注释
            sb.append("  /** ").append(label).append(" */\n");
            // 字段定义
            sb.append("  ").append(name).append("?: ").append(type).append(";\n");
        }

        sb.append("}");

        saveToFile(sb.toString(), className + "/types/" + className + ".ts");
    }


    public static String javaTypeToTs(String javaType) {
        switch (javaType) {
            case "String":
                return "string";
            case "Integer":
            case "int":
            case "Long":
            case "long":
            case "Double":
            case "double":
            case "Float":
            case "float":
                return "number";
            case "Boolean":
            case "boolean":
                return "boolean";
            case "Date":
            case "LocalDate":
            case "LocalDateTime":
                return "string | Date";
            default:
                return "any"; // 默认类型
        }
    }

    public static void saveToFile(String content, String filePath) {
        filePath = baseSavePath + filePath;
        File file = new File(filePath);
        if(!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
            System.out.println("TypeScript interface 已保存到文件：" + filePath);
        } catch (IOException e) {
            System.err.println("保存文件出错：" + e.getMessage());
        }
    }

    public static void generateVuePage(JSONObject parsed,String templateFile,String extPath) throws Exception {
        // 1. FreeMarker 配置
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(Thread.currentThread().getContextClassLoader(), "ftl/vue3");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // 2. 构造数据模型
        Map<String, Object> dataModel = new HashMap<>();
        String className = parsed.getString("className");
        dataModel.put("className", className);
        String lowerCamel = className.substring(0, 1).toLowerCase() + className.substring(1);
        dataModel.put("lowerName", lowerCamel);
        dataModel.put("fields", parsed.getJSONArray("fields"));
        dataModel.put("title", parsed.getString("title"));

        // 3. 加载模板
        Template template = cfg.getTemplate(templateFile + ".ftl");

        // 4. 输出文件路径（比如 ./output/AppCustomerList.vue）
        String fileName = templateFile + ".vue";
        if(templateFile.startsWith("api")){
            fileName = "api-" + parsed.getString("className") + ".ts";
        }
        File outFile = new File(baseSavePath + className + "/" + extPath, fileName);
        if(!outFile.getParentFile().exists())
            outFile.getParentFile().mkdirs();

        try (Writer out = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8")) {
            template.process(dataModel, out);
        }

        System.out.println("✅ Vue 页面生成成功：" + outFile.getAbsolutePath());
    }


    public static void main(String[] args) throws Exception {
        JSONObject parsed = parseEntity(AppPaymentSettleItem.class);
        System.err.println(parsed);
          generateTsInterface(parsed);
        generateVuePage(parsed,"list","");
        generateVuePage(parsed,"modal","components");
        generateVuePage(parsed,"api","api");


    }
}
