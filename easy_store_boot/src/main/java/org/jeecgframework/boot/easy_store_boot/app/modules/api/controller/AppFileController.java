package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller;

import com.alibaba.fastjson.JSONObject;

import org.jeecgframework.boot.easy_store_boot.app.common.DateUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.FileUtil;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;

@RequestMapping("api")
@Controller
public class AppFileController {

    @Value(value = "${es-app.path.upload}")
    private String uploadpath;

    private String[] uploadExtAllows = {"jpg", "jpeg", "png", "gif"};

    /**
     * 预览图片&下载文件
     *
     * @param request
     * @param response
     */
    @GetMapping(value = "upload/static/**")
    public void view(HttpServletRequest request, HttpServletResponse response) {
        // ISO-8859-1 ==> UTF-8 进行编码转换
        String imgPath = extractPathFromPattern(request);
        if(StringUtils.isEmpty(imgPath) || imgPath=="null"){
            return;
        }
        // 其余处理略
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            imgPath = imgPath.replace("..", "");
            if (imgPath.endsWith(",")) {
                imgPath = imgPath.substring(0, imgPath.length() - 1);
            }
            String filePath = uploadpath + File.separator + imgPath;

            File file = new File(filePath);
            if(!file.exists()){
                response.setStatus(404);
                throw new RuntimeException("文件不存在..");
            }

            String contentType = Files.probeContentType(file.toPath());

            if (contentType == null) {
                contentType = "application/octet-stream"; // 默认类型
            }
            response.setContentType(org.springframework.http.MediaType.valueOf(contentType).getType().toString());
            response.addHeader("Content-Length", String.valueOf(file.length()));
            setResponseCache(response);

            inputStream = new BufferedInputStream(new FileInputStream(filePath));
            outputStream = response.getOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            response.flushBuffer();
        } catch (IOException e) {
            response.setStatus(404);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {

                }
            }
        }

    }

    @GetMapping("/upload/pdf/**")
    public void viewPdf(HttpServletRequest request, HttpServletResponse response) {
        try {

            String fileName = extractPathFromPattern(request);

            // 若浏览器url进行了%编码，需解码
            fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());

            // 拼接到物理路径
            String filePath = uploadpath + File.separator + fileName;

            File file = new File(filePath);
            if(!file.exists()){
                response.setStatus(404);
                throw new RuntimeException("文件不存在..");
            }

            // 设置响应头，让浏览器在线预览，而不是下载
            response.setContentType("application/pdf");

            // 若希望下载，换成 attachment
            // response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode(pathInUrl, "UTF-8") + "\"");

            response.setHeader("Content-Disposition", "inline; filename=\"" +
                    new String(fileName.getBytes(StandardCharsets.UTF_8), "ISO-8859-1") + "\"");

            try (InputStream in = Files.newInputStream(file.toPath())) {
                StreamUtils.copy(in, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Error reading PDF file: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @PostMapping(value = "file/upload")
    @ResponseBody
    public Result<?> upload(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Result<String> result = new Result<>();
        String savePath = "";

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartRequest.getFile("file");// 获取上传文件对象

        String fileName = file.getOriginalFilename();
        String ext = FileUtil.getFileExtension(fileName);

        if(ext == null || !Arrays.asList(uploadExtAllows).contains(ext.toLowerCase()))
            throw new AppRunTimeException("不允许的上传文件类型");

        if(file.getSize() > 1024 *1024 * 5)
            throw new AppRunTimeException("文件不能超过5MB");

        String today = DateUtils.formatDate(new Date());
        String newFileName =  today+  '/' + System.currentTimeMillis() + "." + ext.toLowerCase();
        savePath =  "/upload/" + newFileName ;

        File saveFile = new File(uploadpath + savePath);
        if(!saveFile.getParentFile().exists())
            saveFile.getParentFile().mkdirs();

        //保存file到saveFile
        try {
            file.transferTo(saveFile);
        } catch (IOException e) {
            throw new AppRunTimeException("文件保存失败：" + e.getMessage());
        }

        result.setData(savePath);
        result.setSuccess(true);
        return result;
    }

    /**
     *  把指定URL后的字符串全部截断当成参数
     *  这么做是为了防止URL中包含中文或者特殊字符（/等）时，匹配不了的问题
     * @param request
     * @return
     */
    private static String extractPathFromPattern(final HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
    }

    private void setResponseCache(HttpServletResponse response){
        response.addHeader("Cache-Control", "public, max-age=31536000");
        response.addHeader("Expires", String.valueOf(System.currentTimeMillis() + 31536000000L));
    }

}
