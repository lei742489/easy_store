package org.jeecgframework.boot.easy_store_boot.app.common;


import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 公用工具类
 */
public class CommonUtils {

    private static  SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    public static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null; // 或返回空字符串根据需求
        }

        // 移除所有非数字字符
        String cleaned = phoneNumber.replaceAll("\\D+", "");

        // 验证是否为11位手机号
        if (cleaned.length() != 11) {
            return phoneNumber; // 非11位时返回原内容（或抛异常/自定义处理）
        }

        // 格式化为前3位 + &zwnj;****&zwnj; + 后4位
        return cleaned.substring(0, 3) + "****" + cleaned.substring(7);
    }

    //生成验证码
    public static String generateCaptcha(int limit) {
        StringBuilder captcha = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            // 生成0-9之间的随机数字
            int digit = new Random().nextInt(10);
            captcha.append(digit);
        }
        return captcha.toString();
    }

    public static String getString(String s, String defval) {
        if (StringUtils.isEmpty(s)) {
            return (defval);
        }
        return (s.trim());
    }

    /**
     * 内存分页
     * @param items
     * @param currentPage
     * @param pageSize
     * @return
     */
    public static <T> List<T> paginate(List<T> items, Integer currentPage, Integer pageSize) {
        if(currentPage == null || currentPage < 1) currentPage = 1;
        if(pageSize == null || pageSize < 1) pageSize = 10;

        if (items == null || items.isEmpty()) {
            return new ArrayList<>();  // 返回空列表
        }

        // 计算总页数
        int totalItems = items.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        // 处理当前页边界情况
        if (currentPage > totalPages) {
            currentPage = totalPages;  // 当前页超过总页数时，返回最后一页
        }
        if (currentPage < 1) {
            currentPage = 1;  // 当前页小于1时，返回第一页
        }

        // 计算分页的起始索引和结束索引
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        // 返回分页后的子列表
        return items.subList(fromIndex, toIndex);
    }

    /**
     * 生成订单号：年月日时分秒 + 随机数
     * @return 订单号
     */
    public static String generateOrderNumber() {
        // 获取当前时间

        String timeStamp = sdf.format(new Date());

        // 生成 5 位随机数
        Random random = new Random();
        int randomNum = 10000 + random.nextInt(90000); // 保证是 5 位数

        // 拼接订单号
        return timeStamp + randomNum;
    }



    public static String generateRandomString(int length) {
        String characters = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }

        return sb.toString();
    }

    /**
     * 生成随机字串
     * @param length
     * @return
     */
    public static String getRandomString2(int length){
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(3);
            long result=0;
            switch(number){
                case 0:
                    result=Math.round(Math.random()*25+65);
                    sb.append(String.valueOf((char)result));
                    break;
                case 1:
                    result=Math.round(Math.random()*25+97);
                    sb.append(String.valueOf((char)result));
                    break;
                case 2:
                    sb.append(String.valueOf(new Random().nextInt(10)));
                    break;
            }


        }
        return sb.toString();
    }

    /**
     * 将驼峰命名转化成下划线
     * @param para
     * @return
     */
    public static String camelToUnderline(String para){
        if(para.length()<3){
            return para.toLowerCase();
        }
        StringBuilder sb=new StringBuilder(para);
        int temp=0;//定位
        //从第三个字符开始 避免命名不规范
        for(int i=2;i<para.length();i++){
            if(Character.isUpperCase(para.charAt(i))){
                sb.insert(i+temp, "_");
                temp+=1;
            }
        }
        return sb.toString().toLowerCase();
    }


    /**
     * 获取类的所有属性，包括父类
     *
     * @param object
     * @return
     */
    public static Field[] getAllFields(Object object) {
        Class<?> clazz = object.getClass();
        List<Field> fieldList = new ArrayList<>();
        while (clazz != null) {
            fieldList.addAll(new ArrayList<>(Arrays.asList(clazz.getDeclaredFields())));
            clazz = clazz.getSuperclass();
        }
        Field[] fields = new Field[fieldList.size()];
        fieldList.toArray(fields);
        return fields;
    }

    public static void main(String[] args) {
        System.err.println(camelToUnderline("createTime"));
    }

}
