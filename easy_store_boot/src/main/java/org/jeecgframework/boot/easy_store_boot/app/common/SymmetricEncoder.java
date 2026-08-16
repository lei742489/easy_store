package org.jeecgframework.boot.easy_store_boot.app.common;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author lei
 * 数据加、解密工具类
 *
 */
public class SymmetricEncoder {

    private static String sKey = "2e69653f9e7376d0";

    private static String userSKey = "779a193e102f5c4a";

    // 加密
    public static String Encrypt(String sSrc,String key) throws Exception {
        if(key == null) key = sKey;

        if (key == null) {
            System.out.print("Key为空null");
            return null;
        }
        // 判断Key是否为16位
        if (key.length() != 16) {
            System.out.print("Key长度不是16位");
            return null;
        }
        byte[] raw = key.getBytes("utf-8");
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");//"算法/模式/补码方式"
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(sSrc.getBytes("utf-8"));


        return new Base64().encodeToString(encrypted);//此处使用BASE64做转码功能，同时能起到2次加密的作用。
    }

    // 解密
    public static String Decrypt(String sSrc,String key)  {
        if(key == null) key = sKey;
        try {
            // 判断Key是否正确
            if (key == null) {
                System.out.print("Key为空null");
                return null;
            }
            // 判断Key是否为16位
            if (key.length() != 16) {
                System.out.print("Key长度不是16位");
                return null;
            }
            byte[] raw = key.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted1 = new Base64().decode(sSrc);//先用base64解密
            try {
                byte[] original = cipher.doFinal(encrypted1);
                String originalString = new String(original,"utf-8");
                return originalString;
            } catch (Exception e) {
                System.out.println(e.toString());
                return null;
            }
        } catch (Exception ex) {
            System.out.println(ex.toString());
            return null;
        }
    }

    public static void main(String[] args) {
       // System.err.println(createUserToken("1923251386338824193"));
        System.err.println(createUserToken("10000"));
    }



    /**
     * token加密
     * @return
     */
    public static String createUserToken(String userId)  {
        if(StringUtils.isEmpty(userId)) return "";
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userId", userId);
        jsonObject.put("timestamp", System.currentTimeMillis());
        try {
            return Encrypt(jsonObject.toJSONString(), userSKey);
        }catch (Exception e){
            throw new AppRunTimeException("参数加密错误：" + e.getMessage());
        }

    }

    /**
     * token解密
     */
    public static String tokenToUserId(String token) throws Exception {
         if(StringUtils.isEmpty(token))
             return null;
        String jsonStr = Decrypt(token, userSKey);
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        return jsonObject.getString("userId");
    }

}

