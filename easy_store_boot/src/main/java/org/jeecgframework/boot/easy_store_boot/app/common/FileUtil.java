package org.jeecgframework.boot.easy_store_boot.app.common;

import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import java.io.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtil {

    /**
     * 从 Base64 编码字符串中提取文件扩展名，并生成文件名
     *
     * @param base64Str Base64 编码的图片字符串
     * @return 生成的文件名（如 example.png）
     */
    private static   String generateFileNameFromBase64(String base64Str) {
        // 匹配 Base64 编码前缀的文件类型，如 "data:image/png;base64,"
        Pattern pattern = Pattern.compile("data:image/(.*?);base64,");
        Matcher matcher = pattern.matcher(base64Str);

        if (matcher.find()) {
            String fileType = matcher.group(1); // 获取文件类型部分，如 "png"
            return "image_" + System.currentTimeMillis() + "." + fileType; // 生成文件名，带扩展名
        }

        // 如果无法提取文件类型，返回一个默认文件名
        return "image_" + System.currentTimeMillis() + ".jpg"; // 默认使用 jpg 扩展名
    }

    // 保存内容到指定路径的 TXT 文件
    public static  void saveToFile(String filePath, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            throw new AppRunTimeException("写入文件时出错: " + e.getMessage());
        }
    }

    // 从指定路径读取 TXT 文件的内容
    public static  String readFromFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            System.err.println("读取txt文件时出错: " + e.getMessage());
            return "";
        }
        return content.toString();
    }

    /**
     * 获取文件扩展名
     * @param filePath
     * @return
     */
    public static  String getFileExtension(String filePath) {
        // 检查最后一个 '.' 是否存在
        int lastIndex = filePath.lastIndexOf('.');
        if (lastIndex == -1 || lastIndex == filePath.length() - 1) {
            return ""; // 没有扩展名
        }
        return filePath.substring(lastIndex + 1); // 提取扩展名
    }



}
