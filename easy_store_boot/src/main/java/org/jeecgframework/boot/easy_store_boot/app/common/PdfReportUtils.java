package org.jeecgframework.boot.easy_store_boot.app.common;


import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfReportUtils {

    private static final Configuration cfg;

    static {
        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassLoaderForTemplateLoading(
                PdfReportUtils.class.getClassLoader(), "ftl/report"
        );
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /**
     * 生成HTML字符串
     *
     * @param templateName 模板名（如 "test.ftl"）
     * @param data         数据模型
     * @return HTML 字符串
     */
    public static String generateHtml(String templateName, Object data) throws Exception {
        Template template = cfg.getTemplate(templateName);
        StringWriter out = new StringWriter();
        template.process(data, out);
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("index", i);
            row.put("productName", i + "号国际网线");
            row.put("unit", "米");
            row.put("qty", 100 + i);
            row.put("price", "￥0.5");
            row.put("amount", "￥" + (0.5 * (100 + i)));
            row.put("remark", "张哥预订");
            rows.add(row);
        }
        data.put("rows", rows);

        int maxCount = 12;

        String html = generateHtml("PurchaseOrder.ftl", data);
        generatePdf(html,"d:/test.pdf");
    }

    /**
     * 将 HTML 内容生成 PDF，并保存到指定路径
     *
     * @param htmlContent      HTML 字符串
     * @param outputPdfPath PDF 文件保存路径，如 "/tmp/test.pdf"
     */
    public static void generatePdf(String htmlContent, String outputPdfPath) throws Exception {
        File file = new File(outputPdfPath);
        if(!file.getParentFile().exists())
            file.getParentFile().mkdirs();
        try (OutputStream os = Files.newOutputStream(Paths.get(outputPdfPath))) {
            ITextRenderer renderer = new ITextRenderer();

            String osName = System.getProperty("os.name").toLowerCase();
            String fontPath = null;
            String fontFamily = null;

            if (osName.contains("windows")) {
                // Windows -> 微软雅黑
                fontPath = "C:/Windows/Fonts/simsun.ttc";
                fontFamily = "SimSun";
            } else if (osName.contains("linux")) {
                // Linux 常见中文字体路径示例
                // 你也可以上传你自己的 ttf 文件到服务器
                fontPath = "/usr/share/fonts/truetype/arphic/ukai.ttc";
                fontFamily = "AR PL UKai CN";
            } else if (osName.contains("mac")) {
                // macOS 示例
                fontPath = "/System/Library/Fonts/STHeiti Medium.ttc";
                fontFamily = "Heiti SC";
            }

            // 如果存在字体路径，就注册
            if (fontPath != null) {
                try {
                    renderer.getFontResolver().addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    System.out.println("注册中文字体：" + fontPath);
                } catch (Exception e) {
                    System.err.println("字体注册失败：" + fontPath + "，原因：" + e.getMessage());
                }
            }

            // 替换 HTML 中的 font-family
            // 如果 fontFamily 不为空，就动态把 htmlContent 替换成对应字体
            if (fontFamily != null) {
                htmlContent = htmlContent.replaceAll("font-family:[^;\"']*([;\"'])", "font-family: " + fontFamily + "$1");
            }

            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(os);
        }
    }
}
