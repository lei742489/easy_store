package org.jeecgframework.boot.easy_store_boot.app.config.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class ClientVersionInterceptor implements HandlerInterceptor {

    private static final String HEADER_DEV_TYPE = "devType";
    private static final String HEADER_APP_VERSION = "appVersion";

    @Value("${es-app.min-app-version:103}")
    private String minAppVersion;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String devType = trimToEmpty(request.getHeader(HEADER_DEV_TYPE));
        String appVersion = trimToEmpty(request.getHeader(HEADER_APP_VERSION));

        if (!StringUtils.hasText(devType) || !StringUtils.hasText(appVersion)) {
            return writeVersionError(response);
        }

        if ("app".equalsIgnoreCase(devType) && (!StringUtils.hasText(appVersion) || compareVersion(appVersion, minAppVersion) < 0)) {
            return writeVersionError(response);
        }

        return true;
    }

    private boolean writeVersionError(HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getWriter(), Result.error("客户端版本过低，请升级！"));
        return false;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private int compareVersion(String current, String required) {
        return Integer.compare(parseVersionNumber(current), parseVersionNumber(required));
    }

    private int parseVersionNumber(String version) {
        if (!StringUtils.hasText(version)) {
            return 0;
        }
        String digits = version.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            log.debug("Parse version number failed: {}", version);
            return 0;
        }
    }
}
