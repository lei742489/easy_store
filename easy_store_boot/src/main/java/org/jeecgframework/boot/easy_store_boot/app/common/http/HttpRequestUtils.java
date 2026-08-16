package org.jeecgframework.boot.easy_store_boot.app.common.http;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * http请求工具类
 */
@Component
@Slf4j
public class HttpRequestUtils {

    /**
     * 发送get请求
     * @param url
     * @param params
     * @return
     */
    public  String doGet(String url, JSONObject params) {
        try {
            // 拼接参数
            if (params != null && !params.isEmpty()) {
                StringBuilder paramStr = new StringBuilder();
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (paramStr.length() > 0) {
                        paramStr.append("&");
                    }
                    paramStr.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    paramStr.append("=");
                    paramStr.append(URLEncoder.encode(String.valueOf(entry.getValue()), "UTF-8"));
                }

                // 如果 URL 本身已有参数，用 &，否则用 ?
                if (url.contains("?")) {
                    url += "&" + paramStr;
                } else {
                    url += "?" + paramStr;
                }
            }

            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = HttpClientPool.getHttpClient().execute(httpGet)) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new AppRunTimeException("请求异常：" + e.getMessage());
        }
    }

    public static String doPost(String url, JSONObject params, Map<String, String> headers) {
        try{
            HttpPost httpPost = new HttpPost(url);

            // 设置自定义请求头（如果有）
            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    httpPost.setHeader(entry.getKey(), entry.getValue());
                }
            }else{
                // 设置 Content-Type 为 JSON（默认）
                httpPost.setHeader("Content-Type", "application/json");
            }
            // 设置请求体
            if (params != null && !params.isEmpty()) {
                StringEntity entity = new StringEntity(params.toJSONString(), StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
            }

            // 发送请求
            try (CloseableHttpResponse response = HttpClientPool.getHttpClient().execute(httpPost)) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new AppRunTimeException("请求异常：" + e.getMessage());
        }
    }

    public static String doPostText(String url, String bodyText, Map<String, String> headers) {
        try{
            HttpPost httpPost = new HttpPost(url);

            // 设置默认 Content-Type 为 text/plain
            httpPost.setHeader("Content-Type", "text/plain");

            // 设置自定义请求头
            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    httpPost.setHeader(entry.getKey(), entry.getValue());
                }
            }
            // 设置纯文本内容体
            if (bodyText != null && !bodyText.isEmpty()) {
                StringEntity entity = new StringEntity(bodyText, StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
            }

            // 发送请求
            try (CloseableHttpResponse response = HttpClientPool.getHttpClient().execute(httpPost)) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new AppRunTimeException("请求异常：" + e.getMessage());
        }
    }


    public static void main(String[] args) {

    }

    // 获取 URL 中的扩展名
    public  String getFileExtension(String url) {
        if (url != null && url.contains(".")) {
            return url.substring(url.lastIndexOf(".") + 1); // 提取最后一个 '.' 之后的字符串
        }
        return "";
    }

}
