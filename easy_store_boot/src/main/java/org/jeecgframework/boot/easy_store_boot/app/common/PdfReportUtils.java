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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfReportUtils {

    private static final Configuration cfg;

    public static String SIMSUN_FONT_PATH = null;
    private static final String TTC_FONT_INDEX = ",0";

    static {
        cfg = new Configuration(Configuration.VERSION_2_3_28);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassLoaderForTemplateLoading(
                PdfReportUtils.class.getClassLoader(), "ftl/report"
        );
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        try {
            SIMSUN_FONT_PATH = loadFontFromResource("fonts/simsun.ttc");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("宋体字体全局加载成功：" + SIMSUN_FONT_PATH);
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

            try {
                // 从 resources 加载宋体
                renderer.getFontResolver().addFont(
                        getITextFontPath(SIMSUN_FONT_PATH),
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED
                );

                // System.out.println("宋体字体加载成功：" + SIMSUN_FONT_PATH);

            } catch (Exception e) {
                System.err.println("宋体加载失败：" + e.getMessage());
            }

            // 替换 HTML 中的 font-family
            // 如果 fontFamily 不为空，就动态把 htmlContent 替换成对应字体
            htmlContent = htmlContent.replaceAll("font-family:[^;\"']*([;\"'])", "font-family: " + "SimSun" + "$1");

            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(os);
        }
    }

    private static String getITextFontPath(String fontPath) {
        if (fontPath == null) {
            return null;
        }
        return fontPath.toLowerCase().endsWith(".ttc") ? fontPath + TTC_FONT_INDEX : fontPath;
    }

    private static String loadFontFromResource(String resourcePath) throws IOException {
        InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath);

        if (is == null) {
            throw new FileNotFoundException("字体文件不存在: " + resourcePath);
        }

        File tempFont = File.createTempFile("font-", ".ttc");
        tempFont.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFont)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
        return tempFont.getAbsolutePath();
    }
}
